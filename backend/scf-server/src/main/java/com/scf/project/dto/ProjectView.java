package com.scf.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.project.entity.SysProject;

import java.time.Instant;

public record ProjectView(
        String id,
        @JsonProperty("operator_id") String operatorId,
        @JsonProperty("project_code") String projectCode,
        @JsonProperty("project_name") String projectName,
        String countries,
        String currencies,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {
    public static ProjectView from(SysProject p) {
        return new ProjectView(p.getId(), p.getOperatorId(), p.getProjectCode(), p.getProjectName(),
                p.getCountries(), p.getCurrencies(), p.getStatus(), p.getCreatedAt());
    }
}
