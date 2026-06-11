package com.scf.contract;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.contract.provider.MockContractSignProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "scf.contract.sign.callback-verification-mode=TOKEN",
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
class ContractSignReconciliationIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String CALLBACK_TOKEN = "mock-contract-sign-callback-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MockContractSignProvider mockProvider;

    @BeforeEach
    void resetDocuments() {
        jdbcTemplate.update("""
                DELETE FROM scf.tr_contract_sign_task
                WHERE document_id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL')
                """);
        jdbcTemplate.update("""
                UPDATE scf.tr_document
                SET sign_status = 'PENDING', contract_status = 'PENDING_SIGN',
                    sign_provider = NULL, external_sign_ref = NULL
                WHERE id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL')
                """);
        jdbcTemplate.update("""
                DELETE FROM scf.biz_compensation_task
                WHERE business_type = 'CONTRACT_SIGN_CALLBACK'
                """);
    }

    @Test
    void ea043ReconciliationSmoke() throws Exception {
        // 1. 发起签署
        MvcResult signResult = mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(platformHeaders("EA043-SIGN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNING"))
                .andReturn();
        String externalRef = JsonPath.read(signResult.getResponse().getContentAsString(), "$.data.external_sign_ref");

        // 2. 成功回调
        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA043-CB-OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(externalRef, "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNED"));

        // 3. 失败回调（另一单证）
        MvcResult failSign = mvc.perform(post("/documents/center/DOC_EA040_SIGN_FAIL/sign")
                        .headers(platformHeaders("EA043-SIGN-FAIL"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String failRef = JsonPath.read(failSign.getResponse().getContentAsString(), "$.data.external_sign_ref");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA043-CB-FAIL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(failRef, "FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("FAILED"));

        // 4. 未知回调入池
        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA043-CB-UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("MOCK-SIGN-EA043-UNKNOWN", "SUCCESS")))
                .andExpect(status().isNotFound());

        String compensationId = jdbcTemplate.queryForObject("""
                SELECT id FROM scf.biz_compensation_task
                WHERE business_type = 'CONTRACT_SIGN_CALLBACK'
                  AND business_id = 'MOCK-SIGN-EA043-UNKNOWN'
                ORDER BY created_at DESC LIMIT 1
                """, String.class);
        assertThat(compensationId).isNotBlank();

        // 5. 主动查单（未知单号，无本地任务）
        mvc.perform(post("/saga/ops/compensation-tasks/" + compensationId + "/query-sign-status")
                        .headers(platformHeaders("EA043-QUERY-UNKNOWN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualReasonJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(false))
                .andExpect(jsonPath("$.data.provider.provider_status").value("UNKNOWN"));

        // 6. 忽略补偿
        mvc.perform(post("/saga/ops/compensation-tasks/" + compensationId + "/ignore")
                        .headers(platformHeaders("EA043-IGNORE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        assertThat(compensationStatus(compensationId)).isEqualTo("IGNORED");

        // 7. 主动查单对账：待回调任务 + 供应商已成功
        resetDocForQuery("DOC_EA040_SIGN_OK");
        MvcResult pendingSign = mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(platformHeaders("EA043-SIGN-QUERY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String queryRef = JsonPath.read(pendingSign.getResponse().getContentAsString(), "$.data.external_sign_ref");
        mockProvider.simulateProviderStatus(queryRef, "SUCCESS");

        mvc.perform(get("/integrations/contracts/sign/by-ref/" + queryRef)
                        .headers(platformHeaders("EA043-LOOKUP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.external_sign_ref").value(queryRef));

        mvc.perform(post("/integrations/contracts/sign/by-ref/" + queryRef + "/query-status")
                        .headers(platformHeaders("EA043-QUERY-RECON"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reconcile": true, "reason": "EA043 smoke reconcile"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reconciled").value(true))
                .andExpect(jsonPath("$.data.document.sign_status").value("SIGNED"));

        // 8. 关闭另一补偿场景：模拟入池后关闭
        jdbcTemplate.update("""
                DELETE FROM scf.biz_compensation_task
                WHERE business_id = 'MOCK-SIGN-EA043-CLOSE'
                """);
        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA043-CB-CLOSE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("MOCK-SIGN-EA043-CLOSE", "SUCCESS")))
                .andExpect(status().isNotFound());

        String closeTaskId = jdbcTemplate.queryForObject("""
                SELECT id FROM scf.biz_compensation_task
                WHERE business_id = 'MOCK-SIGN-EA043-CLOSE'
                ORDER BY created_at DESC LIMIT 1
                """, String.class);

        mvc.perform(post("/saga/ops/compensation-tasks/" + closeTaskId + "/close")
                        .headers(platformHeaders("EA043-CLOSE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        assertThat(compensationStatus(closeTaskId)).isEqualTo("CLOSED");
    }

    @Test
    void ea043StatusQueryRequiresPermission() throws Exception {
        mvc.perform(get("/integrations/contracts/sign/by-ref/MOCK-SIGN-NOPE")
                        .headers(memberHeaders("EA043-PERM")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    private void resetDocForQuery(String documentId) {
        jdbcTemplate.update("""
                DELETE FROM scf.tr_contract_sign_task WHERE document_id = ?
                """, documentId);
        jdbcTemplate.update("""
                UPDATE scf.tr_document
                SET sign_status = 'PENDING', contract_status = 'PENDING_SIGN',
                    sign_provider = NULL, external_sign_ref = NULL, review_status = 'APPROVED'
                WHERE id = ?
                """, documentId);
    }

    private String compensationStatus(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT compensation_status FROM scf.biz_compensation_task WHERE id = ?",
                String.class,
                taskId);
    }

    private String callbackBody(String externalRef, String status) {
        return """
                {
                  "external_sign_ref": "%s",
                  "callback_status": "%s",
                  "signed_at": "2026-06-01T10:00:00Z",
                  "provider_code": "MOCK"
                }
                """.formatted(externalRef, status);
    }

    private String manualReasonJson() {
        return """
                {"reason": "EA043 manual handling reason text"}
                """;
    }

    private HttpHeaders platformHeaders(String requestId) {
        return headers(platformToken(), requestId);
    }

    private HttpHeaders memberHeaders(String requestId) {
        return headers(memberToken(), requestId);
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String platformToken() {
        return token("U001", "platform_admin", "ENT_PLATFORM_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private String memberToken() {
        return token("U003", "member_user", "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }
}
