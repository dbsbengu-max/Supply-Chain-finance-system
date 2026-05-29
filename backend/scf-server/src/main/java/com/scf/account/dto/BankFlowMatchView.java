package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BankFlowMatchView(
        String id,
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("match_status") String matchStatus) {
}
