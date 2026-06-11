package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.saga.entity.BizCompensationTask;

import java.time.Instant;

public record AgencyPurchaseCompensationTaskView(
        String id,
        @JsonProperty("compensation_type") String compensationType,
        @JsonProperty("compensation_status") String compensationStatus,
        @JsonProperty("retry_count") int retryCount,
        @JsonProperty("next_retry_at") Instant nextRetryAt,
        @JsonProperty("last_error") String lastError,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("executed_at") Instant executedAt
) {
    public static AgencyPurchaseCompensationTaskView from(BizCompensationTask task) {
        return new AgencyPurchaseCompensationTaskView(
                task.getId(),
                task.getCompensationType(),
                task.getCompensationStatus(),
                task.getRetryCount(),
                task.getNextRetryAt(),
                task.getLastError(),
                task.getCreatedAt(),
                task.getExecutedAt());
    }
}
