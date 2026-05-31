package com.scf.agencypurchase.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.saga.entity.BizCompensationTask;

import java.time.Instant;

public record AgencyPurchaseCompensationTaskView(
        String id,
        @JsonProperty("compensation_type") String compensationType,
        @JsonProperty("compensation_status") String compensationStatus,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("executed_at") Instant executedAt
) {
    public static AgencyPurchaseCompensationTaskView from(BizCompensationTask task) {
        return new AgencyPurchaseCompensationTaskView(
                task.getId(),
                task.getCompensationType(),
                task.getCompensationStatus(),
                task.getCreatedAt(),
                task.getExecutedAt());
    }
}
