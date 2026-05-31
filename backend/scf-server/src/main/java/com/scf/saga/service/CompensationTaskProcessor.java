package com.scf.saga.service;

import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.repository.BizCompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CompensationTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompensationTaskProcessor.class);

    private final BizCompensationTaskRepository repository;
    private final AgencyPurchaseCompensationHandler agencyPurchaseCompensationHandler;

    public CompensationTaskProcessor(
            BizCompensationTaskRepository repository,
            AgencyPurchaseCompensationHandler agencyPurchaseCompensationHandler) {
        this.repository = repository;
        this.agencyPurchaseCompensationHandler = agencyPurchaseCompensationHandler;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void process(String taskId) {
        BizCompensationTask task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Compensation task missing: " + taskId));
        if (!"PENDING".equals(task.getCompensationStatus())) {
            return;
        }
        try {
            if ("AGENCY_PURCHASE".equals(task.getBusinessType())) {
                agencyPurchaseCompensationHandler.execute(task);
            } else {
                throw new IllegalStateException("No compensation handler for business type: " + task.getBusinessType());
            }
            task.setCompensationStatus("SUCCESS");
            task.setExecutedAt(Instant.now());
        } catch (Exception ex) {
            log.warn("Compensation task {} failed: {}", task.getId(), ex.getMessage());
            task.setCompensationStatus("FAILED");
        }
        repository.save(task);
    }
}
