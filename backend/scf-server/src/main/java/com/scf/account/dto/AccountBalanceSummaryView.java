package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccountBalanceSummaryView(
        String id,
        @JsonProperty("account_type") String accountType,
        @JsonProperty("account_no") String accountNo,
        @JsonProperty("account_name") String accountName,
        String currency,
        String balance,
        @JsonProperty("frozen_balance") String frozenBalance,
        @JsonProperty("available_balance") String availableBalance,
        @JsonProperty("enterprise_id") String enterpriseId,
        @JsonProperty("funding_party_id") String fundingPartyId) {
}
