package com.scf.risk.service;

import com.scf.audit.service.AuditLogService;
import com.scf.bi.support.BiQueryScope;
import com.scf.bi.support.BiScopeResolver;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.risk.dto.RiskAlertDtos.RiskAlertHandleRequest;
import com.scf.risk.dto.RiskAlertDtos.RiskAlertView;
import com.scf.risk.entity.BiRiskAlertTicket;
import com.scf.risk.repository.BiRiskAlertTicketRepository;
import com.scf.risk.support.RiskAlertMaterializer;
import com.scf.risk.support.RiskAlertMaterializer.MaterializedRiskAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RiskAlertCenterService {

    private static final Set<String> HANDLE_STATUSES = Set.of(
            "OPEN", "ACK", "PROCESSING", "RESOLVED", "DISMISSED");
    private static final Set<String> TERMINAL_STATUSES = Set.of("RESOLVED", "DISMISSED");

    private final BiRiskAlertTicketRepository ticketRepository;
    private final BiScopeResolver scopeResolver;
    private final RiskAlertMaterializer materializer;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final DataScopeHelper dataScopeHelper;

    public RiskAlertCenterService(
            BiRiskAlertTicketRepository ticketRepository,
            BiScopeResolver scopeResolver,
            RiskAlertMaterializer materializer,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            DataScopeHelper dataScopeHelper) {
        this.ticketRepository = ticketRepository;
        this.scopeResolver = scopeResolver;
        this.materializer = materializer;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Transactional
    public void syncForInbox(String operatorId, String projectId, UserContext user) {
        syncTickets(buildInboxScope(operatorId, projectId, user));
    }

    private BiQueryScope buildInboxScope(String operatorId, String projectId, UserContext user) {
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            if (dataScopeHelper.isFundingRole(user)) {
                return new BiQueryScope(
                        operatorId,
                        projectId,
                        null,
                        null,
                        user.enterpriseId(),
                        null,
                        null);
            }
            return BiQueryScope.operatorProject(operatorId, projectId);
        }
        if (dataScopeHelper.isEnterpriseRole(user)) {
            return new BiQueryScope(
                    operatorId,
                    projectId,
                    user.enterpriseId(),
                    user.enterpriseId(),
                    null,
                    user.enterpriseId(),
                    null);
        }
        if (dataScopeHelper.isWarehouseRole(user)) {
            return new BiQueryScope(
                    operatorId,
                    projectId,
                    null,
                    null,
                    null,
                    null,
                    user.enterpriseId());
        }
        return BiQueryScope.operatorProject(operatorId, projectId);
    }

    @Transactional
    public PageResponse<RiskAlertView> list(
            int pageNo,
            int pageSize,
            String alertCode,
            String severity,
            String handleStatus,
            String assigneeUserId) {
        tenantContext.requirePermission("RISK_ALERT_VIEW");
        BiQueryScope scope = scopeResolver.requireScope();
        syncTickets(scope);

        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<BiRiskAlertTicket> page = ticketRepository.findFiltered(
                scope.operatorId(),
                scope.projectId(),
                blankToNull(alertCode),
                blankToNull(severity),
                blankToNull(handleStatus),
                blankToNull(assigneeUserId),
                pageable);
        List<RiskAlertView> records = page.getContent().stream().map(this::toView).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public RiskAlertView get(String id) {
        tenantContext.requirePermission("RISK_ALERT_VIEW");
        BiQueryScope scope = scopeResolver.requireScope();
        BiRiskAlertTicket ticket = loadTicket(scope, id);
        return toView(ticket);
    }

    @Transactional
    public RiskAlertView handle(String id, RiskAlertHandleRequest request) {
        tenantContext.requirePermission("RISK_ALERT_HANDLE");
        BiQueryScope scope = scopeResolver.requireScope();
        UserContext user = SecurityUtils.currentUser();
        BiRiskAlertTicket ticket = loadTicket(scope, id);

        String status = request.handleStatus().trim().toUpperCase();
        if (!HANDLE_STATUSES.contains(status)) {
            throw new BusinessException("VALID_400", "无效的处理状态: " + status, 400);
        }

        ticket.setHandleStatus(status);
        if (request.assigneeUserId() != null && !request.assigneeUserId().isBlank()) {
            ticket.setAssigneeUserId(request.assigneeUserId().trim());
        }
        if (request.assigneeName() != null && !request.assigneeName().isBlank()) {
            ticket.setAssigneeName(request.assigneeName().trim());
        }
        if (request.remark() != null) {
            ticket.setRemark(request.remark().trim());
        }
        if (TERMINAL_STATUSES.contains(status)) {
            ticket.setHandledAt(Instant.now());
        } else {
            ticket.setHandledAt(null);
        }
        ticket.setUpdatedBy(user.userId());
        ticket.setUpdatedAt(Instant.now());
        ticket.setVersionNo(ticket.getVersionNo() + 1);

        auditLogService.log("HANDLE", "RISK_ALERT", ticket.getId(), null, Map.of("handle_status", status));

        return toView(ticketRepository.save(ticket));
    }

    @Transactional
    public RiskAlertView claim(String id) {
        tenantContext.requirePermission("RISK_ALERT_HANDLE");
        UserContext user = SecurityUtils.currentUser();
        return handle(id, new RiskAlertHandleRequest(
                "ACK",
                user.userId(),
                user.loginName(),
                "认领告警"));
    }

    private void syncTickets(BiQueryScope scope) {
        Instant now = Instant.now();
        String systemUser = "system";
        List<MaterializedRiskAlert> activeAlerts = materializer.materialize(scope);
        Set<String> activeKeys = activeAlerts.stream()
                .map(MaterializedRiskAlert::alertKey)
                .collect(java.util.stream.Collectors.toSet());

        for (MaterializedRiskAlert alert : activeAlerts) {
            BiRiskAlertTicket ticket = ticketRepository
                    .findByOperatorIdAndProjectIdAndAlertKey(
                            scope.operatorId(), scope.projectId(), alert.alertKey())
                    .orElse(null);
            if (ticket == null) {
                ticket = new BiRiskAlertTicket();
                ticket.setId(IdGenerator.nextId());
                ticket.setOperatorId(scope.operatorId());
                ticket.setProjectId(scope.projectId());
                ticket.setAlertKey(alert.alertKey());
                ticket.setHandleStatus("OPEN");
                ticket.setDetectedAt(now);
                ticket.setCreatedBy(systemUser);
                ticket.setCreatedAt(now);
                ticket.setVersionNo(1);
            }
            ticket.setAlertCode(alert.alertCode());
            ticket.setSeverity(alert.severity());
            ticket.setTitle(alert.title());
            ticket.setMessage(alert.message());
            ticket.setRelatedId(alert.relatedId());
            ticket.setRelatedType(alert.relatedType());
            ticket.setRelatedLabel(alert.relatedLabel());
            ticket.setAmount(alert.amount());
            ticket.setCurrency(alert.currency());
            if (TERMINAL_STATUSES.contains(ticket.getHandleStatus())
                    && activeKeys.contains(alert.alertKey())) {
                ticket.setHandleStatus("OPEN");
                ticket.setHandledAt(null);
            }
            ticketRepository.save(ticket);
        }
    }

    private BiRiskAlertTicket loadTicket(BiQueryScope scope, String id) {
        return ticketRepository.findByIdAndOperatorIdAndProjectId(id, scope.operatorId(), scope.projectId())
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "风险告警不存在", 404));
    }

    private RiskAlertView toView(BiRiskAlertTicket ticket) {
        String amount = ticket.getAmount() == null ? null : ticket.getAmount().toPlainString();
        return new RiskAlertView(
                ticket.getId(),
                ticket.getAlertCode(),
                ticket.getSeverity(),
                ticket.getTitle(),
                ticket.getMessage(),
                ticket.getRelatedId(),
                ticket.getRelatedType(),
                ticket.getRelatedLabel(),
                amount,
                ticket.getCurrency(),
                ticket.getHandleStatus(),
                ticket.getAssigneeUserId(),
                ticket.getAssigneeName(),
                ticket.getRemark(),
                ticket.getDetectedAt(),
                ticket.getHandledAt(),
                ticket.getUpdatedAt(),
                resolveRelatedRoute(ticket.getRelatedType(), ticket.getRelatedId()));
    }

    public static String resolveRelatedRoute(String relatedType, String relatedId) {
        if (relatedType == null) {
            return null;
        }
        return switch (relatedType) {
            case "FINANCE" -> "/finance/applications";
            case "BANK_FLOW" -> "/accounts/bank-flows";
            case "PRICE" -> "/pricing";
            case "INVENTORY" -> relatedId == null || relatedId.isBlank()
                    ? "/warehouse/inventories"
                    : "/warehouse/inventories/" + relatedId;
            default -> null;
        };
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
