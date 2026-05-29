package com.scf.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FinanceCreateRequest(
        @NotBlank @JsonProperty("product_type") String productType,
        @NotBlank @JsonProperty("source_type") String sourceType,
        @NotBlank @JsonProperty("source_id") String sourceId,
        @NotBlank @JsonProperty("customer_id") String customerId,
        @NotBlank @JsonProperty("funding_party_id") String fundingPartyId,
        @JsonProperty("credit_id") String creditId,
        @NotBlank @JsonProperty("apply_amount") String applyAmount,
        @NotBlank String currency,
        @NotNull @JsonProperty("term_days") Integer termDays,
        @NotBlank @JsonProperty("annual_rate") String annualRate,
        @JsonProperty("guarantee_amount") String guaranteeAmount,
        @JsonProperty("pledge_rate") String pledgeRate
) {
}
