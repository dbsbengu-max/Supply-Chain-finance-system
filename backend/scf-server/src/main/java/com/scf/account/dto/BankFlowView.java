package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record BankFlowView(
        String id,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("external_flow_no") String externalFlowNo,
        @JsonProperty("flow_type") String flowType,
        String amount,
        String currency,
        @JsonProperty("counterparty_name") String counterpartyName,
        @JsonProperty("counterparty_account") String counterpartyAccount,
        @JsonProperty("flow_time") Instant flowTime,
        @JsonProperty("match_status") String matchStatus,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_id") String sourceId) {
}
