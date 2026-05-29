package com.scf.audit;

import com.scf.audit.service.AuditLogService;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditCenterIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String AUDIT_MEMBER_LOG = "AUDIT_TEST_MEMBER_LOG";
    private static final String AUDIT_FUNDING_LOG = "AUDIT_TEST_FUNDING_LOG";

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditLogService auditLogService;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedLogs() {
        jdbcTemplate.update("DELETE FROM scf.audit_operation_log WHERE id IN (?, ?)", AUDIT_MEMBER_LOG, AUDIT_FUNDING_LOG);
        auditLogService.logAsSystem(
                "U003",
                OPERATOR_ID,
                "ENT_MEMBER_001",
                PROJECT_ID,
                "ORDER_CREATE",
                "TRADE_ORDER",
                "ORD_AUDIT_MEMBER",
                null,
                Map.of("order_no", "ORD-AUDIT-M"));
        auditLogService.logAsSystem(
                "U002",
                OPERATOR_ID,
                "ENT_FACTOR_001",
                PROJECT_ID,
                "FINANCE_APPROVE",
                "FINANCE_APPLICATION",
                "FIN_AUDIT_FUNDING",
                null,
                Map.of("finance_status", "APPROVED"));
        jdbcTemplate.update(
                "UPDATE scf.audit_operation_log SET id = ? WHERE object_id = ?",
                AUDIT_MEMBER_LOG,
                "ORD_AUDIT_MEMBER");
        jdbcTemplate.update(
                "UPDATE scf.audit_operation_log SET id = ? WHERE object_id = ?",
                AUDIT_FUNDING_LOG,
                "FIN_AUDIT_FUNDING");
    }

    @Test
    void platformAdminCanListAuditLogs() throws Exception {
        mvc.perform(get("/audit/logs").headers(platformHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void platformAdminCanLoadAuditDetail() throws Exception {
        mvc.perform(get("/audit/logs/" + AUDIT_MEMBER_LOG).headers(platformHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(AUDIT_MEMBER_LOG))
                .andExpect(jsonPath("$.data.after_value").exists());
    }

    @Test
    void memberCannotReadFundingAuditDetail() throws Exception {
        mvc.perform(get("/audit/logs/" + AUDIT_FUNDING_LOG).headers(memberHeaders()))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberCanReadOwnEnterpriseAuditDetail() throws Exception {
        mvc.perform(get("/audit/logs/" + AUDIT_MEMBER_LOG).headers(memberHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.object_id").value("ORD_AUDIT_MEMBER"));
    }

    @Test
    void fundingCanLoadAuditSummary() throws Exception {
        mvc.perform(get("/audit/summary").headers(fundingHeaders()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    private HttpHeaders platformHeaders() {
        return headers(token("U001", "platform_admin", "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001"), "AUDIT-PLATFORM");
    }

    private HttpHeaders fundingHeaders() {
        return headers(token("U002", "funding_user", "ENT_FACTOR_001", "ROLE_FUNDING", "ID002"), "AUDIT-FUNDING");
    }

    private HttpHeaders memberHeaders() {
        return headers(token("U003", "member_user", "ENT_MEMBER_001", "ROLE_MEMBER", "ID003"), "AUDIT-MEMBER");
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId,
                loginName,
                OPERATOR_ID,
                PROJECT_ID,
                enterpriseId,
                roleId,
                identityId));
    }
}
