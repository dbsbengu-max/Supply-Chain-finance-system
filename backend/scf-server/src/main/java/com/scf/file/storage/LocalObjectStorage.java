package com.scf.file.storage;

import com.scf.common.exception.BusinessException;
import com.scf.file.config.FileStorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalObjectStorage implements ObjectStorage {

    private final FileStorageProperties properties;

    public LocalObjectStorage(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject store(String operatorId, String projectId, String fileId, byte[] content) {
        String storageKey = operatorId + "/" + projectId + "/" + fileId;
        Path target = resolvePath(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("FILE_500", "文件存储失败", 500);
        }
        return new StoredObject(properties.getStorageBucket(), storageKey);
    }

    @Override
    public byte[] read(String storageKey) {
        Path target = resolvePath(storageKey);
        if (!Files.exists(target)) {
            throw new BusinessException("FILE_404", "文件不存在", 404);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("FILE_500", "文件读取失败", 500);
        }
    }

    private Path resolvePath(String storageKey) {
        Path base = Paths.get(properties.getStorageDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(storageKey).normalize();
        if (!resolved.startsWith(base)) {
            throw new BusinessException("FILE_400", "非法存储路径", 400);
        }
        return resolved;
    }
}
