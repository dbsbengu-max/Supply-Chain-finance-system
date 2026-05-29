package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ClearingEntryView(
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("finance_no") String financeNo,
        @JsonProperty("finance_status") String financeStatus,
        @JsonProperty("outstanding_principal") String outstandingPrincipal,
        String currency,
        @JsonProperty("unmatched_flows") List<BankFlowView> unmatchedFlows,
        @JsonProperty("clearing_rules") List<ClearingRuleOption> clearingRules) {

    public record ClearingRuleOption(
            String id,
            @JsonProperty("rule_name") String ruleName,
            @JsonProperty("product_type") String productType) {
    }
}
