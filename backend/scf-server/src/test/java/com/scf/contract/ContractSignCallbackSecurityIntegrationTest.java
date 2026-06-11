package com.scf.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.contract.dto.ContractSignDtos.ContractSignCallbackRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "scf.contract.sign.callback-verification-mode=TIMESTAMP_NONCE_SIGNATURE",
        "scf.contract.sign.callback-token=mock-contract-sign-callback-token",
        "scf.contract.sign.callback-signature-window-seconds=300",
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
class ContractSignCallbackSecurityIntegrationTest {

    private static final String SECRET = "mock-contract-sign-callback-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ea041SignatureCallbackSuccess() throws Exception {
        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                "MOCK-SIGN-EA041-OK",
                "SUCCESS",
                Instant.parse("2026-06-01T10:00:00Z"),
                null,
                "MOCK");
        jdbcTemplate.update("""
                INSERT INTO scf.tr_contract_sign_task
                  (id, operator_id, project_id, document_id, provider_code, external_sign_ref, task_status, retry_count, created_by, created_at)
                VALUES
                  ('TASK_EA041_OK', 'OP001', 'PJ001', 'DOC_EA040_SIGN_OK', 'MOCK', 'MOCK-SIGN-EA041-OK', 'PENDING_CALLBACK', 0, 'U001', CURRENT_TIMESTAMP)
                """);

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA041-CB-OK", "nonce-ok", Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNED"));
    }

    @Test
    void ea041BadSignatureDoesNotEnterCompensationPool() throws Exception {
        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                "MOCK-SIGN-BAD-SIG",
                "SUCCESS",
                null,
                null,
                "MOCK");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Timestamp", String.valueOf(Instant.now().getEpochSecond()))
                        .header("X-Contract-Sign-Nonce", "nonce-bad")
                        .header("X-Contract-Sign-Signature", "bad-signature")
                        .header("X-Idempotency-Key", "EA041-CB-BAD-SIG")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));

        assertThat(compensationCount("MOCK-SIGN-BAD-SIG")).isZero();
    }

    @Test
    void ea041ValidSignedUnknownCallbackEntersCompensationPool() throws Exception {
        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                "MOCK-SIGN-UNKNOWN",
                "SUCCESS",
                null,
                null,
                "MOCK");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA041-CB-UNKNOWN", "nonce-unknown", Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));

        assertThat(compensationCount("MOCK-SIGN-UNKNOWN")).isEqualTo(1);
        String status = jdbcTemplate.queryForObject("""
                SELECT compensation_status FROM scf.biz_compensation_task
                WHERE business_type = 'CONTRACT_SIGN_CALLBACK' AND business_id = 'MOCK-SIGN-UNKNOWN'
                ORDER BY created_at DESC LIMIT 1
                """, String.class);
        assertThat(status).isEqualTo("MANUAL_REQUIRED");
    }

    @Test
    void ea044DuplicateCallbackNonceIsRejected() throws Exception {
        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                "MOCK-SIGN-NONCE-REPLAY",
                "SUCCESS",
                null,
                null,
                "MOCK");
        jdbcTemplate.update("""
                INSERT INTO scf.tr_contract_sign_task
                  (id, operator_id, project_id, document_id, provider_code, external_sign_ref, task_status, retry_count, created_by, created_at)
                VALUES
                  ('TASK_EA044_NONCE', 'OP001', 'PJ001', 'DOC_EA040_SIGN_OK', 'MOCK', 'MOCK-SIGN-NONCE-REPLAY', 'PENDING_CALLBACK', 0, 'U001', CURRENT_TIMESTAMP)
                """);

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA044-CB-NONCE-1", "nonce-replay-once", Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA044-CB-NONCE-2", "nonce-replay-once", Instant.now()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea041ExpiredTimestampIsRejectedBeforeCompensationPool() throws Exception {
        ContractSignCallbackRequest body = new ContractSignCallbackRequest(
                "MOCK-SIGN-EXPIRED",
                "SUCCESS",
                null,
                null,
                "MOCK");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .headers(signatureHeaders(body, "EA041-CB-EXPIRED", "nonce-expired", Instant.now().minusSeconds(600)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));

        assertThat(compensationCount("MOCK-SIGN-EXPIRED")).isZero();
    }

    private org.springframework.http.HttpHeaders signatureHeaders(
            ContractSignCallbackRequest body,
            String idempotencyKey,
            String nonce,
            Instant timestamp) throws Exception {
        String ts = String.valueOf(timestamp.getEpochSecond());
        String payload = objectMapper.writeValueAsString(body);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Contract-Sign-Timestamp", ts);
        headers.add("X-Contract-Sign-Nonce", nonce);
        headers.add("X-Contract-Sign-Signature", hmac(ts + "\n" + nonce + "\n" + payload));
        headers.add("X-Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private int compensationCount(String businessId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM scf.biz_compensation_task
                WHERE business_type = 'CONTRACT_SIGN_CALLBACK' AND business_id = ?
                """, Integer.class, businessId);
    }
}
