package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record FxRateCreateRequest(
        @NotBlank @JsonProperty("base_currency") String baseCurrency,
        @NotBlank @JsonProperty("quote_currency") String quoteCurrency,
        @NotBlank String rate,
        @NotNull @JsonProperty("rate_date") LocalDate rateDate,
        @NotBlank @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_name") String sourceName
) {
}
