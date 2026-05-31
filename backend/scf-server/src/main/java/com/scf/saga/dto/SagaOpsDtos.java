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
            @JsonProperty("retry_count") int retryCount,
            @JsonProperty("next_retry_at") Instant nextRetryAt,
            @JsonProperty("last_error") String lastError,
            @JsonProperty("approved_by") String approvedBy,
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
                    task.getRetryCount(),
                    task.getNextRetryAt(),
                    task.getLastError(),
                    task.getApprovedBy(),
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
}
