package com.scf.saga.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.account.service.AccountMarginFreezeService;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository;
import com.scf.agencypurchase.service.AgencyPurchaseOrderRollbackService;
import com.scf.common.exception.BusinessException;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.support.CompensationTypes;
import com.scf.warehouse.service.WarehouseInventoryCompensationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AgencyPurchaseCompensationHandler {

    static final String TYPE_MARGIN_UNFREEZE = CompensationTypes.MARGIN_UNFREEZE;
    static final String TYPE_INVENTORY_UNFREEZE = CompensationTypes.INVENTORY_UNFREEZE;
    static final String TYPE_ORDER_ROLLBACK = CompensationTypes.ORDER_ROLLBACK;

    private final ApAgencyPurchaseApplicationRepository applicationRepository;
    private final AccountMarginFreezeService marginFreezeService;
    private final WarehouseInventoryCompensationService inventoryCompensationService;
    private final AgencyPurchaseOrderRollbackService orderRollbackService;
    private final ObjectMapper objectMapper;

    public AgencyPurchaseCompensationHandler(
            ApAgencyPurchaseApplicationRepository applicationRepository,
            AccountMarginFreezeService marginFreezeService,
            WarehouseInventoryCompensationService inventoryCompensationService,
            AgencyPurchaseOrderRollbackService orderRollbackService,
            ObjectMapper objectMapper) {
        this.applicationRepository = applicationRepository;
        this.marginFreezeService = marginFreezeService;
        this.inventoryCompensationService = inventoryCompensationService;
        this.orderRollbackService = orderRollbackService;
        this.objectMapper = objectMapper;
    }

    public void execute(BizCompensationTask task) {
        if (!"AGENCY_PURCHASE".equals(task.getBusinessType())) {
            throw new BusinessException("SAGA_400", "不支持的补偿业务类型: " + task.getBusinessType(), 400);
        }
        ApAgencyPurchaseApplication app = applicationRepository
                .findById(task.getBusinessId())
                .orElseThrow(() -> new BusinessException("DATA_404", "代采申请不存在", 404));
        if (TYPE_MARGIN_UNFREEZE.equals(task.getCompensationType())) {
            executeMarginUnfreeze(task, app);
            return;
        }
        if (TYPE_INVENTORY_UNFREEZE.equals(task.getCompensationType())) {
            executeInventoryUnfreeze(task, app);
            return;
        }
        if (TYPE_ORDER_ROLLBACK.equals(task.getCompensationType())) {
            executeOrderRollback(task, app);
            return;
        }
        throw new BusinessException("SAGA_400", "不支持的补偿类型: " + task.getCompensationType(), 400);
    }

    private void executeOrderRollback(BizCompensationTask task, ApAgencyPurchaseApplication app) {
        JsonNode payload = parsePayload(task.getActionJson());
        String orderId = payload.hasNonNull("order_id") ? payload.get("order_id").asText() : app.getOrderId();
        if (orderId == null || orderId.isBlank()) {
            throw new BusinessException("VALID_400", "补偿参数缺失: order_id", 400);
        }
        orderRollbackService.rollbackConfirmedOrder(app, orderId);
    }

    private void executeMarginUnfreeze(BizCompensationTask task, ApAgencyPurchaseApplication app) {
        JsonNode payload = parsePayload(task.getActionJson());
        String accountId = text(payload, "account_id");
        BigDecimal amount = decimal(payload, "amount");
        marginFreezeService.unfreezeMargin(accountId, amount, "AGENCY_PURCHASE", app.getId());
    }

    private void executeInventoryUnfreeze(BizCompensationTask task, ApAgencyPurchaseApplication app) {
        JsonNode payload = parsePayload(task.getActionJson());
        String inventoryId = text(payload, "inventory_id");
        BigDecimal quantity = decimal(payload, "quantity");
        inventoryCompensationService.unfreezeInventory(
                inventoryId, app.getOperatorId(), app.getProjectId(), quantity,
                "AGENCY_PURCHASE", app.getId());
    }

    private JsonNode parsePayload(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid compensation action_json", ex);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new BusinessException("VALID_400", "补偿参数缺失: " + field, 400);
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(text(node, field));
    }
}
