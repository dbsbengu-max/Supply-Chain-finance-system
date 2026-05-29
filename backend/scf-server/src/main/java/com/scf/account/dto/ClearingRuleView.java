package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record ClearingRuleView(
        String id,
        @JsonProperty("operator_id") String operatorId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("funding_party_id") String fundingPartyId,
        @JsonProperty("product_type") String productType,
        @JsonProperty("rule_name") String ruleName,
        @JsonProperty("priority_json") String priorityJson,
        @JsonProperty("fee_formula_json") String feeFormulaJson,
        @JsonProperty("currency_rule") String currencyRule,
        @JsonProperty("effective_from") LocalDate effectiveFrom,
        @JsonProperty("effective_to") LocalDate effectiveTo,
        @JsonProperty("review_status") String reviewStatus,
        @JsonProperty("version_no") int versionNo) {
}
