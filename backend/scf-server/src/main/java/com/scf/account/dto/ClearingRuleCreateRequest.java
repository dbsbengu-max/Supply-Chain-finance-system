package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ClearingRuleCreateRequest(
        @JsonProperty("funding_party_id") String fundingPartyId,
        @NotBlank @JsonProperty("product_type") String productType,
        @NotBlank @JsonProperty("rule_name") String ruleName,
        @NotBlank @JsonProperty("priority_json") String priorityJson,
        @JsonProperty("fee_formula_json") String feeFormulaJson,
        @NotBlank @JsonProperty("currency_rule") String currencyRule,
        @NotNull @JsonProperty("effective_from") LocalDate effectiveFrom,
        @JsonProperty("effective_to") LocalDate effectiveTo) {
}
