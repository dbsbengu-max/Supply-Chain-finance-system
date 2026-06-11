package com.scf.contract.service;

import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ContractSignCallbackNonceStore {

    private final ContractSignProperties properties;
    private final ConcurrentMap<String, Instant> seenNonces = new ConcurrentHashMap<>();

    public ContractSignCallbackNonceStore(ContractSignProperties properties) {
        this.properties = properties;
    }

    public void requireFresh(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            throw new BusinessException("AUTH_403", "缺少 X-Contract-Sign-Nonce", 403);
        }
        purgeExpired(Instant.now());
        String key = nonce.trim();
        Instant existing = seenNonces.putIfAbsent(key, Instant.now());
        if (existing != null) {
            throw new BusinessException("AUTH_403", "签章回调 nonce 已使用", 403);
        }
    }

    private void purgeExpired(Instant now) {
        long windowSeconds = Math.max(properties.getCallbackSignatureWindowSeconds(), 1);
        seenNonces.entrySet().removeIf(entry ->
                entry.getValue().plusSeconds(windowSeconds).isBefore(now));
    }
}
