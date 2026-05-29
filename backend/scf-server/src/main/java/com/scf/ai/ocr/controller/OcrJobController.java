package com.scf.ai.ocr.controller;

import com.scf.ai.ocr.dto.OcrConfirmRequest;
import com.scf.ai.ocr.dto.OcrConfirmResponse;
import com.scf.ai.ocr.dto.OcrJobCreateRequest;
import com.scf.ai.ocr.dto.OcrJobView;
import com.scf.ai.ocr.service.OcrJobService;
import com.scf.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/ocr/jobs")
public class OcrJobController {

    private final OcrJobService ocrJobService;

    public OcrJobController(OcrJobService ocrJobService) {
        this.ocrJobService = ocrJobService;
    }

    @PostMapping
    public ApiResponse<OcrJobView> create(
            @Valid @RequestBody OcrJobCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(ocrJobService.createJob(request), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<OcrJobView> get(@PathVariable String id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(ocrJobService.getJob(id), httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<OcrConfirmResponse> confirm(
            @PathVariable String id,
            @Valid @RequestBody OcrConfirmRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(ocrJobService.confirm(id, request), httpRequest.getHeader("X-Request-Id"));
    }
}
