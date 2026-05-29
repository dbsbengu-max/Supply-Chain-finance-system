package com.scf.inbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InboxCenterIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void fundingCanLoadUnifiedInboxFeed() throws Exception {
        mvc.perform(get("/inbox/feed").headers(headers(fundingToken(), "INBOX-FUNDING-FEED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.total").isNumber())
                .andExpect(jsonPath("$.data.events").isArray());
    }

    @Test
    void fundingCanMarkInboxEventRead() throws Exception {
        HttpHeaders headers = headers(fundingToken(), "INBOX-FUNDING-READ");
        MvcResult feedResult = mvc.perform(get("/inbox/feed").headers(headers))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode events = objectMapper.readTree(feedResult.getResponse().getContentAsString())
                .path("data")
                .path("events");
        if (events.isEmpty()) {
            return;
        }

        String eventKey = events.get(0).path("event_key").asText();
        mvc.perform(patch("/inbox/events/read").headers(headers).param("event_key", eventKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
    }

    @Test
    void memberCanAccessInboxFeed() throws Exception {
        mvc.perform(get("/inbox/feed").headers(headers(memberToken(), "INBOX-MEMBER-FEED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
