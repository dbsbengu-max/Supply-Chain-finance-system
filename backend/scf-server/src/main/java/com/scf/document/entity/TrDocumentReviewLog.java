package com.scf.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tr_document_review_log", schema = "scf")
public class TrDocumentReviewLog {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "before_status")
    private String beforeStatus;

    @Column(name = "after_status")
    private String afterStatus;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "operator_role")
    private String operatorRole;

    @Column(name = "reason")
    private String reason;

    @Column(name = "snapshot_json")
    private String snapshotJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getBeforeStatus() { return beforeStatus; }
    public void setBeforeStatus(String beforeStatus) { this.beforeStatus = beforeStatus; }
    public String getAfterStatus() { return afterStatus; }
    public void setAfterStatus(String afterStatus) { this.afterStatus = afterStatus; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
