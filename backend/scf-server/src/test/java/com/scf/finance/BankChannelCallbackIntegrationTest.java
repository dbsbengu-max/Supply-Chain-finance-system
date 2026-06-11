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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/bank_channel_callback_test_seed.sql"})
class BankChannelCallbackIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";
    private static final String BANK_TOKEN = "mock-bank-callback-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void bankTransferDisburseStaysPendingUntilCallback() throws Exception {
        resetFinanceForBankTest();

        MvcResult result = mvc.perform(post("/finance/applications/FIN_BANK_OK/disburse")
                        .headers(disburseHeaders("BANK-DISB-PENDING"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody("200000", "2026-06-30", "BANK_TRANSFER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TO_DISBURSE"))
                .andExpect(jsonPath("$.data.disbursement_status").value("PENDING"))
                .andExpect(jsonPath("$.data.channel_request_id").exists())
                .andReturn();

        String channelRequestId = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.channel_request_id");

        assertEquals("TO_DISBURSE", financeStatus("FIN_BANK_OK"));
        assertEquals("10000000.00", accountBalance("ACC_FUNDING_001").toPlainString());
        assertEquals("0.00", accountBalance("ACC_MEMBER_001").toPlainString());
        assertEquals(0, bankFlowCount());

        mvc.perform(post("/integrations/bank/disburse-callback")
                        .header("X-Bank-Callback-Token", BANK_TOKEN)
                        .header("X-Idempotency-Key", "BANK-CB-OK-1")
                        .header("X-Request-Id", "REQ-BANK-CB-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(channelRequestId, "SUCCESS", "BANK-EXT-001", "200000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursement_status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.finance_status").value("DISBURSED"));

        assertEquals("DISBURSED", financeStatus("FIN_BANK_OK"));
        assertEquals("9800000.00", accountBalance("ACC_FUNDING_001").toPlainString());
        assertEquals("200000.00", accountBalance("ACC_MEMBER_001").toPlainString());
        assertEquals(2, bankFlowCount());
    }

    @Test
    void callbackSuccessIsIdempotent() throws Exception {
        String channelRequestId = submitBankDisburse("BANK-DISB-IDEMP", "200000");

        mvc.perform(post("/integrations/bank/disburse-callback")
                        .header("X-Bank-Callback-Token", BANK_TOKEN)
                        .header("X-Idempotency-Key", "BANK-CB-IDEMP")
                        .header("X-Request-Id", "REQ-BANK-CB-IDEMP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(channelRequestId, "SUCCESS", "BANK-EXT-IDEMP", "200000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").doesNotExist());

        mvc.perform(post("/integrations/bank/disburse-callback")
                        .header("X-Bank-Callback-Token", BANK_TOKEN)
                        .header("X-Idempotency-Key", "BANK-CB-IDEMP")
                        .header("X-Request-Id", "REQ-BANK-CB-IDEMP-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(channelRequestId, "SUCCESS", "BANK-EXT-IDEMP", "200000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));

        assertEquals(2, bankFlowCount());
    }

    @Test
    void callbackFailedKeepsFinanceToDisburse() throws Exception {
        resetFinanceForBankTest();

        String channelRequestId = submitBankDisburse("BANK-DISB-FAIL", "200000");

        mvc.perform(post("/integrations/bank/disburse-callback")
                        .header("X-Bank-Callback-Token", BANK_TOKEN)
                        .header("X-Idempotency-Key", "BANK-CB-FAIL")
                        .header("X-Request-Id", "REQ-BANK-CB-FAIL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(channelRequestId, "FAILED", "BANK-EXT-FAIL", "200000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbursement_status").value("FAILED"))
                .andExpect(jsonPath("$.data.finance_status").value("TO_DISBURSE"));

        assertEquals("TO_DISBURSE", financeStatus("FIN_BANK_OK"));
        assertEquals("10000000.00", accountBalance("ACC_FUNDING_001").toPlainString());
        assertEquals(0, bankFlowCount());
    }

    @Test
    void invalidCallbackTokenIsRejected() throws Exception {
        mvc.perform(post("/integrations/bank/disburse-callback")
                        .header("X-Bank-Callback-Token", "wrong-token")
                        .header("X-Idempotency-Key", "BANK-CB-AUTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody("BANK-REQ-NOPE", "SUCCESS", "BANK-EXT-AUTH", "200000.00")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    private String submitBankDisburse(String idempotencyKey, String amount) throws Exception {
        resetFinanceForBankTest();
        MvcResult result = mvc.perform(post("/finance/applications/FIN_BANK_OK/disburse")
                        .headers(disburseHeaders(idempotencyKey))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(disburseBody(amount, "2026-06-30", "BANK_TRANSFER")))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.channel_request_id");
    }

    private void resetFinanceForBankTest() {
        jdbcTemplate.update("DELETE FROM acct_bank_flow WHERE source_id IN (SELECT id FROM fn_disbursement WHERE finance_id = 'FIN_BANK_OK')");
        jdbcTemplate.update("DELETE FROM fn_disbursement WHERE finance_id = 'FIN_BANK_OK'");
        jdbcTemplate.update("DELETE FROM biz_event_outbox WHERE business_type = 'FINANCE_APPLICATION' AND business_id = 'FIN_BANK_OK'");
        jdbcTemplate.update(
                "DELETE FROM idempotency_record WHERE idempotency_key LIKE 'BANK-CB-%' OR idempotency_key LIKE 'BANK-DISB-%'");
        jdbcTemplate.update("UPDATE fn_finance_application SET finance_status = 'TO_DISBURSE', disbursed_amount = 0 WHERE id = 'FIN_BANK_OK'");
        jdbcTemplate.update("UPDATE acct_virtual_account SET balance = 10000000.00 WHERE id = 'ACC_FUNDING_001'");
        jdbcTemplate.update("UPDATE acct_virtual_account SET balance = 0.00 WHERE id = 'ACC_MEMBER_001'");
    }

    private HttpHeaders disburseHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fundingToken());
        headers.add("X-Request-Id", "REQ-BANK-DISB");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        headers.add("X-Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String fundingToken() {
        return jwtService.generateToken(new UserContext(
                "U002", "funding_user", OPERATOR_ID, PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002"));
    }

    private String disburseBody(String amount, String valueDate, String channel) {
        return """
                {
                  "disburse_amount": "%s",
                  "currency": "CNY",
                  "value_date": "%s",
                  "payer_account_id": "ACC_FUNDING_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "funding_channel": "%s",
                  "remark": "bank channel test"
                }
                """.formatted(amount, valueDate, channel);
    }

    private String callbackBody(
            String channelRequestId, String status, String externalFlowNo, String amount) {
        return """
                {
                  "channel_request_id": "%s",
                  "callback_status": "%s",
                  "external_flow_no": "%s",
                  "amount": "%s",
                  "currency": "CNY",
                  "flow_time": "2026-06-30T10:00:00Z",
                  "counterparty_name": "成员企业收款户",
                  "counterparty_account": "6222****0001"
                }
                """.formatted(channelRequestId, status, externalFlowNo, amount);
    }

    private String financeStatus(String financeId) {
        return jdbcTemplate.queryForObject(
                "SELECT finance_status FROM scf.fn_finance_application WHERE id = ?",
                String.class,
                financeId);
    }

    private BigDecimal accountBalance(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM scf.acct_virtual_account WHERE id = ?",
                BigDecimal.class,
                accountId);
    }

    private int bankFlowCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM scf.acct_bank_flow
                WHERE source_type = 'DISBURSEMENT'
                  AND source_id IN (SELECT id FROM scf.fn_disbursement WHERE finance_id = 'FIN_BANK_OK')
                """,
                Integer.class);
        return count == null ? 0 : count;
    }
}
