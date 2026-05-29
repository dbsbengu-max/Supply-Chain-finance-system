package com.scf.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "md_bank_account", schema = "scf")
public class MdBankAccount {

    @Id
    private String id;

    @Column(name = "enterprise_id", nullable = false)
    private String enterpriseId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;

    @Column(name = "is_repayment_account", nullable = false)
    private short isRepaymentAccount;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_flag", nullable = false)
    private short deletedFlag;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(String enterpriseId) { this.enterpriseId = enterpriseId; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public short getIsRepaymentAccount() { return isRepaymentAccount; }
    public void setIsRepaymentAccount(short isRepaymentAccount) { this.isRepaymentAccount = isRepaymentAccount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public short getDeletedFlag() { return deletedFlag; }
    public void setDeletedFlag(short deletedFlag) { this.deletedFlag = deletedFlag; }
}
