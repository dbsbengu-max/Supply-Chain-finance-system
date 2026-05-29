package com.scf.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecondaryAuthVerifier;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.finance.dto.FinanceCreateRequest;
import com.scf.finance.dto.FinanceDisburseRequest;
import com.scf.finance.dto.FinanceDisburseView;
import com.scf.finance.dto.FinanceView;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.entity.FnDisbursement;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import com.scf.finance.repository.FnDisbursementRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.idempotency.dto.IdempotentExecutionResult;
import com.scf.idempotency.service.IdempotencyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Service
public class FinanceApplicationService {

    private static final Set<String> ALLOWED_CHANNELS = Set.of("BANK_TRANSFER", "INTERNAL_ACCOUNT");

    private final FnFinanceApplicationRepository repository;
    private final FnDisbursementRepository disbursementRepository;
    private final AcctVirtualAccountRepository accountRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final DataScopeHelper dataScopeHelper;
    private final SecondaryAuthVerifier secondaryAuthVerifier;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final DisburseCompletionService disburseCompletionService;

    public FinanceApplicationService(
            FnFinanceApplicationRepository repository,
            FnDisbursementRepository disbursementRepository,
            AcctVirtualAccountRepository accountRepository,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            DataScopeHelper dataScopeHelper,
            SecondaryAuthVerifier secondaryAuthVerifier,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            DisburseCompletionService disburseCompletionService) {
        this.repository = repository;
        this.disbursementRepository = disbursementRepository;
        this.accountRepository = accountRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.dataScopeHelper = dataScopeHelper;
        this.secondaryAuthVerifier = secondaryAuthVerifier;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.disburseCompletionService = disburseCompletionService;
    }

