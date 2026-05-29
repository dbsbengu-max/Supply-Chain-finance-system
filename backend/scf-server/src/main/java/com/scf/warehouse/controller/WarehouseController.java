package com.scf.warehouse.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.common.security.TenantContext;
import com.scf.warehouse.dto.InboundCreateRequest;
import com.scf.warehouse.dto.InboundView;
import com.scf.warehouse.dto.InventoryView;
import com.scf.warehouse.dto.OutboundCreateRequest;
import com.scf.warehouse.dto.OutboundRequestView;
import com.scf.warehouse.dto.QuantityActionRequest;
import com.scf.warehouse.dto.ReleaseRequestView;
import com.scf.warehouse.dto.WarehouseMetaView;
import com.scf.warehouse.dto.WarehouseView;
import com.scf.warehouse.service.WarehouseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/warehouse")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final TenantContext tenantContext;

    public WarehouseController(WarehouseService warehouseService, TenantContext tenantContext) {
        this.warehouseService = warehouseService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/meta")
    public ApiResponse<WarehouseMetaView> meta(HttpServletRequest request) {
        tenantContext.requirePermission("WAREHOUSE_VIEW");
        return ApiResponse.ok(WarehouseMetaView.defaults(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/warehouses")
    public ApiResponse<PageResponse<WarehouseView>> listWarehouses(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.listWarehouses(pageNo, pageSize, status), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/warehouses/{id}")
    public ApiResponse<WarehouseView> getWarehouse(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.getWarehouse(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/inventories")
    public ApiResponse<PageResponse<InventoryView>> listInventories(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "warehouse_id", required = false) String warehouseId,
            @RequestParam(name = "right_status", required = false) String rightStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(
                warehouseService.listInventories(pageNo, pageSize, warehouseId, rightStatus),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/inventories/{id}")
    public ApiResponse<InventoryView> getInventory(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.getInventory(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/inbounds")
    public ApiResponse<InboundView> createInbound(
            @Valid @RequestBody InboundCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.createInbound(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/inventories/{id}/freeze")
    public ApiResponse<InventoryView> freeze(
            @PathVariable String id,
            @Valid @RequestBody QuantityActionRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.freeze(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/inventories/{id}/pledge")
    public ApiResponse<InventoryView> pledge(
            @PathVariable String id,
            @Valid @RequestBody QuantityActionRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.pledge(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/inventories/{id}/release")
    public ApiResponse<ReleaseRequestView> applyRelease(
            @PathVariable String id,
            @Valid @RequestBody QuantityActionRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.applyRelease(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/release-requests/{id}/approve")
    public ApiResponse<InventoryView> approveRelease(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.approveRelease(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/outbounds")
    public ApiResponse<OutboundRequestView> applyOutbound(
            @Valid @RequestBody OutboundCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.applyOutbound(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/outbounds/{id}/confirm")
    public ApiResponse<InventoryView> confirmOutbound(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(warehouseService.confirmOutbound(id), request.getHeader("X-Request-Id"));
    }
}
