package com.scf.bi.controller;

import com.scf.bi.dto.BiDashboardDtos.*;
import com.scf.bi.service.BiDashboardService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bi")
public class BiController {

    private final BiDashboardService biDashboardService;
    private final TenantContext tenantContext;

    public BiController(BiDashboardService biDashboardService, TenantContext tenantContext) {
        this.biDashboardService = biDashboardService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/overview")
    public ApiResponse<BiOverviewView> overview(HttpServletRequest request) {
        tenantContext.requirePermission("BI_VIEW");
        return ApiResponse.ok(biDashboardService.overview(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/trade-trend")
    public ApiResponse<BiTradeTrendView> tradeTrend(
            HttpServletRequest request,
            @RequestParam(name = "months", required = false) Integer months) {
        tenantContext.requirePermission("BI_VIEW");
        return ApiResponse.ok(biDashboardService.tradeTrend(months), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/finance-summary")
    public ApiResponse<BiFinanceSummaryView> financeSummary(HttpServletRequest request) {
        tenantContext.requirePermission("BI_VIEW");
        return ApiResponse.ok(biDashboardService.financeSummary(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/warehouse-summary")
    public ApiResponse<BiWarehouseSummaryView> warehouseSummary(HttpServletRequest request) {
        tenantContext.requirePermission("BI_VIEW");
        return ApiResponse.ok(biDashboardService.warehouseSummary(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/clearing-summary")
    public ApiResponse<BiClearingSummaryView> clearingSummary(HttpServletRequest request) {
        tenantContext.requirePermission("BI_VIEW");
        return ApiResponse.ok(biDashboardService.clearingSummary(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/risk-alerts")
    public ApiResponse<BiRiskAlertsView> riskAlerts(HttpServletRequest request) {
        tenantContext.requirePermission("BI_DRILLDOWN");
        return ApiResponse.ok(biDashboardService.riskAlerts(), request.getHeader("X-Request-Id"));
    }
}
