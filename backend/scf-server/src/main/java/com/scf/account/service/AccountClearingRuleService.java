package com.scf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.account.dto.ClearingRuleCreateRequest;
import com.scf.account.dto.ClearingRuleUpdateRequest;
import com.scf.account.dto.ClearingRuleView;
import com.scf.audit.service.AuditLogService;
import com.scf.clearing.entity.ClearingRule;
import com.scf.clearing.repository.ClearingRuleRepository;
import com.scf.clearing.support.ClearingRuleParser;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class AccountClearingRuleService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "REJECTED");
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";

    private final ClearingRuleRepository ruleRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AccountClearingRuleService(
            ClearingRuleRepository ruleRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    public PageResponse<ClearingRuleView> list(
            int pageNo, int pageSize, String productType, String reviewStatus) {
        tenantContext.requirePermission("CLEARING_RULE_LIST");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<ClearingRule> page = ruleRepository.findScoped(
                operatorId,
                projectId,
                blankToNull(productType),
                blankToNull(reviewStatus),
                resolveFundingScopeId(),
                pageable);
        List<ClearingRuleView> records = page.getContent().stream().map(this::toView).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public ClearingRuleView get(String id) {
        tenantContext.requirePermission("CLEARING_RULE_LIST");
        return toView(loadAccessibleRule(id));
    }

    @Transactional
    public ClearingRuleView create(ClearingRuleCreateRequest request) {
        tenantContext.requirePermission("CLEARING_RULE_CREATE");
        UserContext user = SecurityUtils.currentUser();
        String fundingPartyId = resolveFundingPartyIdForWrite(user, request.fundingPartyId());
        validateRulePayload(request.priorityJson(), fundingPartyId);
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        ClearingRule rule = new ClearingRule();
        rule.setId(IdGenerator.nextId());
        rule.setOperatorId(operatorId);
        rule.setProjectId(projectId);
        applyPayload(rule, fundingPartyId, request.productType(), request.ruleName(),
                request.priorityJson(), request.feeFormulaJson(), request.currencyRule(),
                request.effectiveFrom(), request.effectiveTo());
        rule.setReviewStatus(STATUS_DRAFT);
        rule.setVersionNo(1);
        ClearingRule saved = ruleRepository.save(rule);
        auditLogService.log("CLEARING_RULE_CREATE", "CLEARING_RULE", saved.getId(), null, toAuditMap(saved));
        return toView(saved);
    }

    @Transactional
    public ClearingRuleView update(String id, ClearingRuleUpdateRequest request) {
        tenantContext.requirePermission("CLEARING_RULE_UPDATE");
        UserContext user = SecurityUtils.currentUser();
        String fundingPartyId = resolveFundingPartyIdForWrite(user, request.fundingPartyId());
        validateRulePayload(request.priorityJson(), fundingPartyId);
        ClearingRule rule = loadAccessibleRule(id);
        if (!EDITABLE_STATUSES.contains(rule.getReviewStatus())) {
            throw new BusinessException("STATE_409", "仅草稿或已驳回规则可编辑", 409);
        }
        applyPayload(rule, fundingPartyId, request.productType(), request.ruleName(),
                request.priorityJson(), request.feeFormulaJson(), request.currencyRule(),
                request.effectiveFrom(), request.effectiveTo());
        ClearingRule saved = ruleRepository.save(rule);
        auditLogService.log("CLEARING_RULE_UPDATE", "CLEARING_RULE", saved.getId(), null, toAuditMap(saved));
        return toView(saved);
    }

    @Transactional
    public ClearingRuleView submit(String id) {
        tenantContext.requirePermission("CLEARING_RULE_SUBMIT");
        ClearingRule rule = loadAccessibleRule(id);
        if (!STATUS_DRAFT.equals(rule.getReviewStatus()) && !"REJECTED".equals(rule.getReviewStatus())) {
            throw new BusinessException("STATE_409", "仅草稿或已驳回规则可提交审批", 409);
        }
        rule.setReviewStatus(STATUS_PENDING);
        ClearingRule saved = ruleRepository.save(rule);
        auditLogService.log("CLEARING_RULE_SUBMIT", "CLEARING_RULE", saved.getId(), null, toAuditMap(saved));
        return toView(saved);
    }

    @Transactional
    public ClearingRuleView approve(String id) {
        tenantContext.requirePermission("CLEARING_RULE_APPROVE");
        ClearingRule rule = loadAccessibleRule(id);
        if (!STATUS_PENDING.equals(rule.getReviewStatus())) {
            throw new BusinessException("STATE_409", "仅待审批规则可批准", 409);
        }
        rule.setReviewStatus(STATUS_APPROVED);
        ClearingRule saved = ruleRepository.save(rule);
        auditLogService.log("CLEARING_RULE_APPROVE", "CLEARING_RULE", saved.getId(), null, toAuditMap(saved));
        return toView(saved);
    }

    private ClearingRule loadAccessibleRule(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        ClearingRule rule = ruleRepository.findByIdAndOperatorIdAndProjectId(id, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "清分规则不存在", 404));
        assertFundingAccess(rule.getFundingPartyId());
        return rule;
    }

    private void assertFundingAccess(String fundingPartyId) {
        String scopeId = resolveFundingScopeId();
        if (scopeId == null) {
            return;
        }
        if (fundingPartyId != null && !scopeId.equals(fundingPartyId)) {
            throw new BusinessException("AUTH_403", "无权访问该资方清分规则", 403);
        }
    }

    private String resolveFundingScopeId() {
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isPlatformRole(user) || dataScopeHelper.canReadOperatorData(user)) {
            return null;
        }
        if (dataScopeHelper.isFundingRole(user)) {
            return user.enterpriseId();
        }
        return null;
    }

    private void validateRulePayload(String priorityJson, String fundingPartyId) {
        ClearingRuleParser.parsePriorityOrder(objectMapper, priorityJson);
        assertFundingAccess(fundingPartyId);
    }

    private String resolveFundingPartyIdForWrite(UserContext user, String requestedFundingPartyId) {
        if (dataScopeHelper.isFundingRole(user)) {
            if (requestedFundingPartyId != null
                    && !requestedFundingPartyId.isBlank()
                    && !user.enterpriseId().equals(requestedFundingPartyId.trim())) {
                throw new BusinessException("AUTH_403", "资方用户仅可维护本企业清分规则", 403);
            }
            return user.enterpriseId();
        }
        return blankToNull(requestedFundingPartyId);
    }

    private void applyPayload(
            ClearingRule rule,
            String fundingPartyId,
            String productType,
            String ruleName,
            String priorityJson,
            String feeFormulaJson,
            String currencyRule,
            java.time.LocalDate effectiveFrom,
            java.time.LocalDate effectiveTo) {
        rule.setFundingPartyId(blankToNull(fundingPartyId));
        rule.setProductType(productType.trim());
        rule.setRuleName(ruleName.trim());
        rule.setPriorityJson(priorityJson.trim());
        rule.setFeeFormulaJson(blankToNull(feeFormulaJson));
        rule.setCurrencyRule(currencyRule.trim());
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
    }

    private ClearingRuleView toView(ClearingRule rule) {
        return new ClearingRuleView(
                rule.getId(),
                rule.getOperatorId(),
                rule.getProjectId(),
                rule.getFundingPartyId(),
                rule.getProductType(),
                rule.getRuleName(),
                rule.getPriorityJson(),
                rule.getFeeFormulaJson(),
                rule.getCurrencyRule(),
                rule.getEffectiveFrom(),
                rule.getEffectiveTo(),
                rule.getReviewStatus(),
                rule.getVersionNo());
    }

    private java.util.Map<String, Object> toAuditMap(ClearingRule rule) {
        return java.util.Map.of(
                "rule_name", rule.getRuleName(),
                "product_type", rule.getProductType(),
                "review_status", rule.getReviewStatus(),
                "version_no", rule.getVersionNo());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
