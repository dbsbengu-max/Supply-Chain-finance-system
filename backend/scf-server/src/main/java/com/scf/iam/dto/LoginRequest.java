package com.scf.iam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @JsonProperty("login_name") String loginName,
        @NotBlank String password
) {
}
