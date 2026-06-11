package com.scf.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackRequest;
import com.scf.contract.support.HttpEsignVendorStub;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "scf.contract.sign.default-provider=ESIGN_HTTP",
        "scf.contract.sign.http-provider.enabled=true",
        "scf.contract.sign.http-provider.provider-code=ESIGN_HTTP",
        "scf.contract.sign.http-provider.app-id=ea044-test-app",
        "scf.contract.sign.http-provider.app-secret=ea044-test-secret",
        "scf.contract.sign.callback-verification-mode=TIMESTAMP_NONCE_SIGNATURE",
        "scf.contract.sign.callback-token=mock-contract-sign-callback-token",
        "scf.contract.sign.compensation-pool-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {
        "/sql/permission_test_seed.sql",
        "/sql/finance_disburse_test_seed.sql",
        "/sql/finance_precheck_test_seed.sql",
        "/sql/contract_sign_test_seed.sql"
})
class ContractSignHttpEsignIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String APP_ID = "ea044-test-app";
    private static final String APP_SECRET = "ea044-test-secret";
    private static final String CALLBACK_SECRET = "mock-contract-sign-callback-token";

    private static HttpEsignVendorStub vendorStub;

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void vendorBaseUrl(DynamicPropertyRegistry registry) throws Exception {
        if (vendorStub == null) {
            vendorStub = new HttpEsignVendorStub(APP_ID, APP_SECRET);
        }
        registry.add("scf.contract.sign.http-provider.base-url", vendorStub::baseUrl);
    }

    @AfterAll
    static void stopVendorStub() {
        if (vendorStub != null) {
            vendorStub.close();
            vendorStub = null;
        }
    }

    @BeforeEach
    void resetDocuments() {
        vendorStub.setRejectAuth(false);
        jdbcTemplate.update("""
                DELETE FROM scf.tr_contract_sign_task
                WHERE document_id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL')
                """);
        jdbcTemplate.update("""
                UPDATE scf.tr_document
                SET sign_status = 'PENDING', contract_status = 'PENDING_SIGN',
                    sign_provider = NULL, external_sign_ref = NULL, review_status = 'APPROVED'
                WHERE id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL')
                """);
    }

    @Test
    void ea044HttpProviderSignAndSignatureCallback() throws Exception {
        MvcResult signResult = mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(platformHeaders("EA044-SIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider_code":"ESIGN_HTTP"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNING"))
                .andExpect(jsonPath("$.data.sign_provider").value("ESIGN_HTTP"))
                .andReturn();

        String externalRef = JsonPath.read(signResult.getResponse().getContentAsString(), "$.data.external_sign_ref");
        assertThat(externalRef).startsWith("HTTP-FLOW-");
        assertThat(vendorStub.lastCreateBody()).contains("DOC_EA040_SIGN_OK");

        String taskId = jdbcTemplate.queryForObject("""
                SELECT id FROM scf.tr_contract_sign_task
                WHERE document_id = 'DOC_EA040_SIGN_OK'
                ORDER BY created_at DESC LIMIT 1
                """, String.class);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT provider_request_id FROM scf.tr_contract_sign_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("VREQ-" + taskId);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT provider_trace_id FROM scf.tr_contract_sign_task WHERE id = ?
                """, String.class, taskId)).isEqualTo("VTR-" + taskId);

        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                externalRef,
                "SUCCESS",
                Instant.parse("2026-06-01T10:00:00Z"),
                null,
                "ESIGN_HTTP");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA044-CB-OK", "nonce-ea044-ok", Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNED"));

        vendorStub.setStatus(externalRef, "SUCCESS");
        mvc.perform(get("/integrations/contracts/sign/by-ref/" + externalRef)
                        .headers(platformHeaders("EA044-LOOKUP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.external_sign_ref").value(externalRef));

        mvc.perform(post("/integrations/contracts/sign/by-ref/" + externalRef + "/query-status")
                        .headers(platformHeaders("EA044-QUERY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider.provider_status").value("SUCCESS"));
    }

    @Test
    void ea044HttpProviderConfigShowsConnectionSummary() throws Exception {
        mvc.perform(get("/integrations/contracts/sign/config")
                        .headers(platformHeaders("EA044-CFG")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.default_provider").value("ESIGN_HTTP"))
                .andExpect(jsonPath("$.data.provider_connections[0].provider_code").value("ESIGN_HTTP"))
                .andExpect(jsonPath("$.data.provider_connections[0].enabled").value(true))
                .andExpect(jsonPath("$.data.provider_connections[0].configured").value(true))
                .andExpect(jsonPath("$.data.provider_connections[0].outbound_auth_mode").value("HMAC_SHA256"))
                .andExpect(jsonPath("$.data.provider_connections[0].base_url").value(vendorStub.baseUrl()));
    }

    @Test
    void ea044HttpProviderRejectWhenVendorUnauthorized() throws Exception {
        vendorStub.setRejectAuth(true);
        mvc.perform(post("/documents/center/DOC_EA040_SIGN_FAIL/sign")
                        .headers(platformHeaders("EA044-BAD-SIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider_code":"ESIGN_HTTP"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGN_502"));
    }

    private HttpHeaders signatureHeaders(
            ContractSignCallbackRequest body,
            String idempotencyKey,
            String nonce,
            Instant timestamp) throws Exception {
        String payload = objectMapper.writeValueAsString(body);
        String ts = String.valueOf(timestamp.getEpochSecond());
        String signature = hmacSha256Hex(CALLBACK_SECRET, ts + "\n" + nonce + "\n" + payload);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Contract-Sign-Timestamp", ts);
        headers.add("X-Contract-Sign-Nonce", nonce);
        headers.add("X-Contract-Sign-Signature", signature);
        headers.add("X-Idempotency-Key", idempotencyKey);
        return headers;
    }

    private static String hmacSha256Hex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private HttpHeaders platformHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(platformToken());
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String platformToken() {
        return jwtService.generateToken(new UserContext(
                "U001", "platform_admin", OPERATOR_ID, PROJECT_ID, "ENT_PLATFORM_001", "ROLE_PLATFORM_ADMIN", "ID001"));
    }
}
