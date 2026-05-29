package com.scf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.clearing.entity.ClearingResult;
import com.scf.clearing.entity.FnRepayment;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.saga.service.OutboxDispatcher;
import com.scf.voucher.dto.VoucherDtos.RepaymentSettledPayload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RepaymentSettledOutboxPublisher {

    private final OutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    public RepaymentSettledOutboxPublisher(OutboxDispatcher outboxDispatcher, ObjectMapper objectMapper) {
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
    }

    public void publishRepaymentSettled(
            FnFinanceApplication finance,
            FnRepayment repayment,
            ClearingResult result,
            String financeStatus) {
        if (finance == null || repayment == null || result == null) {
            return;
        }
        if (!"VOUCHER".equals(finance.getSourceType())
                || finance.getSourceId() == null
                || finance.getSourceId().isBlank()) {
            return;
        }
        if (!"VOUCHER_FINANCE".equals(finance.getProductType())) {
            return;
        }
        BigDecimal principal = result.getPrincipalAmount() == null
                ? BigDecimal.ZERO
                : result.getPrincipalAmount();
        RepaymentSettledPayload payload = new RepaymentSettledPayload(
                finance.getId(),
                repayment.getId(),
                finance.getProductType(),
                finance.getSourceType(),
                finance.getSourceId(),
                finance.getProjectId(),
                finance.getOperatorId(),
                finance.getCustomerId(),
                principal.toPlainString(),
                financeStatus);
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize repayment settled payload", ex);
        }
        outboxDispatcher.publish(
                "REPAYMENT_SETTLED",
                "FINANCE_REPAYMENT",
                repayment.getId(),
                "REPAYMENT-SETTLED-" + repayment.getId(),
                payloadJson);
    }
}
