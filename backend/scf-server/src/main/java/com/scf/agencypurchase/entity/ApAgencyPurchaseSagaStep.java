package com.scf.agencypurchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ap_agency_purchase_saga_step", schema = "scf")
public class ApAgencyPurchaseSagaStep {

    @Id
    private String id;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    @Column(name = "step_code", nullable = false)
    private String stepCode;

    @Column(name = "step_status", nullable = false)
    private String stepStatus;

    @Column(name = "detail_json")
    private String detailJson;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getStepStatus() { return stepStatus; }
    public void setStepStatus(String stepStatus) { this.stepStatus = stepStatus; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public Instant getExecutedAt() { return executedAt; }
    public void setExecutedAt(Instant executedAt) { this.executedAt = executedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
