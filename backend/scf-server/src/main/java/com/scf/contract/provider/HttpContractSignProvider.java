package com.scf.contract.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.config.ContractSignProperties.HttpProvider;
import com.scf.contract.provider.model.SignRequestContext;
import com.scf.contract.provider.model.SignRequestResult;
import com.scf.contract.provider.model.SignStatusResult;
import com.scf.contract.provider.outbound.HttpProviderFieldMapper;
import com.scf.contract.provider.outbound.HttpProviderResponseMapper;
import com.scf.contract.provider.outbound.HttpProviderResponseMapper.VendorExchangeTrace;
import com.scf.contract.provider.outbound.OutboundSignAuthContext;
import com.scf.contract.provider.outbound.OutboundSignAuthFactory;
import com.scf.contract.provider.outbound.OutboundSignAuthMode;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Component
public class HttpContractSignProvider implements ContractSignProvider {

    private final ContractSignProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;
    private final OutboundSignAuthFactory authFactory;
    private final HttpProviderFieldMapper fieldMapper;
    private final HttpProviderResponseMapper responseMapper;

    public HttpContractSignProvider(
            ContractSignProperties properties,
            ObjectMapper objectMapper,
            RestTemplateBuilder restTemplateBuilder,
            OutboundSignAuthFactory authFactory,
            HttpProviderFieldMapper fieldMapper,
            HttpProviderResponseMapper responseMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplateBuilder = restTemplateBuilder;
        this.authFactory = authFactory;
        this.fieldMapper = fieldMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public String providerCode() {
        return config().getProviderCode();
    }

    @Override
    public String displayName() {
        return config().getDisplayName();
    }

    @Override
    public String description() {
        return "配置化 HTTP 电子签章 Adapter；出站鉴权 HMAC/RSA/SM2，字段映射与 requestId/traceId 留痕。";
    }

    @Override
    public SignRequestResult createSignRequest(SignRequestContext context) {
        ensureEnabled();
        HttpProvider cfg = config();
        Map<String, Object> payload = fieldMapper.toCreatePayload(cfg, context);
        VendorHttpResponse response = exchange(
                HttpMethod.POST,
                endpoint(cfg.getCreatePath()),
                payload,
                context.taskId());
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String externalRef = responseMapper.externalSignRef(cfg, root);
            if (externalRef == null || externalRef.isBlank()) {
                throw new BusinessException("CONTRACT_SIGN_502", "签章供应商响应缺少 external_sign_ref", 502);
            }
            VendorExchangeTrace trace = responseMapper.extractTrace(cfg, response.headers(), root);
            String status = normalizeCreateStatus(responseMapper.createStatus(cfg, root));
            String message = responseMapper.createMessage(cfg, root);
            return new SignRequestResult(
                    externalRef,
                    status,
                    message,
                    context.taskId(),
                    trace.providerRequestId(),
                    trace.providerTraceId(),
                    summarizeExchange(response.body()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("CONTRACT_SIGN_502", "签章供应商发起响应无法解析: task_id=" + context.taskId(), 502);
        }
    }

    @Override
    public SignStatusResult querySignStatus(String externalSignRef) {
        ensureEnabled();
        if (externalSignRef == null || externalSignRef.isBlank()) {
            throw new BusinessException("VALID_400", "external_sign_ref 不能为空", 400);
        }
        HttpProvider cfg = config();
        String path = cfg.getStatusPath().replace("{externalSignRef}", urlEncode(externalSignRef.trim()));
        VendorHttpResponse response = exchange(HttpMethod.GET, endpoint(path), null, externalSignRef.trim());
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String signedAt = responseMapper.signedAt(cfg, root);
            return new SignStatusResult(
                    responseMapper.externalSignRef(cfg, root) == null ? externalSignRef.trim() : responseMapper.externalSignRef(cfg, root),
                    normalizeQueryStatus(responseMapper.queryStatus(cfg, root)),
                    signedAt == null || signedAt.isBlank() ? null : Instant.parse(signedAt),
                    responseMapper.failureReason(cfg, root));
        } catch (Exception ex) {
            throw new BusinessException("CONTRACT_SIGN_502", "签章供应商查单响应无法解析: " + externalSignRef, 502);
        }
    }

    private VendorHttpResponse exchange(HttpMethod method, String url, Object payload, String platformTraceId) {
        try {
            String body = payload == null ? "" : objectMapper.writeValueAsString(payload);
            HttpHeaders headers = new HttpHeaders();
            headers.add(config().getPlatformTraceHeader(), platformTraceId);
            authFactory.require(config()).apply(headers, authContext(platformTraceId), body);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate().exchange(url, method, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("CONTRACT_SIGN_502", "签章供应商 HTTP 状态异常: " + response.getStatusCode().value(), 502);
            }
            return new VendorHttpResponse(response.getHeaders(), response.getBody() == null ? "{}" : response.getBody());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new BusinessException("CONTRACT_SIGN_502", "签章供应商调用失败: " + ex.getMessage(), 502);
        } catch (Exception ex) {
            throw new BusinessException("CONTRACT_SIGN_500", "签章供应商响应处理失败: " + ex.getMessage(), 500);
        }
    }

    private OutboundSignAuthContext authContext(String platformTraceId) {
        HttpProvider cfg = config();
        return new OutboundSignAuthContext(
                cfg.getAppId(),
                cfg.getAppSecret(),
                cfg.getPrivateKeyPem(),
                cfg.getPublicKeyPem(),
                platformTraceId);
    }

    private RestTemplate restTemplate() {
        HttpProvider cfg = config();
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(Math.max(cfg.getConnectTimeoutMs(), 1)))
                .setReadTimeout(Duration.ofMillis(Math.max(cfg.getReadTimeoutMs(), 1)))
                .build();
    }

