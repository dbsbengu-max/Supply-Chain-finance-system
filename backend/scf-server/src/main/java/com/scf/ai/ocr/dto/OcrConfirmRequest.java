package com.scf.ai.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record OcrConfirmRequest(
        @NotEmpty @JsonProperty("confirmed_fields") Map<String, String> confirmedFields,
        @JsonProperty("reject_reason") String rejectReason
) {
}
