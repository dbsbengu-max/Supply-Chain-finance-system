package com.scf.warehouse.dto;

import com.scf.warehouse.InventoryRightStatus;

import java.util.List;

public record WarehouseMetaView(List<DictItemView> right_statuses) {

    public static WarehouseMetaView defaults() {
        List<DictItemView> statuses = List.of(
                new DictItemView(InventoryRightStatus.IN_STOCK, InventoryRightStatus.label(InventoryRightStatus.IN_STOCK)),
                new DictItemView(InventoryRightStatus.FROZEN, InventoryRightStatus.label(InventoryRightStatus.FROZEN)),
                new DictItemView(InventoryRightStatus.PLEDGED, InventoryRightStatus.label(InventoryRightStatus.PLEDGED)),
                new DictItemView(InventoryRightStatus.RELEASE_REVIEW, InventoryRightStatus.label(InventoryRightStatus.RELEASE_REVIEW)),
                new DictItemView(InventoryRightStatus.RELEASED, InventoryRightStatus.label(InventoryRightStatus.RELEASED)),
                new DictItemView(InventoryRightStatus.PENDING_OUT, InventoryRightStatus.label(InventoryRightStatus.PENDING_OUT)),
                new DictItemView(InventoryRightStatus.OUT_STOCK, InventoryRightStatus.label(InventoryRightStatus.OUT_STOCK)),
                new DictItemView(InventoryRightStatus.INVENTORY_EXCEPTION, InventoryRightStatus.label(InventoryRightStatus.INVENTORY_EXCEPTION)));
        return new WarehouseMetaView(statuses);
    }
}
