package com.scf.finance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.saga.service.OutboxDispatcher;
import com.scf.voucher.dto.VoucherDtos.FinanceDisbursedPayload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FinanceDisburseOutboxPublisher {

    private final OutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    public FinanceDisburseOutboxPublisher(OutboxDispatcher outboxDispatcher, ObjectMapper objectMapper) {
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
    }

    public void publishFinanceDisbursed(FnFinanceApplication app, BigDecimal disbursedAmount) {
        if (app == null || !"DISBURSED".equals(app.getFinanceStatus())) {
            return;
        }
        FinanceDisbursedPayload payload = new FinanceDisbursedPayload(
                app.getId(),
                app.getProductType(),
                app.getSourceType(),
                app.getSourceId(),
                app.getProjectId(),
                app.getOperatorId(),
                disbursedAmount.toPlainString(),
                app.getCustomerId());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize finance disbursed payload", ex);
        }
        outboxDispatcher.publish(
                "FINANCE_DISBURSED",
                "FINANCE_APPLICATION",
                app.getId(),
                "FINANCE-DISBURSED-" + app.getId(),
                payloadJson);
    }
}
