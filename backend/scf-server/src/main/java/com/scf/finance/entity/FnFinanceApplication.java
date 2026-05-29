package com.scf.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fn_finance_application", schema = "scf")
public class FnFinanceApplication {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "finance_no", nullable = false, unique = true)
    private String financeNo;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "funding_party_id", nullable = false)
    private String fundingPartyId;

    @Column(name = "credit_id")
    private String creditId;

    @Column(name = "product_type", nullable = false)
    private String productType;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "apply_amount", nullable = false)
    private BigDecimal applyAmount;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "disbursed_amount", nullable = false)
    private BigDecimal disbursedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "term_days", nullable = false)
    private int termDays;

    @Column(name = "annual_rate", nullable = false)
    private BigDecimal annualRate;

    @Column(name = "guarantee_amount")
    private BigDecimal guaranteeAmount;

    @Column(name = "pledge_rate")
    private BigDecimal pledgeRate;

    @Column(name = "finance_status", nullable = false)
    private String financeStatus;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_flag", nullable = false)
    private short deletedFlag;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getFinanceNo() { return financeNo; }
    public void setFinanceNo(String financeNo) { this.financeNo = financeNo; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFundingPartyId() { return fundingPartyId; }
    public void setFundingPartyId(String fundingPartyId) { this.fundingPartyId = fundingPartyId; }
    public String getCreditId() { return creditId; }
    public void setCreditId(String creditId) { this.creditId = creditId; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public BigDecimal getApplyAmount() { return applyAmount; }
    public void setApplyAmount(BigDecimal applyAmount) { this.applyAmount = applyAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public BigDecimal getDisbursedAmount() { return disbursedAmount; }
    public void setDisbursedAmount(BigDecimal disbursedAmount) { this.disbursedAmount = disbursedAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public int getTermDays() { return termDays; }
    public void setTermDays(int termDays) { this.termDays = termDays; }
    public BigDecimal getAnnualRate() { return annualRate; }
    public void setAnnualRate(BigDecimal annualRate) { this.annualRate = annualRate; }
    public BigDecimal getGuaranteeAmount() { return guaranteeAmount; }
    public void setGuaranteeAmount(BigDecimal guaranteeAmount) { this.guaranteeAmount = guaranteeAmount; }
    public BigDecimal getPledgeRate() { return pledgeRate; }
    public void setPledgeRate(BigDecimal pledgeRate) { this.pledgeRate = pledgeRate; }
    public String getFinanceStatus() { return financeStatus; }
    public void setFinanceStatus(String financeStatus) { this.financeStatus = financeStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public short getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(short deletedFlag) { this.deletedFlag = deletedFlag; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
}
