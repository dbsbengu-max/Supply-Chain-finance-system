package com.scf.risk.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.risk.dto.RiskAlertDtos.RiskAlertHandleRequest;
import com.scf.risk.dto.RiskAlertDtos.RiskAlertView;
import com.scf.risk.service.RiskAlertCenterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/risk/alerts")
public class RiskAlertController {

    private final RiskAlertCenterService riskAlertCenterService;

    public RiskAlertController(RiskAlertCenterService riskAlertCenterService) {
        this.riskAlertCenterService = riskAlertCenterService;
    }

    @GetMapping
    public ApiResponse<PageResponse<RiskAlertView>> list(
            HttpServletRequest request,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "alert_code", required = false) String alertCode,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "handle_status", required = false) String handleStatus,
            @RequestParam(name = "assignee_user_id", required = false) String assigneeUserId) {
        return ApiResponse.ok(
                riskAlertCenterService.list(pageNo, pageSize, alertCode, severity, handleStatus, assigneeUserId),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<RiskAlertView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(riskAlertCenterService.get(id), request.getHeader("X-Request-Id"));
    }

    @PatchMapping("/{id}")
    public ApiResponse<RiskAlertView> handle(
            @PathVariable String id,
            @Valid @RequestBody RiskAlertHandleRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(riskAlertCenterService.handle(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<RiskAlertView> claim(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(riskAlertCenterService.claim(id), request.getHeader("X-Request-Id"));
    }
}
