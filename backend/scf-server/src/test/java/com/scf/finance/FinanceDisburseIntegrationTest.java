package com.scf.finance;

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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/finance_disburse_test_seed.sql"})
class FinanceDisburseIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String OTHER_PROJECT_ID = "PJ_TEST_OTHER";
    private static final String SECONDARY_OK = "MOCK-APPROVED";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ea015FundingCanDisburseWithIdempotencyAndSecondaryAuth() throws Exception {
        MvcResult result = mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-OK"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBURSED"))
                .andExpect(jsonPath("$.data.disburse_amount").value("120000"))
                .andExpect(jsonPath("$.data.idempotency_key").value("EA015-OK"))
                .andReturn();

        String disbursementId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.disbursement_id");
        assertEquals(1, disbursementCount("FIN_DISB_OK"));
        assertEquals("DISBURSED", financeStatus("FIN_DISB_OK"));
        assertEquals("120000.00", disbursedAmount("FIN_DISB_OK").toPlainString());
        assertTrue(disbursementId != null && !disbursementId.isBlank());
        assertEquals("9880000.00", accountBalance("ACC_FUNDING_001").toPlainString());
        assertEquals("120000.00", accountBalance("ACC_MEMBER_001").toPlainString());
    }

    @Test
    void ea015SameIdempotencyKeyAndSameBodyReturnsReplayWithoutSecondDisbursement() throws Exception {
        MvcResult first = mvc.perform(post("/finance/applications/FIN_DISB_IDEMP/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-IDEMP"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("130000", "2026-06-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").doesNotExist())
                .andReturn();

        MvcResult second = mvc.perform(post("/finance/applications/FIN_DISB_IDEMP/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-IDEMP"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("130000", "2026-06-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true))
                .andReturn();

        String firstDisbursementId = JsonPath.read(first.getResponse().getContentAsString(), "$.data.disbursement_id");
        String secondDisbursementId = JsonPath.read(second.getResponse().getContentAsString(), "$.data.disbursement_id");
        assertEquals(firstDisbursementId, secondDisbursementId);
        assertEquals(1, disbursementCount("FIN_DISB_IDEMP"));
        assertEquals(1, idempotencySuccessCount("EA015-IDEMP"));
    }

    @Test
    void ea015SameIdempotencyKeyAndDifferentBodyIsRejected() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_CONFLICT/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-CONFLICT"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("140000", "2026-06-30")))
                .andExpect(status().isOk());

        mvc.perform(post("/finance/applications/FIN_DISB_CONFLICT/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-CONFLICT"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("140000", "2026-07-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_409"));
    }

    @Test
    void ea015MemberCannotExecuteDisburse() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(memberToken(), PROJECT_ID, "EA015-MEMBER-FORBIDDEN"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea015MissingIdempotencyKeyIsRejected() throws Exception {
        HttpHeaders headers = headers(fundingToken(), PROJECT_ID, null);
        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers)
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
    }

    @Test
    void ea015MissingSecondaryAuthIsRejected() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-NO-SECONDARY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
    }

    @Test
    void ea015InvalidSecondaryAuthIsRejected() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-BAD-SECONDARY"))
                        .header("X-Secondary-Auth-Token", "BAD")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea015OnlyToDisburseStateCanDisburse() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_STATE/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-STATE"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("150000", "2026-06-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void ea015PartialDisburseIsRejectedInV11Mvp() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_PARTIAL/disburse")
                        .headers(headers(fundingToken(), PROJECT_ID, "EA015-PARTIAL"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("80000", "2026-06-30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
        assertEquals("TO_DISBURSE", financeStatus("FIN_DISB_PARTIAL"));
        assertEquals(0, disbursementCount("FIN_DISB_PARTIAL"));
    }

    @Test
    void ea015CrossProjectDisburseIsHiddenAndHeaderMismatchIsForbidden() throws Exception {
        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(fundingToken(), OTHER_PROJECT_ID, "EA015-HEADER-MISMATCH"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));

        mvc.perform(post("/finance/applications/FIN_DISB_OK/disburse")
                        .headers(headers(fundingOtherProjectToken(), OTHER_PROJECT_ID, "EA015-CROSS-PROJECT"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("120000", "2026-06-30")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));
    }

    private HttpHeaders headers(String token, String projectId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-EA015");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        if (idempotencyKey != null) {
            headers.add("X-Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    private String fundingToken() {
        return token("U002", "funding_user", PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private String fundingOtherProjectToken() {
        return token("U002", "funding_user", OTHER_PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private String memberToken() {
        return token("U003", "member_user", PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String token(String userId, String loginName, String projectId, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId,
                loginName,
                OPERATOR_ID,
                projectId,
                enterpriseId,
                roleId,
                identityId
        ));
    }

    private String disburseBody(String amount, String valueDate) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "INTERNAL_ACCOUNT",
                  "remark": "EA-015 disburse acceptance"
                }
                """.formatted(amount, valueDate);
    }

    private int disbursementCount(String financeId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scf.fn_disbursement WHERE finance_id = ?",
                Integer.class,
                financeId);
    }

    private int idempotencySuccessCount(String idempotencyKey) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM scf.idempotency_record WHERE idempotency_key = ? AND status = 'SUCCESS'",
                Integer.class,
                idempotencyKey);
    }

    private String financeStatus(String financeId) {
        return jdbcTemplate.queryForObject(
                "SELECT finance_status FROM scf.fn_finance_application WHERE id = ?",
                String.class,
                financeId);
    }

    private BigDecimal disbursedAmount(String financeId) {
        return jdbcTemplate.queryForObject(
                "SELECT disbursed_amount FROM scf.fn_finance_application WHERE id = ?",
                BigDecimal.class,
                financeId);
    }

    private BigDecimal accountBalance(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM scf.acct_virtual_account WHERE id = ?",
                BigDecimal.class,
                accountId);
    }
}
