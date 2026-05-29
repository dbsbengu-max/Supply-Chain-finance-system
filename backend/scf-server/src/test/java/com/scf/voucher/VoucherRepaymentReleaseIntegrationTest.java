package com.scf.voucher;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.saga.entity.BizEventOutbox;
import com.scf.saga.repository.BizEventOutboxRepository;
import com.scf.saga.service.OutboxDispatcher;
import com.scf.saga.service.SagaEventHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/voucher_release_test_seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VoucherRepaymentReleaseIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";
    private static final String VOUCHER_ID = "VOUCHER_REPAY_REL";
    private static final String FINANCE_ID = "FIN_REPAY_RELEASE";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxDispatcher outboxDispatcher;

    @Autowired
    SagaEventHandler sagaEventHandler;

    @Autowired
    BizEventOutboxRepository outboxRepository;

    @Test
    void ea025ClearingExecuteReleasesVoucherLockViaSaga() throws Exception {
        assertEquals(new BigDecimal("200000.00"), lockedAmount(VOUCHER_ID));
        assertEquals(new BigDecimal("300000.00"), availableAmount(VOUCHER_ID));

        String flowId = importAndMatchRepaymentFlow("204000.00", "EA025-FLOW-SETTLE");

        mvc.perform(post("/accounts/clearing/execute")
                        .headers(fundingHeaders("EA025-CLR-EXEC"))
                        .header("X-Idempotency-Key", "EA025-CLR-EXEC")
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance_status").value("SETTLED"));

        outboxDispatcher.dispatchPendingEvents();

        assertEquals(new BigDecimal("0.00"), lockedAmount(VOUCHER_ID));
        assertEquals(new BigDecimal("500000.00"), availableAmount(VOUCHER_ID));
        assertEquals("ACCEPTED", voucherStatus(VOUCHER_ID));
        assertTrue(releaseFlowCount(VOUCHER_ID) >= 1);
        assertTrue(auditCount("VOUCHER_RELEASE", VOUCHER_ID) >= 1);

        mvc.perform(get("/vouchers/" + VOUCHER_ID).headers(fundingHeaders("EA025-DETAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finance_summary.finance_occupied_amount").value("0"))
                .andExpect(jsonPath("$.data.finance_summary.released_amount").value("200000"))
                .andExpect(jsonPath("$.data.finance_summary.pending_redeem_amount").value("500000"));
    }

    @Test
    void ea025DuplicateSagaDispatchDoesNotDoubleRelease() throws Exception {
        String flowId = importAndMatchRepaymentFlow("204000.00", "EA025-FLOW-DUP");

        mvc.perform(post("/accounts/clearing/execute")
                        .headers(fundingHeaders("EA025-CLR-DUP"))
                        .header("X-Idempotency-Key", "EA025-CLR-DUP")
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk());

        outboxDispatcher.dispatchPendingEvents();
        BigDecimal availableAfterFirst = availableAmount(VOUCHER_ID);
        int releaseFlowsAfterFirst = releaseFlowCount(VOUCHER_ID);

        String repaymentId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.fn_repayment WHERE finance_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                FINANCE_ID);
        BizEventOutbox event = outboxRepository.findAll().stream()
                .filter(e -> ("REPAYMENT-SETTLED-" + repaymentId).equals(e.getIdempotencyKey()))
                .findFirst()
                .orElseThrow();
        sagaEventHandler.handle(event);

        assertEquals(availableAfterFirst, availableAmount(VOUCHER_ID));
        assertEquals(releaseFlowsAfterFirst, releaseFlowCount(VOUCHER_ID));
    }

    @Test
    void ea025CalculateOnlyMustNotReleaseVoucherLock() throws Exception {
        String flowId = importAndMatchRepaymentFlow("204000.00", "EA025-FLOW-CALC");

        mvc.perform(post("/accounts/clearing/calculate")
                        .headers(fundingHeaders("EA025-CLR-CALC"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearingBody(flowId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allocation.principal_amount").value("200000.00"));

        assertEquals(new BigDecimal("200000.00"), lockedAmount(VOUCHER_ID));
        assertEquals(new BigDecimal("300000.00"), availableAmount(VOUCHER_ID));
        assertEquals(0, repaymentSettledOutboxCount());
    }

    @Test
    void ea025CrossEnterpriseCannotViewVoucherDetail() throws Exception {
        mvc.perform(get("/vouchers/" + VOUCHER_ID).headers(otherMemberHeaders("EA025-CROSS")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    private String importAndMatchRepaymentFlow(String amount, String externalFlowNo) throws Exception {
        MvcResult importResult = mvc.perform(post("/accounts/bank-flows/import")
                        .headers(fundingHeaders("EA025-IMPORT-" + externalFlowNo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flows": [{
                                    "account_id": "ACC_REPAY_001",
                                    "external_flow_no": "%s",
                                    "amount": "%s",
                                    "currency": "CNY",
                                    "counterparty_name": "百农汇",
                                    "counterparty_account": "6222000000000001",
                                    "flow_time": "2026-06-02T10:00:00Z"
                                  }]
                                }
                                """.formatted(externalFlowNo, amount)))
                .andExpect(status().isOk())
                .andReturn();

        String flowId = JsonPath.read(importResult.getResponse().getContentAsString(), "$.data[0].id");
        mvc.perform(post("/accounts/bank-flows/" + flowId + "/match")
                        .headers(fundingHeaders("EA025-MATCH-" + externalFlowNo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finance_id": "%s"}
                                """.formatted(FINANCE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.match_status").value("MATCHED"));
        return flowId;
    }

    private String clearingBody(String flowId) {
        return """
                {
                  "finance_id": "%s",
                  "bank_flow_id": "%s",
                  "clearing_rule_id": "CLR_RULE_001"
                }
                """.formatted(FINANCE_ID, flowId);
    }

    private HttpHeaders fundingHeaders(String requestId) {
        return headers(fundingToken(), requestId);
    }

    private HttpHeaders otherMemberHeaders(String requestId) {
        return headers(otherMemberToken(), requestId);
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String fundingToken() {
        return jwtService.generateToken(new UserContext(
                "U002", "funding_user", OPERATOR_ID, PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002"));
    }

    private String otherMemberToken() {
        return jwtService.generateToken(new UserContext(
                "U005", "member_b_user", OPERATOR_ID, PROJECT_ID, "ENT_MEMBER_002", "ROLE_MEMBER", "ID005"));
    }

    private BigDecimal lockedAmount(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_amount FROM scf.dv_voucher WHERE id = ?",
                BigDecimal.class,
                voucherId);
    }

    private BigDecimal availableAmount(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_amount FROM scf.dv_voucher WHERE id = ?",
                BigDecimal.class,
                voucherId);
    }

    private String voucherStatus(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT voucher_status FROM scf.dv_voucher WHERE id = ?",
                String.class,
                voucherId);
    }

    private int releaseFlowCount(String voucherId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.dv_voucher_flow
                        WHERE voucher_id = ? AND flow_type = 'FINANCE_RELEASE'
                        """,
                Integer.class,
                voucherId);
        return count == null ? 0 : count;
    }

    private int repaymentSettledOutboxCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.biz_event_outbox
                        WHERE event_type = 'REPAYMENT_SETTLED'
                        """,
                Integer.class);
        return count == null ? 0 : count;
    }

    private int auditCount(String action, String objectId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.audit_operation_log
                        WHERE action = ? AND object_type = 'VOUCHER' AND object_id = ?
                        """,
                Integer.class,
                action,
                objectId);
        return count == null ? 0 : count;
    }
}
