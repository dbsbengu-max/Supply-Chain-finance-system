package com.scf.inbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class InboxDtos {

    private InboxDtos() {}

    public record InboxEventView(
            @JsonProperty("event_key") String eventKey,
            String source,
            String category,
            String severity,
            String title,
            String message,
            @JsonProperty("business_type") String businessType,
            @JsonProperty("business_id") String businessId,
            @JsonProperty("business_label") String businessLabel,
            @JsonProperty("action_route") String actionRoute,
            @JsonProperty("occurred_at") Instant occurredAt,
            boolean read,
            Map<String, String> metadata) {}

    public record InboxSummaryView(
            long total,
            @JsonProperty("unread_count") long unreadCount,
            @JsonProperty("by_source") Map<String, Long> bySource) {}

    public record InboxFeedView(
            InboxSummaryView summary,
            List<InboxEventView> events) {}
}
