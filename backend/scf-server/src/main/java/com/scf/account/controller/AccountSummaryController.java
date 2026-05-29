package com.scf.account.controller;

import com.scf.account.dto.AccountBalanceSummaryView;
import com.scf.account.service.AccountSummaryService;
import com.scf.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountSummaryController {

    private final AccountSummaryService summaryService;

    public AccountSummaryController(AccountSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    public ApiResponse<List<AccountBalanceSummaryView>> summary(HttpServletRequest request) {
        return ApiResponse.ok(summaryService.balanceSummary(), request.getHeader("X-Request-Id"));
    }
}
