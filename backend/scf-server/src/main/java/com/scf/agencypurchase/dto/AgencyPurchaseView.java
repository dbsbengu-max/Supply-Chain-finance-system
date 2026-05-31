package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.agencypurchase.entity.ApAgencyPurchaseApplication;

import java.time.Instant;

public record AgencyPurchaseView(
        String id,
        @JsonProperty("application_no") String applicationNo,
        @JsonProperty("order_mode") String orderMode,
        @JsonProperty("fund_source") String fundSource,
        @JsonProperty("pickup_type") String pickupType,
        @JsonProperty("mode_key") String modeKey,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("trade_company_id") String tradeCompanyId,
        @JsonProperty("order_id") String orderId,
        String currency,
        @JsonProperty("total_amount") String totalAmount,
        @JsonProperty("application_status") String applicationStatus,
        @JsonProperty("saga_status") String sagaStatus,
        String remark,
        @JsonProperty("bpm_instance_id") String bpmInstanceId,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt
) {
    public static AgencyPurchaseView from(ApAgencyPurchaseApplication app) {
        return new AgencyPurchaseView(
                app.getId(),
                app.getApplicationNo(),
                app.getOrderMode(),
                app.getFundSource(),
                app.getPickupType(),
                app.getModeKey(),
                app.getCustomerId(),
                app.getTradeCompanyId(),
                app.getOrderId(),
                app.getCurrency(),
                app.getTotalAmount().toPlainString(),
                app.getApplicationStatus(),
                app.getSagaStatus(),
                app.getRemark(),
                app.getBpmInstanceId(),
                app.getCreatedBy(),
                app.getCreatedAt(),
                app.getUpdatedAt());
    }
}
