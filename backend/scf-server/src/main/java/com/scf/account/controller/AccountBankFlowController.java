package com.scf.account.controller;

import com.scf.account.dto.*;
import com.scf.account.service.AccountBankFlowService;
import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts/bank-flows")
public class AccountBankFlowController {

    private final AccountBankFlowService bankFlowService;

    public AccountBankFlowController(AccountBankFlowService bankFlowService) {
        this.bankFlowService = bankFlowService;
    }

    @GetMapping
    public ApiResponse<PageResponse<BankFlowView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "match_status", required = false) String matchStatus,
            @RequestParam(name = "flow_type", required = false) String flowType,
            @RequestParam(name = "account_id", required = false) String accountId,
            HttpServletRequest request) {
        return ApiResponse.ok(
                bankFlowService.list(pageNo, pageSize, matchStatus, flowType, accountId),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/import")
    public ApiResponse<List<BankFlowView>> importFlows(
            @Valid @RequestBody BankFlowImportRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(bankFlowService.importFlows(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/match")
    public ApiResponse<BankFlowMatchView> match(
            @PathVariable String id,
            @Valid @RequestBody BankFlowMatchRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(bankFlowService.match(id, body), request.getHeader("X-Request-Id"));
    }
}
