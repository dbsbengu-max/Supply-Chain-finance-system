package com.scf.contract.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackRequest;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackResponse;
import com.scf.contract.entity.TrContractSignTask;
import com.scf.contract.repository.TrContractSignTaskRepository;
import com.scf.document.entity.TrDocumentReviewLog;
import com.scf.document.repository.TrDocumentReviewLogRepository;
import com.scf.idempotency.dto.IdempotentExecutionResult;
import com.scf.idempotency.service.IdempotencyService;
import com.scf.trade.entity.TrDocument;
import com.scf.trade.repository.TrDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

@Service
public class ContractSignCallbackService {

    private static final Set<String> ALLOWED_STATUS = Set.of("SUCCESS", "FAILED");
    private static final String SYSTEM_USER = "SYSTEM_CONTRACT_CALLBACK";

    private final ContractSignProperties properties;
    private final TrContractSignTaskRepository taskRepository;
    private final TrDocumentRepository documentRepository;
    private final TrDocumentReviewLogRepository reviewLogRepository;
    private final ContractSignService contractSignService;
    private final ContractSignCallbackCompensationService compensationService;
    private final ContractSignCallbackNonceStore nonceStore;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ContractSignCallbackService(
            ContractSignProperties properties,
            TrContractSignTaskRepository taskRepository,
            TrDocumentRepository documentRepository,
            TrDocumentReviewLogRepository reviewLogRepository,
            ContractSignService contractSignService,
            ContractSignCallbackCompensationService compensationService,
            ContractSignCallbackNonceStore nonceStore,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.contractSignService = contractSignService;
        this.compensationService = compensationService;
        this.nonceStore = nonceStore;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    public ContractSignCallbackResponse handleCallback(
            String callbackToken,
            String timestamp,
            String nonce,
            String signature,
            String idempotencyKey,
            ContractSignCallbackRequest request) {
        String payload = serialize(request);
        requireValidCallbackAuth(callbackToken, timestamp, nonce, signature, payload);
        IdempotentExecutionResult<ContractSignCallbackResponse> result;
        try {
            result = idempotencyService.executeWithReplay(
                    idempotencyKey,
                    "CONTRACT_SIGN_CALLBACK",
                    payload,
                    ContractSignCallbackResponse.class,
                    () -> processCallback(request));
        } catch (BusinessException ex) {
            if (shouldEnqueueCompensation(ex)) {
                compensationService.enqueueManualReview(request, idempotencyKey, ex.getCode(), ex.getMessage());
            }
            throw ex;
        }
        return result.replay() ? result.value().withIdempotentReplay(true) : result.value();
    }

    @Transactional
    protected ContractSignCallbackResponse processCallback(ContractSignCallbackRequest request) {
        if (!ALLOWED_STATUS.contains(request.callbackStatus())) {
            throw new BusinessException("VALID_400", "不支持的 callback_status", 400);
        }

        TrContractSignTask task = taskRepository
                .findByExternalSignRefForUpdate(request.externalSignRef())
                .orElseThrow(() -> new BusinessException("DATA_404", "签署任务不存在", 404));

        TrDocument doc = documentRepository.findById(task.getDocumentId())
                .orElseThrow(() -> new BusinessException("DATA_404", "关联单证不存在", 404));

        if ("SIGNED".equals(task.getTaskStatus()) && "SUCCESS".equals(task.getCallbackStatus())) {
            return toView(doc, task);
        }
        if ("FAILED".equals(task.getTaskStatus()) && "FAILED".equals(task.getCallbackStatus())) {
            return toView(doc, task);
        }

        task.setCallbackPayloadJson(serialize(request));
        task.setUpdatedAt(Instant.now());

        if ("FAILED".equals(request.callbackStatus())) {
            task.setTaskStatus("FAILED");
            task.setCallbackStatus("FAILED");
            task.setFailureReason(blankToDefault(request.failureReason(), "供应商签署失败"));
            taskRepository.save(task);
            contractSignService.applySignFailure(
                    doc,
                    task.getProviderCode(),
                    task.getExternalSignRef(),
                    task.getFailureReason());
            appendSystemReviewLog(doc, "SIGN_CALLBACK_FAILED", doc.getSignStatus(), "FAILED", task.getFailureReason());
            return toView(documentRepository.findById(doc.getId()).orElseThrow(), task);
        }

        contractSignService.applySignSuccess(doc, task, request.signedAt());
        return toView(documentRepository.findById(doc.getId()).orElseThrow(), task);
    }

    private void requireValidCallbackAuth(
            String callbackToken,
            String timestamp,
            String nonce,
            String signature,
            String payload) {
        if ("TIMESTAMP_NONCE_SIGNATURE".equalsIgnoreCase(properties.getCallbackVerificationMode())) {
            requireValidSignature(timestamp, nonce, signature, payload);
            return;
        }
        requireValidToken(callbackToken);
    }

    private void requireValidToken(String callbackToken) {
        if (callbackToken == null || callbackToken.isBlank()) {
            throw new BusinessException("AUTH_403", "缺少 X-Contract-Sign-Callback-Token", 403);
        }
        if (!properties.getCallbackToken().equals(callbackToken.trim())) {
            throw new BusinessException("AUTH_403", "合同签署回调鉴权失败", 403);
        }
    }

    private void requireValidSignature(String timestamp, String nonce, String signature, String payload) {
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature)) {
            throw new BusinessException("AUTH_403", "缺少签章回调验签头", 403);
        }
        Instant callbackTime;
        try {
            callbackTime = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTH_403", "签章回调时间戳非法", 403);
        }
        long skew = Math.abs(Duration.between(callbackTime, Instant.now()).getSeconds());
        if (skew > properties.getCallbackSignatureWindowSeconds()) {
            throw new BusinessException("AUTH_403", "签章回调时间戳超出允许窗口", 403);
        }
        nonceStore.requireFresh(nonce);
        String expected = hmacSha256Hex(properties.getCallbackToken(), timestamp.trim() + "\n" + nonce.trim() + "\n" + payload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("AUTH_403", "签章回调签名校验失败", 403);
        }
    }

    private ContractSignCallbackResponse toView(TrDocument doc, TrContractSignTask task) {
        return new ContractSignCallbackResponse(
                doc.getId(),
                doc.getSignStatus(),
                doc.getContractStatus(),
                task.getExternalSignRef(),
                task.getTaskStatus(),
                null);
    }

    private void appendSystemReviewLog(TrDocument doc, String action, String before, String after, String reason) {
        TrDocumentReviewLog log = new TrDocumentReviewLog();
        log.setId(com.scf.common.util.IdGenerator.nextId());
        log.setDocumentId(doc.getId());
        log.setAction(action);
        log.setBeforeStatus(before);
        log.setAfterStatus(after);
        log.setOperatorId(SYSTEM_USER);
        log.setOperatorRole("SYSTEM");
        log.setReason(reason);
        log.setCreatedAt(Instant.now());
        reviewLogRepository.save(log);
    }

    private String serialize(ContractSignCallbackRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize contract sign callback", ex);
        }
    }

    private static boolean shouldEnqueueCompensation(BusinessException ex) {
        return ex.getCode() != null
                && (ex.getCode().startsWith("DATA_")
                || ex.getCode().startsWith("STATE_")
                || ex.getCode().startsWith("VALID_"));
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate contract sign callback signature", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
