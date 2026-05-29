package com.scf.voucher;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.saga.service.OutboxDispatcher;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/voucher_integration_test_seed.sql"})
class VoucherIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxDispatcher outboxDispatcher;

    @Test
    void ea024MemberCanCreateIssueTransferSplitAndRedeemWithAudit() throws Exception {
        MvcResult createResult = mvc.perform(post("/vouchers")
                        .headers(memberHeaders("EA024-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptor_id": "ENT_CORE_001",
                                  "amount": "100000",
                                  "currency": "CNY",
                                  "due_date": "2026-09-30"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("DRAFT"))
                .andReturn();

        String voucherId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");
        assertTrue(auditCount("VOUCHER_CREATE", voucherId) >= 1);

        mvc.perform(post("/vouchers/" + voucherId + "/issue").headers(memberHeaders("EA024-ISSUE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("ISSUED"));

        mvc.perform(post("/vouchers/" + voucherId + "/transfer")
                        .headers(memberHeaders("EA024-TRANSFER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to_holder_id":"ENT_CORE_001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("TRANSFERRED"));
        assertTrue(auditCount("TRANSFER", voucherId) >= 1);

        MvcResult splitParent = mvc.perform(post("/vouchers")
                        .headers(memberHeaders("EA024-SPLIT-PARENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "acceptor_id": "ENT_CORE_001",
                                  "amount": "200000",
                                  "currency": "CNY",
                                  "due_date": "2026-10-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String splitId = JsonPath.read(splitParent.getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/vouchers/" + splitId + "/issue").headers(memberHeaders("EA024-SPLIT-ISSUE")))
                .andExpect(status().isOk());

        mvc.perform(post("/vouchers/" + splitId + "/split")
                        .headers(memberHeaders("EA024-SPLIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"split_amount":"80000"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available_amount").value("120000"));
        assertTrue(auditCount("VOUCHER_SPLIT", splitId) >= 1);

        mvc.perform(post("/vouchers/" + splitId + "/redeem-apply")
                        .headers(memberHeaders("EA024-REDEEM"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"remark":"mock redeem"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("REDEEM_PENDING"));
        assertTrue(auditCount("REDEEM_APPLY", splitId) >= 1);
    }

    @Test
    void ea024FinanceDisbursedTriggersVoucherFinancingSaga() throws Exception {
        mvc.perform(post("/finance/applications/FIN_VOUCHER_SAGA/disburse")
                        .headers(fundingHeaders("EA024-SAGA-DISB"))
                        .header("X-Idempotency-Key", "EA024-SAGA-DISB")
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "disburse_amount": "300000",
                                  "currency": "CNY",
                                  "value_date": "2026-06-30",
                                  "funding_channel": "INTERNAL_ACCOUNT",
                                  "payer_account_id": "ACC_FUNDING_001",
                                  "receiver_account_id": "ACC_MEMBER_001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBURSED"));

        outboxDispatcher.dispatchPendingEvents();

        assertEquals("FINANCING", voucherStatus("VOUCHER_SAGA_TEST"));
        assertEquals("300000.00", voucherAvailable("VOUCHER_SAGA_TEST").toPlainString());
        assertEquals("300000.00", voucherLocked("VOUCHER_SAGA_TEST").toPlainString());
        assertTrue(auditCount("VOUCHER_ISSUE", "VOUCHER_SAGA_TEST") >= 1);
        assertEquals(1, outboxSuccessCount("FIN_VOUCHER_SAGA"));
    }

    @Test
    void ea024FundingCanListVoucherDetail() throws Exception {
        mvc.perform(get("/vouchers").headers(fundingHeaders("EA024-LIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(get("/vouchers/VOUCHER_SAGA_TEST").headers(fundingHeaders("EA024-DETAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_no").value("DV-SAGA-TEST"));
    }

    private HttpHeaders memberHeaders(String requestId) {
        return headers(memberToken(), requestId);
    }

    private HttpHeaders fundingHeaders(String requestId) {
        return headers(fundingToken(), requestId);
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String memberToken() {
        return jwtService.generateToken(new UserContext(
                "U003", "member_user", OPERATOR_ID, PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003"));
    }

    private String fundingToken() {
        return jwtService.generateToken(new UserContext(
                "U002", "funding_user", OPERATOR_ID, PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002"));
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

    private String voucherStatus(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT voucher_status FROM scf.dv_voucher WHERE id = ?",
                String.class,
                voucherId);
    }

    private java.math.BigDecimal voucherAvailable(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_amount FROM scf.dv_voucher WHERE id = ?",
                java.math.BigDecimal.class,
                voucherId);
    }

    private java.math.BigDecimal voucherLocked(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT locked_amount FROM scf.dv_voucher WHERE id = ?",
                java.math.BigDecimal.class,
                voucherId);
    }

    private int outboxSuccessCount(String financeId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.biz_event_outbox
                        WHERE business_id = ? AND event_type = 'FINANCE_DISBURSED' AND event_status = 'SUCCESS'
                        """,
                Integer.class,
                financeId);
        return count == null ? 0 : count;
    }
}
