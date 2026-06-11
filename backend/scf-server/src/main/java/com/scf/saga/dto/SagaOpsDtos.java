package com.scf.saga.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.entity.BizEventOutbox;

import java.time.Instant;
import java.util.List;

public final class SagaOpsDtos {

    private SagaOpsDtos() {
    }

    public record StatusCountView(
            @JsonProperty("status") String status,
            @JsonProperty("count") long count
    ) {
    }

    public record SagaOpsSummaryView(
            @JsonProperty("outbox_pending") long outboxPending,
            @JsonProperty("outbox_failed") long outboxFailed,
            @JsonProperty("outbox_manual_required") long outboxManualRequired,
            @JsonProperty("compensation_pending") long compensationPending,
            @JsonProperty("compensation_failed") long compensationFailed,
            @JsonProperty("compensation_manual_required") long compensationManualRequired,
            @JsonProperty("outbox_by_status") List<StatusCountView> outboxByStatus,
            @JsonProperty("compensation_by_status") List<StatusCountView> compensationByStatus
    ) {
    }

    public record OutboxEventView(
            @JsonProperty("id") String id,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("event_status") String eventStatus,
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
        public static OutboxEventView from(BizEventOutbox event) {
            return new OutboxEventView(
                    event.getId(),
                    event.getEventType(),
                    event.getBusinessType(),
                    event.getBusinessId(),
                    event.getEventStatus(),
                    event.getRetryCount(),
                    event.getNextRetryAt(),
                    event.getLastError(),
                    event.getCreatedAt(),
                    event.getUpdatedAt());
        }
    }

    public record CompensationTaskOpsView(
            @JsonProperty("id") String id,
            @JsonProperty("compensation_type") String compensationType,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("compensation_status") String compensationStatus,
            @JsonProperty("high_risk") boolean highRisk,
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("approved_by") String approvedBy,
            @JsonProperty("claimed_by") String claimedBy,
            @JsonProperty("executed_at") Instant executedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {
        public static CompensationTaskOpsView from(BizCompensationTask task) {
            return new CompensationTaskOpsView(
                    task.getId(),
                    task.getCompensationType(),
                    task.getBusinessType(),
                    task.getBusinessId(),
                    task.getCompensationStatus(),
                    task.isHighRisk(),
                    task.getRetryCount(),
                    task.getNextRetryAt(),
                    task.getLastError(),
                    task.getApprovedBy(),
                    task.getClaimedBy(),
                    task.getExecutedAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt());
        }
    }

    public record SagaOpsFilterMetaView(
            @JsonProperty("outbox_statuses") List<String> outboxStatuses,
            @JsonProperty("compensation_statuses") List<String> compensationStatuses,
            @JsonProperty("compensation_types") List<String> compensationTypes
    ) {
    }

    public record SagaOpsManualRequest(
            @JsonProperty("reason") String reason
    ) {
    }

    public record OutboxEventDetailView(
            @JsonProperty("id") String id,
            @JsonProperty("event_type") String eventType,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("idempotency_key") String idempotencyKey,
            @JsonProperty("payload_json") String payloadJson,
            @JsonProperty("event_status") String eventStatus,
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("related_route") String relatedRoute
    ) {
        public static OutboxEventDetailView from(BizEventOutbox event, String relatedRoute) {
            return new OutboxEventDetailView(
                    event.getId(),
                    event.getEventType(),
                    event.getBusinessType(),
                    event.getBusinessId(),
                    event.getIdempotencyKey(),
                    event.getPayloadJson(),
                    event.getEventStatus(),
                    event.getRetryCount(),
                    event.getNextRetryAt(),
                    event.getLastError(),
                    event.getCreatedAt(),
                    event.getUpdatedAt(),
                    relatedRoute);
        }
    }

    public record CompensationImpactView(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("order_status") String orderStatus,
            @JsonProperty("finance_application_id") String financeApplicationId,
            @JsonProperty("inventory_id") String inventoryId,
            @JsonProperty("margin_account_id") String marginAccountId,
            @JsonProperty("document_id") String documentId,
            @JsonProperty("external_sign_ref") String externalSignRef,
            @JsonProperty("provider_code") String providerCode,
            @JsonProperty("sign_task_status") String signTaskStatus,
            @JsonProperty("suggested_action") String suggestedAction
    ) {
    }

    public record CompensationAuditEntryView(
            @JsonProperty("action") String action,
            @JsonProperty("user_id") String userId,
            @JsonProperty("operation_at") Instant operationAt,
            @JsonProperty("detail") String detail
    ) {
    }

    public record CompensationTaskDetailView(
            @JsonProperty("id") String id,
            @JsonProperty("source_event_id") String sourceEventId,
            @JsonProperty("compensation_type") String compensationType,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("compensation_status") String compensationStatus,
            @JsonProperty("high_risk") boolean highRisk,
            @JsonProperty("action_json") String actionJson,
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("approved_by") String approvedBy,
            @JsonProperty("claimed_by") String claimedBy,
            @JsonProperty("claimed_at") Instant claimedAt,
            @JsonProperty("submitted_by") String submittedBy,
            @JsonProperty("submitted_at") Instant submittedAt,
            @JsonProperty("handle_reason") String handleReason,
            @JsonProperty("closed_by") String closedBy,
            @JsonProperty("closed_at") Instant closedAt,
            @JsonProperty("executed_at") Instant executedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("related_route") String relatedRoute,
            @JsonProperty("impact") CompensationImpactView impact,
            @JsonProperty("audit_timeline") List<CompensationAuditEntryView> auditTimeline
    ) {
        public static CompensationTaskDetailView from(
                BizCompensationTask task,
                String relatedRoute,
                CompensationImpactView impact,
                List<CompensationAuditEntryView> auditTimeline) {
            return new CompensationTaskDetailView(
                    task.getId(),
                    task.getSourceEventId(),
                    task.getCompensationType(),
                    task.getBusinessType(),
                    task.getBusinessId(),
                    task.getCompensationStatus(),
                    task.isHighRisk(),
                    task.getActionJson(),
                    task.getRetryCount(),
                    task.getNextRetryAt(),
                    task.getLastError(),
                    task.getApprovedBy(),
                    task.getClaimedBy(),
                    task.getClaimedAt(),
                    task.getSubmittedBy(),
                    task.getSubmittedAt(),
                    task.getHandleReason(),
                    task.getClosedBy(),
                    task.getClosedAt(),
                    task.getExecutedAt(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    relatedRoute,
                    impact,
                    auditTimeline);
        }
    }
}
