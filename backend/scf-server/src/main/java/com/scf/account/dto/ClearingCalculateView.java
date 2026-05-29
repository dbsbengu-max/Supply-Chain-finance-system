package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClearingCalculateView(
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("bank_flow_id") String bankFlowId,
        @JsonProperty("clearing_rule_id") String clearingRuleId,
        @JsonProperty("repayment_amount") String repaymentAmount,
        String currency,
        Allocation allocation,
        List<String> warnings) {

    public record Allocation(
            @JsonProperty("penalty_amount") String penaltyAmount,
            @JsonProperty("fee_amount") String feeAmount,
            @JsonProperty("interest_amount") String interestAmount,
            @JsonProperty("principal_amount") String principalAmount,
            @JsonProperty("remaining_amount") String remainingAmount) {
    }

    public ClearingCalculateView withIdempotentReplay(boolean replay) {
        return this;
    }
}
