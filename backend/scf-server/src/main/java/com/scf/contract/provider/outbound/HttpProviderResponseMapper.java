package com.scf.contract.provider.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.config.ContractSignProperties.HttpProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpProviderResponseMapper {

    public VendorExchangeTrace extractTrace(HttpProvider config, HttpHeaders responseHeaders, JsonNode root) {
        JsonNode body = unwrapData(root);
        String requestId = firstNonBlank(
                header(responseHeaders, config.getVendorRequestIdHeader()),
                text(body, config.getResponseRequestIdField(), null));
        String traceId = firstNonBlank(
                header(responseHeaders, config.getVendorTraceIdHeader()),
                text(body, config.getResponseTraceIdField(), null));
        return new VendorExchangeTrace(requestId, traceId);
    }

    public String externalSignRef(HttpProvider config, JsonNode root) {
        return text(unwrapData(root), config.getResponseExternalRefField(), null);
    }

    public String createStatus(HttpProvider config, JsonNode root) {
        JsonNode body = unwrapData(root);
        return firstNonBlank(
                text(body, config.getResponseProviderStatusField(), null),
                text(body, config.getResponseStatusField(), "PENDING_CALLBACK"));
    }

    public String createMessage(HttpProvider config, JsonNode root) {
        JsonNode body = unwrapData(root);
        return firstNonBlank(
                text(body, config.getResponseProviderMessageField(), null),
                text(body, config.getResponseMessageField(), null));
    }

    public String queryStatus(HttpProvider config, JsonNode root) {
        JsonNode body = unwrapData(root);
        return firstNonBlank(
                text(body, config.getResponseProviderStatusField(), null),
                text(body, config.getResponseStatusField(), "UNKNOWN"));
    }

    public String signedAt(HttpProvider config, JsonNode root) {
        return text(unwrapData(root), config.getResponseSignedAtField(), null);
    }

    public String failureReason(HttpProvider config, JsonNode root) {
        JsonNode body = unwrapData(root);
        return firstNonBlank(
                text(body, config.getResponseFailureReasonField(), null),
                text(body, config.getResponseMessageField(), null));
    }

    private static JsonNode unwrapData(JsonNode root) {
        if (root != null && root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (field == null || field.isBlank() || node == null || !node.hasNonNull(field)) {
            return fallback;
        }
        return node.get(field).asText();
    }

    private static String header(HttpHeaders headers, String name) {
        if (headers == null || name == null || name.isBlank()) {
            return null;
        }
        String value = headers.getFirst(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    public record VendorExchangeTrace(String providerRequestId, String providerTraceId) {
    }
}