    private String endpoint(String path) {
        String baseUrl = trimTrailingSlash(config().getBaseUrl());
        String normalizedPath = path == null || path.isBlank() ? "" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return baseUrl + normalizedPath;
    }

    private void ensureEnabled() {
        HttpProvider cfg = config();
        if (!cfg.isEnabled()) {
            throw new BusinessException("CONTRACT_SIGN_409", "HTTP 签章供应商未启用", 409);
        }
        OutboundSignAuthMode mode = OutboundSignAuthMode.fromConfig(cfg.getOutboundAuthMode());
        if (cfg.getBaseUrl() == null || cfg.getBaseUrl().isBlank() || cfg.getAppId() == null || cfg.getAppId().isBlank()) {
            throw new BusinessException("CONTRACT_SIGN_500", "HTTP 签章供应商 endpoint/appId 未配置", 500);
        }
        if (mode == OutboundSignAuthMode.HMAC_SHA256 && (cfg.getAppSecret() == null || cfg.getAppSecret().isBlank())) {
            throw new BusinessException("CONTRACT_SIGN_500", "HTTP 签章供应商 appSecret 未配置", 500);
        }
        if ((mode == OutboundSignAuthMode.RSA_SHA256 || mode == OutboundSignAuthMode.SM2)
                && (cfg.getPrivateKeyPem() == null || cfg.getPrivateKeyPem().isBlank())) {
            throw new BusinessException("CONTRACT_SIGN_500", "HTTP 签章供应商 privateKeyPem 未配置", 500);
        }
    }

    private HttpProvider config() {
        return properties.getHttpProvider();
    }

    private static String normalizeCreateStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "ACCEPTED", "PENDING", "PROCESSING", "SIGNING" -> "PENDING_CALLBACK";
            case "FAIL", "FAILED", "SUBMIT_FAILED" -> "SUBMIT_FAILED";
            case "SUCCESS", "SIGNED", "COMPLETED" -> "SIGNED";
            default -> normalized.isBlank() ? "PENDING_CALLBACK" : normalized;
        };
    }

    private static String normalizeQueryStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "COMPLETED" -> "SUCCESS";
            case "REJECTED", "CANCELLED" -> "FAILED";
            default -> normalized.isBlank() ? "UNKNOWN" : normalized;
        };
    }

    private String summarizeExchange(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        return trimmed.length() <= 2000 ? trimmed : trimmed.substring(0, 2000);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private static String urlEncode(String value) {
        return UriComponentsBuilder.fromPath(value).build().encode().toUriString();
    }

    private record VendorHttpResponse(HttpHeaders headers, String body) {
    }
}
