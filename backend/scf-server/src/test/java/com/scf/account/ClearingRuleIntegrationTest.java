package com.scf.account;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = {"/sql/permission_test_seed.sql", "/sql/clearing_rule_test_seed.sql"})
class ClearingRuleIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Test
    void ea017FundingCanCreateUpdateSubmitAndApproveOwnClearingRule() throws Exception {
        MvcResult create = mvc.perform(post("/accounts/clearing-rules")
                        .headers(headers(fundingToken(), "EA017-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 全流程规则", null, "ORDER_FINANCE", "2026-06-10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.funding_party_id").value("ENT_FACTOR_001"))
                .andExpect(jsonPath("$.data.review_status").value("DRAFT"))
                .andReturn();

        String ruleId = JsonPath.read(create.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(put("/accounts/clearing-rules/" + ruleId)
                        .headers(headers(fundingToken(), "EA017-UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 全流程规则-更新", null, "ORDER_FINANCE", "2026-06-11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule_name").value("EA017 全流程规则-更新"))
                .andExpect(jsonPath("$.data.review_status").value("DRAFT"));

        mvc.perform(post("/accounts/clearing-rules/" + ruleId + "/submit")
                        .headers(headers(fundingToken(), "EA017-SUBMIT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("PENDING"));

        mvc.perform(post("/accounts/clearing-rules/" + ruleId + "/approve")
                        .headers(headers(fundingToken(), "EA017-APPROVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("APPROVED"));

        mvc.perform(put("/accounts/clearing-rules/" + ruleId)
                        .headers(headers(fundingToken(), "EA017-UPDATE-APPROVED"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 已批准后更新", null, "ORDER_FINANCE", "2026-06-12")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void ea017MemberCannotListClearingRules() throws Exception {
        mvc.perform(get("/accounts/clearing-rules")
                        .headers(headers(memberToken(), "EA017-MEMBER-LIST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017MemberCannotCreateClearingRule() throws Exception {
        mvc.perform(post("/accounts/clearing-rules")
                        .headers(headers(memberToken(), "EA017-MEMBER-CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 会员创建", null, "ORDER_FINANCE", "2026-06-10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017MemberCannotUpdateClearingRule() throws Exception {
        mvc.perform(put("/accounts/clearing-rules/CLR_RULE_EA017_APPROVED")
                        .headers(headers(memberToken(), "EA017-MEMBER-UPDATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 会员更新", null, "ORDER_FINANCE", "2026-06-10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017MemberCannotSubmitClearingRule() throws Exception {
        mvc.perform(post("/accounts/clearing-rules/CLR_RULE_EA017_OTHER_FACTOR/submit")
                        .headers(headers(memberToken(), "EA017-MEMBER-SUBMIT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017MemberCannotApproveClearingRule() throws Exception {
        mvc.perform(post("/accounts/clearing-rules/CLR_RULE_EA017_OTHER_FACTOR/approve")
                        .headers(headers(memberToken(), "EA017-MEMBER-APPROVE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017FundingCannotCreateRuleForOtherFundingParty() throws Exception {
        mvc.perform(post("/accounts/clearing-rules")
                        .headers(headers(fundingToken(), "EA017-CREATE-OTHER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody("EA017 越权资方规则", "ENT_CORE_001", "ORDER_FINANCE", "2026-06-10")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017FundingCannotReadOtherFundingPartyPrivateRule() throws Exception {
        mvc.perform(get("/accounts/clearing-rules/CLR_RULE_EA017_OTHER_FACTOR")
                        .headers(headers(fundingToken(), "EA017-READ-OTHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea017InvalidPriorityJsonIsRejected() throws Exception {
        mvc.perform(post("/accounts/clearing-rules")
                        .headers(headers(fundingToken(), "EA017-BAD-JSON"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "product_type": "ORDER_FINANCE",
                                  "rule_name": "EA017 非法规则",
                                  "priority_json": "{\\"bad\\":[]}",
                                  "fee_formula_json": "{\\"interest\\":\\"principal*rate*days/360\\"}",
                                  "currency_rule": "ORIGINAL_CURRENCY",
                                  "effective_from": "2026-06-10"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
    }

    @Test
    void ea017PlatformAdminCanListAndApproveClearingRules() throws Exception {
        mvc.perform(get("/accounts/clearing-rules")
                        .headers(headers(platformAdminToken(), "EA017-ADMIN-LIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    private HttpHeaders headers(String token, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", requestId);
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", PROJECT_ID);
        return headers;
    }

    private String ruleBody(String name, String fundingPartyId, String productType, String effectiveFrom) {
        String funding = fundingPartyId == null ? "" : """
                  "funding_party_id": "%s",
                """.formatted(fundingPartyId);
        return """
                {
                %s
                  "product_type": "%s",
                  "rule_name": "%s",
                  "priority_json": "{\\"priority\\":[\\"interest\\",\\"principal\\"]}",
                  "fee_formula_json": "{\\"interest\\":\\"principal*rate*days/360\\"}",
                  "currency_rule": "ORIGINAL_CURRENCY",
                  "effective_from": "%s"
                }
                """.formatted(funding, productType, name, effectiveFrom);
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
                identityId
        ));
    }
}
