package com.scf.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cr_credit", schema = "scf")
public class CrCredit {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "credit_no", nullable = false, unique = true)
    private String creditNo;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "funding_party_id", nullable = false)
    private String fundingPartyId;

    @Column(name = "credit_limit", nullable = false)
    private BigDecimal creditLimit;

    @Column(name = "used_limit", nullable = false)
    private BigDecimal usedLimit;

    @Column(name = "frozen_limit", nullable = false)
    private BigDecimal frozenLimit;

    @Column(name = "available_limit", nullable = false)
    private BigDecimal availableLimit;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "credit_status", nullable = false)
    private String creditStatus;

    public String getId() { return id; }
    public String getOperatorId() { return operatorId; }
    public String getProjectId() { return projectId; }
    public String getCustomerId() { return customerId; }
    public String getFundingPartyId() { return fundingPartyId; }
    public BigDecimal getAvailableLimit() { return availableLimit; }
    public String getCurrency() { return currency; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getCreditStatus() { return creditStatus; }
}
