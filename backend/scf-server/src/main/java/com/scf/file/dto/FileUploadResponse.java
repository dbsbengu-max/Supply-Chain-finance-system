package com.scf.file.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scf.file.entity.SysFile;

public record FileUploadResponse(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("file_name") String fileName,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("file_size") long fileSize,
        String checksum,
        @JsonProperty("storage_key") String storageKey,
        @JsonProperty("business_type") String businessType,
        @JsonProperty("business_id") String businessId
) {
    public static FileUploadResponse from(SysFile file) {
        return new FileUploadResponse(
                file.getId(),
                file.getFileName(),
                file.getMimeType(),
                file.getFileSize(),
                file.getChecksum(),
                file.getStorageKey(),
                file.getBusinessType(),
                file.getBusinessId());
    }
}
