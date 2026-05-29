package com.scf.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProjectUpdateRequest(
        @JsonProperty("project_name") String projectName,
        String countries,
        String currencies,
        String status
) {
}
