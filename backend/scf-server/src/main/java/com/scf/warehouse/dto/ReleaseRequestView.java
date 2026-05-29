package com.scf.warehouse.dto;

import com.scf.warehouse.entity.WhReleaseRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record ReleaseRequestView(
        String id,
        String request_no,
        String inventory_id,
        BigDecimal quantity,
        String request_status,
        String remark,
        Instant created_at,
        Instant approved_at) {

    public static ReleaseRequestView from(WhReleaseRequest entity) {
        return new ReleaseRequestView(
                entity.getId(),
                entity.getRequestNo(),
                entity.getInventoryId(),
                entity.getQuantity(),
                entity.getRequestStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getApprovedAt());
    }
}
