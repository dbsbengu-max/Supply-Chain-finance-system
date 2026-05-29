package com.scf.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DocumentCreateRequest(
        @NotBlank @JsonProperty("document_type") String documentType,
        @JsonProperty("document_no") String documentNo,
        @NotBlank @JsonProperty("file_id") String fileId
) {
}
