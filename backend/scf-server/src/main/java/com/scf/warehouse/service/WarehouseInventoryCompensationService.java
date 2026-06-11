package com.scf.warehouse.service;

import com.scf.audit.service.AuditLogService;
import com.scf.common.exception.BusinessException;
import com.scf.warehouse.InventoryRightStatus;
import com.scf.warehouse.entity.WhInventory;
import com.scf.warehouse.repository.WhInventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Service
public class WarehouseInventoryCompensationService {

    private final WhInventoryRepository inventoryRepository;
    private final AuditLogService auditLogService;

    public WarehouseInventoryCompensationService(
            WhInventoryRepository inventoryRepository,
            AuditLogService auditLogService) {
        this.inventoryRepository = inventoryRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void unfreezeInventory(
            String inventoryId,
            String operatorId,
            String projectId,
            BigDecimal quantity,
            String businessType,
            String businessId) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        WhInventory inventory = inventoryRepository
                .findByIdAndOperatorIdAndProjectIdAndDeletedFlag(inventoryId, operatorId, projectId, (short) 0)
                .orElseThrow(() -> new BusinessException("DATA_404", "库存不存在", 404));
        BigDecimal releaseQty = quantity.min(inventory.getFrozenQuantity());
        if (releaseQty.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<String, Object> before = snapshot(inventory);
        inventory.setFrozenQuantity(inventory.getFrozenQuantity().subtract(releaseQty));
        inventory.setAvailableQuantity(inventory.getAvailableQuantity().add(releaseQty));
        if (inventory.getFrozenQuantity().compareTo(BigDecimal.ZERO) == 0
                && inventory.getPledgedQuantity().compareTo(BigDecimal.ZERO) == 0
                && InventoryRightStatus.FROZEN.equals(inventory.getRightStatus())) {
            inventory.setRightStatus(InventoryRightStatus.IN_STOCK);
        }
        inventory.setUpdatedAt(Instant.now());
        inventoryRepository.save(inventory);
        auditLogService.logAsSystem(
                "system", operatorId, projectId, null,
                "WAREHOUSE_UNFREEZE", businessType, businessId, before, snapshot(inventory));
    }

    private static Map<String, Object> snapshot(WhInventory inventory) {
        return Map.of(
                "inventory_id", inventory.getId(),
                "available_quantity", inventory.getAvailableQuantity(),
                "frozen_quantity", inventory.getFrozenQuantity(),
                "right_status", inventory.getRightStatus());
    }
}
