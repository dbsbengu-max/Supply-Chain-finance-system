package com.scf.saga.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignStatusQueryView;
import com.scf.saga.dto.SagaOpsDtos.CompensationTaskDetailView;
import com.scf.saga.dto.SagaOpsDtos.CompensationTaskOpsView;
import com.scf.saga.dto.SagaOpsDtos.OutboxEventDetailView;
import com.scf.saga.dto.SagaOpsDtos.OutboxEventView;
import com.scf.saga.dto.SagaOpsDtos.SagaOpsFilterMetaView;
import com.scf.saga.dto.SagaOpsDtos.SagaOpsManualRequest;
import com.scf.saga.dto.SagaOpsDtos.SagaOpsSummaryView;
import com.scf.saga.service.SagaOpsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/saga/ops")
public class SagaOpsController {

    private final SagaOpsService sagaOpsService;

    public SagaOpsController(SagaOpsService sagaOpsService) {
        this.sagaOpsService = sagaOpsService;
    }

    @GetMapping("/summary")
    public ApiResponse<SagaOpsSummaryView> summary(HttpServletRequest request) {
        return ApiResponse.ok(sagaOpsService.summary(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/meta/filters")
    public ApiResponse<SagaOpsFilterMetaView> filterMeta(HttpServletRequest request) {
        return ApiResponse.ok(sagaOpsService.filterMeta(), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/outbox")
    public ApiResponse<PageResponse<OutboxEventView>> listOutbox(
            HttpServletRequest request,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "event_status", required = false) String eventStatus,
            @RequestParam(name = "event_type", required = false) String eventType,
            @RequestParam(name = "business_type", required = false) String businessType,
            @RequestParam(name = "business_id", required = false) String businessId) {
        return ApiResponse.ok(
                sagaOpsService.listOutbox(pageNo, pageSize, eventStatus, eventType, businessType, businessId),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/outbox/{id}")
    public ApiResponse<OutboxEventDetailView> getOutboxDetail(
            @PathVariable("id") String id, HttpServletRequest request) {
        return ApiResponse.ok(sagaOpsService.getOutboxDetail(id), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/compensation-tasks")
    public ApiResponse<PageResponse<CompensationTaskOpsView>> listCompensationTasks(
            HttpServletRequest request,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "compensation_status", required = false) String compensationStatus,
            @RequestParam(name = "business_type", required = false) String businessType,
            @RequestParam(name = "compensation_type", required = false) String compensationType,
            @RequestParam(name = "business_id", required = false) String businessId) {
        return ApiResponse.ok(
                sagaOpsService.listCompensationTasks(
                        pageNo, pageSize, compensationStatus, businessType, compensationType, businessId),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/compensation-tasks/{id}")
    public ApiResponse<CompensationTaskDetailView> getCompensationDetail(
            @PathVariable("id") String id, HttpServletRequest request) {
        return ApiResponse.ok(sagaOpsService.getCompensationDetail(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/outbox/{id}/retry")
    public ApiResponse<Void> retryOutbox(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.retryOutbox(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/retry")
    public ApiResponse<Void> retryCompensation(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.retryCompensationTask(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/approve-execute")
    public ApiResponse<Void> approveCompensation(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.approveCompensationTask(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/claim")
    public ApiResponse<Void> claimCompensation(@PathVariable("id") String id, HttpServletRequest request) {
        sagaOpsService.claimCompensationTask(id);
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/submit-approval")
    public ApiResponse<Void> submitCompensationApproval(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.submitCompensationApproval(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/ignore")
    public ApiResponse<Void> ignoreCompensation(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.ignoreCompensationTask(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/close")
    public ApiResponse<Void> closeCompensation(
            @PathVariable("id") String id,
            @RequestBody SagaOpsManualRequest body,
            HttpServletRequest request) {
        sagaOpsService.closeCompensationTask(id, body == null ? null : body.reason());
        return ApiResponse.ok(null, request.getHeader("X-Request-Id"));
    }

    @PostMapping("/compensation-tasks/{id}/query-sign-status")
    public ApiResponse<ContractSignStatusQueryView> queryCompensationSignStatus(
            @PathVariable("id") String id,
            @RequestBody(required = false) SagaOpsManualRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(
                sagaOpsService.queryCompensationSignStatus(id, body == null ? null : body.reason()),
                request.getHeader("X-Request-Id"));
    }
}
