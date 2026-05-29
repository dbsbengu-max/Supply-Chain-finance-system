package com.scf.warehouse.dto;

import com.scf.warehouse.entity.WhInbound;

import java.math.BigDecimal;
import java.time.Instant;

public record InboundView(
        String id,
        String inbound_no,
        String warehouse_id,
        String inventory_id,
        String sku_id,
        String batch_no,
        String owner_id,
        BigDecimal quantity,
        String inbound_status,
        Instant created_at) {

    public static InboundView from(WhInbound entity) {
        return new InboundView(
                entity.getId(),
                entity.getInboundNo(),
                entity.getWarehouseId(),
                entity.getInventoryId(),
                entity.getSkuId(),
                entity.getBatchNo(),
                entity.getOwnerId(),
                entity.getQuantity(),
                entity.getInboundStatus(),
                entity.getCreatedAt());
    }
}
