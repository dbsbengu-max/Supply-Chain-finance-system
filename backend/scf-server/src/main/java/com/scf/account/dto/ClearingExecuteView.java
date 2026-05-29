package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClearingExecuteView(
        @JsonProperty("repayment_id") String repaymentId,
        @JsonProperty("clearing_result_id") String clearingResultId,
        @JsonProperty("finance_id") String financeId,
        @JsonProperty("finance_status") String financeStatus,
        @JsonProperty("repayment_amount") String repaymentAmount,
        String currency,
        ClearingCalculateView.Allocation allocation,
        List<String> warnings,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("idempotent_replay") Boolean idempotentReplay) {

    public ClearingExecuteView withIdempotentReplay(boolean replay) {
        return new ClearingExecuteView(
                repaymentId,
                clearingResultId,
                financeId,
                financeStatus,
                repaymentAmount,
                currency,
                allocation,
                warnings,
                createdAt,
                replay);
    }
}