    public PageResponse<FinanceView> list(int pageNo, int pageSize, String financeStatus) {
        tenantContext.requirePermission("FINANCE_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));

        Page<FnFinanceApplication> page;
        DataScopeHelper.ScopeType scopeType = dataScopeHelper.financeScope(user);
        if (scopeType == DataScopeHelper.ScopeType.ENTERPRISE) {
            page = repository.findByCustomerScope(operatorId, projectId, user.enterpriseId(), pageable);
        } else if (scopeType == DataScopeHelper.ScopeType.FUNDING_PARTY) {
            page = repository.findByFundingScope(operatorId, projectId, user.enterpriseId(), pageable);
        } else if (scopeType == DataScopeHelper.ScopeType.OPERATOR_PROJECT) {
            page = repository.findByOperatorIdAndProjectIdAndDeletedFlagOrderByCreatedAtDesc(
                    operatorId, projectId, (short) 0, pageable);
        } else {
            throw new BusinessException("AUTH_403", "无融资数据范围", 403);
        }

        var records = page.getContent().stream()
                .filter(f -> financeStatus == null || financeStatus.isBlank() || financeStatus.equals(f.getFinanceStatus()))
                .map(FinanceView::from)
                .toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public FinanceView getById(String id) {
        tenantContext.requirePermission("FINANCE_VIEW");
        return FinanceView.from(loadAccessible(id));
    }

    @Transactional
    public FinanceView create(FinanceCreateRequest request) {
        tenantContext.requirePermission("FINANCE_CREATE");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();

        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(request.customerId())) {
            throw new BusinessException("AUTH_403", "仅可为本企业创建融资申请", 403);
        }

        FnFinanceApplication app = new FnFinanceApplication();
        app.setId(IdGenerator.nextId());
        app.setOperatorId(operatorId);
        app.setProjectId(projectId);
        app.setFinanceNo("FIN-" + System.currentTimeMillis());
        app.setCustomerId(request.customerId());
        app.setFundingPartyId(request.fundingPartyId());
        app.setCreditId(request.creditId());
        app.setProductType(request.productType());
        app.setSourceType(request.sourceType());
        app.setSourceId(request.sourceId());
        app.setApplyAmount(new BigDecimal(request.applyAmount()));
        app.setCurrency(request.currency());
        app.setTermDays(request.termDays());
        app.setAnnualRate(new BigDecimal(request.annualRate()));
        if (request.guaranteeAmount() != null && !request.guaranteeAmount().isBlank()) {
            app.setGuaranteeAmount(new BigDecimal(request.guaranteeAmount()));
        }
        if (request.pledgeRate() != null && !request.pledgeRate().isBlank()) {
            app.setPledgeRate(new BigDecimal(request.pledgeRate()));
        }
        app.setDisbursedAmount(BigDecimal.ZERO);
        app.setFinanceStatus("DRAFT");
        app.setCreatedBy(user.userId());
        app.setCreatedAt(Instant.now());
        app.setDeletedFlag((short) 0);
        app.setVersionNo(1);
        repository.save(app);

        auditLogService.log("FINANCE_CREATE", "FINANCE_APPLICATION", app.getId(), null,
                Map.of("finance_no", app.getFinanceNo()));
        return FinanceView.from(app);
    }

    @Transactional
    public FinanceView submit(String id) {
        tenantContext.requirePermission("FINANCE_CREATE");
        FnFinanceApplication app = loadAccessibleForUpdate(id);
        if (!"DRAFT".equals(app.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "仅草稿融资申请可提交", 409);
        }
        app.setFinanceStatus("SUBMITTED");
        app.setUpdatedBy(SecurityUtils.currentUserId());
        app.setUpdatedAt(Instant.now());
        repository.save(app);
        auditLogService.log("FINANCE_SUBMIT", "FINANCE_APPLICATION", app.getId(), null, Map.of());
        return FinanceView.from(app);
    }

    @Transactional
    public FinanceView approve(String id) {
        tenantContext.requirePermission("FINANCE_APPROVE");
        FnFinanceApplication app = loadAccessibleForUpdate(id);
        if (!"SUBMITTED".equals(app.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "仅已提交申请可审批", 409);
        }
        app.setApprovedAmount(app.getApplyAmount());
        app.setFinanceStatus("TO_DISBURSE");
        app.setUpdatedBy(SecurityUtils.currentUserId());
        app.setUpdatedAt(Instant.now());
        repository.save(app);
        auditLogService.log("FINANCE_APPROVE", "FINANCE_APPLICATION", app.getId(), null, Map.of());
        return FinanceView.from(app);
    }

    public FinanceDisburseView disburse(
            String id,
            FinanceDisburseRequest request,
            String idempotencyKey,
            String secondaryAuthToken) {
        tenantContext.requirePermission("FINANCE_DISBURSE");
        secondaryAuthVerifier.requireValid(secondaryAuthToken);

        String requestBody = buildIdempotencyPayload(id, request);
        IdempotentExecutionResult<FinanceDisburseView> result = idempotencyService.executeWithReplay(
                idempotencyKey,
                "FINANCE_DISBURSE",
                requestBody,
                FinanceDisburseView.class,
                () -> doDisburse(id, request, idempotencyKey));
        return result.replay() ? result.value().withIdempotentReplay(true) : result.value();
    }

    @Transactional
    protected FinanceDisburseView doDisburse(String id, FinanceDisburseRequest request, String idempotencyKey) {
        FnFinanceApplication app = loadAccessibleForUpdate(id);
        assertDisburseScope(app);

        if (!"TO_DISBURSE".equals(app.getFinanceStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可放款", 409);
        }

        BigDecimal disburseAmount = parsePositiveAmount(request.disburseAmount());
        BigDecimal approved = app.getApprovedAmount() == null ? BigDecimal.ZERO : app.getApprovedAmount();
        BigDecimal disbursed = app.getDisbursedAmount() == null ? BigDecimal.ZERO : app.getDisbursedAmount();
        BigDecimal remaining = approved.subtract(disbursed);
        if (disburseAmount.compareTo(remaining) != 0) {
            throw new BusinessException("VALID_400", "V1.1 仅支持一次性全额放款，放款金额必须等于可放款余额", 400);
        }

        if (!app.getCurrency().equals(request.currency())) {
            throw new BusinessException("VALID_400", "币种与融资申请不一致", 400);
        }

        LocalDate valueDate = parseValueDate(request.valueDate());
        if (valueDate.isBefore(LocalDate.now())) {
            throw new BusinessException("VALID_400", "value_date 不能早于当前业务日期", 400);
        }

        if (!ALLOWED_CHANNELS.contains(request.fundingChannel())) {
            throw new BusinessException("VALID_400", "不支持的 funding_channel", 400);
        }

        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        AcctVirtualAccount payer = requireActiveAccount(
                request.payerAccountId(), operatorId, projectId, app.getFundingPartyId(), app.getCurrency());
        AcctVirtualAccount receiver = requireActiveAccount(
                request.receiverAccountId(), operatorId, projectId, app.getCustomerId(), app.getCurrency());

        if (payer.getBalance().compareTo(disburseAmount) < 0) {
            throw new BusinessException("ACCOUNT_409", "出款账户余额不足", 409);
        }

        Instant now = Instant.now();
        FnDisbursement disbursement = new FnDisbursement();
        disbursement.setId(IdGenerator.nextId());
        disbursement.setFinanceId(app.getId());
        disbursement.setDisbursementNo("DISB-" + System.currentTimeMillis());
        disbursement.setAmount(disburseAmount);
        disbursement.setCurrency(app.getCurrency());
        disbursement.setPayAccountId(payer.getId());
        disbursement.setReceiveAccountId(receiver.getId());
        disbursement.setChannel(request.fundingChannel());
        disbursement.setIdempotencyKey(idempotencyKey);
        disbursement.setValueDate(valueDate);
        disbursement.setRemark(trimRemark(request.remark()));
        disbursement.setCreatedAt(now);

        UserContext user = SecurityUtils.currentUser();
        if ("INTERNAL_ACCOUNT".equals(request.fundingChannel())) {
            disbursement.setChannelRequestId("INT-REQ-" + disbursement.getId());
            disbursement.setDisbursementStatus("PENDING");
            disbursementRepository.save(disbursement);

            disburseCompletionService.completeSuccess(
                    disbursement,
                    "INT-FLOW-" + disbursement.getId(),
                    now,
                    null,
                    null,
                    "INT-FLOW-" + disbursement.getId(),
                    user.userId(),
                    operatorId,
                    user.enterpriseId());

            app = repository.findByIdForUpdate(app.getId()).orElseThrow();

            auditLogService.log(
                    "FINANCE_DISBURSE",
                    "FINANCE_APPLICATION",
                    app.getId(),
                    Map.of("finance_status", "TO_DISBURSE"),
                    Map.of(
                            "disbursement_id", disbursement.getId(),
                            "disburse_amount", disburseAmount.toPlainString(),
                            "finance_status", "DISBURSED",
                            "funding_channel", "INTERNAL_ACCOUNT"));

            disbursement = disbursementRepository.findById(disbursement.getId()).orElseThrow();
            return toDisburseView(app, disbursement, request, now, null);
        }

        disbursement.setChannelRequestId("BANK-REQ-" + disbursement.getId());
        disbursement.setDisbursementStatus("PENDING");
        disbursementRepository.save(disbursement);

        auditLogService.log(
                "FINANCE_DISBURSE_SUBMIT",
                "FINANCE_APPLICATION",
                app.getId(),
                Map.of("finance_status", "TO_DISBURSE"),
                Map.of(
                        "disbursement_id", disbursement.getId(),
                        "disburse_amount", disburseAmount.toPlainString(),
                        "disbursement_status", "PENDING",
                        "channel_request_id", disbursement.getChannelRequestId(),
                        "funding_channel", "BANK_TRANSFER"));

        return toDisburseView(app, disbursement, request, now, null);
    }

    private FinanceDisburseView toDisburseView(
            FnFinanceApplication app,
            FnDisbursement disbursement,
            FinanceDisburseRequest request,
            Instant createdAt,
            Boolean idempotentReplay) {
        return new FinanceDisburseView(
                app.getId(),
                app.getFinanceNo(),
                app.getFinanceStatus(),
                disbursement.getId(),
                disbursement.getAmount().toPlainString(),
                app.getCurrency(),
                disbursement.getValueDate(),
                disbursement.getPayAccountId(),
                disbursement.getReceiveAccountId(),
                request.fundingChannel(),
                disbursement.getIdempotencyKey(),
                createdAt,
                disbursement.getDisbursementStatus(),
                disbursement.getChannelRequestId(),
                idempotentReplay);
    }

    private void assertDisburseScope(FnFinanceApplication app) {
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.canReadOperatorData(user)) {
            return;
        }
        if (dataScopeHelper.isFundingRole(user)) {
            if (!user.enterpriseId().equals(app.getFundingPartyId())) {
                throw new BusinessException("AUTH_403", "无权对该融资申请执行放款", 403);
            }
            return;
        }
        throw new BusinessException("AUTH_403", "无权执行放款", 403);
    }

    private AcctVirtualAccount requireActiveAccount(
            String accountId,
            String operatorId,
            String projectId,
            String enterpriseId,
            String currency) {
        AcctVirtualAccount account = accountRepository
                .findByIdAndOperatorIdAndProjectId(accountId, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("ACCOUNT_409", "账户不存在或不可用", 409));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException("ACCOUNT_409", "账户状态不可用", 409);
        }
        if (!enterpriseId.equals(account.getEnterpriseId())) {
            throw new BusinessException("ACCOUNT_409", "账户归属不匹配", 409);
        }
        if (!currency.equals(account.getCurrency())) {
            throw new BusinessException("ACCOUNT_409", "账户币种不匹配", 409);
        }
        return account;
    }

    private static BigDecimal parsePositiveAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("VALID_400", "disburse_amount 必须大于 0", 400);
            }
            return amount;
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALID_400", "disburse_amount 格式非法", 400);
        }
    }

    private static LocalDate parseValueDate(String raw) {
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            throw new BusinessException("VALID_400", "value_date 格式非法", 400);
        }
    }

    private static String trimRemark(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        return remark.trim();
    }

    private String buildIdempotencyPayload(String financeId, FinanceDisburseRequest request) {
        try {
            return objectMapper.writeValueAsString(Map.of("finance_id", financeId, "body", request));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize disburse request", ex);
        }
    }

    private FnFinanceApplication loadAccessible(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        FnFinanceApplication app = repository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, app.getCustomerId(), app.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权访问该融资申请", 403);
        }
        return app;
    }

    private FnFinanceApplication loadAccessibleForUpdate(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        FnFinanceApplication app = repository.findByIdAndOperatorIdAndProjectIdForUpdate(id, operatorId, projectId)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, app.getCustomerId(), app.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权访问该融资申请", 403);
        }
        return app;
    }
}
