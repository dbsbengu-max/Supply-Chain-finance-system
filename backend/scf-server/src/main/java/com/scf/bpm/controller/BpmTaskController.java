package com.scf.bpm.controller;

import com.scf.bpm.service.BpmProcessService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.security.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/bpm/tasks")
public class BpmTaskController {

    private final BpmProcessService bpmProcessService;
    private final TenantContext tenantContext;

    public BpmTaskController(BpmProcessService bpmProcessService, TenantContext tenantContext) {
        this.bpmProcessService = bpmProcessService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/todo")
    public ApiResponse<?> todo(HttpServletRequest request) {
        tenantContext.requirePermission("BPM_TASK_VIEW");
        return ApiResponse.ok(bpmProcessService.listTodoTasks(), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<?> approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        tenantContext.requirePermission("BPM_APPROVE");
        String comment = body == null ? null : body.get("comment");
        return ApiResponse.ok(bpmProcessService.approveTask(id, comment), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<?> reject(@PathVariable String id, @RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        tenantContext.requirePermission("BPM_APPROVE");
        String comment = body == null ? null : body.get("comment");
        return ApiResponse.ok(bpmProcessService.rejectTask(id, comment), request.getHeader("X-Request-Id"));
    }
}
