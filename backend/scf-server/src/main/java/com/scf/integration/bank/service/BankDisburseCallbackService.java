package com.scf.integration.bank.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;
import com.scf.finance.entity.FnDisbursement;
import com.scf.finance.repository.FnDisbursementRepository;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.finance.service.DisburseCompletionService;
import com.scf.idempotency.dto.IdempotentExecutionResult;
import com.scf.idempotency.service.IdempotencyService;
import com.scf.integration.bank.config.BankCallbackProperties;
import com.scf.integration.bank.dto.BankDisburseCallbackRequest;
import com.scf.integration.bank.dto.BankDisburseCallbackView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Service
public class BankDisburseCallbackService {

    private static final Set<String> ALLOWED_STATUS = Set.of("SUCCESS", "FAILED");
    private static final String SYSTEM_USER = "SYSTEM_BANK_CALLBACK";
    private static final String SYSTEM_OPERATOR = "OP001";

    private final BankCallbackProperties bankCallbackProperties;
    private final FnDisbursementRepository disbursementRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final DisburseCompletionService disburseCompletionService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public BankDisburseCallbackService(
            BankCallbackProperties bankCallbackProperties,
            FnDisbursementRepository disbursementRepository,
            FnFinanceApplicationRepository financeRepository,
            DisburseCompletionService disburseCompletionService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.bankCallbackProperties = bankCallbackProperties;
        this.disbursementRepository = disbursementRepository;
        this.financeRepository = financeRepository;
        this.disburseCompletionService = disburseCompletionService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public BankDisburseCallbackView handleCallback(
            String callbackToken,
            String idempotencyKey,
            BankDisburseCallbackRequest request) {
        requireValidToken(callbackToken);
        String payload = buildPayload(request);
        IdempotentExecutionResult<BankDisburseCallbackView> result = idempotencyService.executeWithReplay(
                idempotencyKey,
                "BANK_DISBURSE_CALLBACK",
                payload,
                BankDisburseCallbackView.class,
                () -> processCallback(request));
        return result.replay() ? result.value().withIdempotentReplay(true) : result.value();
    }

    @Transactional
    protected BankDisburseCallbackView processCallback(BankDisburseCallbackRequest request) {
        if (!ALLOWED_STATUS.contains(request.callbackStatus())) {
            throw new BusinessException("VALID_400", "不支持的 callback_status", 400);
        }

        FnDisbursement disbursement = disbursementRepository
                .findByChannelRequestIdForUpdate(request.channelRequestId())
                .orElseThrow(() -> new BusinessException("DATA_404", "通道请求不存在", 404));

        if ("SUCCESS".equals(disbursement.getDisbursementStatus())) {
            return toView(disbursement, request.externalFlowNo());
        }
        if ("FAILED".equals(disbursement.getDisbursementStatus())) {
            return toView(disbursement, request.externalFlowNo());
        }

        BigDecimal callbackAmount = parseAmount(request.amount());
        if (callbackAmount.compareTo(disbursement.getAmount()) != 0) {
            throw new BusinessException("VALID_400", "回调金额与放款单不一致", 400);
        }
        if (!disbursement.getCurrency().equals(request.currency())) {
            throw new BusinessException("VALID_400", "回调币种与放款单不一致", 400);
        }

        if ("FAILED".equals(request.callbackStatus())) {
            disburseCompletionService.markFailed(
                    disbursement,
                    request.externalFlowNo(),
                    SYSTEM_USER,
                    SYSTEM_OPERATOR,
                    null);
            return toView(disbursementRepository.findById(disbursement.getId()).orElseThrow(), request.externalFlowNo());
        }

        if (request.externalFlowNo() == null || request.externalFlowNo().isBlank()) {
            throw new BusinessException("VALID_400", "SUCCESS 回调缺少 external_flow_no", 400);
        }

        disburseCompletionService.completeSuccess(
                disbursement,
                request.externalFlowNo(),
                request.flowTime(),
                request.counterpartyName(),
                request.counterpartyAccount(),
                request.externalFlowNo(),
                SYSTEM_USER,
                SYSTEM_OPERATOR,
                null);

        FnDisbursement updated = disbursementRepository.findById(disbursement.getId()).orElseThrow();
        return toView(updated, request.externalFlowNo());
    }

    private void requireValidToken(String callbackToken) {
        if (callbackToken == null || callbackToken.isBlank()) {
            throw new BusinessException("AUTH_403", "缺少 X-Bank-Callback-Token", 403);
        }
        if (!bankCallbackProperties.getToken().equals(callbackToken.trim())) {
            throw new BusinessException("AUTH_403", "银行回调鉴权失败", 403);
        }
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException("VALID_400", "amount 格式非法", 400);
        }
    }

    private String buildPayload(BankDisburseCallbackRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize bank callback request", ex);
        }
    }

    private BankDisburseCallbackView toView(FnDisbursement disbursement, String externalFlowNo) {
        String financeStatus = financeRepository.findFinanceStatusById(disbursement.getFinanceId())
                .orElse("UNKNOWN");
        return new BankDisburseCallbackView(
                disbursement.getId(),
                disbursement.getDisbursementStatus(),
                disbursement.getFinanceId(),
                financeStatus,
                disbursement.getChannelRequestId(),
                externalFlowNo != null ? externalFlowNo : disbursement.getChannelResponseId(),
                null);
    }
}
