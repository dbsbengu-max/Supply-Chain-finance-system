package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record PriceCreateRequest(
        @NotBlank @JsonProperty("sku_id") String skuId,
        @NotNull @JsonProperty("price_date") LocalDate priceDate,
        @NotBlank String price,
        @NotBlank String currency,
        @NotBlank String unit,
        @NotBlank @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_name") String sourceName,
        @NotBlank @JsonProperty("trust_level") String trustLevel
) {
}
