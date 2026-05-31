package com.scf.saga.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.util.IdGenerator;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizCompensationTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class CompensationTaskService {

    private static final Set<String> RETRYABLE = Set.of("FAILED", "MANUAL_REQUIRED");

    private final BizCompensationTaskRepository repository;
    private final CompensationTaskProcessor processor;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public CompensationTaskService(
            BizCompensationTaskRepository repository,
            CompensationTaskProcessor processor,
            TenantContext tenantContext,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.processor = processor;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public BizCompensationTask enqueue(
            BizEventOutbox sourceEvent,
            String compensationType,
            String businessType,
            String businessId,
            String actionJson) {
        BizCompensationTask task = new BizCompensationTask();
        task.setId(IdGenerator.nextId());
        task.setSourceEventId(sourceEvent == null ? null : sourceEvent.getId());
        task.setCompensationType(compensationType);
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setCompensationStatus("PENDING");
        task.setActionJson(actionJson);
        task.setRetryCount(0);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return repository.save(task);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void manualRetry(String taskId, String reason) {
        tenantContext.requirePermission("SAGA_OPS_MANAGE");
        requireManualReason(reason);
        BizCompensationTask task = requireTask(taskId);
        if (!RETRYABLE.contains(task.getCompensationStatus())) {
            throw new BusinessException("SAGA_409", "当前补偿任务状态不可重试: " + task.getCompensationStatus(), 409);
        }
        Map<String, Object> before = snapshot(task);
        task.setCompensationStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setLastError(null);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        auditLogService.log(
                "SAGA_COMPENSATION_RETRY",
                task.getBusinessType(),
                task.getBusinessId(),
                before,
                snapshotWithReason(task, reason));
        processor.process(taskId);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void approveAndExecute(String taskId, String reason) {
        tenantContext.requirePermission("SAGA_OPS_MANAGE");
        requireManualReason(reason);
        BizCompensationTask task = requireTask(taskId);
        if (!"MANUAL_REQUIRED".equals(task.getCompensationStatus())) {
            throw new BusinessException("SAGA_409", "仅 MANUAL_REQUIRED 状态可批准执行", 409);
        }
        String operator = SecurityUtils.currentUserId();
        Map<String, Object> before = snapshot(task);
        task.setApprovedBy(operator);
        task.setCompensationStatus("PENDING");
        task.setRetryCount(0);
        task.setNextRetryAt(null);
        task.setLastError(null);
        task.setUpdatedAt(Instant.now());
        repository.save(task);
        auditLogService.log(
                "SAGA_COMPENSATION_APPROVE",
                task.getBusinessType(),
                task.getBusinessId(),
                before,
                snapshotWithReason(task, reason));
        processor.process(taskId);
    }

    private static void requireManualReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("VALID_400", "人工操作原因不能为空", 400);
        }
        if (reason.trim().length() < 5) {
            throw new BusinessException("VALID_400", "人工操作原因至少 5 个字符", 400);
        }
    }

    private static Map<String, Object> snapshotWithReason(BizCompensationTask task, String reason) {
        return Map.of(
                "task_id", task.getId(),
                "compensation_type", task.getCompensationType(),
                "compensation_status", task.getCompensationStatus(),
                "retry_count", task.getRetryCount(),
                "manual_reason", reason.trim());
    }

    BizCompensationTask requireTask(String taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DATA_404", "补偿任务不存在", 404));
    }

    private static Map<String, Object> snapshot(BizCompensationTask task) {
        return Map.of(
                "task_id", task.getId(),
                "compensation_type", task.getCompensationType(),
                "compensation_status", task.getCompensationStatus(),
                "retry_count", task.getRetryCount());
    }
}
