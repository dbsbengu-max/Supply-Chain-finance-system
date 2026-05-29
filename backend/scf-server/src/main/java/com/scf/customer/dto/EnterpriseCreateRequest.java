package com.scf.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record EnterpriseCreateRequest(
        @NotBlank @JsonProperty("enterprise_name") String enterpriseName,
        @NotBlank @JsonProperty("enterprise_type") String enterpriseType,
        @NotBlank @JsonProperty("country_region") String countryRegion,
        @JsonProperty("registration_no") String registrationNo,
        @JsonProperty("unified_credit_code") String unifiedCreditCode,
        @JsonProperty("legal_person") String legalPerson
) {
}
