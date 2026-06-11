package com.scf.document.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.common.dto.PageResponse;
import com.scf.document.dto.DocumentDtos.DocumentCenterDetailView;
import com.scf.document.dto.DocumentDtos.DocumentCenterListItem;
import com.scf.document.dto.DocumentDtos.DocumentCenterRegisterRequest;
import com.scf.document.dto.DocumentDtos.DocumentRequirementUpsertRequest;
import com.scf.document.dto.DocumentDtos.DocumentRequirementView;
import com.scf.document.dto.DocumentDtos.DocumentReviewReasonRequest;
import com.scf.document.dto.DocumentDtos.DocumentValidateRequest;
import com.scf.document.dto.DocumentDtos.DocumentValidateResponse;
import com.scf.document.service.DocumentCenterService;
import com.scf.document.service.DocumentRequirementService;
import com.scf.document.service.DocumentValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/documents")
public class DocumentCenterController {

    private final DocumentCenterService documentCenterService;
    private final DocumentRequirementService requirementService;
    private final DocumentValidationService validationService;

    public DocumentCenterController(
            DocumentCenterService documentCenterService,
            DocumentRequirementService requirementService,
            DocumentValidationService validationService) {
        this.documentCenterService = documentCenterService;
        this.requirementService = requirementService;
        this.validationService = validationService;
    }

    @GetMapping("/center")
    public ApiResponse<PageResponse<DocumentCenterListItem>> listCenter(
            HttpServletRequest request,
            @RequestParam(name = "page_no", defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @RequestParam(name = "business_type", required = false) String businessType,
            @RequestParam(name = "business_id", required = false) String businessId,
            @RequestParam(name = "document_type", required = false) String documentType,
            @RequestParam(name = "document_status", required = false) String documentStatus,
            @RequestParam(name = "review_status", required = false) String reviewStatus,
            @RequestParam(name = "contract_status", required = false) String contractStatus) {
        return ApiResponse.ok(
                documentCenterService.list(
                        pageNo, pageSize, businessType, businessId, documentType, documentStatus, reviewStatus, contractStatus),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/center/{id}")
    public ApiResponse<DocumentCenterDetailView> getCenter(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.get(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center")
    public ApiResponse<DocumentCenterDetailView> registerCenter(
            @Valid @RequestBody DocumentCenterRegisterRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.register(body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center/{id}/ocr")
    public ApiResponse<DocumentCenterDetailView> ocrCenter(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.triggerOcr(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center/{id}/submit-review")
    public ApiResponse<DocumentCenterDetailView> submitReview(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.submitReview(id), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center/{id}/approve")
    public ApiResponse<DocumentCenterDetailView> approve(
            @PathVariable String id,
            @RequestBody(required = false) DocumentReviewReasonRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.approve(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center/{id}/reject")
    public ApiResponse<DocumentCenterDetailView> reject(
            @PathVariable String id,
            @RequestBody(required = false) DocumentReviewReasonRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.reject(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/center/{id}/archive")
    public ApiResponse<DocumentCenterDetailView> archive(
            @PathVariable String id,
            @RequestBody(required = false) DocumentReviewReasonRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(documentCenterService.archive(id, body), request.getHeader("X-Request-Id"));
    }

    @GetMapping("/requirements")
    public ApiResponse<List<DocumentRequirementView>> listRequirements(
            HttpServletRequest request,
            @RequestParam(name = "business_type", required = false) String businessType,
            @RequestParam(name = "business_stage", required = false) String businessStage,
            @RequestParam(name = "project_id", required = false) String projectId) {
        return ApiResponse.ok(
                requirementService.list(businessType, businessStage, projectId),
                request.getHeader("X-Request-Id"));
    }

    @PostMapping("/requirements")
    public ApiResponse<DocumentRequirementView> createRequirement(
            @Valid @RequestBody DocumentRequirementUpsertRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(requirementService.create(body), request.getHeader("X-Request-Id"));
    }

    @PutMapping("/requirements/{id}")
    public ApiResponse<DocumentRequirementView> updateRequirement(
            @PathVariable String id,
            @Valid @RequestBody DocumentRequirementUpsertRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(requirementService.update(id, body), request.getHeader("X-Request-Id"));
    }

    @PostMapping("/validate")
    public ApiResponse<DocumentValidateResponse> validate(
            @Valid @RequestBody DocumentValidateRequest body,
            HttpServletRequest request) {
        return ApiResponse.ok(validationService.validate(body), request.getHeader("X-Request-Id"));
    }
}
