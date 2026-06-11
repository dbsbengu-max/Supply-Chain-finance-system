package com.scf.contract.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackRequest;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignInitiateRequest;
import com.scf.contract.dto.ContractSignDtos.ContractSignInitiateResponse;
import com.scf.contract.dto.ContractSignDtos.ContractSignTaskView;
import com.scf.contract.service.ContractSignCallbackService;
import com.scf.contract.service.ContractSignService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/documents/center")
public class ContractSignController {

    private final ContractSignService contractSignService;

    public ContractSignController(ContractSignService contractSignService) {
        this.contractSignService = contractSignService;
    }

    @PostMapping("/{documentId}/sign")
    public ApiResponse<ContractSignInitiateResponse> initiateSign(
            @PathVariable("documentId") String documentId,
            @RequestBody(required = false) ContractSignInitiateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(contractSignService.initiateSign(documentId, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/{documentId}/sign/retry")
    public ApiResponse<ContractSignInitiateResponse> retrySign(
            @PathVariable("documentId") String documentId,
            HttpServletRequest request) {
        return ApiResponse.ok(contractSignService.retrySign(documentId), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{documentId}/sign/tasks")
    public ApiResponse<List<ContractSignTaskView>> listSignTasks(
            @PathVariable("documentId") String documentId,
            HttpServletRequest request) {
        return ApiResponse.ok(contractSignService.listTasks(documentId), request.getHeader("X-Request-Id"));
    }
}

@RestController
@RequestMapping("/integrations/contracts")
class ContractSignIntegrationController {

    private final ContractSignCallbackService callbackService;

    ContractSignIntegrationController(ContractSignCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/sign-callback")
    public ApiResponse<ContractSignCallbackResponse> signCallback(
            @RequestHeader(name = "X-Contract-Sign-Callback-Token", required = false) String callbackToken,
            @RequestHeader(name = "X-Contract-Sign-Timestamp", required = false) String timestamp,
            @RequestHeader(name = "X-Contract-Sign-Nonce", required = false) String nonce,
            @RequestHeader(name = "X-Contract-Sign-Signature", required = false) String signature,
            @RequestHeader(name = "X-Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ContractSignCallbackRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(
                callbackService.handleCallback(callbackToken, timestamp, nonce, signature, idempotencyKey, body),
                request.getHeader("X-Request-Id"));
    }
}
