package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ClearingCalculateRequest(
        @NotBlank @JsonProperty("finance_id") String financeId,
        @NotBlank @JsonProperty("bank_flow_id") String bankFlowId,
        @NotBlank @JsonProperty("clearing_rule_id") String clearingRuleId) {
}
