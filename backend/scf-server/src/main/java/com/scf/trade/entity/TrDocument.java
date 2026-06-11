package com.scf.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tr_document", schema = "scf")
public class TrDocument {

    @Id
    private String id;

    @Column(name = "operator_id", nullable = false)
    private String operatorId;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "business_type", nullable = false)
    private String businessType;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "document_no")
    private String documentNo;

    @Column(name = "file_id", nullable = false)
    private String fileId;

    @Column(name = "ocr_status", nullable = false)
    private String ocrStatus;

    @Column(name = "validation_status", nullable = false)
    private String validationStatus;

    @Column(name = "document_status", nullable = false)
    private String documentStatus;

    @Column(name = "review_status", nullable = false)
    private String reviewStatus;

    @Column(name = "review_result")
    private String reviewResult;

    @Column(name = "review_reason")
    private String reviewReason;

    @Column(name = "contract_status", nullable = false)
    private String contractStatus;

    @Column(name = "sign_status", nullable = false)
    private String signStatus;

    @Column(name = "sign_provider")
    private String signProvider;

    @Column(name = "external_sign_ref")
    private String externalSignRef;

    @Column(name = "ocr_job_id")
    private String ocrJobId;

    @Column(name = "ocr_confidence")
    private BigDecimal ocrConfidence;

    @Column(name = "validation_result_json")
    private String validationResultJson;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getBusinessId() { return businessId; }
    public void setBusinessId(String businessId) { this.businessId = businessId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
    public String getValidationStatus() { return validationStatus; }
    public void setValidationStatus(String validationStatus) { this.validationStatus = validationStatus; }
    public String getDocumentStatus() { return documentStatus; }
    public void setDocumentStatus(String documentStatus) { this.documentStatus = documentStatus; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewResult() { return reviewResult; }
    public void setReviewResult(String reviewResult) { this.reviewResult = reviewResult; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getContractStatus() { return contractStatus; }
    public void setContractStatus(String contractStatus) { this.contractStatus = contractStatus; }
    public String getSignStatus() { return signStatus; }
    public void setSignStatus(String signStatus) { this.signStatus = signStatus; }
    public String getSignProvider() { return signProvider; }
    public void setSignProvider(String signProvider) { this.signProvider = signProvider; }
    public String getExternalSignRef() { return externalSignRef; }
    public void setExternalSignRef(String externalSignRef) { this.externalSignRef = externalSignRef; }
    public String getOcrJobId() { return ocrJobId; }
    public void setOcrJobId(String ocrJobId) { this.ocrJobId = ocrJobId; }
    public BigDecimal getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(BigDecimal ocrConfidence) { this.ocrConfidence = ocrConfidence; }
    public String getValidationResultJson() { return validationResultJson; }
    public void setValidationResultJson(String validationResultJson) { this.validationResultJson = validationResultJson; }
    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Instant expiredAt) { this.expiredAt = expiredAt; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
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
}
