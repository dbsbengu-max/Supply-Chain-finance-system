package com.scf.saga;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import com.scf.finance.service.FinanceApplicationService;
import com.scf.saga.service.CompensationTaskProcessor;
import com.scf.saga.service.OutboxEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/agency_purchase_saga_test_seed.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SagaEa037IntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String ORDER_ID = "ORD_SAGA_SUBMITTED";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OutboxEventProcessor outboxEventProcessor;

    @Autowired
    CompensationTaskProcessor compensationTaskProcessor;

    @MockBean
    FinanceApplicationService financeApplicationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void defaultFinanceMock() {
        lenient().when(financeApplicationService.createFromAgencyPurchase(any())).thenReturn("FN_MOCK");
    }

    @Test
    void ea037OrderConfirmThenFinanceFailEnqueuesOrderRollback() throws Exception {
        when(financeApplicationService.createFromAgencyPurchase(any()))
                .thenThrow(new RuntimeException("finance create failed"));

        String agencyId = createAndApproveThirdParty("EA037-ROLLBACK-ENQ");
        dispatchAgencyApprovedEvent(agencyId);

        assertEquals("FAILED", sagaStatus(agencyId));
        assertEquals("CONFIRMED", orderStatus(ORDER_ID));
        assertEquals(1, compensationCount(agencyId, "ORDER_ROLLBACK"));
    }

    @Test
    void ea037OrderRollbackExecutesAndRevertsOrder() throws Exception {
        when(financeApplicationService.createFromAgencyPurchase(any()))
                .thenThrow(new RuntimeException("finance create failed"));

        String agencyId = createAndApproveThirdParty("EA037-ROLLBACK-EXEC");
        dispatchAgencyApprovedEvent(agencyId);

        String taskId = compensationTaskId(agencyId, "ORDER_ROLLBACK");
        compensationTaskProcessor.process(taskId);

        assertEquals("SUCCESS", compensationStatus(taskId));
        assertEquals("SUBMITTED", orderStatus(ORDER_ID));
    }

    @Test
    void ea037UnauthorizedRetryReturns403() throws Exception {
        String taskId = insertManualRequiredMarginTask("EA037-403");

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(fundingHeaders("EA037-403-RETRY"))
                        .content(manualReasonJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ea037IgnoreWithoutReasonReturns400() throws Exception {
        String taskId = insertManualRequiredMarginTask("EA037-IGNORE-400");

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/ignore")
                        .headers(platformHeaders("EA037-IGNORE-400"))
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ea037HighRiskSelfApproveReturnsFourEyes409() throws Exception {
        String taskId = insertHighRiskOrderRollbackManual("EA037-FOUR-EYES");

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/claim")
                        .headers(platformHeaders("EA037-CLAIM")))
                .andExpect(status().isOk());

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/submit-approval")
                        .headers(platformHeaders("EA037-SUBMIT"))
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/approve-execute")
                        .headers(platformHeaders("EA037-SELF-APPROVE"))
                        .content(manualReasonJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BPM_FOUR_EYES_409"));
    }

    @Test
    void ea037OrderRollbackIsIdempotent() throws Exception {
        when(financeApplicationService.createFromAgencyPurchase(any()))
                .thenThrow(new RuntimeException("finance create failed"));

        String agencyId = createAndApproveThirdParty("EA037-IDEMP");
        dispatchAgencyApprovedEvent(agencyId);
        String taskId = compensationTaskId(agencyId, "ORDER_ROLLBACK");

        compensationTaskProcessor.process(taskId);
        assertEquals("SUBMITTED", orderStatus(ORDER_ID));

        jdbcTemplate.update(
                "UPDATE scf.biz_compensation_task SET compensation_status = 'PENDING', retry_count = 0 WHERE id = ?",
                taskId);
        compensationTaskProcessor.process(taskId);

        assertEquals("SUBMITTED", orderStatus(ORDER_ID));
        assertEquals("SUCCESS", compensationStatus(taskId));
    }

    @Test
    void ea037ClosedTaskCannotRetryReturns409() throws Exception {
        String taskId = insertManualRequiredMarginTask("EA037-CLOSED");

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/close")
                        .headers(platformHeaders("EA037-CLOSE"))
                        .content(manualReasonJson()))
                .andExpect(status().isOk());

        mvc.perform(post("/saga/ops/compensation-tasks/" + taskId + "/retry")
                        .headers(platformHeaders("EA037-RETRY-CLOSED"))
                        .content(manualReasonJson()))
                .andExpect(status().isConflict());
    }

    @Test
    void ea037CompensationDetailIncludesImpactAndHighRisk() throws Exception {
        String taskId = insertHighRiskOrderRollbackManual("EA037-DETAIL");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/saga/ops/compensation-tasks/" + taskId)
                        .headers(platformHeaders("EA037-DETAIL-GET")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.high_risk").value(true))
                .andExpect(jsonPath("$.data.impact.suggested_action").isNotEmpty());
    }

    private String createAndApproveThirdParty(String prefix) throws Exception {
        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(memberHeaders(prefix + "-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdPartyBody()))
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

        String taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM scf.bpm_task WHERE business_id = ? AND approval_status = 'PENDING'",
                String.class, id);
        mvc.perform(post("/bpm/tasks/" + taskId + "/approve")
                        .headers(platformHeaders(prefix + "-APPROVE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"approved\"}"))
                .andExpect(status().isOk());
    }

    private String thirdPartyBody() {
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
                  "remark": "ea037-third-party"
                }
                """.formatted(ORDER_ID);
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

    private String insertManualRequiredMarginTask(String suffix) {
        String agencyId = "AP_EA037_" + suffix;
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        INSERT INTO scf.biz_compensation_task
                        (id, compensation_type, business_type, business_id, compensation_status,
                         action_json, retry_count, high_risk, created_at, updated_at)
                        VALUES (?, 'MARGIN_UNFREEZE', 'AGENCY_PURCHASE', ?, 'MANUAL_REQUIRED', ?, 3, 0, ?, ?)
                        """,
                taskId,
                agencyId,
                "{\"account_id\":\"ACC_MARGIN_SAGA\",\"amount\":\"10.00\"}",
                Instant.now(),
                Instant.now());
        return taskId;
    }

    private String insertHighRiskOrderRollbackManual(String suffix) {
        String agencyId = "AP_EA037_" + suffix;
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                        INSERT INTO scf.biz_compensation_task
                        (id, compensation_type, business_type, business_id, compensation_status,
                         action_json, retry_count, high_risk, created_at, updated_at)
                        VALUES (?, 'ORDER_ROLLBACK', 'AGENCY_PURCHASE', ?, 'MANUAL_REQUIRED', ?, 3, 1, ?, ?)
                        """,
                taskId,
                agencyId,
                "{\"order_id\":\"" + ORDER_ID + "\",\"reason\":\"test\"}",
                Instant.now(),
                Instant.now());
        return taskId;
    }

    private String compensationTaskId(String agencyId, String type) {
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
                type);
    }

    private int compensationCount(String agencyId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM scf.biz_compensation_task
                        WHERE business_type = 'AGENCY_PURCHASE' AND business_id = ?
                          AND compensation_type = ?
                        """,
                Integer.class,
                agencyId,
                type);
        return count == null ? 0 : count;
    }

    private String compensationStatus(String taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT compensation_status FROM scf.biz_compensation_task WHERE id = ?",
                String.class,
                taskId);
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

    private static String manualReasonJson() {
        return "{\"reason\":\"集成测试人工操作原因说明\"}";
    }

    private HttpHeaders memberHeaders(String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateToken(new UserContext(
                "U003", "member_user", OPERATOR_ID, PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003")));
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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

    private String readJson(MvcResult result, String pointer) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString()).at(pointer);
        return node.isMissingNode() ? null : node.asText();
    }
}
