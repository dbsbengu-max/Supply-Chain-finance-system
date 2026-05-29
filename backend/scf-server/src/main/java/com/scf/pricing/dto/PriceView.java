package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.pricing.entity.PrPriceRecord;

import java.time.Instant;
import java.time.LocalDate;

public record PriceView(
        String id,
        @JsonProperty("sku_id") String skuId,
        @JsonProperty("price_date") LocalDate priceDate,
        String price,
        String currency,
        String unit,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_name") String sourceName,
        @JsonProperty("trust_level") String trustLevel,
        @JsonProperty("review_status") String reviewStatus,
        @JsonProperty("created_at") Instant createdAt
) {
    public static PriceView from(PrPriceRecord r) {
        return new PriceView(
                r.getId(), r.getSkuId(), r.getPriceDate(), r.getPrice().toPlainString(),
                r.getCurrency(), r.getUnit(), r.getSourceType(), r.getSourceName(),
                r.getTrustLevel(), r.getReviewStatus(), r.getCreatedAt());
    }
}
