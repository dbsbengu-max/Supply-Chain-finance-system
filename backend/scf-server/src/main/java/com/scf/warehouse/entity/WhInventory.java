package com.scf.warehouse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wh_inventory", schema = "scf")
public class WhInventory {

    @Id
    private String id;

    @Column(name = "warehouse_id", nullable = false)
    private String warehouseId;

    @Column(name = "operator_id")
    private String operatorId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "sku_id", nullable = false)
    private String skuId;

    @Column(name = "batch_no", nullable = false)
    private String batchNo;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "location_code")
    private String locationCode;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "available_quantity", nullable = false)
    private BigDecimal availableQuantity;

    @Column(name = "frozen_quantity", nullable = false)
    private BigDecimal frozenQuantity;

    @Column(name = "pledged_quantity", nullable = false)
    private BigDecimal pledgedQuantity;

    @Column(name = "outbound_pending_quantity", nullable = false)
    private BigDecimal outboundPendingQuantity;

    @Column(name = "valuation_amount")
    private BigDecimal valuationAmount;

    @Column(name = "currency")
    private String currency;

    @Column(name = "right_status", nullable = false)
    private String rightStatus;

    @Column(name = "stocktake_exception", nullable = false)
    private short stocktakeException;

    @Version
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_flag", nullable = false)
    private short deletedFlag;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(BigDecimal availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public BigDecimal getFrozenQuantity() {
        return frozenQuantity;
    }

    public void setFrozenQuantity(BigDecimal frozenQuantity) {
        this.frozenQuantity = frozenQuantity;
    }

    public BigDecimal getPledgedQuantity() {
        return pledgedQuantity;
    }

    public void setPledgedQuantity(BigDecimal pledgedQuantity) {
        this.pledgedQuantity = pledgedQuantity;
    }

    public BigDecimal getOutboundPendingQuantity() {
        return outboundPendingQuantity;
    }

    public void setOutboundPendingQuantity(BigDecimal outboundPendingQuantity) {
        this.outboundPendingQuantity = outboundPendingQuantity;
    }

    public BigDecimal getValuationAmount() {
        return valuationAmount;
    }

    public void setValuationAmount(BigDecimal valuationAmount) {
        this.valuationAmount = valuationAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getRightStatus() {
        return rightStatus;
    }

    public void setRightStatus(String rightStatus) {
        this.rightStatus = rightStatus;
    }

    public short getStocktakeException() {
        return stocktakeException;
    }

    public void setStocktakeException(short stocktakeException) {
        this.stocktakeException = stocktakeException;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public short getDeletedFlag() {
        return deletedFlag;
    }

    public void setDeletedFlag(short deletedFlag) {
        this.deletedFlag = deletedFlag;
    }
}
