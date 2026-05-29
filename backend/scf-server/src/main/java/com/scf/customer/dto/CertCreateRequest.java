package com.scf.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record CertCreateRequest(
        @NotBlank @JsonProperty("cert_type") String certType,
        @JsonProperty("cert_no") String certNo,
        @NotBlank @JsonProperty("file_id") String fileId
) {
}
