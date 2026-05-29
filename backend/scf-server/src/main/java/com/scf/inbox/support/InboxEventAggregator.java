package com.scf.inbox.support;

import com.scf.bpm.entity.BpmTask;
import com.scf.bpm.repository.BpmTaskRepository;
import com.scf.clearing.entity.ClearingRule;
import com.scf.clearing.repository.ClearingRuleRepository;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.UserContext;
import com.scf.finance.entity.FnDisbursement;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.FnDisbursementRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.iam.service.PermissionService;
import com.scf.inbox.dto.InboxDtos.InboxEventView;
import com.scf.risk.entity.BiRiskAlertTicket;
import com.scf.risk.repository.BiRiskAlertTicketRepository;
import com.scf.risk.service.RiskAlertCenterService;
import com.scf.warehouse.entity.WhInventory;
import com.scf.warehouse.repository.WhInventoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class InboxEventAggregator {

    private static final int SOURCE_LIMIT = 50;
    private static final Set<String> OPEN_RISK_STATUSES = Set.of("OPEN", "ACK", "PROCESSING");

    private final PermissionService permissionService;
    private final DataScopeHelper dataScopeHelper;
    private final BpmTaskRepository bpmTaskRepository;
    private final BiRiskAlertTicketRepository riskAlertTicketRepository;
    private final RiskAlertCenterService riskAlertCenterService;
    private final ClearingRuleRepository clearingRuleRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final FnDisbursementRepository disbursementRepository;
    private final WhInventoryRepository inventoryRepository;

    public InboxEventAggregator(
            PermissionService permissionService,
            DataScopeHelper dataScopeHelper,
            BpmTaskRepository bpmTaskRepository,
            BiRiskAlertTicketRepository riskAlertTicketRepository,
            RiskAlertCenterService riskAlertCenterService,
            ClearingRuleRepository clearingRuleRepository,
            FnFinanceApplicationRepository financeRepository,
            FnDisbursementRepository disbursementRepository,
            WhInventoryRepository inventoryRepository) {
        this.permissionService = permissionService;
        this.dataScopeHelper = dataScopeHelper;
        this.bpmTaskRepository = bpmTaskRepository;
        this.riskAlertTicketRepository = riskAlertTicketRepository;
        this.riskAlertCenterService = riskAlertCenterService;
        this.clearingRuleRepository = clearingRuleRepository;
        this.financeRepository = financeRepository;
        this.disbursementRepository = disbursementRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public List<InboxEventView> collect(String operatorId, String projectId, UserContext user, String sourceFilter) {
        List<InboxEventView> events = new ArrayList<>();
        if (sourceFilter == null || sourceFilter.isBlank() || "BPM".equalsIgnoreCase(sourceFilter)) {
            events.addAll(collectBpm(user));
        }
        if (sourceFilter == null || sourceFilter.isBlank() || "RISK".equalsIgnoreCase(sourceFilter)) {
            events.addAll(collectRisk(operatorId, projectId, user));
        }
        if (sourceFilter == null || sourceFilter.isBlank() || "CLEARING".equalsIgnoreCase(sourceFilter)) {
            events.addAll(collectClearing(operatorId, projectId, user));
        }
        if (sourceFilter == null || sourceFilter.isBlank() || "DISBURSE".equalsIgnoreCase(sourceFilter)) {
            events.addAll(collectDisburse(operatorId, projectId, user));
        }
        if (sourceFilter == null || sourceFilter.isBlank() || "WAREHOUSE".equalsIgnoreCase(sourceFilter)) {
            events.addAll(collectWarehouse(operatorId, projectId, user));
        }

        events.sort(Comparator
                .comparingInt((InboxEventView event) -> severityRank(event.severity()))
                .thenComparing(InboxEventView::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return events;
    }

    private List<InboxEventView> collectBpm(UserContext user) {
        if (!permissionService.hasPermission(user, "BPM_TASK_VIEW")) {
            return List.of();
        }
        return bpmTaskRepository.findByAssigneeIdAndApprovalStatus(user.userId(), "PENDING").stream()
                .limit(SOURCE_LIMIT)
                .map(this::toBpmEvent)
                .toList();
    }

    private List<InboxEventView> collectRisk(String operatorId, String projectId, UserContext user) {
        if (!permissionService.hasPermission(user, "RISK_ALERT_VIEW")) {
            return List.of();
        }
        riskAlertCenterService.syncForInbox(operatorId, projectId, user);
        return riskAlertTicketRepository
                .findFiltered(operatorId, projectId, null, null, null, null, PageRequest.of(0, SOURCE_LIMIT))
                .stream()
                .filter(ticket -> OPEN_RISK_STATUSES.contains(ticket.getHandleStatus()))
                .map(this::toRiskEvent)
                .toList();
    }

    private List<InboxEventView> collectClearing(String operatorId, String projectId, UserContext user) {
        if (!permissionService.hasPermission(user, "CLEARING_RULE_APPROVE")) {
            return List.of();
        }
        String fundingScope = dataScopeHelper.isFundingRole(user) ? user.enterpriseId() : null;
        return clearingRuleRepository
                .findScoped(operatorId, projectId, null, "PENDING", fundingScope, PageRequest.of(0, SOURCE_LIMIT))
                .stream()
                .map(this::toClearingEvent)
                .toList();
    }

    private List<InboxEventView> collectDisburse(String operatorId, String projectId, UserContext user) {
        List<InboxEventView> events = new ArrayList<>();
        String fundingPartyId = null;
        String customerId = null;
        if (dataScopeHelper.isFundingRole(user)) {
            fundingPartyId = user.enterpriseId();
        } else if (dataScopeHelper.isEnterpriseRole(user)) {
            customerId = user.enterpriseId();
        } else if (!dataScopeHelper.canReadOperatorData(user)) {
            return List.of();
        }

        if (permissionService.hasPermission(user, "FINANCE_DISBURSE")) {
            financeRepository
                    .findToDisburseInScope(
                            operatorId,
                            projectId,
                            fundingPartyId,
                            customerId,
                            PageRequest.of(0, SOURCE_LIMIT))
                    .forEach(app -> events.add(toToDisburseEvent(app)));
        }

        if (permissionService.hasPermission(user, "FINANCE_VIEW")) {
            disbursementRepository
                    .findPendingInScope(operatorId, projectId, fundingPartyId, PageRequest.of(0, SOURCE_LIMIT))
                    .forEach(disbursement -> events.add(toPendingDisburseEvent(disbursement)));
        }
        return events;
    }

    private List<InboxEventView> collectWarehouse(String operatorId, String projectId, UserContext user) {
        if (!permissionService.hasPermission(user, "WAREHOUSE_VIEW")) {
            return List.of();
        }
        String ownerId = "";
        String warehouseCompanyId = "";
        switch (dataScopeHelper.warehouseInventoryScope(user)) {
            case ENTERPRISE -> ownerId = user.enterpriseId();
            case WAREHOUSE_COMPANY -> warehouseCompanyId = user.enterpriseId();
            case NONE -> {
                return List.of();
            }
            default -> {
            }
        }
        return inventoryRepository
                .findStocktakeExceptions(
                        operatorId, projectId, ownerId, warehouseCompanyId, PageRequest.of(0, SOURCE_LIMIT))
                .stream()
                .map(this::toWarehouseEvent)
                .toList();
    }

    private InboxEventView toBpmEvent(BpmTask task) {
        String route = resolveBpmRoute(task.getBusinessType(), task.getBusinessId());
        return new InboxEventView(
                eventKey("BPM", task.getId()),
                "BPM",
                "TODO",
                "MEDIUM",
                "BPM 待办：" + task.getNodeCode(),
                task.getBusinessType() + " / " + task.getBusinessId() + " 待您审批",
                task.getBusinessType(),
                task.getBusinessId(),
                task.getBusinessId(),
                route,
                task.getSubmittedAt(),
                false,
                Map.of("task_id", task.getId(), "node_code", task.getNodeCode()));
    }

    private InboxEventView toRiskEvent(BiRiskAlertTicket ticket) {
        String severity = ticket.getSeverity() == null ? "MEDIUM" : ticket.getSeverity();
        return new InboxEventView(
                eventKey("RISK", ticket.getId()),
                "RISK",
                "ALERT",
                severity,
                ticket.getTitle(),
                ticket.getMessage(),
                ticket.getRelatedType(),
                ticket.getRelatedId(),
                ticket.getRelatedLabel(),
                RiskAlertCenterService.resolveRelatedRoute(ticket.getRelatedType(), ticket.getRelatedId()),
                ticket.getDetectedAt(),
                false,
                Map.of(
                        "alert_code", ticket.getAlertCode(),
                        "handle_status", ticket.getHandleStatus(),
                        "ticket_id", ticket.getId()));
    }

    private InboxEventView toClearingEvent(ClearingRule rule) {
        return new InboxEventView(
                eventKey("CLEARING", rule.getId()),
                "CLEARING",
                "APPROVAL",
                "MEDIUM",
                "清分规则待审批",
                rule.getRuleName() + "（" + rule.getProductType() + "）待审批",
                "CLEARING_RULE",
                rule.getId(),
                rule.getRuleName(),
                "/accounts/clearing-rules",
                Instant.now(),
                false,
                Map.of("review_status", rule.getReviewStatus()));
    }

    private InboxEventView toToDisburseEvent(FnFinanceApplication app) {
        return new InboxEventView(
                eventKey("DISBURSE_TODO", app.getId()),
                "DISBURSE",
                "TODO",
                "HIGH",
                "融资待放款",
                "融资单 " + app.getFinanceNo() + " 已审批，待执行放款",
                "FINANCE",
                app.getId(),
                app.getFinanceNo(),
                "/finance/applications",
                app.getCreatedAt(),
                false,
                Map.of("finance_status", app.getFinanceStatus(), "amount", money(app.getApprovedAmount())));
    }

    private InboxEventView toPendingDisburseEvent(FnDisbursement disbursement) {
        return new InboxEventView(
                eventKey("DISBURSE_PENDING", disbursement.getId()),
                "DISBURSE",
                "CONFIRM",
                "HIGH",
                "放款渠道确认中",
                "放款单 " + disbursement.getDisbursementNo() + " 等待银行渠道回执",
                "DISBURSEMENT",
                disbursement.getId(),
                disbursement.getDisbursementNo(),
                "/finance/applications",
                disbursement.getCreatedAt(),
                false,
                Map.of(
                        "finance_id", disbursement.getFinanceId(),
                        "disbursement_status", disbursement.getDisbursementStatus(),
                        "amount", money(disbursement.getAmount())));
    }

    private InboxEventView toWarehouseEvent(WhInventory inventory) {
        Instant occurredAt = inventory.getUpdatedAt() == null ? Instant.now() : inventory.getUpdatedAt();
        return new InboxEventView(
                eventKey("WAREHOUSE", inventory.getId()),
                "WAREHOUSE",
                "EXCEPTION",
                "LOW",
                "库存盘点异常",
                "批次 " + inventory.getBatchNo() + " 存在盘点差异，请核实",
                "INVENTORY",
                inventory.getId(),
                inventory.getBatchNo(),
                "/warehouse/inventories/" + inventory.getId(),
                occurredAt,
                false,
                Map.of("warehouse_id", inventory.getWarehouseId(), "sku_id", inventory.getSkuId()));
    }

    static String eventKey(String prefix, String id) {
        return prefix + "|" + id;
    }

    static String resolveBpmRoute(String businessType, String businessId) {
        if (businessType == null) {
            return "/";
        }
        return switch (businessType) {
            case "AGENCY_PURCHASE" -> "/agency-purchase/applications/" + businessId;
            case "FINANCE" -> "/finance/applications";
            case "ORDER" -> "/trade/orders";
            default -> "/";
        };
    }

    private static int severityRank(String severity) {
        if ("HIGH".equals(severity)) {
            return 1;
        }
        if ("MEDIUM".equals(severity)) {
            return 2;
        }
        if ("LOW".equals(severity)) {
            return 3;
        }
        return 4;
    }

    private static String money(java.math.BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
