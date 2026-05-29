package com.scf.bi;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BiDashboardIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Test
    void fundingCanReadBiOverviewAndSummaries() throws Exception {
        HttpHeaders headers = headers(fundingToken(), "BI-FUNDING-OVERVIEW");

        mvc.perform(get("/bi/overview").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.operator_id").value(OPERATOR_ID))
                .andExpect(jsonPath("$.data.project_id").value(PROJECT_ID))
                .andExpect(jsonPath("$.data.order_count").isNumber());

        mvc.perform(get("/bi/trade-trend").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points").isArray());

        mvc.perform(get("/bi/finance-summary").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.by_status").isArray());

        mvc.perform(get("/bi/warehouse-summary").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inventory_lot_count").isNumber());

        mvc.perform(get("/bi/clearing-summary").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executed_clearing_count").isNumber());

        mvc.perform(get("/bi/risk-alerts").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alerts").isArray());
    }

    @Test
    void memberCanReadScopedBiOverview() throws Exception {
        mvc.perform(get("/bi/overview").headers(headers(memberToken(), "BI-MEMBER-OVERVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.order_count").value(1));
    }

    @Test
    void memberCannotDrilldownRiskAlertsOrExportDashboard() throws Exception {
        HttpHeaders headers = headers(memberToken(), "BI-MEMBER-FORBIDDEN");

        mvc.perform(get("/bi/risk-alerts").headers(headers))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));

        mvc.perform(post("/bi/dashboard/export").headers(headers))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void platformAdminSeesProjectLevelOverview() throws Exception {
        mvc.perform(get("/bi/overview").headers(headers(platformAdminToken(), "BI-ADMIN-OVERVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_count").value(1))
                .andExpect(jsonPath("$.data.finance_count").value(2));
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String platformAdminToken() {
        return token("U001", "platform_admin", "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private String fundingToken() {
        return token("U002", "funding_user", "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private String memberToken() {
        return token("U003", "member_user", "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
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
