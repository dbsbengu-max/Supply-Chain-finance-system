package com.scf.customer.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.customer.dto.*;
import com.scf.customer.service.EnterpriseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/customers")
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    public EnterpriseController(EnterpriseService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @GetMapping("/enterprises")
    public ApiResponse<PageResponse<EnterpriseView>> list(
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "enterprise_type", required = false) String enterpriseType,
            @RequestParam(name = "kyc_status", required = false) String kycStatus,
            HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.list(pageNo, pageSize, enterpriseType, kycStatus),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises")
    public ApiResponse<EnterpriseView> create(
            @Valid @RequestBody EnterpriseCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.create(body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/enterprises/{id}")
    public ApiResponse<EnterpriseView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.getById(id), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/enterprises/{id}")
    public ApiResponse<EnterpriseView> update(
            @PathVariable String id,
            @Valid @RequestBody EnterpriseUpdateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.update(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises/{id}/submit-kyc")
    public ApiResponse<EnterpriseView> submitKyc(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.submitKyc(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises/{id}/approve-kyc")
    public ApiResponse<EnterpriseView> approveKyc(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.approveKyc(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises/{id}/reject-kyc")
    public ApiResponse<EnterpriseView> rejectKyc(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String reason = body == null ? null : body.get("reason");
        return ApiResponse.ok(enterpriseService.rejectKyc(id, reason), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/enterprises/{id}/certs")
    public ApiResponse<List<Map<String, Object>>> listCerts(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.listCerts(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises/{id}/certs")
    public ApiResponse<Map<String, Object>> addCert(
            @PathVariable String id,
            @Valid @RequestBody CertCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.addCert(id, body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/enterprises/{id}/bank-accounts")
    public ApiResponse<List<Map<String, Object>>> listAccounts(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.listBankAccounts(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/enterprises/{id}/bank-accounts")
    public ApiResponse<Map<String, Object>> addAccount(
            @PathVariable String id,
            @Valid @RequestBody BankAccountCreateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(enterpriseService.addBankAccount(id, body), request.getHeader("X-Request-Id"));
    }
}
