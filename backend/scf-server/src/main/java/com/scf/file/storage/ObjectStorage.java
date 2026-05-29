package com.scf.file.storage;

/**
 * 对象存储抽象。当前为本地磁盘实现；后续可替换为 OSS/S3 并接入病毒扫描。
 */
public interface ObjectStorage {

    StoredObject store(String operatorId, String projectId, String fileId, byte[] content);

    byte[] read(String storageKey);
}
