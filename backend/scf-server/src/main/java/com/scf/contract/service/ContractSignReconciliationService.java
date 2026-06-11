package com.scf.contract.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.TenantContext;
import com.scf.contract.dto.ContractSignDtos.ContractSignDocumentSummary;
import com.scf.contract.dto.ContractSignDtos.ContractSignLookupView;
import com.scf.contract.dto.ContractSignDtos.ContractSignProviderStatusView;
import com.scf.contract.dto.ContractSignDtos.ContractSignStatusQueryView;
import com.scf.contract.dto.ContractSignDtos.ContractSignTaskView;
import com.scf.contract.entity.TrContractSignTask;
import com.scf.contract.provider.ContractSignProvider;
import com.scf.contract.provider.ContractSignProviderRegistry;
import com.scf.contract.provider.model.SignStatusResult;
import com.scf.contract.repository.TrContractSignTaskRepository;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.repository.BizCompensationTaskRepository;
import com.scf.saga.support.CompensationTypes;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.repository.TrDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class ContractSignReconciliationService {

    private static final Set<String> RECONCILABLE_TASK_STATUS = Set.of("PENDING_CALLBACK", "SUBMITTED", "RETRYABLE");
    private static final Set<String> PROVIDER_SUCCESS = Set.of("SUCCESS", "SIGNED", "COMPLETED");
    private static final Set<String> PROVIDER_FAILED = Set.of("FAILED", "REJECTED", "CANCELLED");

    private final TrContractSignTaskRepository taskRepository;
    private final TrDocumentRepository documentRepository;
    private final ContractSignProviderRegistry providerRegistry;
    private final ContractSignService contractSignService;
    private final BizCompensationTaskRepository compensationTaskRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ContractSignReconciliationService(
            TrContractSignTaskRepository taskRepository,
            TrDocumentRepository documentRepository,
            ContractSignProviderRegistry providerRegistry,
            ContractSignService contractSignService,
            BizCompensationTaskRepository compensationTaskRepository,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.providerRegistry = providerRegistry;
        this.contractSignService = contractSignService;
        this.compensationTaskRepository = compensationTaskRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ContractSignLookupView lookupByExternalSignRef(String externalSignRef) {
        tenantContext.requirePermission("CONTRACT_SIGN_STATUS_QUERY");
        TrContractSignTask task = loadTaskInTenant(externalSignRef);
        TrDocument doc = loadDocument(task);
        return new ContractSignLookupView(
                task.getExternalSignRef(),
                toTaskView(task),
                toDocumentSummary(doc));
    }

    @Transactional
    public ContractSignStatusQueryView querySignStatus(String externalSignRef, boolean reconcile, String reason) {
        tenantContext.requirePermission("CONTRACT_SIGN_STATUS_QUERY");
        TrContractSignTask task = loadTaskInTenant(externalSignRef);
        TrDocument doc = loadDocument(task);
        return executeQuery(task, doc, reconcile, reason, "CONTRACT_SIGN_STATUS_QUERY");
    }

    @Transactional
    public ContractSignStatusQueryView queryFromCompensationTask(String compensationTaskId, String reason) {
        tenantContext.requirePermission("CONTRACT_SIGN_STATUS_QUERY");
        tenantContext.requireAnyPermission("SAGA_OPS_HANDLE", "SAGA_OPS_MANAGE");
        BizCompensationTask compensation = compensationTaskRepository.findById(compensationTaskId)
                .orElseThrow(() -> new BusinessException("DATA_404", "补偿任务不存在", 404));
        if (!CompensationTypes.CONTRACT_SIGN_CALLBACK_REVIEW.equals(compensation.getCompensationType())) {
            throw new BusinessException("STATE_409", "仅签章回调复核补偿任务支持主动查单", 409);
        }
        String externalSignRef = resolveExternalSignRef(compensation);
        String providerCode = resolveProviderCodeFromCompensation(compensation);
        try {
            TrContractSignTask task = loadTaskInTenant(externalSignRef);
            TrDocument doc = loadDocument(task);
            return executeQuery(task, doc, true, reason, "SAGA_CONTRACT_SIGN_STATUS_QUERY");
        } catch (BusinessException ex) {
            if (!"DATA_404".equals(ex.getCode())) {
                throw ex;
            }
            return queryProviderOnly(externalSignRef, providerCode, reason);
        }
    }

    private ContractSignStatusQueryView queryProviderOnly(
            String externalSignRef,
            String providerCode,
            String reason) {
        ContractSignProvider provider = providerRegistry.require(providerCode);
        SignStatusResult providerResult = provider.querySignStatus(externalSignRef);
        auditLogService.log(
                "CONTRACT_SIGN_STATUS_QUERY",
                "CONTRACT_SIGN_CALLBACK",
                externalSignRef,
                null,
                Map.of(
                        "provider_code", providerCode,
                        "provider_status", providerResult.status(),
                        "reconciled", false,
                        "reason", blankToDefault(reason, "Saga补偿主动查单"),
                        "local_task_found", false));
        return new ContractSignStatusQueryView(
                externalSignRef,
                toProviderView(provider, providerResult),
                null,
                null,
                false,
                null,
                "供应商状态为 " + providerResult.status() + "；无本地签署任务，无法对账推进");
    }

    private ContractSignStatusQueryView executeQuery(
            TrContractSignTask task,
            TrDocument doc,
            boolean reconcile,
            String reason,
            String auditAction) {
        ContractSignProvider provider = providerRegistry.require(task.getProviderCode());
        SignStatusResult providerResult = provider.querySignStatus(task.getExternalSignRef());
        boolean reconciled = false;
        String reconcileAction = null;
        String message = "供应商状态: " + providerResult.status();

        if (reconcile && RECONCILABLE_TASK_STATUS.contains(task.getTaskStatus())) {
            if (PROVIDER_SUCCESS.contains(normalizeStatus(providerResult.status()))) {
                contractSignService.applySignSuccess(doc, task, providerResult.signedAt());
                reconciled = true;
                reconcileAction = "APPLY_SUCCESS";
                message = "已根据供应商查单结果推进为 SIGNED";
            } else if (PROVIDER_FAILED.contains(normalizeStatus(providerResult.status()))) {
                task.setTaskStatus("FAILED");
                task.setCallbackStatus("FAILED");
                task.setFailureReason(blankToDefault(providerResult.failureReason(), "供应商查单返回失败"));
                task.setUpdatedAt(Instant.now());
                taskRepository.save(task);
                contractSignService.applySignFailure(
                        doc,
                        task.getProviderCode(),
                        task.getExternalSignRef(),
                        task.getFailureReason());
                reconciled = true;
                reconcileAction = "APPLY_FAILED";
                message = "已根据供应商查单结果推进为 FAILED";
            } else if ("PENDING".equalsIgnoreCase(providerResult.status())) {
                message = "供应商仍处理中，本地状态无需变更";
            } else {
                message = "供应商状态 " + providerResult.status() + " 无法自动对账，请人工处理";
            }
        } else if (reconcile) {
            message = "本地任务状态为 " + task.getTaskStatus() + "，跳过自动对账";
        }

        TrContractSignTask refreshedTask = taskRepository.findById(task.getId()).orElse(task);
        TrDocument refreshedDoc = documentRepository.findById(doc.getId()).orElse(doc);

        auditLogService.log(
                auditAction,
                "TR_DOCUMENT",
                refreshedDoc.getId(),
                null,
                Map.of(
                        "external_sign_ref", task.getExternalSignRef(),
                        "provider_code", task.getProviderCode(),
                        "provider_status", providerResult.status(),
                        "reconciled", reconciled,
                        "reconcile_action", reconcileAction == null ? "" : reconcileAction,
                        "reason", blankToDefault(reason, "主动查单")));

        return new ContractSignStatusQueryView(
                task.getExternalSignRef(),
                toProviderView(provider, providerResult),
                toTaskView(refreshedTask),
                toDocumentSummary(refreshedDoc),
                reconciled,
                reconcileAction,
                message);
    }

    private TrContractSignTask loadTaskInTenant(String externalSignRef) {
        if (externalSignRef == null || externalSignRef.isBlank()) {
            throw new BusinessException("VALID_400", "external_sign_ref 不能为空", 400);
        }
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        TrContractSignTask task = taskRepository
                .findFirstByExternalSignRefOrderByCreatedAtDesc(externalSignRef.trim())
                .orElseThrow(() -> new BusinessException("DATA_404", "签署任务不存在", 404));
        if (!operatorId.equals(task.getOperatorId()) || !projectId.equals(task.getProjectId())) {
            throw new BusinessException("AUTH_403", "无权访问该签署任务", 403);
        }
        return task;
    }

    private TrDocument loadDocument(TrContractSignTask task) {
        return documentRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        task.getDocumentId(), task.getOperatorId(), task.getProjectId(), (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联单证不存在", 404));
    }

    private String resolveExternalSignRef(BizCompensationTask compensation) {
        try {
            if (compensation.getActionJson() != null) {
                JsonNode payload = objectMapper.readTree(compensation.getActionJson());
                if (payload.hasNonNull("external_sign_ref")) {
                    return payload.get("external_sign_ref").asText();
                }
            }
        } catch (Exception ignored) {
            // fall through to business_id
        }
        return compensation.getBusinessId();
    }

    private String resolveProviderCodeFromCompensation(BizCompensationTask compensation) {
        try {
            if (compensation.getActionJson() != null) {
                JsonNode payload = objectMapper.readTree(compensation.getActionJson());
                if (payload.hasNonNull("provider_code")) {
                    return payload.get("provider_code").asText();
                }
            }
        } catch (Exception ignored) {
            // default MOCK
        }
        return "MOCK";
    }

    private ContractSignProviderStatusView toProviderView(ContractSignProvider provider, SignStatusResult result) {
        return new ContractSignProviderStatusView(
                result.externalSignRef(),
                provider.providerCode(),
                result.status(),
                result.signedAt(),
                result.failureReason(),
                provider.supportsStatusQuery());
    }

    private ContractSignTaskView toTaskView(TrContractSignTask task) {
        return ContractSignTaskViews.toView(task);
    }

    private ContractSignDocumentSummary toDocumentSummary(TrDocument doc) {
        return new ContractSignDocumentSummary(
                doc.getId(),
                doc.getDocumentNo(),
                doc.getSignStatus(),
                doc.getContractStatus(),
                doc.getReviewStatus());
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
