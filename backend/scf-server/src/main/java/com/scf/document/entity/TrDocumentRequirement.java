package com.scf.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tr_document_requirement", schema = "scf")
public class TrDocumentRequirement {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id")
    private String projectId;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "business_stage", nullable = false)
    private String businessStage;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "required_flag", nullable = false)
    private short requiredFlag;

    @Column(name = "ocr_required", nullable = false)
    private short ocrRequired;

    @Column(name = "manual_review_required", nullable = false)
    private short manualReviewRequired;

    @Column(name = "min_confidence")
    private BigDecimal minConfidence;

    @Column(name = "enabled", nullable = false)
    private short enabled;

    @Column(name = "sort_no", nullable = false)
    private int sortNo;

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
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getBusinessStage() { return businessStage; }
    public void setBusinessStage(String businessStage) { this.businessStage = businessStage; }
    public String getProductType() { return productType; }
    public void setProductType(String productType) { this.productType = productType; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public short getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(short requiredFlag) { this.requiredFlag = requiredFlag; }
    public short getOcrRequired() { return ocrRequired; }
    public void setOcrRequired(short ocrRequired) { this.ocrRequired = ocrRequired; }
    public short getManualReviewRequired() { return manualReviewRequired; }
    public void setManualReviewRequired(short manualReviewRequired) { this.manualReviewRequired = manualReviewRequired; }
    public BigDecimal getMinConfidence() { return minConfidence; }
    public void setMinConfidence(BigDecimal minConfidence) { this.minConfidence = minConfidence; }
    public short getEnabled() { return enabled; }
    public void setEnabled(short enabled) { this.enabled = enabled; }
    public int getSortNo() { return sortNo; }
    public void setSortNo(int sortNo) { this.sortNo = sortNo; }
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
