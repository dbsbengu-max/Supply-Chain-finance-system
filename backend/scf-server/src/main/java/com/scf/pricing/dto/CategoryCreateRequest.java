package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CategoryCreateRequest(
        @NotBlank @JsonProperty("category_code") String categoryCode,
        @NotBlank @JsonProperty("category_name") String categoryName,
        @NotBlank @JsonProperty("category_type") String categoryType,
        @NotBlank @JsonProperty("default_unit") String defaultUnit
) {
}
