package com.scf.document.service;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.document.dto.DocumentDtos.DocumentValidateMissingItem;
import com.scf.document.dto.DocumentDtos.DocumentValidatePendingItem;
import com.scf.document.dto.DocumentDtos.DocumentValidateRequest;
import com.scf.document.dto.DocumentDtos.DocumentValidateResponse;
import com.scf.document.dto.DocumentDtos.DocumentValidateWarningItem;
import com.scf.document.entity.TrDocumentRequirement;
import com.scf.document.repository.TrDocumentRequirementRepository;
import com.scf.finance.entity.FnFinanceApplication;
import com.scf.finance.repository.FnFinanceApplicationRepository;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.repository.TrDocumentRepository;
import com.scf.trade.repository.TrOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentValidationService {

    private static final String BIZ_TRADE_ORDER = "TRADE_ORDER";
    private static final String BIZ_FINANCE = "FINANCE";

    private final TrDocumentRepository documentRepository;
    private final TrDocumentRequirementRepository requirementRepository;
    private final TrOrderRepository orderRepository;
    private final FnFinanceApplicationRepository financeRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;

    public DocumentValidationService(
            TrDocumentRepository documentRepository,
            TrDocumentRequirementRepository requirementRepository,
            TrOrderRepository orderRepository,
            FnFinanceApplicationRepository financeRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper) {
        this.documentRepository = documentRepository;
        this.requirementRepository = requirementRepository;
        this.orderRepository = orderRepository;
        this.financeRepository = financeRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
    }

    @Transactional(readOnly = true)
    public DocumentValidateResponse validate(DocumentValidateRequest request) {
        tenantContext.requirePermission("DOCUMENT_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        assertBusinessAccessible(request.businessType(), request.businessId(), operatorId, projectId);
        return validateCore(
                operatorId,
                projectId,
                request.businessType(),
                request.businessId(),
                request.businessStage(),
                request.productType());
    }

    @Transactional(readOnly = true)
    public DocumentValidateResponse validateFinanceDisburse(FnFinanceApplication finance) {
        return validateCore(
                finance.getOperatorId(),
                finance.getProjectId(),
                BIZ_FINANCE,
                finance.getId(),
                "DISBURSE",
                finance.getProductType());
    }

    private DocumentValidateResponse validateCore(
            String operatorId,
            String projectId,
            String businessType,
            String businessId,
            String businessStage,
            String productType) {
        List<TrDocumentRequirement> rules = requirementRepository.findActiveRules(
                operatorId,
                projectId,
                businessType,
                businessStage,
                blankToNull(productType));

        List<TrDocument> docs = documentRepository.findByOperatorIdAndProjectIdAndBusinessTypeAndBusinessIdAndDeletedFlag(
                operatorId, projectId, businessType, businessId, (short) 0);

        Map<String, TrDocument> docsByType = new HashMap<>();
        for (TrDocument doc : docs) {
            if (!"ARCHIVED".equals(doc.getDocumentStatus())) {
                docsByType.putIfAbsent(doc.getDocumentType(), doc);
            }
        }

        List<DocumentValidateMissingItem> missing = new ArrayList<>();
        List<DocumentValidatePendingItem> pendingReview = new ArrayList<>();
        List<DocumentValidateWarningItem> warnings = new ArrayList<>();

        for (TrDocumentRequirement rule : rules) {
            TrDocument doc = docsByType.get(rule.getDocumentType());
            if (doc == null && rule.getRequiredFlag() == 1) {
                missing.add(new DocumentValidateMissingItem(
                        rule.getDocumentType(),
                        true,
                        documentTypeLabel(rule.getDocumentType()) + "缺失"));
                continue;
            }
            if (doc == null) {
                continue;
            }
            if (rule.getManualReviewRequired() == 1
                    && !"APPROVED".equals(doc.getReviewStatus())
                    && !"NOT_REQUIRED".equals(doc.getReviewStatus())) {
                pendingReview.add(new DocumentValidatePendingItem(
                        doc.getId(),
                        doc.getDocumentType(),
                        doc.getReviewStatus()));
            }
            if (requiresSignedContract(doc) && !"SIGNED".equals(doc.getSignStatus())) {
                pendingReview.add(new DocumentValidatePendingItem(
                        doc.getId(),
                        doc.getDocumentType(),
                        "SIGN_" + doc.getSignStatus()));
            }
            BigDecimal minConfidence = rule.getMinConfidence() == null ? new BigDecimal("0.85") : rule.getMinConfidence();
            if (rule.getOcrRequired() == 1 && doc.getOcrConfidence() != null
                    && doc.getOcrConfidence().compareTo(minConfidence) < 0) {
                warnings.add(new DocumentValidateWarningItem(
                        doc.getId(),
                        doc.getDocumentType(),
                        "OCR置信度低于" + minConfidence + "，建议人工复核"));
            }
        }

        boolean passed = missing.isEmpty() && pendingReview.isEmpty();
        return new DocumentValidateResponse(
                passed,
                businessType,
                businessId,
                missing,
                pendingReview,
                warnings);
    }

    private void assertBusinessAccessible(String businessType, String businessId, String operatorId, String projectId) {
        if (!BIZ_TRADE_ORDER.equals(businessType)) {
            if (BIZ_FINANCE.equals(businessType)) {
                assertFinanceAccessible(businessId, operatorId, projectId);
                return;
            }
            return;
        }
        TrOrder order = orderRepository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(businessId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联业务对象不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessTradeOrder(user, order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId())) {
            throw new BusinessException("AUTH_403", "无权校验该业务单证", 403);
        }
    }

    private void assertFinanceAccessible(String financeId, String operatorId, String projectId) {
        FnFinanceApplication finance = financeRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(financeId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "关联融资申请不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessFinance(user, finance.getCustomerId(), finance.getFundingPartyId())) {
            throw new BusinessException("AUTH_403", "无权校验该融资单证", 403);
        }
    }

    private static boolean requiresSignedContract(TrDocument doc) {
        return "PURCHASE_CONTRACT".equals(doc.getDocumentType())
                || (doc.getContractStatus() != null
                && !"NOT_CONTRACT".equals(doc.getContractStatus())
                && !"VOID".equals(doc.getContractStatus()));
    }

    private String documentTypeLabel(String documentType) {
        return switch (documentType) {
            case "PURCHASE_CONTRACT" -> "采购合同";
            case "INVOICE" -> "发票";
            case "PACKING_LIST" -> "装箱单";
            default -> documentType;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
