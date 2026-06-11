package com.scf.contract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.dto.ContractSignDtos.ContractSignInitiateRequest;
import com.scf.contract.dto.ContractSignDtos.ContractSignInitiateResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignTaskView;
import com.scf.contract.dto.ContractSignDtos.ContractSignerRequest;
import com.scf.contract.entity.TrContractSignTask;
import com.scf.contract.provider.ContractSignProvider;
import com.scf.contract.provider.ContractSignProviderRegistry;
import com.scf.contract.provider.model.SignRequestContext;
import com.scf.contract.provider.model.SignRequestResult;
import com.scf.contract.repository.TrContractSignTaskRepository;
import com.scf.document.entity.TrDocumentReviewLog;
import com.scf.document.repository.TrDocumentReviewLogRepository;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.repository.TrDocumentRepository;
import com.scf.trade.repository.TrOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractSignService {

    private static final String DOC_TYPE_PURCHASE_CONTRACT = "PURCHASE_CONTRACT";
    private static final Set<String> SIGNING_SIGN_STATUS = Set.of("SIGNING");
    private static final Set<String> RETRYABLE_TASK_STATUS = Set.of("FAILED", "RETRYABLE");

    private final TrDocumentRepository documentRepository;
    private final TrContractSignTaskRepository taskRepository;
    private final TrDocumentReviewLogRepository reviewLogRepository;
    private final TrOrderRepository orderRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final ContractSignProviderRegistry providerRegistry;
    private final ContractSignProperties properties;
    private final ContractSignRolloutResolver rolloutResolver;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public ContractSignService(
            TrDocumentRepository documentRepository,
            TrContractSignTaskRepository taskRepository,
            TrDocumentReviewLogRepository reviewLogRepository,
            TrOrderRepository orderRepository,
            FnFinanceApplicationRepository financeRepository,
            ContractSignProviderRegistry providerRegistry,
            ContractSignProperties properties,
            ContractSignRolloutResolver rolloutResolver,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService,
            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.orderRepository = orderRepository;
        this.financeRepository = financeRepository;
        this.providerRegistry = providerRegistry;
        this.properties = properties;
        this.rolloutResolver = rolloutResolver;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ContractSignTaskView> listTasks(String documentId) {
        tenantContext.requirePermission("DOCUMENT_VIEW");
        loadAccessibleDocument(documentId);
        return taskRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .map(ContractSignTaskViews::toView)
                .toList();
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ContractSignInitiateResponse initiateSign(String documentId, ContractSignInitiateRequest request) {
        tenantContext.requirePermission("DOCUMENT_CONTRACT_SIGN");
        TrDocument doc = loadAccessibleDocument(documentId);
        assertSignEligible(doc);
        if (SIGNING_SIGN_STATUS.contains(doc.getSignStatus())) {
            throw new BusinessException("STATE_409", "合同签署进行中", 409);
        }
        if ("SIGNED".equals(doc.getSignStatus())) {
            throw new BusinessException("STATE_409", "合同已完成签署", 409);
        }

        String providerCode = resolveProviderCode(request);
        ContractSignProvider provider = providerRegistry.require(providerCode);
        UserContext user = SecurityUtils.currentUser();
        Instant now = Instant.now();

        TrContractSignTask task = new TrContractSignTask();
        task.setId(IdGenerator.nextId());
        task.setOperatorId(doc.getOperatorId());
        task.setProjectId(doc.getProjectId());
        task.setDocumentId(doc.getId());
        task.setProviderCode(providerCode);
        task.setTaskStatus("SUBMITTED");
        task.setSignersJson(toJson(request == null ? null : request.signers()));
        task.setCreatedBy(user.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);

        SignRequestResult providerResult = provider.createSignRequest(new SignRequestContext(
                task.getId(),
                doc.getId(),
                doc.getFileId(),
                doc.getDocumentNo(),
                doc.getBusinessType(),
                doc.getBusinessId(),
                mapSigners(request),
                request != null && Boolean.TRUE.equals(request.simulateFailure())));

        task.setExternalSignRef(providerResult.externalSignRef());
        applyProviderTrace(task, providerResult);
        if ("SUBMIT_FAILED".equals(providerResult.providerStatus())) {
            task.setTaskStatus("FAILED");
            task.setFailureReason(providerResult.providerMessage());
            taskRepository.save(task);
            applySignFailure(doc, providerCode, providerResult.externalSignRef(), providerResult.providerMessage());
            appendReviewLog(doc, "SIGN_SUBMIT_FAILED", doc.getSignStatus(), "FAILED", providerResult.providerMessage());
            auditLogService.log("CONTRACT_SIGN_SUBMIT_FAILED", "TR_DOCUMENT", doc.getId(), null, Map.of(
                    "task_id", task.getId(),
                    "provider", providerCode));
            throw new BusinessException("CONTRACT_SIGN_409", providerResult.providerMessage(), 409);
        }

        task.setTaskStatus("PENDING_CALLBACK");
        taskRepository.save(task);

        String beforeSign = doc.getSignStatus();
        doc.setSignProvider(providerCode);
        doc.setExternalSignRef(providerResult.externalSignRef());
        doc.setSignStatus("SIGNING");
        doc.setContractStatus("SIGNING");
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "SIGN_INITIATED", beforeSign, "SIGNING", providerResult.providerMessage());
        auditLogService.log("CONTRACT_SIGN_INITIATED", "TR_DOCUMENT", doc.getId(), null, Map.of(
                "task_id", task.getId(),
                "external_sign_ref", providerResult.externalSignRef(),
                "provider", providerCode,
                "platform_trace_id", blankOrDash(task.getPlatformTraceId()),
                "provider_request_id", blankOrDash(task.getProviderRequestId()),
                "provider_trace_id", blankOrDash(task.getProviderTraceId())));

        return new ContractSignInitiateResponse(
                doc.getId(),
                doc.getSignStatus(),
                doc.getContractStatus(),
                doc.getSignProvider(),
                doc.getExternalSignRef(),
                toTaskView(task));
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ContractSignInitiateResponse retrySign(String documentId) {
        tenantContext.requirePermission("DOCUMENT_CONTRACT_SIGN_RETRY");
        TrDocument doc = loadAccessibleDocument(documentId);
        assertContractDocument(doc);
        if (!"FAILED".equals(doc.getSignStatus()) && !"SIGN_FAILED".equals(doc.getContractStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可重试签署", 409);
        }

        TrContractSignTask lastTask = taskRepository
                .findTopByDocumentIdAndTaskStatusInOrderByCreatedAtDesc(doc.getId(), List.of("FAILED", "RETRYABLE", "PENDING_CALLBACK"))
                .orElseThrow(() -> new BusinessException("DATA_404", "未找到可重试的签署任务", 404));

        if (lastTask.getRetryCount() >= properties.getMaxRetryCount()) {
            throw new BusinessException("CONTRACT_SIGN_409", "已超过最大重试次数", 409);
        }

        ContractSignInitiateRequest retryRequest = new ContractSignInitiateRequest(
                lastTask.getProviderCode(),
                null,
                false);
        lastTask.setRetryCount(lastTask.getRetryCount() + 1);
        lastTask.setLastRetryAt(Instant.now());
        lastTask.setTaskStatus("RETRYABLE");
        lastTask.setUpdatedAt(Instant.now());
        taskRepository.save(lastTask);

        doc.setSignStatus("PENDING");
        doc.setContractStatus("PENDING_SIGN");
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "SIGN_RETRY", "FAILED", "PENDING", "发起签署补偿重试");

        return initiateSign(documentId, retryRequest);
    }

    @Transactional(readOnly = true)
    public List<ContractSignTaskView> listTasksForDocument(String documentId) {
        return taskRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
                .map(ContractSignTaskViews::toView)
                .toList();
    }

    private void assertSignEligible(TrDocument doc) {
        assertContractDocument(doc);
        if ("ARCHIVED".equals(doc.getDocumentStatus())) {
            throw new BusinessException("STATE_409", "单证已归档", 409);
        }
        if (!"APPROVED".equals(doc.getReviewStatus())) {
            throw new BusinessException("STATE_409", "合同须先完成人工复核通过", 409);
        }
    }

    private static void assertContractDocument(TrDocument doc) {
        if (DOC_TYPE_PURCHASE_CONTRACT.equals(doc.getDocumentType())) {
            return;
        }
        if (doc.getContractStatus() != null && !"NOT_CONTRACT".equals(doc.getContractStatus())) {
            return;
        }
        throw new BusinessException("VALID_400", "该单证不是合同类文档", 400);
    }

    void applySignSuccess(TrDocument doc, TrContractSignTask task, Instant signedAt) {
        String before = doc.getSignStatus();
        doc.setSignStatus("SIGNED");
        doc.setContractStatus("SIGNED");
        touch(doc);
        documentRepository.save(doc);

        task.setTaskStatus("SIGNED");
        task.setCallbackStatus("SUCCESS");
        task.setSignedAt(signedAt == null ? Instant.now() : signedAt);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);

        appendReviewLog(doc, "SIGN_COMPLETED", before, "SIGNED", "供应商回调签署成功");
        auditSignEvent(doc, task, "CONTRACT_SIGN_COMPLETED", Map.of(
                "task_id", task.getId(),
                "external_sign_ref", task.getExternalSignRef()));
    }

    void applySignFailure(TrDocument doc, String providerCode, String externalRef, String reason) {
        doc.setSignProvider(providerCode);
        doc.setExternalSignRef(externalRef);
        doc.setSignStatus("FAILED");
        doc.setContractStatus("SIGN_FAILED");
        touch(doc);
        documentRepository.save(doc);
    }

    private void auditSignEvent(TrDocument doc, TrContractSignTask task, String action, Map<String, Object> after) {
        UserContext user = SecurityUtils.optionalCurrentUser();
        if (user != null) {
            auditLogService.log(action, "TR_DOCUMENT", doc.getId(), null, after);
            return;
        }
        auditLogService.logAsSystem(
                "SYSTEM_CONTRACT_CALLBACK",
                doc.getOperatorId(),
                null,
                doc.getProjectId(),
                action,
                "TR_DOCUMENT",
                doc.getId(),
                null,
                after);
    }

    private TrDocument loadAccessibleDocument(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        TrDocument doc = documentRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "单证不存在", 404));
        assertDocumentScope(doc);
        return doc;
    }

    private void assertDocumentScope(TrDocument doc) {
        UserContext user = SecurityUtils.currentUser();
        if ("TRADE_ORDER".equals(doc.getBusinessType())) {
            TrOrder order = orderRepository
                    .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                            doc.getBusinessId(), doc.getOperatorId(), doc.getProjectId(), (short) 0)
                    .orElseThrow(() -> new BusinessException("DATA_404", "关联业务对象不存在", 404));
            if (!dataScopeHelper.canAccessTradeOrder(
                    user, order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId())) {
                throw new BusinessException("AUTH_403", "无权访问该单证", 403);
            }
            return;
        }
        if ("FINANCE".equals(doc.getBusinessType())) {
            FnFinanceApplication finance = financeRepository
                    .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                            doc.getBusinessId(), doc.getOperatorId(), doc.getProjectId(), (short) 0)
                    .orElseThrow(() -> new BusinessException("DATA_404", "关联融资申请不存在", 404));
            if (!dataScopeHelper.canAccessFinance(user, finance.getCustomerId(), finance.getFundingPartyId())) {
                throw new BusinessException("AUTH_403", "无权访问该单证", 403);
            }
        }
    }

    private String resolveProviderCode(ContractSignInitiateRequest request) {
        String requested = request != null ? request.providerCode() : null;
        return rolloutResolver.resolveProviderCode(
                requested,
                tenantContext.requireProjectId(),
                tenantContext.requireOperatorId());
    }

    private List<SignRequestContext.SignerRef> mapSigners(ContractSignInitiateRequest request) {
        if (request == null || request.signers() == null) {
            return List.of();
        }
        return request.signers().stream()
                .map(s -> new SignRequestContext.SignerRef(s.enterpriseId(), s.signerName(), s.signerRole()))
                .toList();
    }

    private ContractSignTaskView toTaskView(TrContractSignTask task) {
        return ContractSignTaskViews.toView(task);
    }

    private void applyProviderTrace(TrContractSignTask task, SignRequestResult providerResult) {
        task.setPlatformTraceId(providerResult.platformTraceId());
        task.setProviderRequestId(providerResult.providerRequestId());
        task.setProviderTraceId(providerResult.providerTraceId());
        task.setProviderExchangeJson(providerResult.providerExchangeSummary());
    }

    private static String blankOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void appendReviewLog(TrDocument doc, String action, String before, String after, String reason) {
        UserContext user = SecurityUtils.optionalCurrentUser();
        TrDocumentReviewLog log = new TrDocumentReviewLog();
        log.setId(IdGenerator.nextId());
        log.setDocumentId(doc.getId());
        log.setAction(action);
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setOperatorId(user == null ? "SYSTEM" : user.userId());
        log.setOperatorRole(user == null ? "SYSTEM" : user.roleId());
        log.setReason(reason);
        log.setSnapshotJson(toJson(Map.of("sign_status", doc.getSignStatus(), "contract_status", doc.getContractStatus())));
        log.setCreatedAt(Instant.now());
        reviewLogRepository.save(log);
    }

    private void touch(TrDocument doc) {
        String userId = SecurityUtils.currentUserId();
        doc.setUpdatedBy(userId == null ? "SYSTEM" : userId);
        doc.setUpdatedAt(Instant.now());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize sign payload", ex);
        }
    }
}
