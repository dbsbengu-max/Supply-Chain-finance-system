package com.scf.bpm.service;

import com.scf.audit.service.AuditLogService;
import com.scf.bpm.callback.BusinessProcessCallback;
import com.scf.bpm.entity.BpmProcessInstance;
import com.scf.bpm.entity.BpmTask;
import com.scf.bpm.repository.BpmProcessInstanceRepository;
import com.scf.bpm.repository.BpmTaskRepository;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.util.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class BpmProcessService {

    private final BpmProcessInstanceRepository instanceRepository;
    private final BpmTaskRepository taskRepository;
    private final AuditLogService auditLogService;
    private final List<BusinessProcessCallback> callbacks;

    public BpmProcessService(
            BpmProcessInstanceRepository instanceRepository,
            BpmTaskRepository taskRepository,
            AuditLogService auditLogService,
            List<BusinessProcessCallback> callbacks) {
        this.instanceRepository = instanceRepository;
        this.taskRepository = taskRepository;
        this.auditLogService = auditLogService;
        this.callbacks = callbacks;
    }

    @Transactional
    public BpmProcessInstance startProcess(String processCode, String businessType, String businessId, String assigneeId) {
        String starterId = SecurityUtils.currentUserId();
        BpmProcessInstance instance = new BpmProcessInstance();
        instance.setId(IdGenerator.nextId());
        instance.setProcessCode(processCode);
        instance.setBusinessType(businessType);
        instance.setBusinessId(businessId);
        instance.setProcessStatus("RUNNING");
        instance.setStartedBy(starterId);
        instance.setStartedAt(Instant.now());
        instanceRepository.save(instance);

        BpmTask task = new BpmTask();
        task.setId(IdGenerator.nextId());
        task.setProcessInstanceId(instance.getId());
        task.setBusinessType(businessType);
        task.setBusinessId(businessId);
        task.setNodeCode("FIRST_APPROVAL");
        task.setAssigneeId(assigneeId);
        task.setApprovalStatus("PENDING");
        task.setSubmittedAt(Instant.now());
        taskRepository.save(task);

        auditLogService.log("BPM_START", "BPM_PROCESS", instance.getId(), null, Map.of("processCode", processCode));
        return instance;
    }

    public List<BpmTask> listTodoTasks() {
        return taskRepository.findByAssigneeIdAndApprovalStatus(SecurityUtils.currentUserId(), "PENDING");
    }

    @Transactional
    public BpmTask approveTask(String taskId, String comment) {
        BpmTask task = loadOwnedTask(taskId);
        BpmProcessInstance instance = loadInstanceForDecision(task);
        invokeBeforeApprove(task);

        task.setApprovalStatus("APPROVED");
        task.setApprovalComment(comment);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        instance.setProcessStatus("COMPLETED");
        instance.setEndedAt(Instant.now());
        instanceRepository.save(instance);

        invokeApproved(task);
        auditLogService.log("BPM_APPROVE", "BPM_TASK", task.getId(), null, Map.of("comment", comment == null ? "" : comment));
        return task;
    }

    @Transactional
    public BpmTask rejectTask(String taskId, String comment) {
        BpmTask task = loadOwnedTask(taskId);
        BpmProcessInstance instance = loadInstanceForDecision(task);
        task.setApprovalStatus("REJECTED");
        task.setApprovalComment(comment);
        task.setCompletedAt(Instant.now());
        taskRepository.save(task);

        instance.setProcessStatus("REJECTED");
        instance.setEndedAt(Instant.now());
        instanceRepository.save(instance);

        callbacks.stream()
                .filter(cb -> cb.supports(task.getBusinessType()))
                .forEach(cb -> cb.onProcessRejected(task.getBusinessType(), task.getBusinessId()));

        auditLogService.log("BPM_REJECT", "BPM_TASK", task.getId(), null, Map.of("comment", comment == null ? "" : comment));
        return task;
    }

    private BpmTask loadOwnedTask(String taskId) {
        return taskRepository.findByIdAndAssigneeId(taskId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new BusinessException("AUTH_403", "无权处理该任务", 403));
    }

    private BpmProcessInstance loadInstanceForDecision(BpmTask task) {
        BpmProcessInstance instance = instanceRepository.findById(task.getProcessInstanceId())
                .orElseThrow(() -> new BusinessException("DATA_404", "流程实例不存在", 404));
        if (SecurityUtils.currentUserId().equals(instance.getStartedBy())) {
            throw new BusinessException("BPM_FOUR_EYES_409", "发起人不能审批或驳回本人发起的流程", 409);
        }
        return instance;
    }

    private void invokeBeforeApprove(BpmTask task) {
        callbacks.stream()
                .filter(cb -> cb.supports(task.getBusinessType()))
                .forEach(cb -> cb.beforeApprove(task.getBusinessType(), task.getBusinessId(), task.getNodeCode()));
    }

    private void invokeApproved(BpmTask task) {
        callbacks.stream()
                .filter(cb -> cb.supports(task.getBusinessType()))
                .forEach(cb -> cb.onProcessApproved(task.getBusinessType(), task.getBusinessId()));
    }
}
