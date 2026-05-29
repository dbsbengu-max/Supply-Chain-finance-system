package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.pricing.entity.FxRate;

import java.time.Instant;
import java.time.LocalDate;

public record FxRateView(
        String id,
        @JsonProperty("base_currency") String baseCurrency,
        @JsonProperty("quote_currency") String quoteCurrency,
        String rate,
        @JsonProperty("rate_date") LocalDate rateDate,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("review_status") String reviewStatus,
        @JsonProperty("created_at") Instant createdAt
) {
    public static FxRateView from(FxRate r) {
        return new FxRateView(
                r.getId(), r.getBaseCurrency(), r.getQuoteCurrency(), r.getRate().toPlainString(),
                r.getRateDate(), r.getSourceType(), r.getReviewStatus(), r.getCreatedAt());
    }
}
