package com.scf.file.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.file.entity.SysFile;

import java.time.Instant;

public record FileView(
        String id,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("file_ext") String fileExt,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("file_size") long fileSize,
        String checksum,
        @JsonProperty("storage_bucket") String storageBucket,
        @JsonProperty("storage_key") String storageKey,
        @JsonProperty("operator_id") String operatorId,
        @JsonProperty("project_id") String projectId,
        @JsonProperty("uploaded_by") String uploadedBy,
        @JsonProperty("uploaded_at") Instant uploadedAt,
        String status,
        @JsonProperty("business_type") String businessType,
        @JsonProperty("business_id") String businessId
) {
    public static FileView from(SysFile file) {
        return new FileView(
                file.getId(),
                file.getFileName(),
                file.getFileExt(),
                file.getMimeType(),
                file.getFileSize(),
                file.getChecksum(),
                file.getStorageBucket(),
                file.getStorageKey(),
                file.getOperatorId(),
                file.getProjectId(),
                file.getUploadedBy(),
                file.getUploadedAt(),
                file.getStatus(),
                file.getBusinessType(),
                file.getBusinessId());
    }
}
