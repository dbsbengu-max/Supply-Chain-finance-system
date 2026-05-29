package com.scf.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record OrderCreateRequest(
        @NotBlank @JsonProperty("order_type") String orderType,
        @NotBlank @JsonProperty("buyer_id") String buyerId,
        @NotBlank @JsonProperty("seller_id") String sellerId,
        @JsonProperty("trade_company_id") String tradeCompanyId,
        @NotBlank String currency,
        @JsonProperty("country_from") String countryFrom,
        @JsonProperty("country_to") String countryTo,
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}
