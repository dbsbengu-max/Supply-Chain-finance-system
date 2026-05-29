package com.scf.agencypurchase;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgencyPurchaseIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String OTHER_PROJECT_ID = "PJ_TEST_OTHER";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Test
    void platformAdminFullFlow_createEditSubmitCancel() throws Exception {
        String createBody = agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001");

        MvcResult createResult = mvc.perform(post("/agency-purchase/applications")
                        .headers(headers(platformAdminToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_status").value("DRAFT"))
                .andExpect(jsonPath("$.data.mode_key").value("SO_SF_PP"))
                .andReturn();

        String id = readJson(createResult, "$.data.id");

        mvc.perform(put("/agency-purchase/applications/" + id)
                        .headers(headers(platformAdminToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001", "120000.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_amount").value("120000.00"));

        mvc.perform(post("/agency-purchase/applications/" + id + "/submit")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_status").value("REVIEWING"))
                .andExpect(jsonPath("$.data.bpm_instance_id", notNullValue()));

        mvc.perform(get("/agency-purchase/applications/" + id)
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bpm_instance_id", notNullValue()));

        mvc.perform(post("/agency-purchase/applications/" + id + "/cancel")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_status").value("CANCELLED"));
    }

    @Test
    void submittedApplicationCannotBeEdited() throws Exception {
        String id = createDraft(platformAdminToken(), PROJECT_ID);
        mvc.perform(post("/agency-purchase/applications/" + id + "/submit")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk());

        mvc.perform(put("/agency-purchase/applications/" + id)
                        .headers(headers(platformAdminToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void fundingUserCanViewButNotCreate() throws Exception {
        mvc.perform(get("/agency-purchase/applications")
                        .headers(headers(fundingToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());

        mvc.perform(post("/agency-purchase/applications")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void warehouseUserCannotAccessAgencyPurchase() throws Exception {
        mvc.perform(get("/agency-purchase/applications")
                        .headers(headers(warehouseToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void memberUserCanCreateOwnCustomerApplication() throws Exception {
        mvc.perform(post("/agency-purchase/applications")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application_status").value("DRAFT"));
    }

    @Test
    void memberUserCannotCreateForOtherCustomer() throws Exception {
        mvc.perform(post("/agency-purchase/applications")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_CORE_001", "ENT_TRADE_001", "ORD001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void crossProjectReadReturnsNotFound() throws Exception {
        String id = createDraft(platformAdminToken(), PROJECT_ID);
        mvc.perform(get("/agency-purchase/applications/" + id)
                        .headers(headers(platformAdminToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));
    }

    @Test
    void flywayMetaIncludesEightValidModes() throws Exception {
        mvc.perform(get("/agency-purchase/meta")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid_modes.length()").value(8))
                .andExpect(jsonPath("$.data.cross_domain_actions.length()").value(6));
    }

    private String createDraft(String token, String projectId) throws Exception {
        MvcResult result = mvc.perform(post("/agency-purchase/applications")
                        .headers(headers(token, projectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agencyBody("ENT_MEMBER_001", "ENT_TRADE_001", "ORD001")))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result, "$.data.id");
    }

    private org.springframework.http.HttpHeaders headers(String token, String projectId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-AGP-TEST");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
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

    private String warehouseToken() {
        return token("U004", "warehouse_user", "ENT_WH_001", "ROLE_WAREHOUSE", "ID004");
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }

    private String agencyBody(String customerId, String tradeCompanyId, String orderId) {
        return agencyBody(customerId, tradeCompanyId, orderId, "100000.00");
    }

    private String agencyBody(String customerId, String tradeCompanyId, String orderId, String amount) {
        return """
                {
                  "order_mode": "STOCK_ORDER",
                  "fund_source": "SELF_FUNDED",
                  "pickup_type": "PAYMENT_PICKUP",
                  "customer_id": "%s",
                  "trade_company_id": "%s",
                  "order_id": "%s",
                  "currency": "CNY",
                  "total_amount": "%s",
                  "remark": "integration-test"
                }
                """.formatted(customerId, tradeCompanyId, orderId, amount);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String readJson(MvcResult result, String field) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode node = root.at(field.replace("$.data.", "/data/").replace("$.code", "/code"));
        return node.isMissingNode() ? null : node.asText();
    }
}
