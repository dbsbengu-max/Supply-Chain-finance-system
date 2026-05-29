package com.scf.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record EnterpriseUpdateRequest(
        @JsonProperty("enterprise_name") String enterpriseName,
        @JsonProperty("registration_no") String registrationNo,
        @JsonProperty("unified_credit_code") String unifiedCreditCode,
        @JsonProperty("legal_person") String legalPerson,
        @JsonProperty("risk_level") String riskLevel
) {
}
