package com.scf.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SkuCreateRequest(
        @NotBlank @JsonProperty("category_id") String categoryId,
        @NotBlank @JsonProperty("sku_code") String skuCode,
        @NotBlank String spec,
        String grade,
        String origin,
        @JsonProperty("package_type") String packageType,
        @NotBlank String unit
) {
}
