package com.scf.contract.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process HTTP vendor stub for {@code HttpContractSignProvider} integration tests.
 * Validates outbound HMAC headers: X-Scf-App-Id / X-Scf-Timestamp / X-Scf-Nonce / X-Scf-Signature.
 */
public final class HttpEsignVendorStub implements AutoCloseable {

    private final HttpServer server;
    private final String appId;
    private final String appSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> statusByRef = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastCreateBody = new AtomicReference<>();
    private final AtomicBoolean rejectAuth = new AtomicBoolean(false);

    public HttpEsignVendorStub(String appId, String appSecret) throws IOException {
        this.appId = appId;
        this.appSecret = appSecret;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sign/create", this::handleCreate);
        server.createContext("/sign/status/", this::handleStatus);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public String lastCreateBody() {
        return lastCreateBody.get();
    }

    public void setStatus(String externalSignRef, String status) {
        statusByRef.put(externalSignRef, status);
    }

    public void setRejectAuth(boolean reject) {
        rejectAuth.set(reject);
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!authorized(exchange, body)) {
            writeJson(exchange, 401, Map.of(
                    "external_sign_ref", "",
                    "status", "REJECTED",
                    "message", "unauthorized",
                    "error_code", "AUTH_FAILED"));
            return;
        }
        lastCreateBody.set(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(body, Map.class);
        String taskId = String.valueOf(payload.get("task_id"));
        String externalRef = "HTTP-FLOW-" + taskId;
        statusByRef.putIfAbsent(externalRef, "PENDING");
        exchange.getResponseHeaders().set("X-Vendor-Request-Id", "VREQ-" + taskId);
        exchange.getResponseHeaders().set("X-Vendor-Trace-Id", "VTR-" + taskId);
        writeJson(exchange, 200, Map.of(
                "external_sign_ref", externalRef,
                "request_id", "VREQ-" + taskId,
                "trace_id", "VTR-" + taskId,
                "status", "ACCEPTED",
                "message", "flow accepted"));
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        if (!authorized(exchange, "")) {
            writeJson(exchange, 401, Map.of("status", "UNKNOWN", "error_code", "AUTH_FAILED"));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/sign/status/";
        if (!path.startsWith(prefix)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        String encodedRef = path.substring(prefix.length());
        String externalRef = URLDecoder.decode(encodedRef, StandardCharsets.UTF_8);
        String status = statusByRef.getOrDefault(externalRef, "UNKNOWN");
        writeJson(exchange, 200, Map.of(
                "external_sign_ref", externalRef,
                "status", status));
    }

    private boolean authorized(HttpExchange exchange, String body) {
        if (rejectAuth.get()) {
            return false;
        }
        String headerAppId = exchange.getRequestHeaders().getFirst("X-Scf-App-Id");
        String timestamp = exchange.getRequestHeaders().getFirst("X-Scf-Timestamp");
        String nonce = exchange.getRequestHeaders().getFirst("X-Scf-Nonce");
        String signature = exchange.getRequestHeaders().getFirst("X-Scf-Signature");
        if (!appId.equals(headerAppId) || timestamp == null || nonce == null || signature == null) {
            return false;
        }
        String payload = timestamp + "\n" + nonce + "\n" + (body == null ? "" : body);
        return hmacSha256Hex(appSecret, payload).equalsIgnoreCase(signature);
    }

    private static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
