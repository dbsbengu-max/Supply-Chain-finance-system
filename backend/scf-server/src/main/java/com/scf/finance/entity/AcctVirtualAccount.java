package com.scf.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "acct_virtual_account", schema = "scf")
public class AcctVirtualAccount {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "enterprise_id", nullable = false)
    private String enterpriseId;

    @Column(name = "funding_party_id")
    private String fundingPartyId;

    @Column(name = "account_type", nullable = false)
    private String accountType;

    @Column(name = "account_no", nullable = false)
    private String accountNo;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "frozen_balance", nullable = false)
    private BigDecimal frozenBalance;

    @Column(nullable = false)
    private String status;

    public String getId() {
        return id;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getEnterpriseId() {
        return enterpriseId;
    }

    public String getFundingPartyId() {
        return fundingPartyId;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getFrozenBalance() {
        return frozenBalance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setFrozenBalance(BigDecimal frozenBalance) {
        this.frozenBalance = frozenBalance;
    }

    public String getStatus() {
        return status;
    }
}
