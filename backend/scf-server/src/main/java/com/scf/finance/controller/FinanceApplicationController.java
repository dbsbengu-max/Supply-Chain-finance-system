package com.scf.finance.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.finance.dto.FinanceCreateRequest;
import com.scf.finance.dto.FinanceDisburseRequest;
import com.scf.finance.dto.FinanceDisburseView;
import com.scf.finance.dto.FinancePreCheckDtos.FinancePreCheckRequest;
import com.scf.finance.dto.FinancePreCheckDtos.FinancePreCheckResponse;
import com.scf.finance.dto.FinanceView;
import com.scf.finance.service.FinanceApplicationService;
import com.scf.finance.service.FinancePreCheckService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/finance/applications")
public class FinanceApplicationController {

    private final FinanceApplicationService financeApplicationService;
    private final FinancePreCheckService financePreCheckService;

    public FinanceApplicationController(
            FinanceApplicationService financeApplicationService,
            FinancePreCheckService financePreCheckService) {
        this.financeApplicationService = financeApplicationService;
        this.financePreCheckService = financePreCheckService;
    }

    @GetMapping
    public ApiResponse<PageResponse<FinanceView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "finance_status", required = false) String financeStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(financeApplicationService.list(pageNo, pageSize, financeStatus),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping
    public ApiResponse<FinanceView> create(
            @Valid @RequestBody FinanceCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(financeApplicationService.create(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<FinanceView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(financeApplicationService.getById(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<FinanceView> submit(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(financeApplicationService.submit(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<FinanceView> approve(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(financeApplicationService.approve(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/pre-check")
    public ApiResponse<FinancePreCheckResponse> preCheck(
            @PathVariable String id,
            @RequestBody(required = false) FinancePreCheckRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(financePreCheckService.preCheck(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/disburse")
    public ApiResponse<FinanceDisburseView> disburse(
            @PathVariable String id,
            @Valid @RequestBody FinanceDisburseRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(
                financeApplicationService.disburse(
                        id,
                        body,
                        request.getHeader("X-Idempotency-Key"),
                        request.getHeader("X-Secondary-Auth-Token")),
                request.getHeader("X-Request-Id"));
    }
}
