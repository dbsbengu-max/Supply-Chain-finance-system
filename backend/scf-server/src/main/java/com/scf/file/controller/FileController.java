package com.scf.file.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.file.dto.FileUploadResponse;
import com.scf.file.dto.FileView;
import com.scf.file.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "business_type", required = false) String businessType,
            @RequestParam(value = "business_id", required = false) String businessId,
            HttpServletRequest request) {
        return ApiResponse.ok(
                fileService.upload(file, businessType, businessId),
                request.getHeader("X-Request-Id"));
    }

    @GetMapping("/{id}")
    public ApiResponse<FileView> get(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.ok(fileService.getById(id), request.getHeader("X-Request-Id"));
    }
}
