package com.scf.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.ai.ocr.dto.OcrFieldView;
import com.scf.ai.ocr.dto.OcrJobCreateRequest;
import com.scf.ai.ocr.dto.OcrJobView;
import com.scf.ai.ocr.service.OcrJobService;
import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.document.dto.DocumentDtos;
import com.scf.document.dto.DocumentDtos.DocumentCenterDetailView;
import com.scf.document.dto.DocumentDtos.DocumentCenterListItem;
import com.scf.document.dto.DocumentDtos.DocumentCenterRegisterRequest;
import com.scf.document.dto.DocumentDtos.DocumentReviewLogView;
import com.scf.document.dto.DocumentDtos.DocumentReviewReasonRequest;
import com.scf.document.entity.TrDocumentReviewLog;
import com.scf.document.repository.TrDocumentReviewLogRepository;
import com.scf.file.service.FileService;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.repository.TrDocumentRepository;
import com.scf.trade.repository.TrOrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentCenterService {

    private static final String BIZ_TRADE_ORDER = "TRADE_ORDER";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String REVIEW_REJECTED = "REJECTED";
    private static final String REVIEW_PENDING = "PENDING";

    private final TrDocumentRepository documentRepository;
    private final TrDocumentReviewLogRepository reviewLogRepository;
    private final TrOrderRepository orderRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;
    private final OcrJobService ocrJobService;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public DocumentCenterService(
            TrDocumentRepository documentRepository,
            TrDocumentReviewLogRepository reviewLogRepository,
            TrOrderRepository orderRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService,
            OcrJobService ocrJobService,
            FileService fileService,
            ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.orderRepository = orderRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
        this.ocrJobService = ocrJobService;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentCenterListItem> list(
            int pageNo,
            int pageSize,
            String businessType,
            String businessId,
            String documentType,
            String documentStatus,
            String reviewStatus,
            String contractStatus) {
        tenantContext.requirePermission("DOCUMENT_VIEW");
        ScopeFilter scope = resolveScope();
        Page<TrDocument> page = documentRepository.findCenterFiltered(
                scope.operatorId(),
                scope.projectId(),
                blankToNull(businessType),
                blankToNull(businessId),
                blankToNull(documentType),
                blankToNull(documentStatus),
                blankToNull(reviewStatus),
                blankToNull(contractStatus),
                scope.enterpriseScope(),
                scope.userScope(),
                scope.accessibleOrderIds(),
                PageRequest.of(Math.max(pageNo - 1, 0), Math.min(Math.max(pageSize, 1), 100)));
        List<DocumentCenterListItem> records = page.getContent().stream().map(this::toListItem).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    @Transactional(readOnly = true)
    public DocumentCenterDetailView get(String id) {
        tenantContext.requirePermission("DOCUMENT_VIEW");
        TrDocument doc = loadAccessibleDocument(id);
        List<DocumentReviewLogView> logs = reviewLogRepository.findByDocumentIdOrderByCreatedAtDesc(id).stream()
                .map(this::toReviewLogView)
                .toList();
        return toDetailView(doc, logs);
    }

    @Transactional
    public DocumentCenterDetailView register(DocumentCenterRegisterRequest request) {
        tenantContext.requirePermission("DOCUMENT_UPLOAD");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        assertBusinessAccessible(request.businessType(), request.businessId(), operatorId, projectId);
        fileService.requireAccessibleFile(request.fileId());

        UserContext user = SecurityUtils.currentUser();
        Instant now = Instant.now();
        TrDocument doc = new TrDocument();
        doc.setId(IdGenerator.nextId());
        doc.setOperatorId(operatorId);
        doc.setProjectId(projectId);
        doc.setBusinessType(request.businessType());
        doc.setBusinessId(request.businessId());
        doc.setDocumentType(request.documentType());
        doc.setDocumentNo(blankToNull(request.documentNo()));
        doc.setFileId(request.fileId());
        doc.setOcrStatus("PENDING");
        doc.setValidationStatus("PENDING");
        doc.setDocumentStatus("UPLOADED");
        doc.setReviewStatus("NOT_REQUIRED");
        if ("PURCHASE_CONTRACT".equals(request.documentType())) {
            doc.setContractStatus("DRAFT");
            doc.setSignStatus("NOT_REQUIRED");
        } else {
            doc.setContractStatus(blankToNull(request.contractStatus()) == null ? "NOT_CONTRACT" : request.contractStatus());
            doc.setSignStatus("NOT_REQUIRED");
        }
        doc.setCreatedBy(user.userId());
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        doc.setDeletedFlag((short) 0);
        documentRepository.save(doc);

        auditLogService.log("DOCUMENT_REGISTER", "TR_DOCUMENT", doc.getId(), null, toAuditSnapshot(doc));

        if (Boolean.TRUE.equals(request.triggerOcr())) {
            tenantContext.requirePermission("AI_OCR_EXECUTE");
            runOcrInternal(doc);
        }
        return get(doc.getId());
    }

    @Transactional
    public DocumentCenterDetailView triggerOcr(String id) {
        tenantContext.requirePermission("AI_OCR_EXECUTE");
        TrDocument doc = loadAccessibleDocument(id);
        assertNotArchived(doc);
        runOcrInternal(doc);
        return get(id);
    }

    @Transactional
    public DocumentCenterDetailView submitReview(String id) {
        tenantContext.requirePermission("DOCUMENT_REVIEW_SUBMIT");
        TrDocument doc = loadAccessibleDocument(id);
        assertNotArchived(doc);
        String before = doc.getReviewStatus();
        doc.setReviewStatus(REVIEW_PENDING);
        doc.setDocumentStatus("REVIEWING");
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "SUBMIT_REVIEW", before, REVIEW_PENDING, null);
        auditLogService.log("DOCUMENT_SUBMIT_REVIEW", "TR_DOCUMENT", id, Map.of("review_status", before), Map.of("review_status", REVIEW_PENDING));
        return get(id);
    }

    @Transactional
    public DocumentCenterDetailView approve(String id, DocumentReviewReasonRequest body) {
        tenantContext.requirePermission("DOCUMENT_REVIEW_APPROVE");
        TrDocument doc = loadAccessibleDocument(id);
        assertNotArchived(doc);
        assertReviewable(doc);
        String before = doc.getReviewStatus();
        UserContext user = SecurityUtils.currentUser();
        doc.setReviewStatus(REVIEW_APPROVED);
        doc.setReviewResult("PASS");
        doc.setReviewReason(blankToNull(body == null ? null : body.reason()));
        doc.setDocumentStatus(REVIEW_APPROVED);
        doc.setReviewedBy(user.userId());
        doc.setReviewedAt(Instant.now());
        doc.setValidationStatus("APPROVED");
        if ("PURCHASE_CONTRACT".equals(doc.getDocumentType()) || isContractStatus(doc.getContractStatus())) {
            doc.setContractStatus("PENDING_SIGN");
            doc.setSignStatus("PENDING");
        }
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "APPROVE", before, REVIEW_APPROVED, doc.getReviewReason());
        auditLogService.log("DOCUMENT_APPROVE", "TR_DOCUMENT", id, Map.of("review_status", before), Map.of("review_status", REVIEW_APPROVED));
        return get(id);
    }

    @Transactional
    public DocumentCenterDetailView reject(String id, DocumentReviewReasonRequest body) {
        tenantContext.requirePermission("DOCUMENT_REVIEW_APPROVE");
        String reason = body == null ? null : blankToNull(body.reason());
        if (reason == null) {
            throw new BusinessException("VALID_400", "驳回必须填写原因", 400);
        }
        TrDocument doc = loadAccessibleDocument(id);
        assertNotArchived(doc);
        assertReviewable(doc);
        String before = doc.getReviewStatus();
        UserContext user = SecurityUtils.currentUser();
        doc.setReviewStatus(REVIEW_REJECTED);
        doc.setReviewResult("FAIL");
        doc.setReviewReason(reason);
        doc.setDocumentStatus(REVIEW_REJECTED);
        doc.setReviewedBy(user.userId());
        doc.setReviewedAt(Instant.now());
        doc.setValidationStatus("REJECTED");
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "REJECT", before, REVIEW_REJECTED, reason);
        auditLogService.log("DOCUMENT_REJECT", "TR_DOCUMENT", id, Map.of("review_status", before), Map.of("review_status", REVIEW_REJECTED, "reason", reason));
        return get(id);
    }

    @Transactional
    public DocumentCenterDetailView archive(String id, DocumentReviewReasonRequest body) {
        tenantContext.requirePermission("DOCUMENT_ARCHIVE");
        TrDocument doc = loadAccessibleDocument(id);
        if (STATUS_ARCHIVED.equals(doc.getDocumentStatus())) {
            throw new BusinessException("STATE_409", "单证已归档", 409);
        }
        String before = doc.getDocumentStatus();
        doc.setDocumentStatus(STATUS_ARCHIVED);
        doc.setContractStatus("VOID");
        touch(doc);
        documentRepository.save(doc);
        appendReviewLog(doc, "ARCHIVE", before, STATUS_ARCHIVED, body == null ? null : body.reason());
        auditLogService.log("DOCUMENT_ARCHIVE", "TR_DOCUMENT", id, Map.of("document_status", before), Map.of("document_status", STATUS_ARCHIVED));
        return get(id);
    }

    private void runOcrInternal(TrDocument doc) {
        OcrJobView job = ocrJobService.createJob(new OcrJobCreateRequest(
                doc.getFileId(),
                "TRADE_DOCUMENT",
                doc.getBusinessId(),
                "TABLE_OCR"));
        BigDecimal confidence = averageConfidence(job.fields());
        doc.setOcrJobId(job.id());
        doc.setOcrConfidence(confidence);
        doc.setOcrStatus("COMPLETED");
        doc.setDocumentStatus("OCR_COMPLETED");
        doc.setValidationStatus("PENDING_CONFIRM");
        if (confidence != null && confidence.compareTo(new BigDecimal("0.85")) < 0) {
            doc.setReviewStatus(REVIEW_PENDING);
        }
        touch(doc);
        documentRepository.save(doc);
        auditLogService.log("DOCUMENT_OCR", "TR_DOCUMENT", doc.getId(), null, Map.of("ocr_job_id", job.id(), "ocr_confidence", confidence));
    }

    private TrDocument loadAccessibleDocument(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        TrDocument doc = documentRepository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "单证不存在", 404));
        assertDocumentAccessible(doc);
        return doc;
    }

    private void assertDocumentAccessible(TrDocument doc) {
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user) || dataScopeHelper.isWarehouseRole(user)) {
            return;
        }
        if (doc.getCreatedBy().equals(user.userId())) {
            return;
        }
        if (BIZ_TRADE_ORDER.equals(doc.getBusinessType())) {
            TrOrder order = orderRepository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                            doc.getBusinessId(), doc.getOperatorId(), doc.getProjectId(), (short) 0)
                    .orElseThrow(() -> new BusinessException("DATA_404", "单证不存在", 404));
            if (!dataScopeHelper.canAccessTradeOrder(user, order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId())) {
                throw new BusinessException("AUTH_403", "无权访问该单证", 403);
            }
            return;
        }
        throw new BusinessException("AUTH_403", "无权访问该单证", 403);
    }

    private void assertBusinessAccessible(String businessType, String businessId, String operatorId, String projectId) {
        if (!BIZ_TRADE_ORDER.equals(businessType)) {
            return;
        }
        TrOrder order = orderRepository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(businessId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联业务对象不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessTradeOrder(user, order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId())) {
            throw new BusinessException("AUTH_403", "无权登记该业务单证", 403);
        }
    }

    private void assertNotArchived(TrDocument doc) {
        if (STATUS_ARCHIVED.equals(doc.getDocumentStatus())) {
            throw new BusinessException("STATE_409", "已归档单证不可继续操作", 409);
        }
    }

    private void assertReviewable(TrDocument doc) {
        if (STATUS_ARCHIVED.equals(doc.getDocumentStatus())) {
            throw new BusinessException("STATE_409", "已归档单证不可复核", 409);
        }
    }

    private ScopeFilter resolveScope() {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.canReadOperatorData(user) || dataScopeHelper.isFundingRole(user) || dataScopeHelper.isWarehouseRole(user)) {
            return new ScopeFilter(operatorId, projectId, null, null, List.of("__ALL__"));
        }
        if (dataScopeHelper.isEnterpriseRole(user)) {
            List<String> orderIds = orderRepository.findByEnterpriseScope(
                            operatorId, projectId, user.enterpriseId(), PageRequest.of(0, 500))
                    .map(TrOrder::getId)
                    .getContent();
            if (orderIds.isEmpty()) {
                orderIds = List.of("__NONE__");
            }
            return new ScopeFilter(operatorId, projectId, user.enterpriseId(), user.userId(), orderIds);
        }
        throw new BusinessException("AUTH_403", "无权查看单证中心", 403);
    }

    private void appendReviewLog(TrDocument doc, String action, String before, String after, String reason) {
        UserContext user = SecurityUtils.currentUser();
        TrDocumentReviewLog log = new TrDocumentReviewLog();
        log.setId(IdGenerator.nextId());
        log.setDocumentId(doc.getId());
        log.setAction(action);
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setOperatorId(user.userId());
        log.setOperatorRole(user.roleId());
        log.setReason(reason);
        log.setSnapshotJson(toJson(snapshotOf(doc)));
        log.setCreatedAt(Instant.now());
        reviewLogRepository.save(log);
    }

    private DocumentCenterListItem toListItem(TrDocument doc) {
        return new DocumentCenterListItem(
                doc.getId(),
                doc.getBusinessType(),
                doc.getBusinessId(),
                doc.getDocumentType(),
                doc.getDocumentNo(),
                doc.getFileId(),
                doc.getDocumentStatus(),
                doc.getReviewStatus(),
                doc.getContractStatus(),
                doc.getSignStatus(),
                doc.getOcrStatus(),
                doc.getOcrConfidence(),
                doc.getOcrJobId(),
                doc.getUpdatedAt(),
                doc.getCreatedAt());
    }

    private DocumentCenterDetailView toDetailView(TrDocument doc, List<DocumentReviewLogView> logs) {
        return new DocumentCenterDetailView(
                doc.getId(),
                doc.getOperatorId(),
                doc.getProjectId(),
                doc.getBusinessType(),
                doc.getBusinessId(),
                doc.getDocumentType(),
                doc.getDocumentNo(),
                doc.getFileId(),
                doc.getDocumentStatus(),
                doc.getReviewStatus(),
                doc.getReviewResult(),
                doc.getReviewReason(),
                doc.getContractStatus(),
                doc.getSignStatus(),
                doc.getSignProvider(),
                doc.getExternalSignRef(),
                doc.getOcrStatus(),
                doc.getOcrJobId(),
                doc.getOcrConfidence(),
                doc.getValidationStatus(),
                doc.getValidationResultJson(),
                doc.getIssuedAt(),
                doc.getExpiredAt(),
                doc.getReviewedBy(),
                doc.getReviewedAt(),
                doc.getCreatedBy(),
                doc.getCreatedAt(),
                doc.getUpdatedAt(),
                logs);
    }

    private DocumentReviewLogView toReviewLogView(TrDocumentReviewLog log) {
        return new DocumentReviewLogView(
                log.getId(),
                log.getAction(),
                log.getBeforeStatus(),
                log.getAfterStatus(),
                log.getOperatorId(),
                log.getOperatorRole(),
                log.getReason(),
                log.getCreatedAt());
    }

    private Map<String, Object> snapshotOf(TrDocument doc) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("ocr_job_id", doc.getOcrJobId());
        snapshot.put("ocr_confidence", doc.getOcrConfidence());
        snapshot.put("review_status", doc.getReviewStatus());
        return snapshot;
    }

    private Map<String, Object> toAuditSnapshot(TrDocument doc) {
        return Map.of(
                "business_type", doc.getBusinessType(),
                "business_id", doc.getBusinessId(),
                "document_type", doc.getDocumentType(),
                "file_id", doc.getFileId());
    }

    private BigDecimal averageConfidence(List<OcrFieldView> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (OcrFieldView field : fields) {
            sum = sum.add(field.confidence());
        }
        return sum.divide(BigDecimal.valueOf(fields.size()), 4, RoundingMode.HALF_UP);
    }

    private void touch(TrDocument doc) {
        doc.setUpdatedBy(SecurityUtils.currentUserId());
        doc.setUpdatedAt(Instant.now());
    }

    private static boolean isContractStatus(String contractStatus) {
        return contractStatus != null && !"NOT_CONTRACT".equals(contractStatus) && !"VOID".equals(contractStatus);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private record ScopeFilter(
            String operatorId,
            String projectId,
            String enterpriseScope,
            String userScope,
            List<String> accessibleOrderIds) {
    }
}
