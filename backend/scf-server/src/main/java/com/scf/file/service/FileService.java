package com.scf.file.service;

import com.scf.common.exception.BusinessException;
import com.scf.common.security.SecurityUtils;
import com.scf.common.security.TenantContext;
import com.scf.common.security.UserContext;
import com.scf.common.util.IdGenerator;
import com.scf.file.config.FileStorageProperties;
import com.scf.file.dto.FileUploadResponse;
import com.scf.file.dto.FileView;
import com.scf.file.entity.SysFile;
import com.scf.file.repository.SysFileRepository;
import com.scf.file.storage.ObjectStorage;
import com.scf.file.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Service
public class FileService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final SysFileRepository fileRepository;
    private final TenantContext tenantContext;
    private final FileStorageProperties properties;
    private final ObjectStorage objectStorage;

    public FileService(
            SysFileRepository fileRepository,
            TenantContext tenantContext,
            FileStorageProperties properties,
            ObjectStorage objectStorage) {
        this.fileRepository = fileRepository;
        this.tenantContext = tenantContext;
        this.properties = properties;
        this.objectStorage = objectStorage;
    }

    @Transactional
    public FileUploadResponse upload(MultipartFile multipart, String businessType, String businessId) {
        tenantContext.requirePermission("FILE_UPLOAD");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        validateUpload(multipart);
        validateBusinessBinding(businessType, businessId);

        byte[] content = readBytes(multipart);
        if (content.length > properties.getMaxSizeBytes()) {
            throw new BusinessException("FILE_400", "文件大小超过限制", 400);
        }

        String fileId = IdGenerator.nextId();
        String originalName = sanitizeFileName(multipart.getOriginalFilename());
        String ext = extractExtension(originalName);
        String checksum = sha256(content);

        // 预留：病毒扫描 / 对象存储切换可在 ObjectStorage 实现层扩展
        StoredObject stored = objectStorage.store(operatorId, projectId, fileId, content);

        UserContext user = SecurityUtils.currentUser();
        SysFile entity = new SysFile();
        entity.setId(fileId);
        entity.setOperatorId(operatorId);
        entity.setProjectId(projectId);
        entity.setFileName(originalName);
        entity.setFileExt(ext);
        entity.setMimeType(normalizeMime(multipart.getContentType()));
        entity.setFileSize(content.length);
        entity.setStorageBucket(stored.storageBucket());
        entity.setStorageKey(stored.storageKey());
        entity.setChecksum(checksum);
        entity.setUploadedBy(user.userId());
        entity.setUploadedAt(Instant.now());
        entity.setStatus(STATUS_ACTIVE);
        entity.setBusinessType(normalizeOptional(businessType));
        entity.setBusinessId(normalizeOptional(businessId));
        fileRepository.save(entity);

        return FileUploadResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public FileView getById(String id) {
        tenantContext.requirePermission("FILE_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();

        SysFile file = fileRepository
                .findByIdAndOperatorIdAndProjectIdAndStatus(id, operatorId, projectId, STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException("FILE_404", "文件不存在", 404));
        return FileView.from(file);
    }

    @Transactional(readOnly = true)
    public SysFile requireAccessibleFile(String fileId) {
        tenantContext.requirePermission("FILE_VIEW");
        String operatorId = tenantContext.requireOperatorId();
        String projectId = tenantContext.requireProjectId();
        return fileRepository
                .findByIdAndOperatorIdAndProjectIdAndStatus(fileId, operatorId, projectId, STATUS_ACTIVE)
                .orElseThrow(() -> new BusinessException("FILE_404", "文件不存在", 404));
    }

    private void validateBusinessBinding(String businessType, String businessId) {
        if (businessType != null && !businessType.isBlank() && businessType.length() > 64) {
            throw new BusinessException("FILE_400", "business_type 过长", 400);
        }
        if (businessId != null && !businessId.isBlank() && businessId.length() > 64) {
            throw new BusinessException("FILE_400", "business_id 过长", 400);
        }
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void validateUpload(MultipartFile multipart) {
        if (multipart == null || multipart.isEmpty()) {
            throw new BusinessException("FILE_400", "上传文件不能为空", 400);
        }
        String originalName = multipart.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException("FILE_400", "文件名不能为空", 400);
        }

        String ext = extractExtension(sanitizeFileName(originalName));
        Set<String> allowedExt = properties.normalizedExtensions();
        if (ext.isBlank() || !allowedExt.contains(ext)) {
            throw new BusinessException("FILE_400", "不支持的文件扩展名: " + ext, 400);
        }

        String mime = normalizeMime(multipart.getContentType());
        Set<String> allowedMime = properties.normalizedMimeTypes();
        if (!allowedMime.contains(mime)) {
            throw new BusinessException("FILE_400", "不支持的 MIME 类型: " + mime, 400);
        }
    }

    private static byte[] readBytes(MultipartFile multipart) {
        try {
            return multipart.getBytes();
        } catch (IOException e) {
            throw new BusinessException("FILE_400", "无法读取上传文件", 400);
        }
    }

    private static String sanitizeFileName(String name) {
        String trimmed = name.trim();
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\\")) {
            throw new BusinessException("FILE_400", "非法文件名", 400);
        }
        return trimmed;
    }

    private static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
