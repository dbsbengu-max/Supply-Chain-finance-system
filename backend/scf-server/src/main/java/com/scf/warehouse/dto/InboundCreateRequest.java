package com.scf.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InboundCreateRequest(
        @NotBlank String warehouse_id,
        @NotBlank String sku_id,
        @NotBlank String batch_no,
        @NotBlank String owner_id,
        String location_code,
        @NotNull @DecimalMin("0.000001") BigDecimal quantity,
        BigDecimal valuation_amount,
        String currency) {
}
