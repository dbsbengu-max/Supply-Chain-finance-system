package com.scf.finance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.finance.entity.FnFinanceApplication;

import java.time.Instant;

public record FinanceView(
        String id,
        @JsonProperty("finance_no") String financeNo,
        @JsonProperty("product_type") String productType,
        @JsonProperty("source_type") String sourceType,
        @JsonProperty("source_id") String sourceId,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("funding_party_id") String fundingPartyId,
        @JsonProperty("credit_id") String creditId,
        @JsonProperty("apply_amount") String applyAmount,
        @JsonProperty("approved_amount") String approvedAmount,
        String currency,
        @JsonProperty("term_days") int termDays,
        @JsonProperty("annual_rate") String annualRate,
        @JsonProperty("finance_status") String financeStatus,
        @JsonProperty("created_at") Instant createdAt
) {
    public static FinanceView from(FnFinanceApplication f) {
        return new FinanceView(
                f.getId(),
                f.getFinanceNo(),
                f.getProductType(),
                f.getSourceType(),
                f.getSourceId(),
                f.getCustomerId(),
                f.getFundingPartyId(),
                f.getCreditId(),
                f.getApplyAmount().toPlainString(),
                f.getApprovedAmount() == null ? null : f.getApprovedAmount().toPlainString(),
                f.getCurrency(),
                f.getTermDays(),
                f.getAnnualRate().toPlainString(),
                f.getFinanceStatus(),
                f.getCreatedAt());
    }
}
