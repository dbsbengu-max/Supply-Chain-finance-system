package com.scf.account.controller;

import com.scf.account.dto.*;
import com.scf.account.service.AccountClearingService;
import com.scf.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts/clearing")
public class AccountClearingController {

    private final AccountClearingService clearingService;

    public AccountClearingController(AccountClearingService clearingService) {
        this.clearingService = clearingService;
    }

    @GetMapping("/entry")
    public ApiResponse<ClearingEntryView> entry(
            @RequestParam(name = "finance_id", required = false) String financeId,
            HttpServletRequest request) {
        return ApiResponse.ok(clearingService.getEntry(financeId), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/calculate")
    public ApiResponse<ClearingCalculateView> calculate(
            @Valid @RequestBody ClearingCalculateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(clearingService.calculate(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/execute")
    public ApiResponse<ClearingExecuteView> execute(
            @Valid @RequestBody ClearingExecuteRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(
                clearingService.execute(
                        body,
                        request.getHeader("X-Idempotency-Key"),
                        request.getHeader("X-Secondary-Auth-Token")),
                request.getHeader("X-Request-Id"));
    }
}
