package com.scf.integration.bank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record BankDisburseCallbackRequest(
        @NotBlank @JsonProperty("channel_request_id") String channelRequestId,
        @NotBlank @JsonProperty("callback_status") String callbackStatus,
        @JsonProperty("external_flow_no") String externalFlowNo,
        @NotBlank String amount,
        @NotBlank String currency,
        @JsonProperty("flow_time") Instant flowTime,
        @JsonProperty("counterparty_name") String counterpartyName,
        @JsonProperty("counterparty_account") String counterpartyAccount
) {
}
