package com.scf.ai.ocr.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "ai_ocr_field", schema = "scf")
public class AiOcrField {

    @Id
    private String id;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "suggested_value")
    private String suggestedValue;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @Column(name = "source_text")
    private String sourceText;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "bbox")
    private String bbox;

    @Column(name = "confirm_status", nullable = false)
    private String confirmStatus;

    @Column(name = "confirmed_value")
    private String confirmedValue;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getSuggestedValue() { return suggestedValue; }
    public void setSuggestedValue(String suggestedValue) { this.suggestedValue = suggestedValue; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public String getBbox() { return bbox; }
    public void setBbox(String bbox) { this.bbox = bbox; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public String getConfirmedValue() { return confirmedValue; }
    public void setConfirmedValue(String confirmedValue) { this.confirmedValue = confirmedValue; }
}
