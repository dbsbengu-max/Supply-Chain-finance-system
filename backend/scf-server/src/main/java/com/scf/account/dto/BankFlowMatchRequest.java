package com.scf.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record BankFlowMatchRequest(
        @NotBlank @JsonProperty("finance_id") String financeId) {
}
