package com.scf.agencypurchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.saga.service.CompensationTaskProcessor;
import com.scf.saga.service.OutboxDispatcher;
import com.scf.saga.service.OutboxEventProcessor;
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
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/agency_purchase_saga_test_seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AgencyPurchaseSagaIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxDispatcher outboxDispatcher;

    @Autowired
    OutboxEventProcessor outboxEventProcessor;

    @Autowired
    CompensationTaskProcessor compensationTaskProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ea027ThirdPartyFundedSagaCreatesFinanceAndConfirmsOrder() throws Exception {
        String id = createAndApproveThirdParty("ORD_SAGA_SUBMITTED", "EA027-TP-FLOW");
        dispatchAgencyApprovedEvent(id);

        assertEquals("SUCCESS", sagaStatus(id));
        assertEquals("CONFIRMED", orderStatus("ORD_SAGA_SUBMITTED"));
        assertTrue(financeCountForAgency(id) >= 1);
        assertStepStatus(id, "ORDER_CONFIRM", "SUCCESS");
        assertStepStatus(id, "FINANCE_CREATE", "SUCCESS");
        assertStepStatus(id, "MARGIN_FREEZE", "SKIPPED");
    }

    @Test
    void ea027SelfFundedSagaFreezesMargin() throws Exception {
        String id = createSelfFundedAndApprove("EA027-SF-FLOW");
        dispatchAgencyApprovedEvent(id);

        assertEquals("SUCCESS", sagaStatus(id));
        assertEquals(new BigDecimal("10000.00"), marginFrozen("ACC_MARGIN_SAGA"));
        assertStepStatus(id, "MARGIN_FREEZE", "SUCCESS");
        assertStepStatus(id, "FINANCE_CREATE", "SKIPPED");
    }

    @Test
    void ea027StockPrepareSagaFreezesInventory() throws Exception {
        String id = createStockPrepareAndApprove("EA027-SP-FLOW");
        dispatchAgencyApprovedEvent(id);

        assertEquals("SUCCESS", sagaStatus(id));
        assertEquals(new BigDecimal("90.000000"), inventoryAvailable("INV_SAGA"));
        assertEquals(new BigDecimal("10.000000"), inventoryFrozen("INV_SAGA"));
        assertStepStatus(id, "INVENTORY_FREEZE", "SUCCESS");
    }

    @Test
    void ea027InsufficientMarginFailsAndCreatesCompensationTask() throws Exception {
        String id = createSelfFundedLowMarginAndApprove("EA027-FAIL-FLOW");
        assertEquals(1, outboxCount(id));
        dispatchAgencyApprovedEvent(id);

        assertEquals("FAILED", sagaStatus(id));
        assertStepStatus(id, "MARGIN_FREEZE", "FAILED");
        assertEquals(0, compensationCount(id));
    }

    @Test
    void ea027DuplicateDispatchIsIdempotent() throws Exception {
        String id = createAndApproveThirdParty("ORD_SAGA_SUBMITTED", "EA027-IDEMP-FLOW");
        dispatchAgencyApprovedEvent(id);
        int financeCountAfterFirst = financeCountForAgency(id);
        BigDecimal frozenAfterFirst = marginFrozen("ACC_MARGIN_SAGA");

        dispatchAgencyApprovedEvent(id);

        assertEquals(financeCountAfterFirst, financeCountForAgency(id));
        assertEquals(frozenAfterFirst, marginFrozen("ACC_MARGIN_SAGA"));
        assertEquals("SUCCESS", sagaStatus(id));
    }

    @Test
    void ea027PartialFailureEnqueuesAndExecutesMarginCompensation() throws Exception {
        String id = createStockPrepareInsufficientInventoryAndApprove("EA027-COMP-FLOW");
        dispatchAgencyApprovedEvent(id);

        assertEquals("FAILED", sagaStatus(id));
        assertEquals(new BigDecimal("10000.00"), marginFrozen("ACC_MARGIN_SAGA"));
        assertEquals(1, compensationCount(id));

        String taskId = compensationTaskId(id, "MARGIN_UNFREEZE");
        compensationTaskProcessor.process(taskId);

        assertEquals("SUCCESS", compensationTaskStatus(taskId));
        assertEquals(new BigDecimal("0.00"), marginFrozen("ACC_MARGIN_SAGA"));
    }

    @Test
    void ea027InventoryCompensationUnfreezesFrozenStock() throws Exception {
        String id = createStockPrepareAndApprove("EA027-INV-COMP");
        dispatchAgencyApprovedEvent(id);
        assertEquals("SUCCESS", sagaStatus(id));
        assertEquals(new BigDecimal("10.000000"), inventoryFrozen("INV_SAGA"));

        String taskId = enqueueInventoryUnfreeze(id, "10");
        compensationTaskProcessor.process(taskId);

        assertEquals("SUCCESS", compensationTaskStatus(taskId));
        assertEquals(new BigDecimal("100.000000"), inventoryAvailable("INV_SAGA"));
        assertEquals(new BigDecimal("0.000000"), inventoryFrozen("INV_SAGA"));
    }

    @Test
    void ea027BpmRejectDoesNotRunSaga() throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders("EA027-REJECT-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfFundedBody("ACC_MARGIN_SAGA")))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");

        mvc.perform(post("/agency-purchase/applications/" + id + "/submit")
                        .headers(memberHeaders("EA027-REJECT-SUBMIT")))
                .andExpect(status().isOk());

        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class, id);
        mvc.perform(post("/bpm/tasks/" + taskId + "/reject")
                        .headers(platformHeaders("EA027-REJECT-BPM"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"rejected\"}"))
                .andExpect(status().isOk());

        assertEquals("REJECTED", applicationStatus(id));
        assertEquals(0, outboxCount(id));
    }

    private String createAndApproveThirdParty(String orderId, String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPartyBody(orderId)))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");
        submitAndApprove(id, prefix);
        return id;
    }

    private String createSelfFundedAndApprove(String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfFundedBody("ACC_MARGIN_SAGA")))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");
        submitAndApprove(id, prefix);
        return id;
    }

    private String createSelfFundedLowMarginAndApprove(String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfFundedBody("ACC_MARGIN_LOW")))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");
        submitAndApprove(id, prefix);
        return id;
    }

    private String createStockPrepareAndApprove(String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockPrepareBody()))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");
        submitAndApprove(id, prefix);
        return id;
    }

    private void submitAndApprove(String id, String prefix) throws Exception {
        mvc.perform(post("/agency-purchase/applications/" + id + "/submit")
                        .headers(memberHeaders(prefix + "-SUBMIT")))
                .andExpect(status().isOk());
        assertEquals("REVIEWING", applicationStatus(id));

        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class, id);
        mvc.perform(post("/bpm/tasks/" + taskId + "/approve")
                        .headers(platformHeaders(prefix + "-APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk());
        assertEquals("APPROVED", applicationStatus(id));
    }

    private String thirdPartyBody(String orderId) {
        return """
                {
                  "order_mode": "STOCK_ORDER",
                  "fund_source": "THIRD_PARTY_FUNDED",
                  "pickup_type": "PAYMENT_PICKUP",
                  "customer_id": "ENT_MEMBER_001",
                  "trade_company_id": "ENT_TRADE_001",
                  "order_id": "%s",
                  "currency": "CNY",
                  "total_amount": "100000.00",
                  "remark": "ea027-third-party"
                }
                """.formatted(orderId);
    }

    private String selfFundedBody(String marginAccountId) {
        return """
                {
                  "order_mode": "STOCK_ORDER",
                  "fund_source": "SELF_FUNDED",
                  "pickup_type": "PAYMENT_PICKUP",
                  "customer_id": "ENT_MEMBER_001",
                  "trade_company_id": "ENT_TRADE_001",
                  "order_id": "ORD_SAGA_SUBMITTED",
                  "margin_account_id": "%s",
                  "currency": "CNY",
                  "total_amount": "100000.00",
                  "remark": "ea027-self-funded"
                }
                """.formatted(marginAccountId);
    }

    private String createStockPrepareInsufficientInventoryAndApprove(String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stockPrepareBodyWithQty("999")))
                .andExpect(status().isOk())
                .andReturn();
        String id = readJson(createResult, "/data/id");
        submitAndApprove(id, prefix);
        return id;
    }

    private String stockPrepareBodyWithQty(String qty) {
        return """
                {
                  "order_mode": "STOCK_PREPARE",
                  "fund_source": "SELF_FUNDED",
                  "pickup_type": "PAYMENT_PICKUP",
                  "customer_id": "ENT_MEMBER_001",
                  "trade_company_id": "ENT_TRADE_001",
                  "inventory_id": "INV_SAGA",
                  "margin_account_id": "ACC_MARGIN_SAGA",
                  "inventory_freeze_quantity": "%s",
                  "currency": "CNY",
                  "total_amount": "100000.00",
                  "remark": "ea027-stock-prepare-fail"
                }
                """.formatted(qty);
    }

    private String enqueueInventoryUnfreeze(String agencyId, String qty) {
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        INSERT INTO scf.biz_compensation_task
                        (id, compensation_type, business_type, business_id, compensation_status, action_json, created_at)
                        VALUES (?, 'INVENTORY_UNFREEZE', 'AGENCY_PURCHASE', ?, 'PENDING', ?, CURRENT_TIMESTAMP)
                        """,
                taskId,
                agencyId,
                "{\"inventory_id\":\"INV_SAGA\",\"quantity\":\"" + qty + "\",\"reason\":\"test\"}");
        return taskId;
    }

    private String compensationTaskId(String agencyId, String compensationType) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT id FROM scf.biz_compensation_task
                        WHERE business_type = 'AGENCY_PURCHASE' AND business_id = ?
                          AND compensation_type = ?
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                String.class,
                agencyId,
                compensationType);
    }

    private String compensationTaskStatus(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT compensation_status FROM scf.biz_compensation_task WHERE id = ?",
                String.class,
                taskId);
    }

    private String stockPrepareBody() {
        return """
                {
                  "order_mode": "STOCK_PREPARE",
                  "fund_source": "SELF_FUNDED",
                  "pickup_type": "PAYMENT_PICKUP",
                  "customer_id": "ENT_MEMBER_001",
                  "trade_company_id": "ENT_TRADE_001",
                  "inventory_id": "INV_SAGA",
                  "margin_account_id": "ACC_MARGIN_SAGA",
                  "inventory_freeze_quantity": "10",
                  "currency": "CNY",
                  "total_amount": "100000.00",
                  "remark": "ea027-stock-prepare"
                }
                """;
    }

    private void dispatchAgencyApprovedEvent(String applicationId) {
        String outboxId = jdbcTemplate.queryForObject(
                """
                        SELECT id FROM scf.biz_event_outbox
                        WHERE business_type = 'AGENCY_PURCHASE' AND business_id = ?
                          AND event_type = 'AGENCY_PURCHASE_APPROVED'
                        ORDER BY created_at DESC
                        LIMIT 1
                        """,
                String.class,
                applicationId);
        outboxEventProcessor.process(outboxId);
    }

    private HttpHeaders memberHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(memberToken());
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private HttpHeaders platformHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(platformToken());
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String platformToken() {
        return jwtService.generateToken(new UserContext(
                "U001", "platform_admin", OPERATOR_ID, PROJECT_ID, "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001"));
    }

    private String memberToken() {
        return jwtService.generateToken(new UserContext(
                "U003", "member_user", OPERATOR_ID, PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003"));
    }

    private String readJson(MvcResult result, String pointer) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer);
        return node.isMissingNode() ? null : node.asText();
    }

    private String applicationStatus(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT application_status FROM scf.ap_agency_purchase_application WHERE id = ?",
                String.class, id);
    }

    private String sagaStatus(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT saga_status FROM scf.ap_agency_purchase_application WHERE id = ?",
                String.class, id);
    }

    private String orderStatus(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT order_status FROM scf.tr_order WHERE id = ?",
                String.class, orderId);
    }

    private int financeCountForAgency(String agencyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.fn_finance_application
                        WHERE source_type = 'AGENCY_PURCHASE' AND source_id = ? AND deleted_flag = 0
                        """,
                Integer.class, agencyId);
        return count == null ? 0 : count;
    }

    private int outboxCount(String agencyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.biz_event_outbox
                        WHERE business_type = 'AGENCY_PURCHASE' AND business_id = ?
                        """,
                Integer.class, agencyId);
        return count == null ? 0 : count;
    }

    private int compensationCount(String agencyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.biz_compensation_task
                        WHERE business_type = 'AGENCY_PURCHASE' AND business_id = ?
                        """,
                Integer.class, agencyId);
        return count == null ? 0 : count;
    }

    private BigDecimal marginFrozen(String accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT frozen_balance FROM scf.acct_virtual_account WHERE id = ?",
                BigDecimal.class, accountId);
    }

    private BigDecimal inventoryAvailable(String inventoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_quantity FROM scf.wh_inventory WHERE id = ?",
                BigDecimal.class, inventoryId);
    }

    private BigDecimal inventoryFrozen(String inventoryId) {
        return jdbcTemplate.queryForObject(
                "SELECT frozen_quantity FROM scf.wh_inventory WHERE id = ?",
                BigDecimal.class, inventoryId);
    }

    private void assertStepStatus(String applicationId, String stepCode, String expected) {
        String status = jdbcTemplate.queryForObject(
                """
                        SELECT step_status FROM scf.ap_agency_purchase_saga_step
                        WHERE application_id = ? AND step_code = ?
                        """,
                String.class, applicationId, stepCode);
        assertEquals(expected, status);
    }
}
