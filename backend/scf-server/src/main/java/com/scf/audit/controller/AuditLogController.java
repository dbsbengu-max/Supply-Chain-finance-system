package com.scf.audit.controller;

import com.scf.audit.dto.AuditDtos.AuditFilterMetaView;
import com.scf.audit.dto.AuditDtos.AuditLogDetailView;
import com.scf.audit.dto.AuditDtos.AuditLogView;
import com.scf.audit.dto.AuditDtos.AuditSummaryView;
import com.scf.audit.service.AuditCenterService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/audit")
public class AuditLogController {

    private final AuditCenterService auditCenterService;

    public AuditLogController(AuditCenterService auditCenterService) {
        this.auditCenterService = auditCenterService;
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<AuditLogView>> list(
            HttpServletRequest request,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(name = "object_type", required = false) String objectType,
            @RequestParam(name = "object_id", required = false) String objectId,
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(name = "from_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromAt,
            @RequestParam(name = "to_at", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toAt,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(
                auditCenterService.list(pageNo, pageSize, action, objectType, objectId, userId, fromAt, toAt, keyword),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<AuditLogDetailView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(auditCenterService.get(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/summary")
    public ApiResponse<AuditSummaryView> summary(
            HttpServletRequest request,
            @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(auditCenterService.summary(days), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/meta/filters")
    public ApiResponse<AuditFilterMetaView> filterMeta(HttpServletRequest request) {
        return ApiResponse.ok(auditCenterService.filterMeta(), request.getHeader("X-Request-Id"));
    }
}
