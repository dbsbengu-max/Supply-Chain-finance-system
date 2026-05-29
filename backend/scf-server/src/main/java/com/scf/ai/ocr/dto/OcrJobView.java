package com.scf.ai.ocr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record OcrJobView(
        String id,
        @JsonProperty("file_id") String fileId,
        @JsonProperty("business_type") String businessType,
        @JsonProperty("business_id") String businessId,
        @JsonProperty("recognition_type") String recognitionType,
        String status,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("created_by") String createdBy,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("confirmed_by") String confirmedBy,
        @JsonProperty("confirmed_at") Instant confirmedAt,
        List<OcrFieldView> fields,
        @JsonProperty("pending_manual_count") int pendingManualCount
) {
}
