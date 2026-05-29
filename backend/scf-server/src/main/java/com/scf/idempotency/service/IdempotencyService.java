package com.scf.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;
import com.scf.common.util.IdGenerator;
import com.scf.idempotency.entity.IdempotencyRecord;
import com.scf.idempotency.repository.IdempotencyRecordRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final int retentionDays;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper,
            @Value("${scf.idempotency.retention-days:180}") int retentionDays) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public <T> T execute(String idempotencyKey, String businessType, String requestBody, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String requestHash = IdGenerator.sha256(requestBody == null ? "" : requestBody);
        var existing = repository.findByIdempotencyKeyAndRequestHash(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if ("SUCCESS".equals(record.getStatus()) && record.getResultJson() != null) {
                return deserialize(record.getResultJson());
            }
            if ("PROCESSING".equals(record.getStatus())) {
                throw new BusinessException("DATA_409", "请求处理中，请勿重复提交", 409);
            }
        }

        var conflict = repository.findFirstByIdempotencyKey(idempotencyKey);
        if (conflict.isPresent() && !conflict.get().getRequestHash().equals(requestHash)) {
            throw new BusinessException("DATA_409", "幂等键与请求参数不一致", 409);
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(IdGenerator.nextId());
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setBusinessType(businessType);
        record.setStatus("PROCESSING");
        record.setCreatedAt(Instant.now());
        record.setExpiredAt(Instant.now().plus(retentionDays, ChronoUnit.DAYS));
        repository.save(record);

        T result = action.get();
        record.setStatus("SUCCESS");
        record.setResultJson(serialize(result));
        record.setHttpStatus(200);
        record.setUpdatedAt(Instant.now());
        repository.save(record);
        return result;
    }

    @Transactional
    public <T> com.scf.idempotency.dto.IdempotentExecutionResult<T> executeWithReplay(
            String idempotencyKey,
            String businessType,
            String requestBody,
            Class<T> responseType,
            Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("VALID_400", "缺少 X-Idempotency-Key", 400);
        }

        String requestHash = IdGenerator.sha256(requestBody == null ? "" : requestBody);
        var existing = repository.findByIdempotencyKeyAndRequestHash(idempotencyKey, requestHash);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if ("SUCCESS".equals(record.getStatus()) && record.getResultJson() != null) {
                return new com.scf.idempotency.dto.IdempotentExecutionResult<>(
                        deserialize(record.getResultJson(), responseType), true);
            }
            if ("PROCESSING".equals(record.getStatus())) {
                throw new BusinessException("DATA_409", "请求处理中，请勿重复提交", 409);
            }
        }

        var conflict = repository.findFirstByIdempotencyKey(idempotencyKey);
        if (conflict.isPresent() && !conflict.get().getRequestHash().equals(requestHash)) {
            throw new BusinessException("DATA_409", "幂等键与请求参数不一致", 409);
        }

        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(IdGenerator.nextId());
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setBusinessType(businessType);
        record.setStatus("PROCESSING");
        record.setCreatedAt(Instant.now());
        record.setExpiredAt(Instant.now().plus(retentionDays, ChronoUnit.DAYS));
        repository.save(record);

        T result = action.get();
        record.setStatus("SUCCESS");
        record.setResultJson(serialize(result));
        record.setHttpStatus(200);
        record.setUpdatedAt(Instant.now());
        repository.save(record);
        return new com.scf.idempotency.dto.IdempotentExecutionResult<>(result, false);
    }

    @SuppressWarnings("unchecked")
    private <T> T deserialize(String json) {
        try {
            return (T) objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency result", e);
        }
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency result", e);
        }
    }

    private <T> T deserialize(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency result", e);
        }
    }
}
