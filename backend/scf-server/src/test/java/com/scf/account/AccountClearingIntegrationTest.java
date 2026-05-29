package com.scf.account;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/clearing_test_seed.sql"})
class AccountClearingIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void importMatchCalculateAndExecuteClearing() throws Exception {
        MvcResult importResult = mvc.perform(post("/accounts/bank-flows/import")
                        .headers(fundingHeaders("CLR-REQ-IMPORT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flows": [{
                                    "account_id": "ACC_REPAY_001",
                                    "external_flow_no": "CLR-TEST-001",
                                    "amount": "120000.00",
                                    "currency": "CNY",
                                    "counterparty_name": "百农汇",
                                    "counterparty_account": "6222000000000001",
                                    "flow_time": "2026-06-01T10:00:00Z"
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].match_status").value("UNMATCHED"))
                .andReturn();

        String flowId = JsonPath.read(importResult.getResponse().getContentAsString(), "$.data[0].id");
        assertEquals("120000.00", accountBalance("ACC_REPAY_001").toPlainString());

        mvc.perform(post("/accounts/bank-flows/" + flowId + "/match")
                        .headers(fundingHeaders("CLR-REQ-MATCH"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finance_id": "FIN_CLEAR_OK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.match_status").value("MATCHED"));

        mvc.perform(get("/accounts/clearing/entry")
                        .param("finance_id", "FIN_CLEAR_OK")
                        .headers(fundingHeaders("CLR-REQ-ENTRY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance_id").value("FIN_CLEAR_OK"))
                .andExpect(jsonPath("$.data.clearing_rules[0].id").value("CLR_RULE_001"));

        mvc.perform(post("/accounts/clearing/calculate")
                        .headers(fundingHeaders("CLR-REQ-CALC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repayment_amount").value("120000.00"))
                .andExpect(jsonPath("$.data.allocation.interest_amount").value("4000.00"))
                .andExpect(jsonPath("$.data.allocation.principal_amount").value("116000.00"))
                .andExpect(jsonPath("$.data.allocation.remaining_amount").value("0.00"));

        mvc.perform(post("/accounts/clearing/execute")
                        .headers(fundingHeaders("CLR-EXEC-OK"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance_status").value("REPAYING"))
                .andExpect(jsonPath("$.data.allocation.principal_amount").value("116000.00"));

        assertEquals("REPAYING", financeStatus("FIN_CLEAR_OK"));
        assertEquals(1, repaymentCount(flowId));

        mvc.perform(post("/accounts/clearing/execute")
                        .headers(fundingHeaders("CLR-EXEC-OK"))
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));

        assertEquals(1, repaymentCount(flowId));
    }

    @Test
    void listBankFlowsRequiresPermission() throws Exception {
        mvc.perform(get("/accounts/bank-flows")
                        .headers(fundingHeaders("CLR-REQ-LIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    private String clearingBody(String flowId) {
        return """
                {
                  "finance_id": "FIN_CLEAR_OK",
                  "bank_flow_id": "%s",
                  "clearing_rule_id": "CLR_RULE_001"
                }
                """.formatted(flowId);
    }

    private HttpHeaders fundingHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(fundingToken());
        headers.set("X-Operator-Id", OPERATOR_ID);
        headers.set("X-Project-Id", PROJECT_ID);
        headers.set("X-Request-Id", requestId);
        headers.set("X-Idempotency-Key", requestId);
        return headers;
    }

    private String fundingToken() {
        return jwtService.generateToken(new UserContext(
                "U002", "funding_user", OPERATOR_ID, PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002"));
    }

    private java.math.BigDecimal accountBalance(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM scf.acct_virtual_account WHERE id = ?",
                java.math.BigDecimal.class,
                accountId);
    }

    private String financeStatus(String financeId) {
        return jdbcTemplate.queryForObject(
                "SELECT finance_status FROM scf.fn_finance_application WHERE id = ?",
                String.class,
                financeId);
    }

    private int repaymentCount(String flowId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scf.fn_repayment WHERE bank_flow_id = ? AND repayment_status = 'EXECUTED'",
                Integer.class,
                flowId);
        return count == null ? 0 : count;
    }
}
