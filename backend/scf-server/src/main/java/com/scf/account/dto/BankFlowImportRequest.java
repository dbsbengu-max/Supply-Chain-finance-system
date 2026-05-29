package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BankFlowImportRequest(
        @NotEmpty @Valid List<BankFlowImportItem> flows) {

    public record BankFlowImportItem(
            @NotBlank @JsonProperty("account_id") String accountId,
            @NotBlank @JsonProperty("external_flow_no") String externalFlowNo,
            @NotBlank String amount,
            @NotBlank String currency,
            @JsonProperty("counterparty_name") String counterpartyName,
            @JsonProperty("counterparty_account") String counterpartyAccount,
            @NotBlank @JsonProperty("flow_time") String flowTime) {
    }
}
