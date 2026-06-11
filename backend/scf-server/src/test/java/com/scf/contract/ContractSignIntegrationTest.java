package com.scf.contract;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {
        "/sql/permission_test_seed.sql",
        "/sql/finance_disburse_test_seed.sql",
        "/sql/finance_precheck_test_seed.sql",
        "/sql/contract_sign_test_seed.sql"
})
class ContractSignIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String CALLBACK_TOKEN = "mock-contract-sign-callback-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ea040InitiateSignAndCallbackSuccess() throws Exception {
        MvcResult signResult = mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNING"))
                .andExpect(jsonPath("$.data.external_sign_ref").exists())
                .andReturn();

        String externalRef = JsonPath.read(signResult.getResponse().getContentAsString(), "$.data.external_sign_ref");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA040-CB-OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(externalRef, "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNED"))
                .andExpect(jsonPath("$.data.contract_status").value("SIGNED"));

        mvc.perform(get("/documents/center/DOC_EA040_SIGN_OK/sign/tasks")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].task_status").value("SIGNED"));
    }

    @Test
    void ea040CallbackFailedThenRetrySuccess() throws Exception {
        MvcResult signResult = mvc.perform(post("/documents/center/DOC_EA040_SIGN_FAIL/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String externalRef = JsonPath.read(signResult.getResponse().getContentAsString(), "$.data.external_sign_ref");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA040-CB-FAIL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(externalRef, "FAILED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("FAILED"));

        mvc.perform(post("/documents/center/DOC_EA040_SIGN_FAIL/sign/retry")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNING"));

        String newRef = jdbcTemplate.queryForObject(
                "SELECT external_sign_ref FROM scf.tr_contract_sign_task WHERE document_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                "DOC_EA040_SIGN_FAIL");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA040-CB-RETRY-OK")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(newRef, "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sign_status").value("SIGNED"));
    }

    @Test
    void ea040ValidateBlocksUnsignedContract() throws Exception {
        mvc.perform(post("/documents/validate")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_id": "FIN_PRECHECK_NO_DOC",
                                  "business_stage": "DISBURSE",
                                  "product_type": "AGENCY_PURCHASE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false));
    }

    @Test
    void ea040SignRequiresApprovedReview() throws Exception {
        jdbcTemplate.update("""
                UPDATE scf.tr_document
                SET review_status = 'PENDING', contract_status = 'DRAFT', sign_status = 'NOT_REQUIRED'
                WHERE id = 'DOC_EA040_SIGN_OK'
                """);

        mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void ea040DuplicateSignWhileSigningReturns409() throws Exception {
        mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void ea040ProviderSubmitFailurePersistsFailureState() throws Exception {
        mvc.perform(post("/documents/center/DOC_EA040_SIGN_FAIL/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "simulate_failure": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONTRACT_SIGN_409"));

        String signStatus = jdbcTemplate.queryForObject(
                "SELECT sign_status FROM scf.tr_document WHERE id = ?",
                String.class,
                "DOC_EA040_SIGN_FAIL");
        String taskStatus = jdbcTemplate.queryForObject(
                "SELECT task_status FROM scf.tr_contract_sign_task WHERE document_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                "DOC_EA040_SIGN_FAIL");

        org.assertj.core.api.Assertions.assertThat(signStatus).isEqualTo("FAILED");
        org.assertj.core.api.Assertions.assertThat(taskStatus).isEqualTo("FAILED");
    }

    @Test
    void ea040CallbackRequiresToken() throws Exception {
        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Idempotency-Key", "EA040-CB-AUTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("MOCK-SIGN-NOPE", "SUCCESS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea040CallbackIsIdempotent() throws Exception {
        MvcResult signResult = mvc.perform(post("/documents/center/DOC_EA040_SIGN_OK/sign")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .header("X-Request-Id", "REQ-EA040-IDEMP-SIGN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String externalRef = JsonPath.read(signResult.getResponse().getContentAsString(), "$.data.external_sign_ref");

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA040-CB-IDEMP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(externalRef, "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").doesNotExist());

        mvc.perform(post("/integrations/contracts/sign-callback")
                        .header("X-Contract-Sign-Callback-Token", CALLBACK_TOKEN)
                        .header("X-Idempotency-Key", "EA040-CB-IDEMP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(externalRef, "SUCCESS")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));
    }

    private HttpHeaders headers(String token, String projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-EA040");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        return headers;
    }

    private String callbackBody(String externalRef, String status) {
        return """
                {
                  "external_sign_ref": "%s",
                  "callback_status": "%s",
                  "signed_at": "2026-06-01T10:00:00Z"
                }
                """.formatted(externalRef, status);
    }

    private String platformToken() {
        return token("U001", "platform_admin", "ENT_PLATFORM_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }
}
