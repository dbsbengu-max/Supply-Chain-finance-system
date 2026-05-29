package com.scf.bi.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/bi/dashboard")
public class BiDashboardController {

    private final TenantContext tenantContext;

    public BiDashboardController(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @PostMapping("/export")
    public ApiResponse<Map<String, Object>> export(HttpServletRequest request) {
        tenantContext.requirePermission("BI_EXPORT");
        return ApiResponse.ok(
                Map.of("export_status", "ACCEPTED", "message", "BI export mock accepted"),
                request.getHeader("X-Request-Id"));
    }
}
