package com.scf.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "dv_voucher_flow", schema = "scf")
public class DvVoucherFlow {

    @Id
    private String id;

    @Column(name = "voucher_id", nullable = false)
    private String voucherId;

    @Column(name = "flow_type", nullable = false)
    private String flowType;

    @Column(name = "from_holder_id")
    private String fromHolderId;

    @Column(name = "to_holder_id")
    private String toHolderId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "before_available_amount", nullable = false)
    private BigDecimal beforeAvailableAmount;

    @Column(name = "after_available_amount", nullable = false)
    private BigDecimal afterAvailableAmount;

    @Column(name = "related_voucher_id")
    private String relatedVoucherId;

    @Column(name = "operated_by", nullable = false)
    private String operatedBy;

    @Column(name = "operated_at", nullable = false)
    private Instant operatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVoucherId() {
        return voucherId;
    }

    public void setVoucherId(String voucherId) {
        this.voucherId = voucherId;
    }

    public String getFlowType() {
        return flowType;
    }

    public void setFlowType(String flowType) {
        this.flowType = flowType;
    }

    public String getFromHolderId() {
        return fromHolderId;
    }

    public void setFromHolderId(String fromHolderId) {
        this.fromHolderId = fromHolderId;
    }

    public String getToHolderId() {
        return toHolderId;
    }

    public void setToHolderId(String toHolderId) {
        this.toHolderId = toHolderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBeforeAvailableAmount() {
        return beforeAvailableAmount;
    }

    public void setBeforeAvailableAmount(BigDecimal beforeAvailableAmount) {
        this.beforeAvailableAmount = beforeAvailableAmount;
    }

    public BigDecimal getAfterAvailableAmount() {
        return afterAvailableAmount;
    }

    public void setAfterAvailableAmount(BigDecimal afterAvailableAmount) {
        this.afterAvailableAmount = afterAvailableAmount;
    }

    public String getRelatedVoucherId() {
        return relatedVoucherId;
    }

    public void setRelatedVoucherId(String relatedVoucherId) {
        this.relatedVoucherId = relatedVoucherId;
    }

    public String getOperatedBy() {
        return operatedBy;
    }

    public void setOperatedBy(String operatedBy) {
        this.operatedBy = operatedBy;
    }

    public Instant getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(Instant operatedAt) {
        this.operatedAt = operatedAt;
    }
}
