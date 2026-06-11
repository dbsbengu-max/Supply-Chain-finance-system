package com.scf.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentCenterRegisterRequest(
            @NotBlank @JsonProperty("business_type") String businessType,
            @NotBlank @JsonProperty("business_id") String businessId,
            @NotBlank @JsonProperty("document_type") String documentType,
            @NotBlank @JsonProperty("file_id") String fileId,
            @JsonProperty("document_no") String documentNo,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("trigger_ocr") Boolean triggerOcr
    ) {
    }

    public record DocumentReviewReasonRequest(
            @JsonProperty("reason") String reason
    ) {
    }

    public record DocumentCenterListItem(
            @JsonProperty("id") String id,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("document_no") String documentNo,
            @JsonProperty("file_id") String fileId,
            @JsonProperty("document_status") String documentStatus,
            @JsonProperty("review_status") String reviewStatus,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("sign_status") String signStatus,
            @JsonProperty("ocr_status") String ocrStatus,
            @JsonProperty("ocr_confidence") BigDecimal ocrConfidence,
            @JsonProperty("ocr_job_id") String ocrJobId,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }

    public record DocumentReviewLogView(
            @JsonProperty("id") String id,
            @JsonProperty("action") String action,
            @JsonProperty("before_status") String beforeStatus,
            @JsonProperty("after_status") String afterStatus,
            @JsonProperty("operator_id") String operatorId,
            @JsonProperty("operator_role") String operatorRole,
            @JsonProperty("reason") String reason,
            @JsonProperty("created_at") Instant createdAt
    ) {
    }

    public record DocumentCenterDetailView(
            @JsonProperty("id") String id,
            @JsonProperty("operator_id") String operatorId,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("document_no") String documentNo,
            @JsonProperty("file_id") String fileId,
            @JsonProperty("document_status") String documentStatus,
            @JsonProperty("review_status") String reviewStatus,
            @JsonProperty("review_result") String reviewResult,
            @JsonProperty("review_reason") String reviewReason,
            @JsonProperty("contract_status") String contractStatus,
            @JsonProperty("sign_status") String signStatus,
            @JsonProperty("sign_provider") String signProvider,
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("ocr_status") String ocrStatus,
            @JsonProperty("ocr_job_id") String ocrJobId,
            @JsonProperty("ocr_confidence") BigDecimal ocrConfidence,
            @JsonProperty("validation_status") String validationStatus,
            @JsonProperty("validation_result_json") String validationResultJson,
            @JsonProperty("issued_at") Instant issuedAt,
            @JsonProperty("expired_at") Instant expiredAt,
            @JsonProperty("reviewed_by") String reviewedBy,
            @JsonProperty("reviewed_at") Instant reviewedAt,
            @JsonProperty("created_by") String createdBy,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("review_logs") List<DocumentReviewLogView> reviewLogs
    ) {
    }

    public record DocumentRequirementUpsertRequest(
            @JsonProperty("project_id") String projectId,
            @NotBlank @JsonProperty("business_type") String businessType,
            @NotBlank @JsonProperty("business_stage") String businessStage,
            @JsonProperty("product_type") String productType,
            @NotBlank @JsonProperty("document_type") String documentType,
            @JsonProperty("required_flag") Boolean requiredFlag,
            @JsonProperty("ocr_required") Boolean ocrRequired,
            @JsonProperty("manual_review_required") Boolean manualReviewRequired,
            @JsonProperty("min_confidence") BigDecimal minConfidence,
            @JsonProperty("enabled") Boolean enabled,
            @JsonProperty("sort_no") Integer sortNo
    ) {
    }

    public record DocumentRequirementView(
            @JsonProperty("id") String id,
            @JsonProperty("project_id") String projectId,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_stage") String businessStage,
            @JsonProperty("product_type") String productType,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("required_flag") boolean requiredFlag,
            @JsonProperty("ocr_required") boolean ocrRequired,
            @JsonProperty("manual_review_required") boolean manualReviewRequired,
            @JsonProperty("min_confidence") BigDecimal minConfidence,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("sort_no") int sortNo,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
    }

    public record DocumentValidateRequest(
            @NotBlank @JsonProperty("business_type") String businessType,
            @NotBlank @JsonProperty("business_id") String businessId,
            @NotBlank @JsonProperty("business_stage") String businessStage,
            @JsonProperty("product_type") String productType
    ) {
    }

    public record DocumentValidateMissingItem(
            @JsonProperty("document_type") String documentType,
            @JsonProperty("required") boolean required,
            @JsonProperty("message") String message
    ) {
    }

    public record DocumentValidatePendingItem(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("review_status") String reviewStatus
    ) {
    }

    public record DocumentValidateWarningItem(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("document_type") String documentType,
            @JsonProperty("message") String message
    ) {
    }

    public record DocumentValidateResponse(
            @JsonProperty("passed") boolean passed,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("missing") List<DocumentValidateMissingItem> missing,
            @JsonProperty("pending_review") List<DocumentValidatePendingItem> pendingReview,
            @JsonProperty("warnings") List<DocumentValidateWarningItem> warnings
    ) {
    }
}
