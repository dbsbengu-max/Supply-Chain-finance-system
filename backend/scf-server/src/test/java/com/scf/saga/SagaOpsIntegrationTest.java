package com.scf.saga;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.saga.service.CompensationTaskExecutor;
import com.scf.saga.service.CompensationTaskProcessor;
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
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/agency_purchase_saga_test_seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SagaOpsIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String AGENCY_ID = "AP_SAGA_OPS";
    private static final String AGENCY_INV_ID = "AP_SAGA_OPS_INV";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    CompensationTaskExecutor compensationTaskExecutor;

    @Autowired
    CompensationTaskProcessor compensationTaskProcessor;

    @Test
    void ea028WorkerExecutesPendingCompensationAutomatically() {
        jdbcTemplate.update(
                "UPDATE scf.acct_virtual_account SET frozen_balance = ? WHERE id = ?",
                new BigDecimal("10000.00"), "ACC_MARGIN_SAGA");
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MARGIN_SAGA", "10000.00");
        assertEquals(new BigDecimal("10000.00"), marginFrozen("ACC_MARGIN_SAGA"));

        compensationTaskExecutor.executeReadyTasks();

        assertEquals("SUCCESS", compensationStatus(taskId));
        assertEquals(new BigDecimal("0.00"), marginFrozen("ACC_MARGIN_SAGA"));
    }

    @Test
    void ea028WorkerExecutesPendingInventoryUnfreezeAutomatically() {
        jdbcTemplate.update("""
                UPDATE scf.wh_inventory
                SET available_quantity = ?, frozen_quantity = ?
                WHERE id = ?
                """, new BigDecimal("90.000000"), new BigDecimal("10.000000"), "INV_SAGA");
        String taskId = insertPendingInventoryUnfreeze(AGENCY_INV_ID, "10");

        compensationTaskExecutor.executeReadyTasks();

        assertEquals("SUCCESS", compensationStatus(taskId));
        assertEquals(new BigDecimal("100.000000"), inventoryAvailable("INV_SAGA"));
        assertEquals(new BigDecimal("0.000000"), inventoryFrozen("INV_SAGA"));
    }

    @Test
    void ea028ManualRetryInventoryUnfreezeViaApi() throws Exception {
        jdbcTemplate.update("""
                UPDATE scf.wh_inventory
                SET available_quantity = ?, frozen_quantity = ?
                WHERE id = ?
                """, new BigDecimal("80.000000"), new BigDecimal("20.000000"), "INV_SAGA");
        String taskId = insertPendingInventoryUnfreeze(AGENCY_INV_ID, "20");
        jdbcTemplate.update(
                "UPDATE scf.biz_compensation_task SET action_json = ? WHERE id = ?",
                "{\"inventory_id\":\"INV_MISSING\",\"quantity\":\"20\"}",
                taskId);
        compensationTaskProcessor.process(taskId);
        assertEquals("FAILED", compensationStatus(taskId));

        jdbcTemplate.update(
                "UPDATE scf.biz_compensation_task SET action_json = ? WHERE id = ?",
                "{\"inventory_id\":\"INV_SAGA\",\"quantity\":\"20\"}",
                taskId);

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(platformHeaders("EA028-INV-RETRY"))
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        assertEquals("SUCCESS", compensationStatus(taskId));
        assertEquals(new BigDecimal("100.000000"), inventoryAvailable("INV_SAGA"));
        assertEquals(new BigDecimal("0.000000"), inventoryFrozen("INV_SAGA"));
    }

    @Test
    void ea028FailedCompensationSchedulesRetryAndSkipsEarlyRetry() {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MISSING", "100.00");
        compensationTaskProcessor.process(taskId);

        assertEquals("FAILED", compensationStatus(taskId));
        assertEquals(1, retryCount(taskId));
        assertNotNull(nextRetryAt(taskId));

        compensationTaskProcessor.process(taskId);
        assertEquals(1, retryCount(taskId));
    }

    @Test
    void ea028MaxRetriesMovesToManualRequired() {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MISSING", "100.00");
        jdbcTemplate.update(
                "UPDATE scf.biz_compensation_task SET retry_count = ? WHERE id = ?",
                CompensationTaskProcessor.RETRY_MINUTES.length - 1,
                taskId);

        compensationTaskProcessor.process(taskId);

        assertEquals("MANUAL_REQUIRED", compensationStatus(taskId));
        assertEquals(CompensationTaskProcessor.RETRY_MINUTES.length, retryCount(taskId));
    }

    @Test
    void ea028ManualRetryCompensationViaApi() throws Exception {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MISSING", "100.00");
        compensationTaskProcessor.process(taskId);
        assertEquals("FAILED", compensationStatus(taskId));

        jdbcTemplate.update(
                "UPDATE scf.biz_compensation_task SET action_json = ? WHERE id = ?",
                "{\"account_id\":\"ACC_MARGIN_SAGA\",\"amount\":\"100.00\"}",
                taskId);
        jdbcTemplate.update(
                "UPDATE scf.acct_virtual_account SET frozen_balance = ? WHERE id = ?",
                new BigDecimal("100.00"), "ACC_MARGIN_SAGA");

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(platformHeaders("EA028-RETRY"))
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        assertEquals("SUCCESS", compensationStatus(taskId));
    }

    @Test
    void ea028ApproveExecuteManualRequiredCompensation() throws Exception {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MARGIN_SAGA", "100.00");
        jdbcTemplate.update(
                "UPDATE scf.acct_virtual_account SET frozen_balance = ? WHERE id = ?",
                new BigDecimal("100.00"), "ACC_MARGIN_SAGA");
        jdbcTemplate.update("""
                UPDATE scf.biz_compensation_task
                SET compensation_status = 'MANUAL_REQUIRED', retry_count = ?, last_error = 'mock manual'
                WHERE id = ?
                """, CompensationTaskProcessor.RETRY_MINUTES.length, taskId);

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/approve-execute")
                        .headers(platformHeaders("EA028-APPROVE"))
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        assertEquals("SUCCESS", compensationStatus(taskId));
        assertEquals("U001", approvedBy(taskId));
    }

    @Test
    void ea028OpsSummaryReturnsBacklogCounts() throws Exception {
        insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MARGIN_SAGA", "50.00");

        mvc.perform(get("/saga/ops/summary").headers(platformHeaders("EA028-SUMMARY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.compensation_pending").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void ea028FundingUserCanViewButNotRetry() throws Exception {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MISSING", "10.00");
        compensationTaskProcessor.process(taskId);

        mvc.perform(get("/saga/ops/compensation-tasks")
                        .headers(fundingHeaders("EA028-VIEW"))
                        .param("business_id", AGENCY_ID))
                .andExpect(status().isOk());

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(fundingHeaders("EA028-FORBID"))
                        .content(manualReasonJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ea029ManualRetryRequiresReason() throws Exception {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MISSING", "10.00");
        compensationTaskProcessor.process(taskId);

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(platformHeaders("EA029-NO-REASON"))
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ea029CompensationDetailReturnsActionJsonAndRoute() throws Exception {
        String taskId = insertPendingMarginUnfreeze(AGENCY_ID, "ACC_MARGIN_SAGA", "50.00");

        mvc.perform(get("/saga/ops/compensation-tasks/" + taskId).headers(platformHeaders("EA029-DETAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.business_id").value(AGENCY_ID))
                .andExpect(jsonPath("$.data.action_json").isNotEmpty())
                .andExpect(jsonPath("$.data.related_route")
                        .value("/agency-purchase/applications/" + AGENCY_ID));
    }

    private static String manualReasonJson() {
        return "{\"reason\":\"集成测试人工操作原因说明\"}";
    }

    private String insertPendingMarginUnfreeze(String agencyId, String accountId, String amount) {
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO scf.biz_compensation_task
                (id, compensation_type, business_type, business_id, compensation_status,
                 action_json, retry_count, created_at, updated_at)
                VALUES (?, 'MARGIN_UNFREEZE', 'AGENCY_PURCHASE', ?, 'PENDING', ?, 0, ?, ?)
                """,
                taskId,
                agencyId,
                "{\"account_id\":\"" + accountId + "\",\"amount\":\"" + amount + "\"}",
                Instant.now(),
                Instant.now());
        return taskId;
    }

    private String insertPendingInventoryUnfreeze(String agencyId, String quantity) {
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("""
                INSERT INTO scf.biz_compensation_task
                (id, compensation_type, business_type, business_id, compensation_status,
                 action_json, retry_count, created_at, updated_at)
                VALUES (?, 'INVENTORY_UNFREEZE', 'AGENCY_PURCHASE', ?, 'PENDING', ?, 0, ?, ?)
                """,
                taskId,
                agencyId,
                "{\"inventory_id\":\"INV_SAGA\",\"quantity\":\"" + quantity + "\"}",
                Instant.now(),
                Instant.now());
        return taskId;
    }

    private String compensationStatus(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT compensation_status FROM scf.biz_compensation_task WHERE id = ?",
                String.class,
                taskId);
    }

    private int retryCount(String taskId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM scf.biz_compensation_task WHERE id = ?",
                Integer.class,
                taskId);
        return count == null ? 0 : count;
    }

    private Instant nextRetryAt(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT next_retry_at FROM scf.biz_compensation_task WHERE id = ?",
                Instant.class,
                taskId);
    }

    private String approvedBy(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT approved_by FROM scf.biz_compensation_task WHERE id = ?",
                String.class,
                taskId);
    }

    private BigDecimal marginFrozen(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM scf.acct_virtual_account WHERE id = ?",
                BigDecimal.class,
                accountId);
    }

    private BigDecimal inventoryAvailable(String inventoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_quantity FROM scf.wh_inventory WHERE id = ?",
                BigDecimal.class,
                inventoryId);
    }

    private BigDecimal inventoryFrozen(String inventoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT frozen_quantity FROM scf.wh_inventory WHERE id = ?",
                BigDecimal.class,
                inventoryId);
    }

    private HttpHeaders platformHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateToken(new UserContext(
                "U001", "platform_admin", OPERATOR_ID, PROJECT_ID, "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001")));
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders fundingHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateToken(new UserContext(
                "U002", "funding_user", OPERATOR_ID, PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002")));
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
