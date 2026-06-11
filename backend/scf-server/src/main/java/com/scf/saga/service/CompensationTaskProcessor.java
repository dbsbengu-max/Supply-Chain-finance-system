package com.scf.saga.service;

import com.scf.audit.service.AuditLogService;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.repository.BizCompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

@Service
public class CompensationTaskProcessor {

    private static final Logger log = LoggerFactory.getLogger(CompensationTaskProcessor.class);
    public static final int[] RETRY_MINUTES = {1, 3, 5, 10, 30};
    private static final Set<String> MANUAL_ONLY_BUSINESS_TYPES = Set.of("CONTRACT_SIGN_CALLBACK");

    private final BizCompensationTaskRepository repository;
    private final AgencyPurchaseCompensationHandler agencyPurchaseCompensationHandler;
    private final AuditLogService auditLogService;
    private final com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository applicationRepository;

    public CompensationTaskProcessor(
            BizCompensationTaskRepository repository,
            AgencyPurchaseCompensationHandler agencyPurchaseCompensationHandler,
            AuditLogService auditLogService,
            com.scf.agencypurchase.repository.ApAgencyPurchaseApplicationRepository applicationRepository) {
        this.repository = repository;
        this.agencyPurchaseCompensationHandler = agencyPurchaseCompensationHandler;
        this.auditLogService = auditLogService;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Exception.class)
    public void process(String taskId) {
        BizCompensationTask task = repository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Compensation task missing: " + taskId));
        if (!canProcess(task)) {
            return;
        }
        if (MANUAL_ONLY_BUSINESS_TYPES.contains(task.getBusinessType())) {
            ensureManualRequired(task);
            return;
        }
        task.setCompensationStatus("PROCESSING");
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        try {
            if ("AGENCY_PURCHASE".equals(task.getBusinessType())) {
                agencyPurchaseCompensationHandler.execute(task);
            } else {
                throw new IllegalStateException("No compensation handler for business type: " + task.getBusinessType());
            }
            task.setCompensationStatus("SUCCESS");
            task.setExecutedAt(Instant.now());
            task.setLastError(null);
            task.setNextRetryAt(null);
        } catch (Exception ex) {
            log.warn("Compensation task {} failed: {}", task.getId(), ex.getMessage());
            markFailure(task, ex.getMessage());
        }
        task.setUpdatedAt(Instant.now());
        repository.save(task);
    }

    private boolean canProcess(BizCompensationTask task) {
        String status = task.getCompensationStatus();
        if ("SUCCESS".equals(status)
                || "PROCESSING".equals(status)
                || "MANUAL_REQUIRED".equals(status)
                || "CLAIMED".equals(status)
                || "APPROVED".equals(status)
                || "IGNORED".equals(status)
                || "CLOSED".equals(status)) {
            return false;
        }
        if ("FAILED".equals(status)) {
            return task.getNextRetryAt() == null || !task.getNextRetryAt().isAfter(Instant.now());
        }
        return "PENDING".equals(status);
    }

    private void markFailure(BizCompensationTask task, String error) {
        int retry = task.getRetryCount() + 1;
        task.setRetryCount(retry);
        task.setLastError(error);
        if (retry >= RETRY_MINUTES.length) {
            task.setCompensationStatus("MANUAL_REQUIRED");
            task.setNextRetryAt(null);
            auditLogService.logAsSystem(
                    "system",
                    resolveOperatorId(task),
                    null,
                    resolveProjectId(task),
                    "SAGA_COMPENSATION_MANUAL",
                    task.getBusinessType(),
                    task.getBusinessId(),
                    Map.of(
                            "task_id", task.getId(),
                            "compensation_type", task.getCompensationType(),
                            "compensation_status", "PROCESSING"),
                    Map.of(
                            "compensation_status", "MANUAL_REQUIRED",
                            "retry_count", retry,
                            "last_error", error));
        } else {
            task.setCompensationStatus("FAILED");
            task.setNextRetryAt(Instant.now().plus(RETRY_MINUTES[retry - 1], ChronoUnit.MINUTES));
        }
    }

    private String resolveOperatorId(BizCompensationTask task) {
        if ("AGENCY_PURCHASE".equals(task.getBusinessType())) {
            return applicationRepository.findById(task.getBusinessId())
                    .map(com.scf.agencypurchase.entity.ApAgencyPurchaseApplication::getOperatorId)
                    .orElse("OP001");
        }
        return "OP001";
    }

    private String resolveProjectId(BizCompensationTask task) {
        if ("AGENCY_PURCHASE".equals(task.getBusinessType())) {
            return applicationRepository.findById(task.getBusinessId())
                    .map(com.scf.agencypurchase.entity.ApAgencyPurchaseApplication::getProjectId)
                    .orElse(null);
        }
        return null;
    }

    private void ensureManualRequired(BizCompensationTask task) {
        if ("MANUAL_REQUIRED".equals(task.getCompensationStatus())) {
            return;
        }
        task.setCompensationStatus("MANUAL_REQUIRED");
        task.setNextRetryAt(null);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
    }
}
