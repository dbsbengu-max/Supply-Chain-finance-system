package com.scf.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.customer.entity.MdEnterprise;

import java.time.Instant;

public record EnterpriseView(
        String id,
        @JsonProperty("operator_id") String operatorId,
        @JsonProperty("enterprise_code") String enterpriseCode,
        @JsonProperty("enterprise_name") String enterpriseName,
        @JsonProperty("enterprise_type") String enterpriseType,
        @JsonProperty("country_region") String countryRegion,
        @JsonProperty("registration_no") String registrationNo,
        @JsonProperty("unified_credit_code") String unifiedCreditCode,
        @JsonProperty("legal_person") String legalPerson,
        @JsonProperty("kyc_status") String kycStatus,
        @JsonProperty("risk_level") String riskLevel,
        String status,
        @JsonProperty("created_at") Instant createdAt
) {
    public static EnterpriseView from(MdEnterprise e) {
        return new EnterpriseView(
                e.getId(), e.getOperatorId(), e.getEnterpriseCode(), e.getEnterpriseName(),
                e.getEnterpriseType(), e.getCountryRegion(), e.getRegistrationNo(),
                e.getUnifiedCreditCode(), e.getLegalPerson(), e.getKycStatus(),
                e.getRiskLevel(), e.getStatus(), e.getCreatedAt());
    }
}
