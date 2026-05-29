package com.scf.excel.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.excel.dto.ExcelImportConfirmRequest;
import com.scf.excel.dto.ExcelImportConfirmResponse;
import com.scf.excel.dto.ExcelImportCreateRequest;
import com.scf.excel.dto.ExcelImportJobView;
import com.scf.excel.service.ExcelImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/imports/excel/jobs")
public class ExcelImportController {

    private final ExcelImportService excelImportService;

    public ExcelImportController(ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
    }

    @PostMapping
    public ApiResponse<ExcelImportJobView> create(
            @Valid @RequestBody ExcelImportCreateRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(excelImportService.createJob(request), httpRequest.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExcelImportJobView> get(@PathVariable String id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(excelImportService.getJob(id), httpRequest.getHeader("X-Request-Id"));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ExcelImportConfirmResponse> confirm(
            @PathVariable String id,
            @Valid @RequestBody ExcelImportConfirmRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(excelImportService.confirm(id, request), httpRequest.getHeader("X-Request-Id"));
    }
}
