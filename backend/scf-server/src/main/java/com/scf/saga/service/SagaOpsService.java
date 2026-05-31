package com.scf.saga.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.TenantContext;
import com.scf.saga.dto.SagaOpsDtos.CompensationTaskDetailView;
import com.scf.saga.dto.SagaOpsDtos.CompensationTaskOpsView;
import com.scf.saga.dto.SagaOpsDtos.OutboxEventDetailView;
import com.scf.saga.dto.SagaOpsDtos.OutboxEventView;
import com.scf.saga.dto.SagaOpsDtos.SagaOpsFilterMetaView;
import com.scf.saga.dto.SagaOpsDtos.SagaOpsSummaryView;
import com.scf.saga.dto.SagaOpsDtos.StatusCountView;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizCompensationTaskRepository;
import com.scf.saga.repository.BizEventOutboxRepository;
import com.scf.saga.support.SagaBusinessRouteResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SagaOpsService {

    private static final Set<String> OUTBOX_RETRYABLE = Set.of("FAILED", "MANUAL_REQUIRED");
    private static final List<String> OUTBOX_STATUSES = List.of(
            "PENDING", "PROCESSING", "SUCCESS", "FAILED", "MANUAL_REQUIRED");
    private static final List<String> COMPENSATION_STATUSES = List.of(
            "PENDING", "PROCESSING", "SUCCESS", "FAILED", "MANUAL_REQUIRED");
    private static final List<String> COMPENSATION_TYPES = List.of(
            "MARGIN_UNFREEZE", "INVENTORY_UNFREEZE");

    private final BizEventOutboxRepository outboxRepository;
    private final BizCompensationTaskRepository compensationTaskRepository;
    private final OutboxEventProcessor outboxEventProcessor;
    private final CompensationTaskService compensationTaskService;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public SagaOpsService(
            BizEventOutboxRepository outboxRepository,
            BizCompensationTaskRepository compensationTaskRepository,
            OutboxEventProcessor outboxEventProcessor,
            CompensationTaskService compensationTaskService,
            TenantContext tenantContext,
            AuditLogService auditLogService) {
        this.outboxRepository = outboxRepository;
        this.compensationTaskRepository = compensationTaskRepository;
        this.outboxEventProcessor = outboxEventProcessor;
        this.compensationTaskService = compensationTaskService;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public SagaOpsSummaryView summary() {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        List<StatusCountView> outboxCounts = OUTBOX_STATUSES.stream()
                .map(status -> new StatusCountView(status, outboxRepository.countByEventStatus(status)))
                .filter(item -> item.count() > 0)
                .toList();
        List<StatusCountView> compensationCounts = COMPENSATION_STATUSES.stream()
                .map(status -> new StatusCountView(status, compensationTaskRepository.countByCompensationStatus(status)))
                .filter(item -> item.count() > 0)
                .toList();
        return new SagaOpsSummaryView(
                outboxRepository.countByEventStatus("PENDING"),
                outboxRepository.countByEventStatus("FAILED"),
                outboxRepository.countByEventStatus("MANUAL_REQUIRED"),
                compensationTaskRepository.countByCompensationStatus("PENDING"),
                compensationTaskRepository.countByCompensationStatus("FAILED"),
                compensationTaskRepository.countByCompensationStatus("MANUAL_REQUIRED"),
                outboxCounts,
                compensationCounts);
    }

    @Transactional(readOnly = true)
    public PageResponse<OutboxEventView> listOutbox(
            int pageNo,
            int pageSize,
            String status,
            String eventType,
            String businessType,
            String businessId) {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        Page<BizEventOutbox> page = outboxRepository.search(
                blankToNull(status),
                blankToNull(eventType),
                blankToNull(businessType),
                blankToNull(businessId),
                PageRequest.of(Math.max(pageNo - 1, 0), clampPageSize(pageSize)));
        List<OutboxEventView> records = page.getContent().stream()
                .map(OutboxEventView::from)
                .toList();
        return PageResponse.of(pageNo, page.getSize(), page.getTotalElements(), records);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompensationTaskOpsView> listCompensationTasks(
            int pageNo,
            int pageSize,
            String status,
            String businessType,
            String compensationType,
            String businessId) {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        Page<com.scf.saga.entity.BizCompensationTask> page = compensationTaskRepository.search(
                blankToNull(status),
                blankToNull(businessType),
                blankToNull(compensationType),
                blankToNull(businessId),
                PageRequest.of(Math.max(pageNo - 1, 0), clampPageSize(pageSize)));
        List<CompensationTaskOpsView> records = page.getContent().stream()
                .map(CompensationTaskOpsView::from)
                .toList();
        return PageResponse.of(pageNo, page.getSize(), page.getTotalElements(), records);
    }

    @Transactional(readOnly = true)
    public OutboxEventDetailView getOutboxDetail(String eventId) {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        BizEventOutbox event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException("DATA_404", "Outbox 事件不存在", 404));
        return OutboxEventDetailView.from(
                event, SagaBusinessRouteResolver.resolve(event.getBusinessType(), event.getBusinessId()));
    }

    @Transactional(readOnly = true)
    public CompensationTaskDetailView getCompensationDetail(String taskId) {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        com.scf.saga.entity.BizCompensationTask task = compensationTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("DATA_404", "补偿任务不存在", 404));
        return CompensationTaskDetailView.from(
                task, SagaBusinessRouteResolver.resolve(task.getBusinessType(), task.getBusinessId()));
    }

    @Transactional(readOnly = true)
    public SagaOpsFilterMetaView filterMeta() {
        tenantContext.requirePermission("SAGA_OPS_VIEW");
        return new SagaOpsFilterMetaView(OUTBOX_STATUSES, COMPENSATION_STATUSES, COMPENSATION_TYPES);
    }

    @Transactional
    public void retryOutbox(String eventId, String reason) {
        tenantContext.requirePermission("SAGA_OPS_MANAGE");
        requireManualReason(reason);
        BizEventOutbox event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException("DATA_404", "Outbox 事件不存在", 404));
        if (!OUTBOX_RETRYABLE.contains(event.getEventStatus())) {
            throw new BusinessException("SAGA_409", "当前 Outbox 状态不可重试: " + event.getEventStatus(), 409);
        }
        Map<String, Object> before = Map.of(
                "event_id", event.getId(),
                "event_status", event.getEventStatus(),
                "retry_count", event.getRetryCount());
        event.setEventStatus("PENDING");
        event.setRetryCount(0);
        event.setNextRetryAt(null);
        event.setLastError(null);
        event.setUpdatedAt(java.time.Instant.now());
        outboxRepository.save(event);
        auditLogService.log(
                "SAGA_OUTBOX_RETRY",
                event.getBusinessType(),
                event.getBusinessId(),
                before,
                Map.of("event_status", "PENDING", "retry_count", 0, "manual_reason", reason.trim()));
        outboxEventProcessor.process(eventId);
    }

    public void retryCompensationTask(String taskId, String reason) {
        compensationTaskService.manualRetry(taskId, reason);
    }

    public void approveCompensationTask(String taskId, String reason) {
        compensationTaskService.approveAndExecute(taskId, reason);
    }

    private static void requireManualReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("VALID_400", "人工操作原因不能为空", 400);
        }
        if (reason.trim().length() < 5) {
            throw new BusinessException("VALID_400", "人工操作原因至少 5 个字符", 400);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampPageSize(int pageSize) {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
