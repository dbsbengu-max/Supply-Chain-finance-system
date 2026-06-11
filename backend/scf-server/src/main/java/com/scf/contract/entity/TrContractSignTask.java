package com.scf.contract.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tr_contract_sign_task")
public class TrContractSignTask {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(name = "document_id", nullable = false, length = 64)
    private String documentId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "external_sign_ref", length = 128)
    private String externalSignRef;

    @Column(name = "task_status", nullable = false, length = 32)
    private String taskStatus;

    @Column(name = "callback_status", length = 32)
    private String callbackStatus;

    @Column(name = "signers_json", columnDefinition = "text")
    private String signersJson;

    @Column(name = "callback_payload_json", columnDefinition = "text")
    private String callbackPayloadJson;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_retry_at")
    private Instant lastRetryAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "platform_trace_id", length = 128)
    private String platformTraceId;

    @Column(name = "provider_request_id", length = 128)
    private String providerRequestId;

    @Column(name = "provider_trace_id", length = 128)
    private String providerTraceId;

    @Column(name = "provider_exchange_json", columnDefinition = "text")
    private String providerExchangeJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getProviderCode() { return providerCode; }
    public void setProviderCode(String providerCode) { this.providerCode = providerCode; }
    public String getExternalSignRef() { return externalSignRef; }
    public void setExternalSignRef(String externalSignRef) { this.externalSignRef = externalSignRef; }
    public String getTaskStatus() { return taskStatus; }
    public void setTaskStatus(String taskStatus) { this.taskStatus = taskStatus; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public String getSignersJson() { return signersJson; }
    public void setSignersJson(String signersJson) { this.signersJson = signersJson; }
    public String getCallbackPayloadJson() { return callbackPayloadJson; }
    public void setCallbackPayloadJson(String callbackPayloadJson) { this.callbackPayloadJson = callbackPayloadJson; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getLastRetryAt() { return lastRetryAt; }
    public void setLastRetryAt(Instant lastRetryAt) { this.lastRetryAt = lastRetryAt; }
    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant signedAt) { this.signedAt = signedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getPlatformTraceId() { return platformTraceId; }
    public void setPlatformTraceId(String platformTraceId) { this.platformTraceId = platformTraceId; }
    public String getProviderRequestId() { return providerRequestId; }
    public void setProviderRequestId(String providerRequestId) { this.providerRequestId = providerRequestId; }
    public String getProviderTraceId() { return providerTraceId; }
    public void setProviderTraceId(String providerTraceId) { this.providerTraceId = providerTraceId; }
    public String getProviderExchangeJson() { return providerExchangeJson; }
    public void setProviderExchangeJson(String providerExchangeJson) { this.providerExchangeJson = providerExchangeJson; }
}
