package com.scf.audit.service;

import com.scf.audit.dto.AuditDtos;
import com.scf.audit.dto.AuditDtos.AuditFilterMetaView;
import com.scf.audit.dto.AuditDtos.AuditLogDetailView;
import com.scf.audit.dto.AuditDtos.AuditLogView;
import com.scf.audit.dto.AuditDtos.AuditSummaryItemView;
import com.scf.audit.dto.AuditDtos.AuditSummaryView;
import com.scf.audit.entity.AuditOperationLog;
import com.scf.audit.repository.AuditOperationLogRepository;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.iam.entity.SysUser;
import com.scf.iam.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuditCenterService {

    private final AuditOperationLogRepository repository;
    private final SysUserRepository userRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;

    public AuditCenterService(
            AuditOperationLogRepository repository,
            SysUserRepository userRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogView> list(
            int pageNo,
            int pageSize,
            String action,
            String objectType,
            String objectId,
            String userId,
            Instant fromAt,
            Instant toAt,
            String keyword) {
        tenantContext.requirePermission("AUDIT_VIEW");
        ScopeParams scope = resolveScope();
        Page<AuditOperationLog> page = repository.findFiltered(
                scope.operatorId(),
                scope.projectId(),
                scope.enterpriseScope(),
                scope.userScope(),
                blankToNull(action),
                blankToNull(objectType),
                blankToNull(objectId),
                blankToNull(userId),
                fromAt,
                toAt,
                blankToNull(keyword),
                PageRequest.of(Math.max(pageNo - 1, 0), Math.min(Math.max(pageSize, 1), 100)));

        Map<String, String> userNames = loadUserNames(page.getContent());
        List<AuditLogView> records = page.getContent().stream()
                .map(row -> toView(row, userNames))
                .toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    @Transactional(readOnly = true)
    public AuditLogDetailView get(String id) {
        tenantContext.requirePermission("AUDIT_VIEW");
        ScopeParams scope = resolveScope();
        AuditOperationLog log = repository.findByIdAndOperatorId(id, scope.operatorId())
                .orElseThrow(() -> new BusinessException("DATA_404", "审计记录不存在", 404));
        assertAccessible(log, scope);
        Map<String, String> userNames = loadUserNames(List.of(log));
        return toDetailView(log, userNames);
    }

    @Transactional(readOnly = true)
    public AuditSummaryView summary(int days) {
        tenantContext.requirePermission("AUDIT_VIEW");
        ScopeParams scope = resolveScope();
        int windowDays = Math.min(Math.max(days, 1), 90);
        Instant fromAt = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<Object[]> rows = repository.countByObjectTypeSince(
                scope.operatorId(),
                scope.projectId(),
                scope.enterpriseScope(),
                scope.userScope(),
                fromAt);
        long total = rows.stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();
        List<AuditSummaryItemView> items = rows.stream()
                .map(row -> new AuditSummaryItemView(
                        String.valueOf(row[0]),
                        labelObjectType(String.valueOf(row[0])),
                        ((Number) row[1]).longValue()))
                .toList();
        return new AuditSummaryView(total, items);
    }

    @Transactional(readOnly = true)
    public AuditFilterMetaView filterMeta() {
        tenantContext.requirePermission("AUDIT_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        return new AuditFilterMetaView(
                repository.distinctActions(operatorId),
                repository.distinctObjectTypes(operatorId));
    }

    private ScopeParams resolveScope() {
        UserContext user = SecurityUtils.currentUser();
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user)) {
            return new ScopeParams(operatorId, projectId, null, null);
        }
        return new ScopeParams(operatorId, projectId, user.enterpriseId(), user.userId());
    }

    private void assertAccessible(AuditOperationLog log, ScopeParams scope) {
        if (scope.enterpriseScope() == null) {
            return;
        }
        boolean allowed = scope.enterpriseScope().equals(log.getEnterpriseId())
                || scope.userScope().equals(log.getUserId());
        if (!allowed) {
            throw new BusinessException("DATA_404", "审计记录不存在", 404);
        }
    }

    private Map<String, String> loadUserNames(List<AuditOperationLog> logs) {
        Set<String> userIds = logs.stream().map(AuditOperationLog::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (SysUser user : userRepository.findAllById(userIds)) {
            names.put(user.getId(), user.getUserName());
        }
        return names;
    }

    private AuditLogView toView(AuditOperationLog log, Map<String, String> userNames) {
        return new AuditLogView(
                log.getId(),
                log.getAction(),
                labelAction(log.getAction()),
                log.getObjectType(),
                labelObjectType(log.getObjectType()),
                log.getObjectId(),
                log.getUserId(),
                userNames.getOrDefault(log.getUserId(), log.getUserId()),
                log.getEnterpriseId(),
                log.getProjectId(),
                log.getIpAddress(),
                log.getOperationAt(),
                resolveRoute(log.getObjectType(), log.getObjectId()));
    }

    private AuditLogDetailView toDetailView(AuditOperationLog log, Map<String, String> userNames) {
        return new AuditLogDetailView(
                log.getId(),
                log.getAction(),
                labelAction(log.getAction()),
                log.getObjectType(),
                labelObjectType(log.getObjectType()),
                log.getObjectId(),
                log.getUserId(),
                userNames.getOrDefault(log.getUserId(), log.getUserId()),
                log.getEnterpriseId(),
                log.getProjectId(),
                log.getIpAddress(),
                log.getOperationAt(),
                log.getBeforeValue(),
                log.getAfterValue(),
                resolveRoute(log.getObjectType(), log.getObjectId()));
    }

    static String resolveRoute(String objectType, String objectId) {
        if (objectType == null || objectId == null) {
            return null;
        }
        return switch (objectType) {
            case "TRADE_ORDER" -> "/trade/orders";
            case "FINANCE_APPLICATION" -> "/finance/applications";
            case "ENTERPRISE" -> "/customers";
            case "INVENTORY" -> "/warehouse/inventories/" + objectId;
            case "CLEARING_RULE" -> "/accounts/clearing-rules";
            case "CLEARING_EXECUTION" -> "/accounts/clearing";
            case "BANK_FLOW" -> "/accounts/bank-flows";
            case "AGENCY_PURCHASE" -> "/agency-purchase/applications/" + objectId;
            case "RISK_ALERT" -> "/risk/alerts";
            case "PRICE" -> "/pricing";
            case "PROJECT" -> "/projects";
            case "BPM_TASK", "BPM_PROCESS" -> "/inbox?source=BPM";
            case "VOUCHER" -> "/vouchers/" + objectId;
            default -> null;
        };
    }

    private static String labelAction(String action) {
        return AuditDtos.actionLabels().getOrDefault(action, action);
    }

    private static String labelObjectType(String objectType) {
        return AuditDtos.objectTypeLabels().getOrDefault(objectType, objectType);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ScopeParams(String operatorId, String projectId, String enterpriseScope, String userScope) {
    }
}
