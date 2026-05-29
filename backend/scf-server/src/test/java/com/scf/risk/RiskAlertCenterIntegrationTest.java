package com.scf.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RiskAlertCenterIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fundingCanListAndHandleRiskAlerts() throws Exception {
        HttpHeaders headers = headers(fundingToken(), "RISK-FUNDING-LIST");

        MvcResult listResult = mvc.perform(get("/risk/alerts").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();

        JsonNode records = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data")
                .path("records");
        if (records.isEmpty()) {
            return;
        }

        String alertId = records.get(0).path("id").asText();

        mvc.perform(get("/risk/alerts/" + alertId).headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(alertId));

        mvc.perform(post("/risk/alerts/" + alertId + "/claim").headers(headers))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle_status").value("ACK"));

        mvc.perform(patch("/risk/alerts/" + alertId)
                        .headers(headers)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"handle_status":"PROCESSING","remark":"跟进中"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handle_status").value("PROCESSING"));
    }

    @Test
    void memberCannotAccessRiskAlertCenter() throws Exception {
        HttpHeaders headers = headers(memberToken(), "RISK-MEMBER-FORBIDDEN");

        mvc.perform(get("/risk/alerts").headers(headers))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
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
