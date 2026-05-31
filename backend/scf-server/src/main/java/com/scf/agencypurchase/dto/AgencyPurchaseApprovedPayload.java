package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgencyPurchaseApprovedPayload(
        @JsonProperty("application_id") String applicationId,
        @JsonProperty("operator_id") String operatorId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("order_mode") String orderMode,
        @JsonProperty("fund_source") String fundSource,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("trade_company_id") String tradeCompanyId,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("inventory_id") String inventoryId,
        @JsonProperty("margin_account_id") String marginAccountId,
        @JsonProperty("margin_amount") String marginAmount,
        @JsonProperty("inventory_freeze_quantity") String inventoryFreezeQuantity,
        @JsonProperty("total_amount") String totalAmount,
        String currency
) {
}
