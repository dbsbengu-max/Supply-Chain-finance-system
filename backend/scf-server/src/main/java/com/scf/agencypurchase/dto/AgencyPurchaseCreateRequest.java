package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgencyPurchaseCreateRequest(
        @NotBlank @JsonProperty("order_mode") String orderMode,
        @NotBlank @JsonProperty("fund_source") String fundSource,
        @NotBlank @JsonProperty("pickup_type") String pickupType,
        @NotBlank @JsonProperty("customer_id") String customerId,
        @NotBlank @JsonProperty("trade_company_id") String tradeCompanyId,
        @JsonProperty("order_id") String orderId,
        @JsonProperty("inventory_id") String inventoryId,
        @JsonProperty("margin_account_id") String marginAccountId,
        @JsonProperty("margin_amount") String marginAmount,
        @JsonProperty("inventory_freeze_quantity") String inventoryFreezeQuantity,
        @NotBlank String currency,
        @NotBlank @JsonProperty("total_amount") String totalAmount,
        String remark
) {
}
