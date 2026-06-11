package com.scf.agencypurchase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.agencypurchase.dto.AgencyPurchaseApprovedPayload;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;
import com.scf.saga.service.OutboxDispatcher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AgencyPurchaseApprovedOutboxPublisher {

    public static final String EVENT_TYPE = "AGENCY_PURCHASE_APPROVED";
    public static final String BUSINESS_TYPE = "AGENCY_PURCHASE";

    private final OutboxDispatcher outboxDispatcher;
    private final ObjectMapper objectMapper;

    public AgencyPurchaseApprovedOutboxPublisher(OutboxDispatcher outboxDispatcher, ObjectMapper objectMapper) {
        this.outboxDispatcher = outboxDispatcher;
        this.objectMapper = objectMapper;
    }

    public void publishApproved(ApAgencyPurchaseApplication app) {
        AgencyPurchaseApprovedPayload payload = new AgencyPurchaseApprovedPayload(
                app.getId(),
                app.getOperatorId(),
                app.getProjectId(),
                app.getOrderMode(),
                app.getFundSource(),
                app.getCustomerId(),
                app.getTradeCompanyId(),
                app.getOrderId(),
                app.getInventoryId(),
                app.getMarginAccountId(),
                money(app.getMarginAmount()),
                qty(app.getInventoryFreezeQuantity()),
                app.getTotalAmount().toPlainString(),
                app.getCurrency());
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agency purchase approved payload", ex);
        }
        outboxDispatcher.publish(
                EVENT_TYPE,
                BUSINESS_TYPE,
                app.getId(),
                "AGENCY-PURCHASE-APPROVED-" + app.getId(),
                payloadJson);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String qty(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
