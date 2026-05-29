package com.scf.integration.bank.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.integration.bank.dto.BankDisburseCallbackRequest;
import com.scf.integration.bank.dto.BankDisburseCallbackView;
import com.scf.integration.bank.service.BankDisburseCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/bank")
public class BankIntegrationController {

    private final BankDisburseCallbackService bankDisburseCallbackService;

    public BankIntegrationController(BankDisburseCallbackService bankDisburseCallbackService) {
        this.bankDisburseCallbackService = bankDisburseCallbackService;
    }

    @PostMapping("/disburse-callback")
    public ApiResponse<BankDisburseCallbackView> disburseCallback(
            @Valid @RequestBody BankDisburseCallbackRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(
                bankDisburseCallbackService.handleCallback(
                        request.getHeader("X-Bank-Callback-Token"),
                        request.getHeader("X-Idempotency-Key"),
                        body),
                request.getHeader("X-Request-Id"));
    }
}
