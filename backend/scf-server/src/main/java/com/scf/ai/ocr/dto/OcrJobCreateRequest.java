package com.scf.ai.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record OcrJobCreateRequest(
        @NotBlank @JsonProperty("file_id") String fileId,
        @NotBlank @JsonProperty("business_type") String businessType,
        @JsonProperty("business_id") String businessId,
        @NotBlank @JsonProperty("recognition_type") String recognitionType
) {
}
