package com.scf.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank @JsonProperty("project_code") String projectCode,
        @NotBlank @JsonProperty("project_name") String projectName,
        @NotBlank String countries,
        @NotBlank String currencies
) {
}
