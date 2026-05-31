package com.scf.saga.service;

import com.scf.agencypurchase.service.AgencyPurchaseApprovedOutboxPublisher;
import com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository;
import com.scf.audit.service.AuditLogService;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);
    private static final int[] RETRY_MINUTES = {1, 3, 5, 10, 30};

    private final BizEventOutboxRepository outboxRepository;
    private final SagaEventHandler sagaEventHandler;
    private final ApAgencyPurchaseApplicationRepository agencyPurchaseApplicationRepository;
    private final AuditLogService auditLogService;

    public OutboxEventProcessor(
            BizEventOutboxRepository outboxRepository,
            SagaEventHandler sagaEventHandler,
            ApAgencyPurchaseApplicationRepository agencyPurchaseApplicationRepository,
            AuditLogService auditLogService) {
        this.outboxRepository = outboxRepository;
        this.sagaEventHandler = sagaEventHandler;
        this.agencyPurchaseApplicationRepository = agencyPurchaseApplicationRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(String eventId) {
        BizEventOutbox event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("Outbox event missing: " + eventId));
        if (!"PENDING".equals(event.getEventStatus()) && !"FAILED".equals(event.getEventStatus())) {
            return;
        }
        if (event.getNextRetryAt() != null && event.getNextRetryAt().isAfter(Instant.now())) {
            return;
        }
        event.setEventStatus("PROCESSING");
        event.setUpdatedAt(Instant.now());
        outboxRepository.save(event);
        try {
            sagaEventHandler.handle(event);
            if (isAgencyPurchaseSagaFailed(event)) {
                markFailed(event, "Agency purchase saga failed");
            } else {
                event.setEventStatus("SUCCESS");
                event.setLastError(null);
            }
        } catch (Exception ex) {
            int retry = event.getRetryCount() + 1;
            event.setRetryCount(retry);
            event.setLastError(ex.getMessage());
            if (retry >= RETRY_MINUTES.length) {
                event.setEventStatus("MANUAL_REQUIRED");
                auditLogService.logAsSystem(
                        "system",
                        null,
                        null,
                        null,
                        "SAGA_COMPENSATE",
                        event.getBusinessType(),
                        event.getBusinessId(),
                        Map.of(
                                "event_id", event.getId(),
                                "event_type", event.getEventType(),
                                "event_status", "PROCESSING"),
                        Map.of(
                                "event_status", "MANUAL_REQUIRED",
                                "retry_count", retry,
                                "last_error", ex.getMessage()));
            } else {
                event.setEventStatus("FAILED");
                event.setNextRetryAt(Instant.now().plus(RETRY_MINUTES[retry - 1], ChronoUnit.MINUTES));
            }
            log.warn("Outbox dispatch failed for event {}: {}", event.getId(), ex.getMessage());
        }
        event.setUpdatedAt(Instant.now());
        outboxRepository.save(event);
    }

    private boolean isAgencyPurchaseSagaFailed(BizEventOutbox event) {
        if (!AgencyPurchaseApprovedOutboxPublisher.EVENT_TYPE.equals(event.getEventType())) {
            return false;
        }
        return agencyPurchaseApplicationRepository.findById(event.getBusinessId())
                .map(app -> "FAILED".equals(app.getSagaStatus()))
                .orElse(false);
    }

    private void markFailed(BizEventOutbox event, String error) {
        int retry = event.getRetryCount() + 1;
        event.setRetryCount(retry);
        event.setLastError(error);
        if (retry >= RETRY_MINUTES.length) {
            event.setEventStatus("MANUAL_REQUIRED");
            auditLogService.logAsSystem(
                    "system",
                    null,
                    null,
                    null,
                    "SAGA_COMPENSATE",
                    event.getBusinessType(),
                    event.getBusinessId(),
                    Map.of(
                            "event_id", event.getId(),
                            "event_type", event.getEventType(),
                            "event_status", "PROCESSING"),
                    Map.of(
                            "event_status", "MANUAL_REQUIRED",
                            "retry_count", retry,
                            "last_error", error));
        } else {
            event.setEventStatus("FAILED");
            event.setNextRetryAt(Instant.now().plus(RETRY_MINUTES[retry - 1], ChronoUnit.MINUTES));
        }
    }
}
