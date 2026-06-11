package com.scf.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.provider.HttpContractSignProvider;
import com.scf.contract.provider.model.SignRequestContext;
import com.scf.contract.provider.model.SignRequestResult;
import com.scf.contract.provider.model.SignStatusResult;
import com.scf.contract.provider.outbound.HmacSha256OutboundSignAuth;
import com.scf.contract.provider.outbound.HttpProviderFieldMapper;
import com.scf.contract.provider.outbound.HttpProviderResponseMapper;
import com.scf.contract.provider.outbound.OutboundSignAuthFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpContractSignProviderTest {

    private static final String APP_ID = "app-ea044";
    private static final String APP_SECRET = "ea044-provider-secret";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void ea044HttpProviderCreateAndQueryWithSignedHeaders() throws Exception {
        AtomicReference<String> createBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sign/create", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            createBody.set(body);
            signaturePayload(exchange, body);
            writeJson(exchange, 200, """
                    {"data":{"external_sign_ref":"REAL-EA044-001","provider_status":"ACCEPTED","provider_message":"accepted"}}
                    """);
        });
        server.createContext("/sign/status/REAL-EA044-001", exchange -> {
            String payload = signaturePayload(exchange, "");
            if (!verify(payload, exchange.getRequestHeaders().getFirst("X-Scf-Signature"))) {
                writeJson(exchange, 401, "{\"message\":\"bad signature\"}");
                return;
            }
            writeJson(exchange, 200, """
                    {"external_sign_ref":"REAL-EA044-001","status":"COMPLETED","signed_at":"2026-06-01T10:00:00Z"}
                    """);
        });
        server.start();

        HttpContractSignProvider provider = provider("http://127.0.0.1:" + server.getAddress().getPort());
        SignRequestResult createResult = provider.createSignRequest(new SignRequestContext(
                "TASK_EA044",
                "DOC_EA044",
                "FILE_EA044",
                "CONTRACT-001",
                "TRADE_ORDER",
                "ORD_EA044",
                List.of(new SignRequestContext.SignerRef("ENT_A", "Alice", "BUYER")),
                false));

        assertThat(createResult.externalSignRef()).isEqualTo("REAL-EA044-001");
        assertThat(createResult.providerStatus()).isEqualTo("PENDING_CALLBACK");
        assertThat(createBody.get()).contains("\"task_id\":\"TASK_EA044\"");

        SignStatusResult statusResult = provider.querySignStatus("REAL-EA044-001");
        assertThat(statusResult.status()).isEqualTo("SUCCESS");
        assertThat(statusResult.signedAt()).isEqualTo(Instant.parse("2026-06-01T10:00:00Z"));
    }

    @Test
    void ea044HttpProviderRejectsMissingConfiguration() {
        ContractSignProperties properties = new ContractSignProperties();
        properties.getHttpProvider().setEnabled(true);
        HttpContractSignProvider provider = new HttpContractSignProvider(
                properties,
                new ObjectMapper(),
                new RestTemplateBuilder(),
                new OutboundSignAuthFactory(List.of(new HmacSha256OutboundSignAuth())),
                new HttpProviderFieldMapper(),
                new HttpProviderResponseMapper());

        assertThatThrownBy(() -> provider.querySignStatus("REAL-MISSING-CONFIG"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("endpoint/appId");
    }

    private HttpContractSignProvider provider(String baseUrl) {
        ContractSignProperties properties = new ContractSignProperties();
        properties.getHttpProvider().setEnabled(true);
        properties.getHttpProvider().setBaseUrl(baseUrl);
        properties.getHttpProvider().setAppId(APP_ID);
        properties.getHttpProvider().setAppSecret(APP_SECRET);
        OutboundSignAuthFactory authFactory = new OutboundSignAuthFactory(List.of(new HmacSha256OutboundSignAuth()));
        return new HttpContractSignProvider(
                properties,
                new ObjectMapper(),
                new RestTemplateBuilder(),
                authFactory,
                new HttpProviderFieldMapper(),
                new HttpProviderResponseMapper());
    }

    private static String signaturePayload(HttpExchange exchange, String body) {
        String timestamp = exchange.getRequestHeaders().getFirst("X-Scf-Timestamp");
        String nonce = exchange.getRequestHeaders().getFirst("X-Scf-Nonce");
        assertThat(exchange.getRequestHeaders().getFirst("X-Scf-App-Id")).isEqualTo(APP_ID);
        assertThat(timestamp).isNotBlank();
        assertThat(nonce).isNotBlank();
        assertThat(exchange.getRequestHeaders().getFirst("X-Scf-Signature"))
                .isEqualTo(hmac(timestamp + "\n" + nonce + "\n" + body));
        return timestamp + "\n" + nonce + "\n" + body;
    }

    private static boolean verify(String payload, String signature) {
        return hmac(payload).equals(signature);
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
