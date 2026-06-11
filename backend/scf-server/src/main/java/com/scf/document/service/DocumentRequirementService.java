package com.scf.document.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.util.IdGenerator;
import com.scf.document.dto.DocumentDtos.DocumentRequirementUpsertRequest;
import com.scf.document.dto.DocumentDtos.DocumentRequirementView;
import com.scf.document.entity.TrDocumentRequirement;
import com.scf.document.repository.TrDocumentRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DocumentRequirementService {

    private final TrDocumentRequirementRepository repository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;

    public DocumentRequirementService(
            TrDocumentRequirementRepository repository,
            TenantContext tenantContext,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<DocumentRequirementView> list(String businessType, String businessStage, String projectId) {
        tenantContext.requirePermission("DOCUMENT_REQUIREMENT_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String scopedProject = projectId == null || projectId.isBlank() ? tenantContext.projectId() : projectId;
        return repository.findManagedList(
                        operatorId,
                        blankToNull(scopedProject),
                        blankToNull(businessType),
                        blankToNull(businessStage))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public DocumentRequirementView create(DocumentRequirementUpsertRequest request) {
        tenantContext.requirePermission("DOCUMENT_REQUIREMENT_MANAGE");
        String operatorId = tenantContext.requireOperatorId();
        Instant now = Instant.now();
        TrDocumentRequirement row = new TrDocumentRequirement();
        row.setId(IdGenerator.nextId());
        row.setOperatorId(operatorId);
        row.setProjectId(blankToNull(request.projectId()));
        applyUpsert(row, request);
        row.setCreatedBy(SecurityUtils.currentUserId());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setDeletedFlag((short) 0);
        row.setVersionNo(1);
        repository.save(row);
        auditLogService.log("DOCUMENT_REQUIREMENT_CREATE", "TR_DOCUMENT_REQUIREMENT", row.getId(), null, toAudit(row));
        return toView(row);
    }

    @Transactional
    public DocumentRequirementView update(String id, DocumentRequirementUpsertRequest request) {
        tenantContext.requirePermission("DOCUMENT_REQUIREMENT_MANAGE");
        String operatorId = tenantContext.requireOperatorId();
        TrDocumentRequirement row = repository.findByIdAndOperatorIdAndDeletedFlag(id, operatorId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "必备单证规则不存在", 404));
        Map<String, Object> before = toAudit(row);
        applyUpsert(row, request);
        row.setUpdatedBy(SecurityUtils.currentUserId());
        row.setUpdatedAt(Instant.now());
        row.setVersionNo(row.getVersionNo() + 1);
        repository.save(row);
        auditLogService.log("DOCUMENT_REQUIREMENT_UPDATE", "TR_DOCUMENT_REQUIREMENT", id, before, toAudit(row));
        return toView(row);
    }

    private void applyUpsert(TrDocumentRequirement row, DocumentRequirementUpsertRequest request) {
        row.setBusinessType(request.businessType());
        row.setBusinessStage(request.businessStage());
        row.setProductType(blankToNull(request.productType()));
        row.setDocumentType(request.documentType());
        row.setRequiredFlag(boolFlag(request.requiredFlag(), true));
        row.setOcrRequired(boolFlag(request.ocrRequired(), false));
        row.setManualReviewRequired(boolFlag(request.manualReviewRequired(), true));
        row.setMinConfidence(request.minConfidence() == null ? new BigDecimal("0.8500") : request.minConfidence());
        row.setEnabled(boolFlag(request.enabled(), true));
        row.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        if (request.projectId() != null) {
            row.setProjectId(blankToNull(request.projectId()));
        }
    }

    private DocumentRequirementView toView(TrDocumentRequirement row) {
        return new DocumentRequirementView(
                row.getId(),
                row.getProjectId(),
                row.getBusinessType(),
                row.getBusinessStage(),
                row.getProductType(),
                row.getDocumentType(),
                row.getRequiredFlag() == 1,
                row.getOcrRequired() == 1,
                row.getManualReviewRequired() == 1,
                row.getMinConfidence(),
                row.getEnabled() == 1,
                row.getSortNo(),
                row.getUpdatedAt());
    }

    private Map<String, Object> toAudit(TrDocumentRequirement row) {
        return Map.of(
                "business_type", row.getBusinessType(),
                "business_stage", row.getBusinessStage(),
                "document_type", row.getDocumentType(),
                "enabled", row.getEnabled());
    }

    private short boolFlag(Boolean value, boolean defaultValue) {
        boolean resolved = value == null ? defaultValue : value;
        return (short) (resolved ? 1 : 0);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
