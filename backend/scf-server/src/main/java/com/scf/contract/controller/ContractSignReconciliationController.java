package com.scf.contract.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignLookupView;
import com.scf.contract.dto.ContractSignDtos.ContractSignStatusQueryRequest;
import com.scf.contract.dto.ContractSignDtos.ContractSignStatusQueryView;
import com.scf.contract.service.ContractSignReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integrations/contracts/sign")
public class ContractSignReconciliationController {

    private final ContractSignReconciliationService reconciliationService;

    public ContractSignReconciliationController(ContractSignReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/by-ref/{externalSignRef}")
    public ApiResponse<ContractSignLookupView> lookupByExternalSignRef(
            @PathVariable("externalSignRef") String externalSignRef,
            HttpServletRequest request) {
        return ApiResponse.ok(
                reconciliationService.lookupByExternalSignRef(externalSignRef),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/by-ref/{externalSignRef}/query-status")
    public ApiResponse<ContractSignStatusQueryView> querySignStatus(
            @PathVariable("externalSignRef") String externalSignRef,
            @RequestBody(required = false) ContractSignStatusQueryRequest body,
            HttpServletRequest request) {
        boolean reconcile = body != null && Boolean.TRUE.equals(body.reconcile());
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(
                reconciliationService.querySignStatus(externalSignRef, reconcile, reason),
                request.getHeader("X-Request-Id"));
    }
}
