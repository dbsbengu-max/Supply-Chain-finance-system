package com.scf.warehouse.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.dto.PageResponse;
import com.scf.common.exception.BusinessException;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.warehouse.InventoryRightStatus;
import com.scf.warehouse.dto.InboundCreateRequest;
import com.scf.warehouse.dto.InboundView;
import com.scf.warehouse.dto.InventoryView;
import com.scf.warehouse.dto.OutboundCreateRequest;
import com.scf.warehouse.dto.OutboundRequestView;
import com.scf.warehouse.dto.QuantityActionRequest;
import com.scf.warehouse.dto.ReleaseRequestView;
import com.scf.warehouse.dto.WarehouseView;
import com.scf.warehouse.entity.WhInbound;
import com.scf.warehouse.entity.WhInventory;
import com.scf.warehouse.entity.WhOutboundRequest;
import com.scf.warehouse.entity.WhReleaseRequest;
import com.scf.warehouse.entity.WhWarehouse;
import com.scf.warehouse.repository.WhInboundRepository;
import com.scf.warehouse.repository.WhInventoryRepository;
import com.scf.warehouse.repository.WhOutboundRequestRepository;
import com.scf.warehouse.repository.WhReleaseRequestRepository;
import com.scf.warehouse.repository.WhWarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class WarehouseService {

    private static final Set<String> FREEZE_ALLOWED_STATUS = Set.of(
            InventoryRightStatus.IN_STOCK, InventoryRightStatus.RELEASED);
    private static final Set<String> OUTBOUND_APPLY_STATUS = Set.of(
            InventoryRightStatus.IN_STOCK, InventoryRightStatus.RELEASED);

    private final WhWarehouseRepository warehouseRepository;
    private final WhInventoryRepository inventoryRepository;
    private final WhInboundRepository inboundRepository;
    private final WhReleaseRequestRepository releaseRequestRepository;
    private final WhOutboundRequestRepository outboundRequestRepository;
    private final TenantContext tenantContext;
    private final DataScopeHelper dataScopeHelper;
    private final AuditLogService auditLogService;

    public WarehouseService(
            WhWarehouseRepository warehouseRepository,
            WhInventoryRepository inventoryRepository,
            WhInboundRepository inboundRepository,
            WhReleaseRequestRepository releaseRequestRepository,
            WhOutboundRequestRepository outboundRequestRepository,
            TenantContext tenantContext,
            DataScopeHelper dataScopeHelper,
            AuditLogService auditLogService) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.inboundRepository = inboundRepository;
        this.releaseRequestRepository = releaseRequestRepository;
        this.outboundRequestRepository = outboundRequestRepository;
        this.tenantContext = tenantContext;
        this.dataScopeHelper = dataScopeHelper;
        this.auditLogService = auditLogService;
    }

    public PageResponse<WarehouseView> listWarehouses(int pageNo, int pageSize, String status) {
        tenantContext.requirePermission("WAREHOUSE_VIEW");
        ScopeFilter scope = resolveScope();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<WhWarehouse> page = warehouseRepository.search(
                scope.operatorId(),
                scope.projectId(),
                scope.warehouseCompanyId(),
                filterOrEmpty(status),
                pageable);
        var records = page.getContent().stream().map(WarehouseView::from).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public WarehouseView getWarehouse(String id) {
        tenantContext.requirePermission("WAREHOUSE_VIEW");
        ScopeFilter scope = resolveScope();
        WhWarehouse warehouse = warehouseRepository
                .findByIdAndOperatorIdAndProjectId(id, scope.operatorId(), scope.projectId())
                .orElseThrow(() -> notFound("仓库不存在"));
        assertWarehouseScope(warehouse, scope);
        return WarehouseView.from(warehouse);
    }

    public PageResponse<InventoryView> listInventories(
            int pageNo, int pageSize, String warehouseId, String rightStatus) {
        tenantContext.requirePermission("WAREHOUSE_VIEW");
        ScopeFilter scope = resolveScope();
        PageRequest pageable = PageRequest.of(Math.max(pageNo - 1, 0), Math.max(pageSize, 1));
        Page<WhInventory> page = inventoryRepository.search(
                scope.operatorId(),
                scope.projectId(),
                filterOrEmpty(warehouseId),
                filterOrEmpty(rightStatus),
                scope.ownerId(),
                scope.warehouseCompanyId(),
                pageable);
        var records = page.getContent().stream().map(InventoryView::from).toList();
        return PageResponse.of(pageNo, pageSize, page.getTotalElements(), records);
    }

    public InventoryView getInventory(String id) {
        tenantContext.requirePermission("WAREHOUSE_VIEW");
        return InventoryView.from(loadAccessibleInventory(id));
    }

    @Transactional
    public InboundView createInbound(InboundCreateRequest request) {
        tenantContext.requirePermission("WAREHOUSE_INBOUND");
        ScopeFilter scope = resolveScope();
        UserContext user = SecurityUtils.currentUser();
        WhWarehouse warehouse = loadWarehouseInScope(request.warehouse_id(), scope);

        if (dataScopeHelper.isEnterpriseRole(user) && !user.enterpriseId().equals(request.owner_id())) {
            throw new BusinessException("AUTH_403", "仅可为本企业办理入库", 403);
        }
        if (inventoryRepository.existsByWarehouseIdAndSkuIdAndBatchNoAndDeletedFlag(
                warehouse.getId(), request.sku_id(), request.batch_no(), (short) 0)) {
            throw new BusinessException("INVENTORY_409", "同仓同SKU同批次已存在", 409);
        }

        Instant now = Instant.now();
        WhInventory inventory = new WhInventory();
        inventory.setId(IdGenerator.nextId());
        inventory.setWarehouseId(warehouse.getId());
        inventory.setOperatorId(scope.operatorId());
        inventory.setProjectId(scope.projectId());
        inventory.setSkuId(request.sku_id());
        inventory.setBatchNo(request.batch_no());
        inventory.setOwnerId(request.owner_id());
        inventory.setLocationCode(request.location_code());
        inventory.setQuantity(request.quantity());
        inventory.setAvailableQuantity(request.quantity());
        inventory.setFrozenQuantity(BigDecimal.ZERO);
        inventory.setPledgedQuantity(BigDecimal.ZERO);
        inventory.setOutboundPendingQuantity(BigDecimal.ZERO);
        inventory.setValuationAmount(request.valuation_amount());
        inventory.setCurrency(request.currency() != null ? request.currency() : "CNY");
        inventory.setRightStatus(InventoryRightStatus.IN_STOCK);
        inventory.setStocktakeException((short) 0);
        inventory.setCreatedBy(user.userId());
        inventory.setCreatedAt(now);
        inventory.setUpdatedBy(user.userId());
        inventory.setUpdatedAt(now);
        inventory.setDeletedFlag((short) 0);
        inventoryRepository.save(inventory);

        WhInbound inbound = new WhInbound();
        inbound.setId(IdGenerator.nextId());
        inbound.setOperatorId(scope.operatorId());
        inbound.setProjectId(scope.projectId());
        inbound.setInboundNo("IN-" + System.currentTimeMillis());
        inbound.setWarehouseId(warehouse.getId());
        inbound.setSkuId(request.sku_id());
        inbound.setBatchNo(request.batch_no());
        inbound.setOwnerId(request.owner_id());
        inbound.setLocationCode(request.location_code());
        inbound.setQuantity(request.quantity());
        inbound.setValuationAmount(request.valuation_amount());
        inbound.setCurrency(inventory.getCurrency());
        inbound.setInboundStatus("COMPLETED");
        inbound.setInventoryId(inventory.getId());
        inbound.setCreatedBy(user.userId());
        inbound.setCreatedAt(now);
        inbound.setDeletedFlag((short) 0);
        inboundRepository.save(inbound);

        auditLogService.log("WAREHOUSE_INBOUND", "INVENTORY", inventory.getId(), null, snapshot(inventory));
        return InboundView.from(inbound);
    }

    @Transactional
    public InventoryView freeze(String inventoryId, QuantityActionRequest request) {
        tenantContext.requirePermission("WAREHOUSE_FREEZE");
        WhInventory inventory = loadAccessibleInventory(inventoryId);
        Map<String, Object> before = snapshot(inventory);
        assertNotStocktakeException(inventory);
        assertStatus(inventory, FREEZE_ALLOWED_STATUS, "当前状态不允许冻结");
        BigDecimal qty = request.quantity();
        if (inventory.getAvailableQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "可用数量不足", 400);
        }
        String fromStatus = inventory.getRightStatus();
        inventory.setAvailableQuantity(inventory.getAvailableQuantity().subtract(qty));
        inventory.setFrozenQuantity(inventory.getFrozenQuantity().add(qty));
        transition(inventory, fromStatus, InventoryRightStatus.FROZEN);
        touch(inventory);
        inventoryRepository.save(inventory);
        auditLogService.log("WAREHOUSE_FREEZE", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return InventoryView.from(inventory);
    }

    @Transactional
    public InventoryView pledge(String inventoryId, QuantityActionRequest request) {
        tenantContext.requirePermission("WAREHOUSE_PLEDGE");
        WhInventory inventory = loadAccessibleInventory(inventoryId);
        Map<String, Object> before = snapshot(inventory);
        assertNotStocktakeException(inventory);
        if (!InventoryRightStatus.FROZEN.equals(inventory.getRightStatus())
                && inventory.getFrozenQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVENTORY_400", "仅已冻结库存可质押", 400);
        }
        BigDecimal qty = request.quantity();
        if (inventory.getFrozenQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "冻结数量不足", 400);
        }
        String fromStatus = inventory.getRightStatus();
        inventory.setFrozenQuantity(inventory.getFrozenQuantity().subtract(qty));
        inventory.setPledgedQuantity(inventory.getPledgedQuantity().add(qty));
        transition(inventory, fromStatus, InventoryRightStatus.PLEDGED);
        touch(inventory);
        inventoryRepository.save(inventory);
        auditLogService.log("WAREHOUSE_PLEDGE", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return InventoryView.from(inventory);
    }

    @Transactional
    public ReleaseRequestView applyRelease(String inventoryId, QuantityActionRequest request) {
        tenantContext.requirePermission("WAREHOUSE_RELEASE");
        WhInventory inventory = loadAccessibleInventory(inventoryId);
        Map<String, Object> before = snapshot(inventory);
        assertNotStocktakeException(inventory);
        if (!InventoryRightStatus.PLEDGED.equals(inventory.getRightStatus())) {
            throw new BusinessException("INVENTORY_400", "仅已质押库存可申请解押", 400);
        }
        if (inventory.getPledgedQuantity().compareTo(request.quantity()) < 0) {
            throw new BusinessException("INVENTORY_400", "质押数量不足", 400);
        }
        releaseRequestRepository
                .findByInventoryIdAndRequestStatusAndDeletedFlag(inventoryId, "PENDING", (short) 0)
                .ifPresent(existing -> {
                    throw new BusinessException("INVENTORY_409", "已有待审批解押申请", 409);
                });

        String fromStatus = inventory.getRightStatus();
        transition(inventory, fromStatus, InventoryRightStatus.RELEASE_REVIEW);
        touch(inventory);
        inventoryRepository.save(inventory);

        UserContext user = SecurityUtils.currentUser();
        ScopeFilter scope = resolveScope();
        WhReleaseRequest releaseRequest = new WhReleaseRequest();
        releaseRequest.setId(IdGenerator.nextId());
        releaseRequest.setOperatorId(scope.operatorId());
        releaseRequest.setProjectId(scope.projectId());
        releaseRequest.setRequestNo("REL-" + System.currentTimeMillis());
        releaseRequest.setInventoryId(inventoryId);
        releaseRequest.setQuantity(request.quantity());
        releaseRequest.setRequestStatus("PENDING");
        releaseRequest.setRemark(request.remark());
        releaseRequest.setCreatedBy(user.userId());
        releaseRequest.setCreatedAt(Instant.now());
        releaseRequest.setDeletedFlag((short) 0);
        releaseRequestRepository.save(releaseRequest);

        auditLogService.log("WAREHOUSE_RELEASE_APPLY", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return ReleaseRequestView.from(releaseRequest);
    }

    @Transactional
    public InventoryView approveRelease(String requestId) {
        tenantContext.requirePermission("WAREHOUSE_RELEASE");
        ScopeFilter scope = resolveScope();
        WhReleaseRequest releaseRequest = releaseRequestRepository.findById(requestId)
                .filter(r -> r.getDeletedFlag() == 0)
                .filter(r -> scope.operatorId().equals(r.getOperatorId()))
                .filter(r -> scope.projectId().equals(r.getProjectId()))
                .orElseThrow(() -> notFound("解押申请不存在"));
        if (!"PENDING".equals(releaseRequest.getRequestStatus())) {
            throw new BusinessException("INVENTORY_400", "解押申请状态不允许审批", 400);
        }

        WhInventory inventory = loadAccessibleInventory(releaseRequest.getInventoryId());
        Map<String, Object> before = snapshot(inventory);
        if (!InventoryRightStatus.RELEASE_REVIEW.equals(inventory.getRightStatus())) {
            throw new BusinessException("INVENTORY_400", "库存不在解押审核状态", 400);
        }
        BigDecimal qty = releaseRequest.getQuantity();
        if (inventory.getPledgedQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "质押数量不足", 400);
        }

        inventory.setPledgedQuantity(inventory.getPledgedQuantity().subtract(qty));
        inventory.setAvailableQuantity(inventory.getAvailableQuantity().add(qty));
        String toStatus = inventory.getPledgedQuantity().compareTo(BigDecimal.ZERO) > 0
                ? InventoryRightStatus.PLEDGED
                : InventoryRightStatus.RELEASED;
        transition(inventory, InventoryRightStatus.RELEASE_REVIEW, toStatus);
        touch(inventory);
        inventoryRepository.save(inventory);

        UserContext user = SecurityUtils.currentUser();
        releaseRequest.setRequestStatus("APPROVED");
        releaseRequest.setApprovedBy(user.userId());
        releaseRequest.setApprovedAt(Instant.now());
        releaseRequestRepository.save(releaseRequest);

        auditLogService.log("WAREHOUSE_RELEASE_APPROVE", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return InventoryView.from(inventory);
    }

    @Transactional
    public OutboundRequestView applyOutbound(OutboundCreateRequest request) {
        tenantContext.requirePermission("WAREHOUSE_OUTBOUND");
        WhInventory inventory = loadAccessibleInventory(request.inventory_id());
        Map<String, Object> before = snapshot(inventory);
        assertNotStocktakeException(inventory);
        assertNoPledgedForOutbound(inventory);
        assertStatus(inventory, OUTBOUND_APPLY_STATUS, "当前状态不允许出库申请");
        BigDecimal qty = request.quantity();
        if (inventory.getAvailableQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "可用数量不足", 400);
        }

        String fromStatus = inventory.getRightStatus();
        inventory.setAvailableQuantity(inventory.getAvailableQuantity().subtract(qty));
        inventory.setOutboundPendingQuantity(inventory.getOutboundPendingQuantity().add(qty));
        transition(inventory, fromStatus, InventoryRightStatus.PENDING_OUT);
        touch(inventory);
        inventoryRepository.save(inventory);

        UserContext user = SecurityUtils.currentUser();
        ScopeFilter scope = resolveScope();
        WhOutboundRequest outbound = new WhOutboundRequest();
        outbound.setId(IdGenerator.nextId());
        outbound.setOperatorId(scope.operatorId());
        outbound.setProjectId(scope.projectId());
        outbound.setRequestNo("OUT-" + System.currentTimeMillis());
        outbound.setInventoryId(inventory.getId());
        outbound.setQuantity(qty);
        outbound.setRequestStatus("PENDING");
        outbound.setRemark(request.remark());
        outbound.setCreatedBy(user.userId());
        outbound.setCreatedAt(Instant.now());
        outbound.setDeletedFlag((short) 0);
        outboundRequestRepository.save(outbound);

        auditLogService.log("WAREHOUSE_OUTBOUND_APPLY", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return OutboundRequestView.from(outbound);
    }

    @Transactional
    public InventoryView confirmOutbound(String requestId) {
        tenantContext.requirePermission("WAREHOUSE_OUTBOUND");
        ScopeFilter scope = resolveScope();
        WhOutboundRequest outbound = outboundRequestRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        requestId, scope.operatorId(), scope.projectId(), (short) 0)
                .orElseThrow(() -> notFound("出库申请不存在"));
        if (!"PENDING".equals(outbound.getRequestStatus())) {
            throw new BusinessException("INVENTORY_400", "出库申请状态不允许确认", 400);
        }

        WhInventory inventory = loadAccessibleInventory(outbound.getInventoryId());
        Map<String, Object> before = snapshot(inventory);
        assertNoPledgedForOutbound(inventory);
        BigDecimal qty = outbound.getQuantity();
        if (inventory.getOutboundPendingQuantity().compareTo(qty) < 0) {
            throw new BusinessException("INVENTORY_400", "待出库数量不足", 400);
        }

        inventory.setOutboundPendingQuantity(inventory.getOutboundPendingQuantity().subtract(qty));
        inventory.setQuantity(inventory.getQuantity().subtract(qty));
        String toStatus = inventory.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                ? InventoryRightStatus.OUT_STOCK
                : inventory.getRightStatus();
        if (InventoryRightStatus.OUT_STOCK.equals(toStatus)) {
            transition(inventory, inventory.getRightStatus(), InventoryRightStatus.OUT_STOCK);
        } else if (inventory.getOutboundPendingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            transition(inventory, InventoryRightStatus.PENDING_OUT, InventoryRightStatus.IN_STOCK);
        }
        touch(inventory);
        inventoryRepository.save(inventory);

        UserContext user = SecurityUtils.currentUser();
        outbound.setRequestStatus("CONFIRMED");
        outbound.setConfirmedBy(user.userId());
        outbound.setConfirmedAt(Instant.now());
        outboundRequestRepository.save(outbound);

        auditLogService.log("WAREHOUSE_OUTBOUND_CONFIRM", "INVENTORY", inventory.getId(), before, snapshot(inventory));
        return InventoryView.from(inventory);
    }

    private WhInventory loadAccessibleInventory(String id) {
        ScopeFilter scope = resolveScope();
        WhInventory inventory = inventoryRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(
                        id, scope.operatorId(), scope.projectId(), (short) 0)
                .orElseThrow(() -> notFound("库存不存在"));
        assertInventoryScope(inventory, scope);
        return inventory;
    }

    private WhWarehouse loadWarehouseInScope(String warehouseId, ScopeFilter scope) {
        WhWarehouse warehouse = warehouseRepository
                .findByIdAndOperatorIdAndProjectId(warehouseId, scope.operatorId(), scope.projectId())
                .orElseThrow(() -> notFound("仓库不存在"));
        assertWarehouseScope(warehouse, scope);
        return warehouse;
    }

    private void assertWarehouseScope(WhWarehouse warehouse, ScopeFilter scope) {
        if (!scope.warehouseCompanyId().isEmpty()
                && !scope.warehouseCompanyId().equals(warehouse.getWarehouseCompanyId())) {
            throw notFound("仓库不存在");
        }
    }

    private void assertInventoryScope(WhInventory inventory, ScopeFilter scope) {
        if (!scope.ownerId().isEmpty() && !scope.ownerId().equals(inventory.getOwnerId())) {
            throw notFound("库存不存在");
        }
        WhWarehouse warehouse = warehouseRepository.findById(inventory.getWarehouseId())
                .orElseThrow(() -> notFound("库存不存在"));
        assertWarehouseScope(warehouse, scope);
    }

    private ScopeFilter resolveScope() {
        UserContext user = SecurityUtils.currentUser();
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        DataScopeHelper.ScopeType scopeType = dataScopeHelper.warehouseInventoryScope(user);
        return switch (scopeType) {
            case OPERATOR_PROJECT -> new ScopeFilter(operatorId, projectId, "", "", "");
            case WAREHOUSE_COMPANY -> new ScopeFilter(
                    operatorId, projectId, user.enterpriseId() != null ? user.enterpriseId() : "", "", "");
            case ENTERPRISE -> new ScopeFilter(
                    operatorId, projectId, "", user.enterpriseId() != null ? user.enterpriseId() : "", "");
            default -> throw new BusinessException("AUTH_403", "无仓储数据范围", 403);
        };
    }

    private static void assertNotStocktakeException(WhInventory inventory) {
        if (inventory.getStocktakeException() == 1
                || InventoryRightStatus.INVENTORY_EXCEPTION.equals(inventory.getRightStatus())) {
            throw new BusinessException("INVENTORY_463", "盘库异常状态禁止质押或出库", 409);
        }
    }

    private static void assertNoPledgedForOutbound(WhInventory inventory) {
        if (inventory.getPledgedQuantity().compareTo(BigDecimal.ZERO) > 0
                || InventoryRightStatus.PLEDGED.equals(inventory.getRightStatus())
                || InventoryRightStatus.RELEASE_REVIEW.equals(inventory.getRightStatus())) {
            throw new BusinessException("INVENTORY_409", "已质押库存不允许出库", 409);
        }
    }

    private static void assertStatus(WhInventory inventory, Set<String> allowed, String message) {
        if (!allowed.contains(inventory.getRightStatus())) {
            throw new BusinessException("INVENTORY_400", message, 400);
        }
    }

    private static void transition(WhInventory inventory, String fromStatus, String toStatus) {
        if (!fromStatus.equals(toStatus)) {
            InventoryRightStatus.assertTransition(fromStatus, toStatus);
            inventory.setRightStatus(toStatus);
        }
    }

    private void touch(WhInventory inventory) {
        UserContext user = SecurityUtils.currentUser();
        inventory.setUpdatedBy(user.userId());
        inventory.setUpdatedAt(Instant.now());
    }

    private static Map<String, Object> snapshot(WhInventory inventory) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", inventory.getId());
        map.put("right_status", inventory.getRightStatus());
        map.put("quantity", inventory.getQuantity());
        map.put("available_quantity", inventory.getAvailableQuantity());
        map.put("frozen_quantity", inventory.getFrozenQuantity());
        map.put("pledged_quantity", inventory.getPledgedQuantity());
        map.put("outbound_pending_quantity", inventory.getOutboundPendingQuantity());
        map.put("stocktake_exception", inventory.getStocktakeException());
        return map;
    }

    private static String filterOrEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private static BusinessException notFound(String message) {
        return new BusinessException("INVENTORY_404", message, 404);
    }

    private record ScopeFilter(
            String operatorId,
            String projectId,
            String warehouseCompanyId,
            String ownerId,
            String unused) {
    }
}
