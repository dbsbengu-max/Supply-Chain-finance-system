package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AgencyPurchaseSagaStepView(
        @JsonProperty("step_code") String stepCode,
        @JsonProperty("step_status") String stepStatus,
        @JsonProperty("detail_json") String detailJson,
        @JsonProperty("executed_at") Instant executedAt
) {
}
