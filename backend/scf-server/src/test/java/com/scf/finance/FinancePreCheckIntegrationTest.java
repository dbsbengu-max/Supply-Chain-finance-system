package com.scf.finance;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {
        "/sql/permission_test_seed.sql",
        "/sql/finance_disburse_test_seed.sql",
        "/sql/finance_precheck_test_seed.sql"
})
class FinancePreCheckIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Test
    void ea039PreCheckAllPassed() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_OK/pre-check")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preCheckBody("80000", "2026-06-30", "EA039-OK-KEY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.checks[?(@.code=='DOCUMENTS')].result").value("PASSED"))
                .andExpect(jsonPath("$.data.checks[?(@.code=='CREDIT_AVAILABLE')].result").value("PASSED"));
    }

    @Test
    void ea039PreCheckFailsOnMissingDocuments() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_NO_DOC/pre-check")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preCheckBody("90000", "2026-06-30", "EA039-NODOC-KEY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.checks[?(@.code=='DOCUMENTS')].result").value("FAILED"));
    }

    @Test
    void ea039PreCheckFailsOnInsufficientCredit() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_LOW_CREDIT/pre-check")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preCheckBody("500000", "2026-06-30", "EA039-CREDIT-KEY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.checks[?(@.code=='CREDIT_AVAILABLE')].result").value("FAILED"));
    }

    @Test
    void ea039PreCheckFailsOnInsufficientBalance() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_LOW_BAL/pre-check")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preCheckBodyLowBalance("70000", "2026-06-30", "EA039-BAL-KEY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.checks[?(@.code=='PAYER_ACCOUNT')].result").value("FAILED"));
    }

    @Test
    void ea039DisburseBlockedWhenPreCheckFails() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_NO_DOC/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA039-DISB-BLOCK"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("90000", "2026-06-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_PRECHECK_409"));
    }

    @Test
    void ea039MemberCannotRunPreCheck() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_OK/pre-check")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea039PreCheckWarnsWithoutSecondaryAuth() throws Exception {
        mvc.perform(post("/finance/applications/FIN_PRECHECK_OK/pre-check")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preCheckBodyWithoutSecondary("80000", "2026-06-30", "EA039-NO-2FA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.checks[?(@.code=='SECONDARY_AUTH')].result").value("WARNING"));
    }

    private HttpHeaders headers(String token, String projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-EA039");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        return headers;
    }

    private HttpHeaders headers(String token, String projectId, String idempotencyKey) {
        HttpHeaders headers = headers(token, projectId);
        headers.add("X-Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String preCheckBody(String amount, String valueDate, String idempotencyKey) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "INTERNAL_ACCOUNT",
                  "idempotency_key": "%s",
                  "secondary_auth_token": "%s"
                }
                """.formatted(amount, valueDate, idempotencyKey, SECONDARY_OK);
    }

    private String preCheckBodyWithoutSecondary(String amount, String valueDate, String idempotencyKey) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "INTERNAL_ACCOUNT",
                  "idempotency_key": "%s"
                }
                """.formatted(amount, valueDate, idempotencyKey);
    }

    private String preCheckBodyLowBalance(String amount, String valueDate, String idempotencyKey) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_LOW",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "INTERNAL_ACCOUNT",
                  "idempotency_key": "%s",
                  "secondary_auth_token": "%s"
                }
                """.formatted(amount, valueDate, idempotencyKey, SECONDARY_OK);
    }

    private String disburseBody(String amount, String valueDate) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "INTERNAL_ACCOUNT"
                }
                """.formatted(amount, valueDate);
    }

    private String fundingToken() {
        return token("U002", "funding_user", "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private String memberToken() {
        return token("U003", "member_user", "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }
}
