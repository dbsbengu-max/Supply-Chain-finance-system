package com.scf.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinanceDisburseRequest(
        @NotBlank @JsonProperty("disburse_amount") String disburseAmount,
        @NotBlank String currency,
        @NotBlank @JsonProperty("value_date") String valueDate,
        @NotBlank @JsonProperty("payer_account_id") String payerAccountId,
        @NotBlank @JsonProperty("receiver_account_id") String receiverAccountId,
        @NotBlank @JsonProperty("funding_channel") String fundingChannel,
        @Size(max = 500) String remark
) {
}
