package com.scf.agencypurchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ap_agency_purchase_application", schema = "scf")
public class ApAgencyPurchaseApplication {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "application_no", nullable = false)
    private String applicationNo;

    @Column(name = "order_mode", nullable = false)
    private String orderMode;

    @Column(name = "fund_source", nullable = false)
    private String fundSource;

    @Column(name = "pickup_type", nullable = false)
    private String pickupType;

    @Column(name = "mode_key", nullable = false)
    private String modeKey;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "trade_company_id", nullable = false)
    private String tradeCompanyId;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "application_status", nullable = false)
    private String applicationStatus;

    @Column(name = "remark")
    private String remark;

    @Column(name = "bpm_instance_id")
    private String bpmInstanceId;

    @Column(name = "inventory_id")
    private String inventoryId;

    @Column(name = "margin_account_id")
    private String marginAccountId;

    @Column(name = "margin_amount")
    private BigDecimal marginAmount;

    @Column(name = "margin_frozen_amount")
    private BigDecimal marginFrozenAmount;

    @Column(name = "inventory_freeze_quantity")
    private BigDecimal inventoryFreezeQuantity;

    @Column(name = "finance_application_id")
    private String financeApplicationId;

    @Column(name = "saga_status")
    private String sagaStatus;

    @Column(name = "saga_last_error")
    private String sagaLastError;

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
    public String getApplicationNo() { return applicationNo; }
    public void setApplicationNo(String applicationNo) { this.applicationNo = applicationNo; }
    public String getOrderMode() { return orderMode; }
    public void setOrderMode(String orderMode) { this.orderMode = orderMode; }
    public String getFundSource() { return fundSource; }
    public void setFundSource(String fundSource) { this.fundSource = fundSource; }
    public String getPickupType() { return pickupType; }
    public void setPickupType(String pickupType) { this.pickupType = pickupType; }
    public String getModeKey() { return modeKey; }
    public void setModeKey(String modeKey) { this.modeKey = modeKey; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getTradeCompanyId() { return tradeCompanyId; }
    public void setTradeCompanyId(String tradeCompanyId) { this.tradeCompanyId = tradeCompanyId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getBpmInstanceId() { return bpmInstanceId; }
    public void setBpmInstanceId(String bpmInstanceId) { this.bpmInstanceId = bpmInstanceId; }
    public String getInventoryId() { return inventoryId; }
    public void setInventoryId(String inventoryId) { this.inventoryId = inventoryId; }
    public String getMarginAccountId() { return marginAccountId; }
    public void setMarginAccountId(String marginAccountId) { this.marginAccountId = marginAccountId; }
    public BigDecimal getMarginAmount() { return marginAmount; }
    public void setMarginAmount(BigDecimal marginAmount) { this.marginAmount = marginAmount; }
    public BigDecimal getMarginFrozenAmount() { return marginFrozenAmount; }
    public void setMarginFrozenAmount(BigDecimal marginFrozenAmount) { this.marginFrozenAmount = marginFrozenAmount; }
    public BigDecimal getInventoryFreezeQuantity() { return inventoryFreezeQuantity; }
    public void setInventoryFreezeQuantity(BigDecimal inventoryFreezeQuantity) { this.inventoryFreezeQuantity = inventoryFreezeQuantity; }
    public String getFinanceApplicationId() { return financeApplicationId; }
    public void setFinanceApplicationId(String financeApplicationId) { this.financeApplicationId = financeApplicationId; }
    public String getSagaStatus() { return sagaStatus; }
    public void setSagaStatus(String sagaStatus) { this.sagaStatus = sagaStatus; }
    public String getSagaLastError() { return sagaLastError; }
    public void setSagaLastError(String sagaLastError) { this.sagaLastError = sagaLastError; }
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
