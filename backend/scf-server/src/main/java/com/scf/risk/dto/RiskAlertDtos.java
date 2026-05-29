package com.scf.risk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class RiskAlertDtos {

    private RiskAlertDtos() {}

    public record RiskAlertView(
            String id,
            @JsonProperty("alert_code") String alertCode,
            String severity,
            String title,
            String message,
            @JsonProperty("related_id") String relatedId,
            @JsonProperty("related_type") String relatedType,
            @JsonProperty("related_label") String relatedLabel,
            String amount,
            String currency,
            @JsonProperty("handle_status") String handleStatus,
            @JsonProperty("assignee_user_id") String assigneeUserId,
            @JsonProperty("assignee_name") String assigneeName,
            String remark,
            @JsonProperty("detected_at") Instant detectedAt,
            @JsonProperty("handled_at") Instant handledAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("related_route") String relatedRoute) {}

    public record RiskAlertHandleRequest(
            @NotBlank @JsonProperty("handle_status") String handleStatus,
            @JsonProperty("assignee_user_id") String assigneeUserId,
            @Size(max = 100) @JsonProperty("assignee_name") String assigneeName,
            @Size(max = 500) String remark) {}
}
