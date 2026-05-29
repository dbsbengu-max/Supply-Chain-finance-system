package com.scf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.account.dto.*;
import com.scf.audit.service.AuditLogService;
import com.scf.clearing.entity.ClearingResult;
import com.scf.clearing.entity.ClearingRule;
import com.scf.clearing.entity.FnRepayment;
import com.scf.clearing.repository.ClearingResultRepository;
import com.scf.clearing.repository.ClearingRuleRepository;
import com.scf.clearing.repository.FnRepaymentRepository;
import com.scf.clearing.service.ClearingEngineService;
import com.scf.clearing.support.ClearingRuleParser;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecondaryAuthVerifier;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.finance.entity.AcctBankFlow;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.AcctBankFlowRepository;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.idempotency.dto.IdempotentExecutionResult;
import com.scf.idempotency.service.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AccountClearingService {

    private static final Set<String> REPAYABLE_STATUSES = Set.of("DISBURSED", "REPAYING", "OVERDUE");
    private static final String STATUS_EXECUTED = "EXECUTED";

    private final FnFinanceApplicationRepository financeRepository;
    private final AcctBankFlowRepository bankFlowRepository;
    private final AcctVirtualAccountRepository accountRepository;
    private final ClearingRuleRepository clearingRuleRepository;
    private final FnRepaymentRepository repaymentRepository;
    private final ClearingResultRepository clearingResultRepository;
    private final AccountBankFlowService bankFlowService;
    private final ClearingEngineService clearingEngineService;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final SecondaryAuthVerifier secondaryAuthVerifier;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final RepaymentSettledOutboxPublisher repaymentSettledOutboxPublisher;

    public AccountClearingService(
            FnFinanceApplicationRepository financeRepository,
            AcctBankFlowRepository bankFlowRepository,
            AcctVirtualAccountRepository accountRepository,
            ClearingRuleRepository clearingRuleRepository,
            FnRepaymentRepository repaymentRepository,
            ClearingResultRepository clearingResultRepository,
            AccountBankFlowService bankFlowService,
            ClearingEngineService clearingEngineService,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            SecondaryAuthVerifier secondaryAuthVerifier,
            IdempotencyService idempotencyService,
            AuditLogService auditLogService,
            ObjectMapper objectMapper,
            RepaymentSettledOutboxPublisher repaymentSettledOutboxPublisher) {
        this.financeRepository = financeRepository;
        this.bankFlowRepository = bankFlowRepository;
        this.accountRepository = accountRepository;
        this.clearingRuleRepository = clearingRuleRepository;
        this.repaymentRepository = repaymentRepository;
        this.clearingResultRepository = clearingResultRepository;
        this.bankFlowService = bankFlowService;
        this.clearingEngineService = clearingEngineService;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.secondaryAuthVerifier = secondaryAuthVerifier;
        this.idempotencyService = idempotencyService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.repaymentSettledOutboxPublisher = repaymentSettledOutboxPublisher;
    }

    public ClearingEntryView getEntry(String financeId) {
        tenantContext.requirePermission("CLEARING_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        List<BankFlowView> unmatched = bankFlowRepository.findUnmatchedInFlows(operatorId, projectId).stream()
                .map(bankFlowService::toView)
                .toList();

        if (financeId == null || financeId.isBlank()) {
            return new ClearingEntryView(null, null, null, null, null, unmatched, listRuleOptions(operatorId, projectId, null, null));
        }

        FnFinanceApplication finance = loadAccessibleFinance(financeId, operatorId, projectId);
        BigDecimal outstanding = computeOutstandingPrincipal(finance);
        List<ClearingEntryView.ClearingRuleOption> rules = listRuleOptions(
                operatorId, projectId, finance.getProductType(), finance.getFundingPartyId());
        return new ClearingEntryView(
                finance.getId(),
                finance.getFinanceNo(),
                finance.getFinanceStatus(),
                outstanding.toPlainString(),
                finance.getCurrency(),
                unmatched,
                rules);
    }

    public ClearingCalculateView calculate(ClearingCalculateRequest request) {
        tenantContext.requirePermission("CLEARING_CALCULATE");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        return buildCalculateView(operatorId, projectId, request);
    }

    public ClearingExecuteView execute(
            ClearingExecuteRequest request, String idempotencyKey, String secondaryAuthToken) {
        tenantContext.requirePermission("CLEARING_EXECUTE");
        secondaryAuthVerifier.requireValid(secondaryAuthToken);

        String requestBody = buildIdempotencyPayload(request);
        IdempotentExecutionResult<ClearingExecuteView> result = idempotencyService.executeWithReplay(
                idempotencyKey,
                "CLEARING_EXECUTE",
                requestBody,
                ClearingExecuteView.class,
                () -> doExecute(request));
        return result.replay() ? result.value().withIdempotentReplay(true) : result.value();
    }

    @Transactional
    protected ClearingExecuteView doExecute(ClearingExecuteRequest request) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();

        ClearingContext ctx = resolveContext(
                operatorId, projectId, request.financeId(), request.bankFlowId(), request.clearingRuleId(), true);
        if (repaymentRepository.existsByBankFlowIdAndRepaymentStatus(ctx.flow().getId(), STATUS_EXECUTED)) {
            throw new BusinessException("STATE_409", "该流水已完成清分", 409);
        }

        Instant now = Instant.now();
        FnRepayment repayment = new FnRepayment();
        repayment.setId(IdGenerator.nextId());
        repayment.setFinanceId(ctx.finance().getId());
        repayment.setRepaymentNo("REPAY-" + System.currentTimeMillis());
        repayment.setBankFlowId(ctx.flow().getId());
        repayment.setAmount(ctx.repaymentAmount());
        repayment.setCurrency(ctx.finance().getCurrency());
        repayment.setRepaymentStatus(STATUS_EXECUTED);
        repayment.setCreatedAt(now);
        repaymentRepository.save(repayment);

        ClearingResult result = new ClearingResult();
        result.setId(IdGenerator.nextId());
        result.setRepaymentId(repayment.getId());
        result.setClearingRuleId(ctx.rule().getId());
        result.setPenaltyAmount(bucket(ctx.allocation(), "penalty"));
        result.setFeeAmount(bucket(ctx.allocation(), "fee"));
        result.setInterestAmount(bucket(ctx.allocation(), "interest"));
        result.setPrincipalAmount(bucket(ctx.allocation(), "principal"));
        result.setPlatformFeeAmount(bucket(ctx.allocation(), "platform_fee"));
        result.setMarginAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        result.setResidualAmount(bucket(ctx.allocation(), "residual"));
        result.setClearingStatus(STATUS_EXECUTED);
        result.setCreatedAt(now);
        clearingResultRepository.save(result);

        transferClearedFunds(ctx);

        FnFinanceApplication finance = financeRepository.findByIdForUpdate(ctx.finance().getId()).orElseThrow();
        BigDecimal outstandingAfter = computeOutstandingPrincipal(finance);
        String newStatus = outstandingAfter.compareTo(BigDecimal.ZERO) <= 0 ? "SETTLED" : "REPAYING";
        finance.setFinanceStatus(newStatus);
        finance.setUpdatedBy(user.userId());
        finance.setUpdatedAt(now);
        financeRepository.save(finance);

        repaymentSettledOutboxPublisher.publishRepaymentSettled(finance, repayment, result, newStatus);

        auditLogService.log(
                "CLEARING_EXECUTE",
                "FINANCE_APPLICATION",
                finance.getId(),
                Map.of("finance_status", ctx.finance().getFinanceStatus()),
                Map.of(
                        "repayment_id", repayment.getId(),
                        "clearing_result_id", result.getId(),
                        "finance_status", newStatus,
                        "repayment_amount", ctx.repaymentAmount().toPlainString()));

        ClearingCalculateView.Allocation allocation = toAllocationView(ctx.allocation());
        return new ClearingExecuteView(
                repayment.getId(),
                result.getId(),
                finance.getId(),
                newStatus,
                ctx.repaymentAmount().toPlainString(),
                finance.getCurrency(),
                allocation,
                ctx.warnings(),
                now,
                null);
    }

    private void transferClearedFunds(ClearingContext ctx) {
        BigDecimal transferAmount = ctx.repaymentAmount().subtract(bucket(ctx.allocation(), "residual"));
        if (transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        AcctVirtualAccount repaymentAccount = accountRepository.findByIdForUpdate(ctx.flow().getAccountId())
                .orElseThrow(() -> new BusinessException("ACCOUNT_409", "回款账户不存在", 409));
        if (repaymentAccount.getBalance().compareTo(transferAmount) < 0) {
            throw new BusinessException("ACCOUNT_409", "回款账户余额不足", 409);
        }

        AcctVirtualAccount fundingAccount = accountRepository
                .findByOperatorIdAndProjectIdAndFundingPartyId(
                        repaymentAccount.getOperatorId(),
                        repaymentAccount.getProjectId(),
                        ctx.finance().getFundingPartyId())
                .stream()
                .filter(a -> "ACTIVE".equals(a.getStatus()))
                .filter(a -> "INTERNAL".equals(a.getAccountType()) || "DISBURSE".equals(a.getAccountType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("ACCOUNT_409", "资金方结算账户不存在", 409));

        fundingAccount = accountRepository.findByIdForUpdate(fundingAccount.getId()).orElseThrow();
        repaymentAccount.setBalance(repaymentAccount.getBalance().subtract(transferAmount));
        fundingAccount.setBalance(fundingAccount.getBalance().add(transferAmount));
        accountRepository.save(repaymentAccount);
        accountRepository.save(fundingAccount);
    }

    private ClearingCalculateView buildCalculateView(
            String operatorId, String projectId, ClearingCalculateRequest request) {
        ClearingContext ctx = resolveContext(
                operatorId,
                projectId,
                request.financeId(),
                request.bankFlowId(),
                request.clearingRuleId(),
                false);
        return new ClearingCalculateView(
                ctx.finance().getId(),
                ctx.flow().getId(),
                ctx.rule().getId(),
                ctx.repaymentAmount().toPlainString(),
                ctx.finance().getCurrency(),
                toAllocationView(ctx.allocation()),
                ctx.warnings());
    }

    private ClearingContext resolveContext(
            String operatorId,
            String projectId,
            String financeId,
            String bankFlowId,
            String clearingRuleId,
            boolean forExecute) {
        FnFinanceApplication finance = loadAccessibleFinance(financeId, operatorId, projectId);
        if (!REPAYABLE_STATUSES.contains(finance.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "融资单状态不可清分", 409);
        }

        AcctBankFlow flow = bankFlowService.loadScopedFlow(bankFlowId, operatorId, projectId);
        if (!"IN".equals(flow.getFlowType())) {
            throw new BusinessException("VALID_400", "清分流水必须为 IN 类型", 400);
        }
        if (!AccountBankFlowService.MATCH_MATCHED.equals(flow.getMatchStatus())) {
            throw new BusinessException("STATE_409", "流水尚未匹配融资单", 409);
        }
        if (!finance.getId().equals(flow.getSourceId())
                || !AccountBankFlowService.SOURCE_FINANCE.equals(flow.getSourceType())) {
            throw new BusinessException("VALID_400", "流水与融资单匹配关系不一致", 400);
        }
        if (!finance.getCurrency().equals(flow.getCurrency())) {
            throw new BusinessException("VALID_400", "流水币种与融资申请不一致", 400);
        }
        if (forExecute && repaymentRepository.existsByBankFlowIdAndRepaymentStatus(flow.getId(), STATUS_EXECUTED)) {
            throw new BusinessException("STATE_409", "该流水已完成清分", 409);
        }

        ClearingRule rule = clearingRuleRepository
                .findByIdAndOperatorIdAndProjectId(clearingRuleId, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "清分规则不存在", 404));
        if (!"APPROVED".equals(rule.getReviewStatus())) {
            throw new BusinessException("STATE_409", "清分规则未生效", 409);
        }
        if (!finance.getProductType().equals(rule.getProductType())) {
            throw new BusinessException("VALID_400", "清分规则与融资产品类型不匹配", 400);
        }

        List<String> priorityOrder = ClearingRuleParser.parsePriorityOrder(objectMapper, rule.getPriorityJson());
        BigDecimal repaymentAmount = flow.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal outstandingPrincipal = computeOutstandingPrincipal(finance);
        BigDecimal interest = clearingEngineService.estimateInterest(
                outstandingPrincipal, finance.getAnnualRate(), finance.getTermDays());

        ClearingEngineService.OutstandingBuckets outstanding = new ClearingEngineService.OutstandingBuckets(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                interest,
                outstandingPrincipal,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        ClearingEngineService.AllocationResult allocationResult =
                clearingEngineService.allocate(repaymentAmount, priorityOrder, outstanding);

        return new ClearingContext(finance, flow, rule, repaymentAmount, allocationResult.buckets(), allocationResult.warnings());
    }

    private BigDecimal computeOutstandingPrincipal(FnFinanceApplication finance) {
        BigDecimal disbursed = finance.getDisbursedAmount() == null
                ? BigDecimal.ZERO
                : finance.getDisbursedAmount();
        BigDecimal repaid = clearingResultRepository.sumExecutedPrincipalByFinanceId(finance.getId());
        BigDecimal outstanding = disbursed.subtract(repaid).setScale(2, RoundingMode.HALF_UP);
        return outstanding.max(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    private List<ClearingEntryView.ClearingRuleOption> listRuleOptions(
            String operatorId, String projectId, String productType, String fundingPartyId) {
        return clearingRuleRepository.findApprovedRules(operatorId, projectId, productType, fundingPartyId).stream()
                .map(r -> new ClearingEntryView.ClearingRuleOption(r.getId(), r.getRuleName(), r.getProductType()))
                .toList();
    }

    private FnFinanceApplication loadAccessibleFinance(String financeId, String operatorId, String projectId) {
        FnFinanceApplication finance = financeRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(financeId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, finance.getCustomerId(), finance.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权访问该融资申请", 403);
        }
        return finance;
    }

    private static ClearingCalculateView.Allocation toAllocationView(Map<String, BigDecimal> buckets) {
        return new ClearingCalculateView.Allocation(
                bucket(buckets, "penalty").toPlainString(),
                bucket(buckets, "fee").toPlainString(),
                bucket(buckets, "interest").toPlainString(),
                bucket(buckets, "principal").toPlainString(),
                bucket(buckets, "residual").toPlainString());
    }

    private static BigDecimal bucket(Map<String, BigDecimal> buckets, String key) {
        BigDecimal value = buckets.get(key);
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildIdempotencyPayload(ClearingExecuteRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize clearing execute request", ex);
        }
    }

    private record ClearingContext(
            FnFinanceApplication finance,
            AcctBankFlow flow,
            ClearingRule rule,
            BigDecimal repaymentAmount,
            Map<String, BigDecimal> allocation,
            List<String> warnings) {
    }
}
