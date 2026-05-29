package com.scf.account.controller;

import com.scf.account.dto.ClearingRuleCreateRequest;
import com.scf.account.dto.ClearingRuleUpdateRequest;
import com.scf.account.dto.ClearingRuleView;
import com.scf.account.service.AccountClearingRuleService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/clearing-rules")
public class AccountClearingRuleController {

    private final AccountClearingRuleService ruleService;

    public AccountClearingRuleController(AccountClearingRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public ApiResponse<PageResponse<ClearingRuleView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "product_type", required = false) String productType,
            @RequestParam(name = "review_status", required = false) String reviewStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(
                ruleService.list(pageNo, pageSize, productType, reviewStatus),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ClearingRuleView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(ruleService.get(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping
    public ApiResponse<ClearingRuleView> create(
            @Valid @RequestBody ClearingRuleCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(ruleService.create(body), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/{id}")
    public ApiResponse<ClearingRuleView> update(
            @PathVariable String id,
            @Valid @RequestBody ClearingRuleUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(ruleService.update(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ClearingRuleView> submit(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(ruleService.submit(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ClearingRuleView> approve(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(ruleService.approve(id), request.getHeader("X-Request-Id"));
    }
}
