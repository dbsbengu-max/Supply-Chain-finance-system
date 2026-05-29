package com.scf.warehouse.dto;

import com.scf.warehouse.entity.WhOutboundRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record OutboundRequestView(
        String id,
        String request_no,
        String inventory_id,
        BigDecimal quantity,
        String request_status,
        String remark,
        Instant created_at,
        Instant confirmed_at) {

    public static OutboundRequestView from(WhOutboundRequest entity) {
        return new OutboundRequestView(
                entity.getId(),
                entity.getRequestNo(),
                entity.getInventoryId(),
                entity.getQuantity(),
                entity.getRequestStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getConfirmedAt());
    }
}
