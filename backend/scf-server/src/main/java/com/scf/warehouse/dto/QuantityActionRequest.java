package com.scf.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record QuantityActionRequest(
        @NotNull @DecimalMin("0.000001") BigDecimal quantity,
        String remark) {
}
