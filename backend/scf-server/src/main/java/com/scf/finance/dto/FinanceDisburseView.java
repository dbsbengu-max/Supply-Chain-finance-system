package com.scf.finance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FinanceDisburseView(
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("finance_no") String financeNo,
        String status,
        @JsonProperty("disbursement_id") String disbursementId,
        @JsonProperty("disburse_amount") String disburseAmount,
        String currency,
        @JsonProperty("value_date") LocalDate valueDate,
        @JsonProperty("payer_account_id") String payerAccountId,
        @JsonProperty("receiver_account_id") String receiverAccountId,
        @JsonProperty("funding_channel") String fundingChannel,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("disbursement_status") String disbursementStatus,
        @JsonProperty("channel_request_id") String channelRequestId,
        @JsonProperty("idempotent_replay") Boolean idempotentReplay
) {
    public FinanceDisburseView withIdempotentReplay(boolean replay) {
        return new FinanceDisburseView(
                financeId,
                financeNo,
                status,
                disbursementId,
                disburseAmount,
                currency,
                valueDate,
                payerAccountId,
                receiverAccountId,
                fundingChannel,
                idempotencyKey,
                createdAt,
                disbursementStatus,
                channelRequestId,
                replay);
    }
}
