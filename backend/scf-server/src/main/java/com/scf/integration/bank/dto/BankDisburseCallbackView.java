package com.scf.integration.bank.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BankDisburseCallbackView(
        @JsonProperty("disbursement_id") String disbursementId,
        @JsonProperty("disbursement_status") String disbursementStatus,
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("finance_status") String financeStatus,
        @JsonProperty("channel_request_id") String channelRequestId,
        @JsonProperty("external_flow_no") String externalFlowNo,
        @JsonProperty("idempotent_replay") Boolean idempotentReplay
) {
    public BankDisburseCallbackView withIdempotentReplay(boolean replay) {
        return new BankDisburseCallbackView(
                disbursementId,
                disbursementStatus,
                financeId,
                financeStatus,
                channelRequestId,
                externalFlowNo,
                replay);
    }
}
