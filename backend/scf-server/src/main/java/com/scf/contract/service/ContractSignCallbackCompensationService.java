package com.scf.contract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.util.IdGenerator;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackRequest;
import com.scf.saga.entity.BizCompensationTask;
import com.scf.saga.repository.BizCompensationTaskRepository;
import com.scf.saga.support.CompensationTypes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContractSignCallbackCompensationService {

    private final ContractSignProperties properties;
    private final BizCompensationTaskRepository repository;
    private final ObjectMapper objectMapper;

    public ContractSignCallbackCompensationService(
            ContractSignProperties properties,
            BizCompensationTaskRepository repository,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueueManualReview(
            ContractSignCallbackRequest request,
            String idempotencyKey,
            String reasonCode,
            String reasonMessage) {
        if (!properties.isCompensationPoolEnabled()) {
            return;
        }
        BizCompensationTask task = new BizCompensationTask();
        task.setId(IdGenerator.nextId());
        task.setCompensationType(CompensationTypes.CONTRACT_SIGN_CALLBACK_REVIEW);
        task.setBusinessType("CONTRACT_SIGN_CALLBACK");
        task.setBusinessId(blankToDefault(request.externalSignRef(), "UNKNOWN"));
        task.setCompensationStatus("MANUAL_REQUIRED");
        task.setActionJson(actionJson(request, idempotencyKey, reasonCode, reasonMessage));
        task.setRetryCount(0);
        task.setHighRiskFlag(false);
        task.setLastError(reasonCode + ": " + reasonMessage);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        repository.save(task);
    }

    private String actionJson(
            ContractSignCallbackRequest request,
            String idempotencyKey,
            String reasonCode,
            String reasonMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "MANUAL_REVIEW_CONTRACT_SIGN_CALLBACK");
        payload.put("external_sign_ref", request.externalSignRef());
        payload.put("callback_status", request.callbackStatus());
        payload.put("provider_code", request.providerCode());
        payload.put("signed_at", request.signedAt());
        payload.put("failure_reason", request.failureReason());
        payload.put("idempotency_key", idempotencyKey);
        payload.put("reason_code", reasonCode);
        payload.put("reason_message", reasonMessage);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize contract sign callback compensation", ex);
        }
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
