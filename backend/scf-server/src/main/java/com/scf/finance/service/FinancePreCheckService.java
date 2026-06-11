package com.scf.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.bpm.entity.BpmProcessInstance;
import com.scf.bpm.repository.BpmProcessInstanceRepository;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecondaryAuthVerifier;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.document.dto.DocumentDtos.DocumentValidateResponse;
import com.scf.document.service.DocumentValidationService;
import com.scf.finance.dto.FinanceDisburseRequest;
import com.scf.finance.dto.FinancePreCheckDtos.FinancePreCheckItem;
import com.scf.finance.dto.FinancePreCheckDtos.FinancePreCheckRequest;
import com.scf.finance.dto.FinancePreCheckDtos.FinancePreCheckResponse;
import com.scf.finance.entity.AcctVirtualAccount;
import com.scf.finance.entity.CrCredit;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.AcctVirtualAccountRepository;
import com.scf.finance.repository.CrCreditRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FinancePreCheckService {

    private static final String BPM_BUSINESS_TYPE = "FINANCE_APPLICATION";
    private static final Set<String> BPM_APPROVED_STATUSES = Set.of("COMPLETED", "APPROVED");
    private static final Set<String> ALLOWED_CHANNELS = Set.of("BANK_TRANSFER", "INTERNAL_ACCOUNT");

    private final FnFinanceApplicationRepository financeRepository;
    private final DocumentValidationService documentValidationService;
    private final CrCreditRepository creditRepository;
    private final AcctVirtualAccountRepository accountRepository;
    private final BpmProcessInstanceRepository bpmRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final SecondaryAuthVerifier secondaryAuthVerifier;
    private final ObjectMapper objectMapper;

    public FinancePreCheckService(
            FnFinanceApplicationRepository financeRepository,
            DocumentValidationService documentValidationService,
            CrCreditRepository creditRepository,
            AcctVirtualAccountRepository accountRepository,
            BpmProcessInstanceRepository bpmRepository,
            IdempotencyRecordRepository idempotencyRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            SecondaryAuthVerifier secondaryAuthVerifier,
            ObjectMapper objectMapper) {
        this.financeRepository = financeRepository;
        this.documentValidationService = documentValidationService;
        this.creditRepository = creditRepository;
        this.accountRepository = accountRepository;
        this.bpmRepository = bpmRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.secondaryAuthVerifier = secondaryAuthVerifier;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public FinancePreCheckResponse preCheck(String financeId, FinancePreCheckRequest request) {
        tenantContext.requirePermission("FINANCE_PRECHECK");
        FnFinanceApplication app = loadAccessible(financeId);
        return buildResponse(app, request == null ? emptyRequest() : request);
    }

    public void assertDisbursePreCheck(
            FnFinanceApplication app,
            FinanceDisburseRequest request,
            String idempotencyKey,
            String idempotencyRequestBody) {
        FinancePreCheckRequest preCheckRequest = new FinancePreCheckRequest(
                request.disburseAmount(),
                request.currency(),
                request.valueDate(),
                request.payerAccountId(),
                request.receiverAccountId(),
                request.fundingChannel(),
                idempotencyKey,
                SecondaryAuthVerifier.MOCK_APPROVED_TOKEN);
        FinancePreCheckResponse result = buildResponse(app, preCheckRequest, idempotencyRequestBody);
        if (!result.passed()) {
            throw new BusinessException("FINANCE_PRECHECK_409", summarizeFailures(result), 409);
        }
    }

    private FinancePreCheckResponse buildResponse(FnFinanceApplication app, FinancePreCheckRequest request) {
        return buildResponse(app, request, null);
    }

    private FinancePreCheckResponse buildResponse(
            FnFinanceApplication app,
            FinancePreCheckRequest request,
            String idempotencyRequestBody) {
        List<FinancePreCheckItem> checks = new ArrayList<>();
        checks.add(checkFinanceStatus(app));
        checks.add(checkBpmStatus(app));
        checks.add(checkCredit(app, request));
        DocumentValidateResponse documentValidation = documentValidationService.validateFinanceDisburse(app);
        checks.add(checkDocuments(documentValidation));
        checks.addAll(checkAccounts(app, request));
        checks.add(checkIdempotency(app.getId(), request, idempotencyRequestBody));
        checks.add(checkSecondaryAuth(request));

        boolean passed = checks.stream().noneMatch(item -> "FAILED".equals(item.result()));
        return new FinancePreCheckResponse(app.getId(), passed, checks, documentValidation);
    }

    private FinancePreCheckItem checkFinanceStatus(FnFinanceApplication app) {
        if ("TO_DISBURSE".equals(app.getFinanceStatus())) {
            return passed("FINANCE_STATUS", "融资申请处于待放款状态");
        }
        return failed("FINANCE_STATUS", "当前状态不可放款：" + app.getFinanceStatus());
    }

    private FinancePreCheckItem checkBpmStatus(FnFinanceApplication app) {
        return bpmRepository.findTopByBusinessTypeAndBusinessIdOrderByStartedAtDesc(BPM_BUSINESS_TYPE, app.getId())
                .map(instance -> {
                    if (BPM_APPROVED_STATUSES.contains(instance.getProcessStatus())) {
                        return passed("BPM_APPROVED", "BPM 流程已通过");
                    }
                    return failed("BPM_APPROVED", "BPM 流程未完成，当前状态：" + instance.getProcessStatus());
                })
                .orElseGet(() -> passed("BPM_APPROVED", "无 BPM 实例，已通过平台审批进入待放款"));
    }

    private FinancePreCheckItem checkCredit(FnFinanceApplication app, FinancePreCheckRequest request) {
        BigDecimal required = resolveDisburseAmount(app, request);
        if (app.getCreditId() == null || app.getCreditId().isBlank()) {
            return warning("CREDIT_AVAILABLE", "未关联授信，跳过额度校验");
        }
        CrCredit credit = creditRepository
                .findByIdAndOperatorIdAndProjectId(app.getCreditId(), app.getOperatorId(), app.getProjectId())
                .orElse(null);
        if (credit == null) {
            return failed("CREDIT_AVAILABLE", "关联授信不存在");
        }
        if (!"ACTIVE".equals(credit.getCreditStatus())) {
            return failed("CREDIT_AVAILABLE", "授信状态不可用：" + credit.getCreditStatus());
        }
        if (!app.getCurrency().equals(credit.getCurrency())) {
            return failed("CREDIT_AVAILABLE", "授信币种与融资申请不一致");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(credit.getStartDate()) || today.isAfter(credit.getEndDate())) {
            return failed("CREDIT_AVAILABLE", "授信不在有效期内");
        }
        if (credit.getAvailableLimit().compareTo(required) < 0) {
            return failed(
                    "CREDIT_AVAILABLE",
                    "可用额度不足，需要 " + required.toPlainString() + "，可用 " + credit.getAvailableLimit().toPlainString());
        }
        return passed("CREDIT_AVAILABLE", "可用额度充足");
    }

    private FinancePreCheckItem checkDocuments(DocumentValidateResponse documentValidation) {
        if (documentValidation.passed()) {
            if (documentValidation.warnings() != null && !documentValidation.warnings().isEmpty()) {
                return warning("DOCUMENTS", "必备单证齐备，存在 " + documentValidation.warnings().size() + " 条 OCR 预警");
            }
            return passed("DOCUMENTS", "必备单证齐备且复核通过");
        }
        int missing = documentValidation.missing() == null ? 0 : documentValidation.missing().size();
        int pending = documentValidation.pendingReview() == null ? 0 : documentValidation.pendingReview().size();
        return failed("DOCUMENTS", "单证校验未通过：缺失 " + missing + " 项，待复核 " + pending + " 项");
    }

    private List<FinancePreCheckItem> checkAccounts(FnFinanceApplication app, FinancePreCheckRequest request) {
        List<FinancePreCheckItem> items = new ArrayList<>();
        if (request.payerAccountId() == null || request.payerAccountId().isBlank()) {
            items.add(warning("PAYER_ACCOUNT", "未指定出款账户，跳过余额校验"));
        } else {
            items.add(checkPayerAccount(app, request));
        }
        if (request.receiverAccountId() == null || request.receiverAccountId().isBlank()) {
            items.add(warning("RECEIVER_ACCOUNT", "未指定收款账户，跳过账户校验"));
        } else {
            items.add(checkReceiverAccount(app, request));
        }
        if (request.fundingChannel() != null && !request.fundingChannel().isBlank()
                && !ALLOWED_CHANNELS.contains(request.fundingChannel())) {
            items.add(failed("FUNDING_CHANNEL", "不支持的 funding_channel"));
        }
        return items;
    }

    private FinancePreCheckItem checkPayerAccount(FnFinanceApplication app, FinancePreCheckRequest request) {
        BigDecimal amount = resolveDisburseAmount(app, request);
        try {
            AcctVirtualAccount payer = loadActiveAccount(
                    request.payerAccountId(), app.getOperatorId(), app.getProjectId(),
                    app.getFundingPartyId(), app.getCurrency());
            if (payer.getBalance().compareTo(amount) < 0) {
                return failed("PAYER_ACCOUNT", "出款账户余额不足");
            }
            return passed("PAYER_ACCOUNT", "出款账户余额充足");
        } catch (BusinessException ex) {
            return failed("PAYER_ACCOUNT", ex.getMessage());
        }
    }

    private FinancePreCheckItem checkReceiverAccount(FnFinanceApplication app, FinancePreCheckRequest request) {
        try {
            loadActiveAccount(
                    request.receiverAccountId(), app.getOperatorId(), app.getProjectId(),
                    app.getCustomerId(), app.getCurrency());
            return passed("RECEIVER_ACCOUNT", "收款账户可用");
        } catch (BusinessException ex) {
            return failed("RECEIVER_ACCOUNT", ex.getMessage());
        }
    }

    private FinancePreCheckItem checkIdempotency(
            String financeId, FinancePreCheckRequest request, String idempotencyRequestBody) {
        String key = request.idempotencyKey();
        if (key == null || key.isBlank()) {
            return warning("IDEMPOTENCY_KEY", "放款接口要求 X-Idempotency-Key");
        }
        String requestBody = idempotencyRequestBody != null && !idempotencyRequestBody.isBlank()
                ? idempotencyRequestBody
                : buildDisbursePayload(financeId, request);
        String requestHash = IdGenerator.sha256(requestBody);
        var conflict = idempotencyRepository.findFirstByIdempotencyKey(key.trim());
        if (conflict.isPresent() && !requestHash.equals(conflict.get().getRequestHash())) {
            return failed("IDEMPOTENCY_KEY", "幂等键与历史请求参数不一致");
        }
        return passed("IDEMPOTENCY_KEY", "幂等键可用");
    }

    private FinancePreCheckItem checkSecondaryAuth(FinancePreCheckRequest request) {
        String token = request.secondaryAuthToken();
        if (token == null || token.isBlank()) {
            return warning("SECONDARY_AUTH", "放款前需二次确认令牌");
        }
        try {
            secondaryAuthVerifier.requireValid(token);
            return passed("SECONDARY_AUTH", "二次确认令牌有效");
        } catch (BusinessException ex) {
            return failed("SECONDARY_AUTH", ex.getMessage());
        }
    }

    private AcctVirtualAccount loadActiveAccount(
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

    private BigDecimal resolveDisburseAmount(FnFinanceApplication app, FinancePreCheckRequest request) {
        if (request.disburseAmount() != null && !request.disburseAmount().isBlank()) {
            return parsePositiveAmount(request.disburseAmount());
        }
        BigDecimal approved = app.getApprovedAmount() == null ? BigDecimal.ZERO : app.getApprovedAmount();
        BigDecimal disbursed = app.getDisbursedAmount() == null ? BigDecimal.ZERO : app.getDisbursedAmount();
        return approved.subtract(disbursed);
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

    private String buildDisbursePayload(String financeId, FinancePreCheckRequest request) {
        FinanceDisburseRequest body = new FinanceDisburseRequest(
                request.disburseAmount(),
                request.currency(),
                request.valueDate(),
                request.payerAccountId(),
                request.receiverAccountId(),
                request.fundingChannel(),
                null);
        try {
            return objectMapper.writeValueAsString(Map.of("finance_id", financeId, "body", body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize pre-check disburse payload", ex);
        }
    }

    private FnFinanceApplication loadAccessible(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        FnFinanceApplication app = financeRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, app.getCustomerId(), app.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权访问该融资申请", 403);
        }
        return app;
    }

    private static FinancePreCheckRequest emptyRequest() {
        return new FinancePreCheckRequest(null, null, null, null, null, null, null, null);
    }

    private static FinancePreCheckItem passed(String code, String message) {
        return new FinancePreCheckItem(code, "PASSED", message);
    }

    private static FinancePreCheckItem failed(String code, String message) {
        return new FinancePreCheckItem(code, "FAILED", message);
    }

    private static FinancePreCheckItem warning(String code, String message) {
        return new FinancePreCheckItem(code, "WARNING", message);
    }

    private static String summarizeFailures(FinancePreCheckResponse response) {
        return response.checks().stream()
                .filter(item -> "FAILED".equals(item.result()))
                .map(FinancePreCheckItem::message)
                .findFirst()
                .orElse("放款前置校验未通过");
    }
}
