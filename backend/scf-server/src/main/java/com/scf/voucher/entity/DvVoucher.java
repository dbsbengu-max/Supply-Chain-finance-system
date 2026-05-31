package com.scf.voucher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dv_voucher", schema = "scf")
public class DvVoucher {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "voucher_no", nullable = false, unique = true)
    private String voucherNo;

    @Column(name = "issuer_id", nullable = false)
    private String issuerId;

    @Column(name = "acceptor_id", nullable = false)
    private String acceptorId;

    @Column(name = "holder_id", nullable = false)
    private String holderId;

    @Column(name = "parent_voucher_id")
    private String parentVoucherId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "available_amount", nullable = false)
    private BigDecimal availableAmount;

    @Column(name = "locked_amount", nullable = false)
    private BigDecimal lockedAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "voucher_status", nullable = false)
    private String voucherStatus;

    @Column(name = "evidence_status", nullable = false)
    private String evidenceStatus;

    @Column(name = "version_no", nullable = false)
    private int versionNo = 1;

    @Column(name = "bpm_instance_id")
    private String bpmInstanceId;

    @Column(name = "redeem_restore_status")
    private String redeemRestoreStatus;

    @Column(name = "redeem_amount")
    private BigDecimal redeemAmount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getVoucherNo() {
        return voucherNo;
    }

    public void setVoucherNo(String voucherNo) {
        this.voucherNo = voucherNo;
    }

    public String getIssuerId() {
        return issuerId;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }

    public String getAcceptorId() {
        return acceptorId;
    }

    public void setAcceptorId(String acceptorId) {
        this.acceptorId = acceptorId;
    }

    public String getHolderId() {
        return holderId;
    }

    public void setHolderId(String holderId) {
        this.holderId = holderId;
    }

    public String getParentVoucherId() {
        return parentVoucherId;
    }

    public void setParentVoucherId(String parentVoucherId) {
        this.parentVoucherId = parentVoucherId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public void setAvailableAmount(BigDecimal availableAmount) {
        this.availableAmount = availableAmount;
    }

    public BigDecimal getLockedAmount() {
        return lockedAmount;
    }

    public void setLockedAmount(BigDecimal lockedAmount) {
        this.lockedAmount = lockedAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getVoucherStatus() {
        return voucherStatus;
    }

    public void setVoucherStatus(String voucherStatus) {
        this.voucherStatus = voucherStatus;
    }

    public String getEvidenceStatus() {
        return evidenceStatus;
    }

    public void setEvidenceStatus(String evidenceStatus) {
        this.evidenceStatus = evidenceStatus;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getBpmInstanceId() {
        return bpmInstanceId;
    }

    public void setBpmInstanceId(String bpmInstanceId) {
        this.bpmInstanceId = bpmInstanceId;
    }

    public String getRedeemRestoreStatus() {
        return redeemRestoreStatus;
    }

    public void setRedeemRestoreStatus(String redeemRestoreStatus) {
        this.redeemRestoreStatus = redeemRestoreStatus;
    }

    public BigDecimal getRedeemAmount() {
        return redeemAmount;
    }

    public void setRedeemAmount(BigDecimal redeemAmount) {
        this.redeemAmount = redeemAmount;
    }
}
