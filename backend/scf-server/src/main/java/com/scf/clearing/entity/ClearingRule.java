package com.scf.clearing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "clearing_rule", schema = "scf")
public class ClearingRule {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "funding_party_id")
    private String fundingPartyId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(name = "priority_json", nullable = false)
    private String priorityJson;

    @Column(name = "fee_formula_json")
    private String feeFormulaJson;

    @Column(name = "currency_rule", nullable = false)
    private String currencyRule;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    public String getId() {
        return id;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getFundingPartyId() {
        return fundingPartyId;
    }

    public String getProductType() {
        return productType;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getPriorityJson() {
        return priorityJson;
    }

    public String getFeeFormulaJson() {
        return feeFormulaJson;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public String getCurrencyRule() {
        return currencyRule;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public void setFundingPartyId(String fundingPartyId) {
        this.fundingPartyId = fundingPartyId;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public void setPriorityJson(String priorityJson) {
        this.priorityJson = priorityJson;
    }

    public void setFeeFormulaJson(String feeFormulaJson) {
        this.feeFormulaJson = feeFormulaJson;
    }

    public void setCurrencyRule(String currencyRule) {
        this.currencyRule = currencyRule;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }
}
