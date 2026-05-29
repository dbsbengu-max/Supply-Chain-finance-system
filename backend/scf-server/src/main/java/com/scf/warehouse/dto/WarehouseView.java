package com.scf.warehouse.dto;

import com.scf.warehouse.entity.WhWarehouse;

public record WarehouseView(
        String id,
        String operator_id,
        String project_id,
        String warehouse_company_id,
        String warehouse_code,
        String warehouse_name,
        String country_region,
        String address,
        String warehouse_type,
        String status) {

    public static WarehouseView from(WhWarehouse entity) {
        return new WarehouseView(
                entity.getId(),
                entity.getOperatorId(),
                entity.getProjectId(),
                entity.getWarehouseCompanyId(),
                entity.getWarehouseCode(),
                entity.getWarehouseName(),
                entity.getCountryRegion(),
                entity.getAddress(),
                entity.getWarehouseType(),
                entity.getStatus());
    }
}
