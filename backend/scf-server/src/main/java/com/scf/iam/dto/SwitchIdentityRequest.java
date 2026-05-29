package com.scf.iam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SwitchIdentityRequest(
        @NotBlank @JsonProperty("identity_id") String identityId
) {
}
