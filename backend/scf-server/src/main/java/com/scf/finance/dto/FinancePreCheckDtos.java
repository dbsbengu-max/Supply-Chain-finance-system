package com.scf.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.document.dto.DocumentDtos.DocumentValidateResponse;

import java.util.List;

public final class FinancePreCheckDtos {

    private FinancePreCheckDtos() {
    }

    public record FinancePreCheckRequest(
            @JsonProperty("disburse_amount") String disburseAmount,
            String currency,
            @JsonProperty("value_date") String valueDate,
            @JsonProperty("payer_account_id") String payerAccountId,
            @JsonProperty("receiver_account_id") String receiverAccountId,
            @JsonProperty("funding_channel") String fundingChannel,
            @JsonProperty("idempotency_key") String idempotencyKey,
            @JsonProperty("secondary_auth_token") String secondaryAuthToken
    ) {
    }

    public record FinancePreCheckItem(
            String code,
            String result,
            String message
    ) {
    }

    public record FinancePreCheckResponse(
            @JsonProperty("finance_id") String financeId,
            boolean passed,
            List<FinancePreCheckItem> checks,
            @JsonProperty("document_validation") DocumentValidateResponse documentValidation
    ) {
    }
}
