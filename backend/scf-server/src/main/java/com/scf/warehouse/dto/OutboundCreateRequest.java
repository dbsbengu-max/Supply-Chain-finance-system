package com.scf.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OutboundCreateRequest(
        @NotBlank String inventory_id,
        @NotNull @DecimalMin("0.000001") BigDecimal quantity,
        String remark) {
}
