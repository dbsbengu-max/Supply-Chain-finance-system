package com.scf.agencypurchase.controller;

import com.scf.agencypurchase.dto.AgencyPurchaseCreateRequest;
import com.scf.agencypurchase.dto.AgencyPurchaseDetailView;
import com.scf.agencypurchase.dto.AgencyPurchaseMetaView;
import com.scf.agencypurchase.dto.AgencyPurchaseView;
import com.scf.agencypurchase.service.AgencyPurchaseApplicationService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.common.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/agency-purchase")
public class AgencyPurchaseController {

    private final AgencyPurchaseApplicationService applicationService;
    private final TenantContext tenantContext;

    public AgencyPurchaseController(
            AgencyPurchaseApplicationService applicationService,
            TenantContext tenantContext) {
        this.applicationService = applicationService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/meta")
    public ApiResponse<AgencyPurchaseMetaView> meta(HttpServletRequest request) {
        tenantContext.requirePermission("AGENCY_PURCHASE_VIEW");
        return ApiResponse.ok(AgencyPurchaseMetaView.defaults(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/applications")
    public ApiResponse<PageResponse<AgencyPurchaseView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "application_status", required = false) String applicationStatus,
            @RequestParam(name = "saga_status", required = false) String sagaStatus,
            @RequestParam(name = "order_mode", required = false) String orderMode,
            @RequestParam(name = "fund_source", required = false) String fundSource,
            @RequestParam(name = "pickup_type", required = false) String pickupType,
            @RequestParam(name = "customer_id", required = false) String customerId,
            @RequestParam(name = "created_from", required = false) String createdFrom,
            @RequestParam(name = "created_to", required = false) String createdTo,
            HttpServletRequest request) {
        return ApiResponse.ok(applicationService.list(
                        pageNo, pageSize, applicationStatus, sagaStatus, orderMode, fundSource, pickupType,
                        customerId, createdFrom, createdTo),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/applications")
    public ApiResponse<AgencyPurchaseView> create(
            @Valid @RequestBody AgencyPurchaseCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(applicationService.create(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/applications/{id}")
    public ApiResponse<AgencyPurchaseView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.getById(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/applications/{id}/detail")
    public ApiResponse<AgencyPurchaseDetailView> detail(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.getDetailById(id), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/applications/{id}")
    public ApiResponse<AgencyPurchaseView> update(
            @PathVariable String id,
            @Valid @RequestBody AgencyPurchaseCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(applicationService.update(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/applications/{id}/submit")
    public ApiResponse<AgencyPurchaseView> submit(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.submit(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/applications/{id}/cancel")
    public ApiResponse<AgencyPurchaseView> cancel(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(applicationService.cancel(id), request.getHeader("X-Request-Id"));
    }
}
