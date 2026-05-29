package com.scf.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record OrderItemRequest(
        @NotBlank @JsonProperty("sku_id") String skuId,
        @NotBlank String quantity,
        @NotBlank String unit,
        @NotBlank @JsonProperty("unit_price") String unitPrice,
        @JsonProperty("delivery_date") LocalDate deliveryDate
) {
}
