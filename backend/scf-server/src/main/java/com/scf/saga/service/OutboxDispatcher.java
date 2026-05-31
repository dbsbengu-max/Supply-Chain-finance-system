package com.scf.saga.service;

import com.scf.common.util.IdGenerator;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizEventOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxDispatcher {

    private final BizEventOutboxRepository outboxRepository;
    private final OutboxEventProcessor eventProcessor;

    public OutboxDispatcher(BizEventOutboxRepository outboxRepository, OutboxEventProcessor eventProcessor) {
        this.outboxRepository = outboxRepository;
        this.eventProcessor = eventProcessor;
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
    public void dispatchPendingEvents() {
        List<BizEventOutbox> events = outboxRepository.findPendingEvents(Instant.now());
        for (BizEventOutbox event : events) {
            eventProcessor.process(event.getId());
        }
    }
}
