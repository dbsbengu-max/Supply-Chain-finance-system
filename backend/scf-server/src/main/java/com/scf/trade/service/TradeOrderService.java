package com.scf.trade.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.pricing.repository.MdSkuRepository;
import com.scf.trade.dto.*;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.entity.TrOrder;
import com.scf.trade.entity.TrOrderItem;
import com.scf.trade.repository.TrDocumentRepository;
import com.scf.trade.repository.TrOrderItemRepository;
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
public class TradeOrderService {

    private static final String BIZ_TYPE_ORDER = "TRADE_ORDER";

    private final TrOrderRepository orderRepository;
    private final TrOrderItemRepository itemRepository;
    private final TrDocumentRepository documentRepository;
    private final MdSkuRepository skuRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final DataScopeHelper dataScopeHelper;

    public TradeOrderService(
            TrOrderRepository orderRepository,
            TrOrderItemRepository itemRepository,
            TrDocumentRepository documentRepository,
            MdSkuRepository skuRepository,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            DataScopeHelper dataScopeHelper) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.documentRepository = documentRepository;
        this.skuRepository = skuRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.dataScopeHelper = dataScopeHelper;
    }

    public PageResponse<OrderView> list(int pageNo, int pageSize, String orderStatus) {
        tenantContext.requirePermission("ORDER_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        UserContext user = SecurityUtils.currentUser();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));

        Page<TrOrder> page;
        DataScopeHelper.ScopeType scopeType = dataScopeHelper.tradeOrderScope(user);
        if (scopeType == DataScopeHelper.ScopeType.ENTERPRISE) {
            page = orderRepository.findByEnterpriseScope(operatorId, projectId, user.enterpriseId(), pageable);
        } else if (scopeType == DataScopeHelper.ScopeType.OPERATOR_PROJECT) {
            page = orderRepository.findByOperatorIdAndProjectIdAndDeletedFlagOrderByCreatedAtDesc(
                    operatorId, projectId, (short) 0, pageable);
        } else {
            throw new BusinessException("AUTH_403", "无订单数据范围", 403);
        }

        List<OrderView> records = page.getContent().stream()
                .filter(o -> orderStatus == null || orderStatus.isBlank() || orderStatus.equals(o.getOrderStatus()))
                .map(OrderView::fromSummary)
                .toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public OrderView getById(String id) {
        tenantContext.requirePermission("ORDER_VIEW");
        TrOrder order = loadAccessible(id);
        List<TrOrderItem> items = itemRepository.findByOrderId(order.getId());
        return OrderView.from(order, items);
    }

    @Transactional
    public OrderView create(OrderCreateRequest request) {
        tenantContext.requirePermission("ORDER_CREATE");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        String userId = SecurityUtils.currentUserId();

        BigDecimal total = BigDecimal.ZERO;
        List<TrOrderItem> pendingItems = new ArrayList<>();
        for (OrderItemRequest line : request.items()) {
            skuRepository.findById(line.skuId())
                    .orElseThrow(() -> new BusinessException("DATA_404", "SKU 不存在: " + line.skuId(), 404));
            BigDecimal qty = new BigDecimal(line.quantity());
            BigDecimal unitPrice = new BigDecimal(line.unitPrice());
            BigDecimal amount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);

            TrOrderItem item = new TrOrderItem();
            item.setId(IdGenerator.nextId());
            item.setSkuId(line.skuId());
            item.setQuantity(qty);
            item.setUnit(line.unit());
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            item.setDeliveryDate(line.deliveryDate());
            pendingItems.add(item);
        }

        TrOrder order = new TrOrder();
        order.setId(IdGenerator.nextId());
        order.setOperatorId(operatorId);
        order.setProjectId(projectId);
        order.setOrderNo("ORD-" + System.currentTimeMillis());
        order.setOrderType(request.orderType());
        order.setBuyerId(request.buyerId());
        order.setSellerId(request.sellerId());
        order.setTradeCompanyId(request.tradeCompanyId());
        order.setTotalAmount(total);
        order.setCurrency(request.currency());
        order.setCountryFrom(request.countryFrom());
        order.setCountryTo(request.countryTo());
        order.setOrderStatus("DRAFT");
        order.setCreatedBy(userId);
        order.setCreatedAt(Instant.now());
        order.setDeletedFlag((short) 0);
        order.setVersionNo(1);
        orderRepository.save(order);

        for (TrOrderItem item : pendingItems) {
            item.setOrderId(order.getId());
            itemRepository.save(item);
        }

        auditLogService.log("ORDER_CREATE", "TRADE_ORDER", order.getId(), null, Map.of("order_no", order.getOrderNo()));
        return OrderView.from(order, pendingItems);
    }

    @Transactional
    public OrderView update(String id, OrderCreateRequest request) {
        tenantContext.requirePermission("ORDER_UPDATE");
        TrOrder order = loadAccessible(id);
        assertDraftOrRejected(order);

        itemRepository.deleteByOrderId(order.getId());

        BigDecimal total = BigDecimal.ZERO;
        List<TrOrderItem> pendingItems = new ArrayList<>();
        for (OrderItemRequest line : request.items()) {
            skuRepository.findById(line.skuId())
                    .orElseThrow(() -> new BusinessException("DATA_404", "SKU 不存在: " + line.skuId(), 404));
            BigDecimal qty = new BigDecimal(line.quantity());
            BigDecimal unitPrice = new BigDecimal(line.unitPrice());
            BigDecimal amount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
            total = total.add(amount);

            TrOrderItem item = new TrOrderItem();
            item.setId(IdGenerator.nextId());
            item.setOrderId(order.getId());
            item.setSkuId(line.skuId());
            item.setQuantity(qty);
            item.setUnit(line.unit());
            item.setUnitPrice(unitPrice);
            item.setAmount(amount);
            item.setDeliveryDate(line.deliveryDate());
            pendingItems.add(item);
        }

        order.setOrderType(request.orderType());
        order.setBuyerId(request.buyerId());
        order.setSellerId(request.sellerId());
        order.setTradeCompanyId(request.tradeCompanyId());
        order.setTotalAmount(total);
        order.setCurrency(request.currency());
        order.setCountryFrom(request.countryFrom());
        order.setCountryTo(request.countryTo());
        order.setUpdatedBy(SecurityUtils.currentUserId());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        itemRepository.saveAll(pendingItems);

        auditLogService.log("ORDER_UPDATE", "TRADE_ORDER", order.getId(), null, Map.of("order_no", order.getOrderNo()));
        return OrderView.from(order, pendingItems);
    }

    @Transactional
    public OrderView submit(String id) {
        tenantContext.requirePermission("ORDER_SUBMIT");
        TrOrder order = loadAccessible(id);
        assertDraftOrRejected(order);
        order.setOrderStatus("SUBMITTED");
        order.setUpdatedBy(SecurityUtils.currentUserId());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditLogService.log("ORDER_SUBMIT", "TRADE_ORDER", order.getId(), null, Map.of());
        return getById(id);
    }

    @Transactional
    public OrderView confirm(String id) {
        tenantContext.requirePermission("ORDER_CONFIRM");
        TrOrder order = loadAccessible(id);
        if (!"SUBMITTED".equals(order.getOrderStatus())) {
            throw new BusinessException("STATE_409", "仅已提交订单可确认", 409);
        }
        order.setOrderStatus("CONFIRMED");
        order.setSignedAt(Instant.now());
        order.setUpdatedBy(SecurityUtils.currentUserId());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditLogService.log("ORDER_CONFIRM", "TRADE_ORDER", order.getId(), null, Map.of());
        return getById(id);
    }

    @Transactional
    public OrderView cancel(String id) {
        tenantContext.requirePermission("ORDER_CANCEL");
        TrOrder order = loadAccessible(id);
        UserContext user = SecurityUtils.currentUser();
        if (dataScopeHelper.isEnterpriseRole(user) && !user.userId().equals(order.getCreatedBy())) {
            throw new BusinessException("AUTH_403", "仅创建方可取消订单", 403);
        }
        if ("CONFIRMED".equals(order.getOrderStatus()) || "CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException("STATE_409", "当前状态不可取消", 409);
        }
        order.setOrderStatus("CANCELLED");
        order.setUpdatedBy(SecurityUtils.currentUserId());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return getById(id);
    }

    public List<Map<String, Object>> listDocuments(String orderId) {
        tenantContext.requirePermission("DOCUMENT_VIEW");
        loadAccessible(orderId);
        return documentRepository.findByBusinessTypeAndBusinessIdAndDeletedFlag(BIZ_TYPE_ORDER, orderId, (short) 0)
                .stream()
                .map(this::toDocView)
                .toList();
    }

    @Transactional
    public Map<String, Object> addDocument(String orderId, DocumentCreateRequest request) {
        tenantContext.requirePermission("DOCUMENT_UPLOAD");
        TrOrder order = loadAccessible(orderId);
        TrDocument doc = new TrDocument();
        doc.setId(IdGenerator.nextId());
        doc.setOperatorId(order.getOperatorId());
        doc.setProjectId(order.getProjectId());
        doc.setBusinessType(BIZ_TYPE_ORDER);
        doc.setBusinessId(orderId);
        doc.setDocumentType(request.documentType());
        doc.setDocumentNo(request.documentNo());
        doc.setFileId(request.fileId());
        doc.setOcrStatus("PENDING");
        doc.setValidationStatus("PENDING");
        doc.setDocumentStatus("UPLOADED");
        doc.setReviewStatus("NOT_REQUIRED");
        doc.setContractStatus("NOT_CONTRACT");
        doc.setSignStatus("NOT_REQUIRED");
        doc.setCreatedBy(SecurityUtils.currentUserId());
        doc.setCreatedAt(Instant.now());
        doc.setDeletedFlag((short) 0);
        documentRepository.save(doc);
        return toDocView(doc);
    }

    @Transactional
    public Map<String, Object> ocrDocument(String documentId) {
        tenantContext.requirePermission("AI_OCR_EXECUTE");
        TrDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException("DATA_404", "单据不存在", 404));
        loadAccessible(doc.getBusinessId());
        doc.setOcrStatus("COMPLETED");
        doc.setValidationStatus("PENDING_CONFIRM");
        documentRepository.save(doc);
        return Map.of(
                "id", doc.getId(),
                "ocr_status", doc.getOcrStatus(),
                "extracted", Map.of("document_no", doc.getDocumentNo() == null ? "" : doc.getDocumentNo()));
    }

    public Map<String, Object> validateBackground(String orderId) {
        tenantContext.requirePermission("ORDER_VALIDATE");
        TrOrder order = loadAccessible(orderId);
        List<TrDocument> docs = documentRepository.findByBusinessTypeAndBusinessIdAndDeletedFlag(
                BIZ_TYPE_ORDER, orderId, (short) 0);

        List<Map<String, Object>> checks = new ArrayList<>();
        if (docs.isEmpty()) {
            checks.add(Map.of(
                    "check_code", "DOCUMENT_EXISTS",
                    "check_name", "贸易单据完整性",
                    "result", "WARNING",
                    "message", "尚未上传合同/发票/提单等单据"));
        } else {
            checks.add(Map.of(
                    "check_code", "DOCUMENT_EXISTS",
                    "check_name", "贸易单据完整性",
                    "result", "PASS",
                    "message", "已上传 " + docs.size() + " 份单据"));
        }
        if (!"CONFIRMED".equals(order.getOrderStatus())) {
            checks.add(Map.of(
                    "check_code", "ORDER_STATUS",
                    "check_name", "订单确认状态",
                    "result", "WARNING",
                    "message", "订单尚未确认，当前状态: " + order.getOrderStatus()));
        }

        boolean passed = checks.stream().noneMatch(c -> "FAIL".equals(c.get("result")));
        String riskLevel = checks.stream().anyMatch(c -> "WARNING".equals(c.get("result"))) ? "MEDIUM" : "LOW";
        return Map.of("passed", passed, "risk_level", riskLevel, "checks", checks);
    }

    private TrOrder loadAccessible(String id) {
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        TrOrder order = orderRepository.findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        id, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "订单不存在", 404));
        UserContext user = SecurityUtils.currentUser();
        if (!dataScopeHelper.canAccessTradeOrder(user, order.getBuyerId(), order.getSellerId(), order.getTradeCompanyId())) {
            throw new BusinessException("AUTH_403", "无权访问该订单", 403);
        }
        return order;
    }

    private void assertDraftOrRejected(TrOrder order) {
        if (!"DRAFT".equals(order.getOrderStatus())) {
            throw new BusinessException("STATE_409", "仅草稿订单可提交", 409);
        }
    }

    private Map<String, Object> toDocView(TrDocument doc) {
        return Map.of(
                "id", doc.getId(),
                "document_type", doc.getDocumentType(),
                "document_no", doc.getDocumentNo() == null ? "" : doc.getDocumentNo(),
                "file_id", doc.getFileId(),
                "ocr_status", doc.getOcrStatus(),
                "validation_status", doc.getValidationStatus());
    }
}
