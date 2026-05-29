package com.scf.saga.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.util.IdGenerator;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int[] RETRY_MINUTES = {1, 3, 5, 10, 30};

    private final BizEventOutboxRepository outboxRepository;
    private final SagaEventHandler sagaEventHandler;
    private final AuditLogService auditLogService;

    public OutboxDispatcher(
            BizEventOutboxRepository outboxRepository,
            SagaEventHandler sagaEventHandler,
            AuditLogService auditLogService) {
        this.outboxRepository = outboxRepository;
        this.sagaEventHandler = sagaEventHandler;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public BizEventOutbox publish(String eventType, String businessType, String businessId, String idempotencyKey, String payloadJson) {
        BizEventOutbox event = new BizEventOutbox();
        event.setId(IdGenerator.nextId());
        event.setEventType(eventType);
        event.setBusinessType(businessType);
        event.setBusinessId(businessId);
        event.setIdempotencyKey(idempotencyKey);
        event.setPayloadJson(payloadJson);
        event.setEventStatus("PENDING");
        event.setRetryCount(0);
        event.setCreatedAt(Instant.now());
        return outboxRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${scf.outbox.poll-interval-ms:30000}")
    @Transactional
    public void dispatchPendingEvents() {
        List<BizEventOutbox> events = outboxRepository.findPendingEvents(Instant.now());
        for (BizEventOutbox event : events) {
            dispatchOne(event);
        }
    }

    private void dispatchOne(BizEventOutbox event) {
        event.setEventStatus("PROCESSING");
        event.setUpdatedAt(Instant.now());
        outboxRepository.save(event);
        try {
            sagaEventHandler.handle(event);
            event.setEventStatus("SUCCESS");
            event.setLastError(null);
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
}
