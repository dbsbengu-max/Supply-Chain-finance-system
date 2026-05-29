package com.scf.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "scf.file")
public class FileStorageProperties {

    private String storageDir = "./data/uploads";
    private String storageBucket = "local";
    private long maxSizeBytes = 10 * 1024 * 1024;
    private List<String> allowedExtensions = List.of("pdf", "jpg", "jpeg", "png", "xlsx", "xls", "doc", "docx");
    private List<String> allowedMimeTypes = List.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword"
    );

    public String getStorageDir() {
        return storageDir;
    }

    public void setStorageDir(String storageDir) {
        this.storageDir = storageDir;
    }

    public String getStorageBucket() {
        return storageBucket;
    }

    public void setStorageBucket(String storageBucket) {
        this.storageBucket = storageBucket;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public List<String> getAllowedMimeTypes() {
        return allowedMimeTypes;
    }

    public void setAllowedMimeTypes(List<String> allowedMimeTypes) {
        this.allowedMimeTypes = allowedMimeTypes;
    }

    public Set<String> normalizedExtensions() {
        return allowedExtensions.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    public Set<String> normalizedMimeTypes() {
        return allowedMimeTypes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
