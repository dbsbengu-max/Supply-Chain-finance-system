package com.scf.agencypurchase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.account.service.AccountMarginFreezeService;
import com.scf.agencypurchase.dto.AgencyPurchaseApprovedPayload;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.agencypurchase.entity.ApAgencyPurchaseSagaStep;
import com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository;
import com.scf.agencypurchase.repository.ApAgencyPurchaseSagaStepRepository;
import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.util.IdGenerator;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.finance.service.FinanceApplicationService;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.service.CompensationTaskService;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.repository.TrOrderRepository;
import com.scf.warehouse.InventoryRightStatus;
import com.scf.warehouse.entity.WhInventory;
import com.scf.warehouse.repository.WhInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AgencyPurchaseSagaService {

    private static final Logger log = LoggerFactory.getLogger(AgencyPurchaseSagaService.class);

    static final String STEP_ORDER_CONFIRM = "ORDER_CONFIRM";
    static final String STEP_MARGIN_FREEZE = "MARGIN_FREEZE";
    static final String STEP_INVENTORY_FREEZE = "INVENTORY_FREEZE";
    static final String STEP_FINANCE_CREATE = "FINANCE_CREATE";

    private static final Set<String> FREEZE_ALLOWED_STATUS = Set.of(
            InventoryRightStatus.IN_STOCK, InventoryRightStatus.RELEASED);

    private static final BigDecimal DEFAULT_MARGIN_RATE = new BigDecimal("0.10");

    private final ApAgencyPurchaseApplicationRepository applicationRepository;
    private final ApAgencyPurchaseSagaStepRepository sagaStepRepository;
    private final TrOrderRepository orderRepository;
    private final WhInventoryRepository inventoryRepository;
    private final AccountMarginFreezeService marginFreezeService;
    private final ObjectProvider<FinanceApplicationService> financeApplicationServiceProvider;
    private final FnFinanceApplicationRepository financeRepository;
    private final CompensationTaskService compensationTaskService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AgencyPurchaseSagaService(
            ApAgencyPurchaseApplicationRepository applicationRepository,
            ApAgencyPurchaseSagaStepRepository sagaStepRepository,
            TrOrderRepository orderRepository,
            WhInventoryRepository inventoryRepository,
            AccountMarginFreezeService marginFreezeService,
            ObjectProvider<FinanceApplicationService> financeApplicationServiceProvider,
            FnFinanceApplicationRepository financeRepository,
            CompensationTaskService compensationTaskService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.sagaStepRepository = sagaStepRepository;
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.marginFreezeService = marginFreezeService;
        this.financeApplicationServiceProvider = financeApplicationServiceProvider;
        this.financeRepository = financeRepository;
        this.compensationTaskService = compensationTaskService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void handleAgencyPurchaseApproved(BizEventOutbox event) {
        AgencyPurchaseApprovedPayload payload = parsePayload(event.getPayloadJson());
        ApAgencyPurchaseApplication app = applicationRepository.findByIdForUpdate(payload.applicationId())
                .orElseThrow(() -> new BusinessException("DATA_404", "代采申请不存在", 404));
        if (!"APPROVED".equals(app.getApplicationStatus())) {
            throw new BusinessException("STATE_409", "代采申请未处于已通过状态", 409);
        }
        if ("SUCCESS".equals(app.getSagaStatus())) {
            log.info("Agency purchase saga already succeeded for {}", app.getId());
            return;
        }
        app.setSagaStatus("RUNNING");
        app.setSagaLastError(null);
        applicationRepository.save(app);

        List<String> completedSteps = new ArrayList<>();
        try {
            if (hasText(app.getOrderId())) {
                runStep(app, event, STEP_ORDER_CONFIRM, completedSteps, () -> confirmOrder(app));
            } else {
                skipStep(app, STEP_ORDER_CONFIRM, "无关联订单");
            }
            if ("SELF_FUNDED".equals(app.getFundSource())) {
                runStep(app, event, STEP_MARGIN_FREEZE, completedSteps, () -> freezeMargin(app));
            } else {
                skipStep(app, STEP_MARGIN_FREEZE, "非自有资金模式跳过保证金");
            }
            if ("STOCK_PREPARE".equals(app.getOrderMode()) && hasText(app.getInventoryId())) {
                runStep(app, event, STEP_INVENTORY_FREEZE, completedSteps, () -> freezeInventory(app));
            } else {
                skipStep(app, STEP_INVENTORY_FREEZE, "非备货模式或无库存 ID");
            }
            if ("THIRD_PARTY_FUNDED".equals(app.getFundSource())) {
                runStep(app, event, STEP_FINANCE_CREATE, completedSteps, () -> createFinance(app));
            } else {
                skipStep(app, STEP_FINANCE_CREATE, "非第三方资金模式跳过融资");
            }
            app.setSagaStatus("SUCCESS");
            app.setSagaLastError(null);
            applicationRepository.save(app);
            auditLogService.logAsSystem(
                    "system", app.getOperatorId(), app.getProjectId(), null,
                    "AGENCY_PURCHASE_SAGA_SUCCESS", "AGENCY_PURCHASE", app.getId(),
                    Map.of("saga_status", "RUNNING"),
                    Map.of("saga_status", "SUCCESS"));
        } catch (Exception ex) {
            app.setSagaStatus("FAILED");
            app.setSagaLastError(ex.getMessage());
            applicationRepository.save(app);
            enqueueCompensations(event, app, completedSteps, ex.getMessage());
        }
    }

    private void runStep(
            ApAgencyPurchaseApplication app,
            BizEventOutbox event,
            String stepCode,
            List<String> completedSteps,
            Runnable action) {
        ApAgencyPurchaseSagaStep existing = sagaStepRepository
                .findByApplicationIdAndStepCode(app.getId(), stepCode)
                .orElse(null);
        if (existing != null && "SUCCESS".equals(existing.getStepStatus())) {
            completedSteps.add(stepCode);
            return;
        }
        try {
            action.run();
            recordStep(app.getId(), stepCode, "SUCCESS", null);
            completedSteps.add(stepCode);
        } catch (Exception ex) {
            recordStep(app.getId(), stepCode, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private void skipStep(ApAgencyPurchaseApplication app, String stepCode, String reason) {
        ApAgencyPurchaseSagaStep existing = sagaStepRepository
                .findByApplicationIdAndStepCode(app.getId(), stepCode)
                .orElse(null);
        if (existing != null && ("SUCCESS".equals(existing.getStepStatus()) || "SKIPPED".equals(existing.getStepStatus()))) {
            return;
        }
        recordStep(app.getId(), stepCode, "SKIPPED", reason);
    }

    private void confirmOrder(ApAgencyPurchaseApplication app) {
        TrOrder order = orderRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        app.getOrderId(), app.getOperatorId(), app.getProjectId(), (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联订单不存在", 404));
        if ("CONFIRMED".equals(order.getOrderStatus())) {
            return;
        }
        if (!"SUBMITTED".equals(order.getOrderStatus())) {
            throw new BusinessException("STATE_409", "订单状态不允许 Saga 确认: " + order.getOrderStatus(), 409);
        }
        order.setOrderStatus("CONFIRMED");
        order.setSignedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditLogService.logAsSystem(
                "system", app.getOperatorId(), app.getProjectId(), null,
                "ORDER_CONFIRM", "TRADE_ORDER", order.getId(), null,
                Map.of("order_status", "CONFIRMED", "source", "AGENCY_PURCHASE_SAGA"));
    }

    private void freezeMargin(ApAgencyPurchaseApplication app) {
        if (!hasText(app.getMarginAccountId())) {
            throw new BusinessException("VALID_400", "自有资金模式需指定 margin_account_id", 400);
        }
        BigDecimal amount = app.getMarginAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = app.getTotalAmount().multiply(DEFAULT_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP);
            app.setMarginAmount(amount);
        }
        if (app.getMarginFrozenAmount() != null && app.getMarginFrozenAmount().compareTo(amount) >= 0) {
            return;
        }
        BigDecimal frozen = marginFreezeService.freezeMargin(
                app.getMarginAccountId(), amount, "AGENCY_PURCHASE", app.getId());
        app.setMarginFrozenAmount(frozen);
        applicationRepository.save(app);
    }

    private void freezeInventory(ApAgencyPurchaseApplication app) {
        BigDecimal qty = app.getInventoryFreezeQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            qty = BigDecimal.ONE;
            app.setInventoryFreezeQuantity(qty);
        }
        WhInventory inventory = inventoryRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        app.getInventoryId(), app.getOperatorId(), app.getProjectId(), (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "库存不存在", 404));
        if (inventory.getStocktakeException() == 1) {
            throw new BusinessException("INVENTORY_400", "库存处于盘点异常状态", 400);
        }
        if (!FREEZE_ALLOWED_STATUS.contains(inventory.getRightStatus())
                && inventory.getFrozenQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "当前库存状态不允许冻结", 400);
        }
        if (inventory.getAvailableQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "可用数量不足", 400);
        }
        inventory.setAvailableQuantity(inventory.getAvailableQuantity().subtract(qty));
        inventory.setFrozenQuantity(inventory.getFrozenQuantity().add(qty));
        if (!InventoryRightStatus.FROZEN.equals(inventory.getRightStatus())) {
            inventory.setRightStatus(InventoryRightStatus.FROZEN);
        }
        inventory.setUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
        auditLogService.logAsSystem(
                "system", app.getOperatorId(), app.getProjectId(), null,
                "WAREHOUSE_FREEZE", "INVENTORY", inventory.getId(), null,
                Map.of("quantity", qty, "source", "AGENCY_PURCHASE_SAGA"));
    }

    private void createFinance(ApAgencyPurchaseApplication app) {
        if (hasText(app.getFinanceApplicationId())) {
            return;
        }
        List<FnFinanceApplication> existing = financeRepository
                .findByOperatorIdAndProjectIdAndSourceTypeAndSourceIdAndDeletedFlagOrderByCreatedAtDesc(
                        app.getOperatorId(), app.getProjectId(), "AGENCY_PURCHASE", app.getId(), (short) 0);
        if (!existing.isEmpty()) {
            app.setFinanceApplicationId(existing.get(0).getId());
            applicationRepository.save(app);
            return;
        }
        String financeId = financeApplicationServiceProvider.getObject().createFromAgencyPurchase(app);
        app.setFinanceApplicationId(financeId);
        applicationRepository.save(app);
    }

    private void enqueueCompensations(
            BizEventOutbox event,
            ApAgencyPurchaseApplication app,
            List<String> completedSteps,
            String error) {
        for (String step : completedSteps) {
            if (STEP_MARGIN_FREEZE.equals(step) && app.getMarginFrozenAmount() != null) {
                compensationTaskService.enqueue(
                        event,
                        "MARGIN_UNFREEZE",
                        "AGENCY_PURCHASE",
                        app.getId(),
                        "{\"account_id\":\"" + app.getMarginAccountId()
                                + "\",\"amount\":\"" + app.getMarginFrozenAmount().toPlainString()
                                + "\",\"reason\":\"" + escape(error) + "\"}");
            }
            if (STEP_INVENTORY_FREEZE.equals(step)) {
                compensationTaskService.enqueue(
                        event,
                        "INVENTORY_UNFREEZE",
                        "AGENCY_PURCHASE",
                        app.getId(),
                        "{\"inventory_id\":\"" + app.getInventoryId()
                                + "\",\"quantity\":\"" + qty(app.getInventoryFreezeQuantity())
                                + "\",\"reason\":\"" + escape(error) + "\"}");
            }
        }
    }

    private void recordStep(String applicationId, String stepCode, String status, String detail) {
        ApAgencyPurchaseSagaStep step = sagaStepRepository
                .findByApplicationIdAndStepCode(applicationId, stepCode)
                .orElseGet(() -> {
                    ApAgencyPurchaseSagaStep created = new ApAgencyPurchaseSagaStep();
                    created.setId(IdGenerator.nextId());
                    created.setApplicationId(applicationId);
                    created.setStepCode(stepCode);
                    created.setCreatedAt(Instant.now());
                    return created;
                });
        step.setStepStatus(status);
        step.setDetailJson(detail);
        step.setExecutedAt(Instant.now());
        sagaStepRepository.save(step);
    }

    private AgencyPurchaseApprovedPayload parsePayload(String json) {
        try {
            return objectMapper.readValue(json, AgencyPurchaseApprovedPayload.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid agency purchase approved payload", ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String qty(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "'");
    }
}
