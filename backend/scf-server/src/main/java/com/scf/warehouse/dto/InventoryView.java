package com.scf.warehouse.dto;

import com.scf.warehouse.InventoryRightStatus;
import com.scf.warehouse.entity.WhInventory;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryView(
        String id,
        String warehouse_id,
        String operator_id,
        String project_id,
        String sku_id,
        String batch_no,
        String owner_id,
        String location_code,
        BigDecimal quantity,
        BigDecimal available_quantity,
        BigDecimal frozen_quantity,
        BigDecimal pledged_quantity,
        BigDecimal outbound_pending_quantity,
        BigDecimal valuation_amount,
        String currency,
        String right_status,
        String right_status_label,
        boolean stocktake_exception,
        int version_no,
        String created_by,
        Instant created_at,
        Instant updated_at) {

    public static InventoryView from(WhInventory entity) {
        return new InventoryView(
                entity.getId(),
                entity.getWarehouseId(),
                entity.getOperatorId(),
                entity.getProjectId(),
                entity.getSkuId(),
                entity.getBatchNo(),
                entity.getOwnerId(),
                entity.getLocationCode(),
                entity.getQuantity(),
                entity.getAvailableQuantity(),
                entity.getFrozenQuantity(),
                entity.getPledgedQuantity(),
                entity.getOutboundPendingQuantity(),
                entity.getValuationAmount(),
                entity.getCurrency(),
                entity.getRightStatus(),
                InventoryRightStatus.label(entity.getRightStatus()),
                entity.getStocktakeException() == 1,
                entity.getVersionNo(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
