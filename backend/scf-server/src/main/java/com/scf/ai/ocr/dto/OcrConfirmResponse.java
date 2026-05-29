package com.scf.ai.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OcrConfirmResponse(
        @JsonProperty("job_id") String jobId,
        boolean confirmed,
        @JsonProperty("applied_to_business") boolean appliedToBusiness,
        String message
) {
}
