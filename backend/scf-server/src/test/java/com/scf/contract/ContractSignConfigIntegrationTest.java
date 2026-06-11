package com.scf.contract;

import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/permission_test_seed.sql")
class ContractSignConfigIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Test
    void ea041PlatformCanReadSignConfigAndProviders() throws Exception {
        mvc.perform(get("/integrations/contracts/sign/config")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.default_provider").value("MOCK"))
                .andExpect(jsonPath("$.data.callback_verification_mode").value("TOKEN"))
                .andExpect(jsonPath("$.data.compensation_pool_enabled").value(true));

        mvc.perform(get("/integrations/contracts/sign/providers")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.provider_code=='MOCK')].display_name").exists());
    }

    @Test
    void ea041MemberCannotReadSignConfig() throws Exception {
        mvc.perform(get("/integrations/contracts/sign/config")
                        .headers(headers(memberToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    private HttpHeaders headers(String token, String projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-EA041-CFG");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        return headers;
    }

    private String platformToken() {
        return token("U001", "platform_admin", "ENT_PLATFORM_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private String memberToken() {
        return token("U003", "member_user", "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String token(String userId, String loginName, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }
}
