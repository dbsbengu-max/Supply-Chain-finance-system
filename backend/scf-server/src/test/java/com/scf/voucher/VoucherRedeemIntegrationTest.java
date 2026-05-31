package com.scf.voucher;

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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/voucher_release_test_seed.sql", "/sql/voucher_redeem_test_seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VoucherRedeemIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String SECONDARY_OK = "MOCK-APPROVED";
    private static final String VOUCHER_LOCKED = "VOUCHER_REPAY_REL";
    private static final String VOUCHER_REDEEM = "VOUCHER_REDEEM_TEST";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ea026RedeemApplyRejectedWhenFinanceLockNotReleased() throws Exception {
        mvc.perform(post("/vouchers/" + VOUCHER_LOCKED + "/redeem-apply")
                        .headers(memberHeaders("EA026-LOCK-APPLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"should fail\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_LOCK_409"));
    }

    @Test
    void ea026StarterCannotApproveOwnRedeemApplication() throws Exception {
        mvc.perform(post("/vouchers/" + VOUCHER_REDEEM + "/redeem-apply")
                        .headers(platformHeaders("EA026-SELF-APPLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"self approve test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("REDEEM_PENDING"));

        jdbcTemplate.update(
                "UPDATE scf.bpm_task SET assignee_id = 'U001' WHERE business_id = ? AND approval_status = 'PENDING'",
                VOUCHER_REDEEM);
        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class,
                VOUCHER_REDEEM);

        mvc.perform(post("/bpm/tasks/" + taskId + "/approve")
                        .headers(platformHeaders("EA026-SELF-APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"self approve\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BPM_FOUR_EYES_409"));
    }

    @Test
    void ea026BpmRejectSetsRejectedTerminalStatus() throws Exception {
        mvc.perform(post("/vouchers/" + VOUCHER_REDEEM + "/redeem-apply")
                        .headers(memberHeaders("EA026-REJECT-APPLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"reject flow\"}"))
                .andExpect(status().isOk());

        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class,
                VOUCHER_REDEEM);
        mvc.perform(post("/bpm/tasks/" + taskId + "/reject")
                        .headers(fundingHeaders("EA026-REJECT-BPM"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"rejected\"}"))
                .andExpect(status().isOk());

        assertEquals("REJECTED", voucherStatus(VOUCHER_REDEEM));
    }

    @Test
    void ea026RedeemExecuteIsIdempotentAndDoesNotDoubleDebit() throws Exception {
        mvc.perform(post("/vouchers/" + VOUCHER_REDEEM + "/redeem-apply")
                        .headers(memberHeaders("EA026-FLOW-APPLY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"full flow\"}"))
                .andExpect(status().isOk());

        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class,
                VOUCHER_REDEEM);
        mvc.perform(post("/bpm/tasks/" + taskId + "/approve")
                        .headers(fundingHeaders("EA026-FLOW-APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk());

        assertEquals("REDEEM_APPROVED", voucherStatus(VOUCHER_REDEEM));
        BigDecimal payerBefore = accountBalance("ACC_CORE_001");
        BigDecimal receiverBefore = accountBalance("ACC_MEMBER_001");

        mvc.perform(post("/vouchers/" + VOUCHER_REDEEM + "/redeem-execute")
                        .headers(fundingHeaders("EA026-FLOW-EXEC-1"))
                        .header("X-Idempotency-Key", "EA026-REDEEM-EXEC")
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemExecuteBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voucher_status").value("REDEEMED"));

        BigDecimal payerAfterFirst = accountBalance("ACC_CORE_001");
        BigDecimal receiverAfterFirst = accountBalance("ACC_MEMBER_001");
        assertEquals(payerBefore.subtract(new BigDecimal("100000.00")), payerAfterFirst);
        assertEquals(receiverBefore.add(new BigDecimal("100000.00")), receiverAfterFirst);

        mvc.perform(post("/vouchers/" + VOUCHER_REDEEM + "/redeem-execute")
                        .headers(fundingHeaders("EA026-FLOW-EXEC-2"))
                        .header("X-Idempotency-Key", "EA026-REDEEM-EXEC")
                        .header("X-Secondary-Auth-Token", SECONDARY_OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemExecuteBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idempotent_replay").value(true));

        assertEquals(payerAfterFirst, accountBalance("ACC_CORE_001"));
        assertEquals(receiverAfterFirst, accountBalance("ACC_MEMBER_001"));
        assertEquals(1, redeemPayFlowCount(VOUCHER_REDEEM));
    }

    private String redeemExecuteBody() {
        return """
                {
                  "payer_account_id": "ACC_CORE_001",
                  "receiver_account_id": "ACC_MEMBER_001",
                  "remark": "mock execute"
                }
                """;
    }

    private HttpHeaders memberHeaders(String requestId) {
        return headers(memberToken(), requestId);
    }

    private HttpHeaders fundingHeaders(String requestId) {
        return headers(fundingToken(), requestId);
    }

    private HttpHeaders platformHeaders(String requestId) {
        return headers(platformToken(), requestId);
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

    private String platformToken() {
        return jwtService.generateToken(new UserContext(
                "U001", "platform_admin", OPERATOR_ID, PROJECT_ID, "ENT_PLATFORM", "ROLE_PLATFORM_ADMIN", "ID001"));
    }

    private String voucherStatus(String voucherId) {
        return jdbcTemplate.queryForObject(
                "SELECT voucher_status FROM scf.dv_voucher WHERE id = ?",
                String.class,
                voucherId);
    }

    private BigDecimal accountBalance(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM scf.acct_virtual_account WHERE id = ?",
                BigDecimal.class,
                accountId);
    }

    private int redeemPayFlowCount(String voucherId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.dv_voucher_flow
                        WHERE voucher_id = ? AND flow_type = 'REDEEM_PAY'
                        """,
                Integer.class,
                voucherId);
        return count == null ? 0 : count;
    }
}
