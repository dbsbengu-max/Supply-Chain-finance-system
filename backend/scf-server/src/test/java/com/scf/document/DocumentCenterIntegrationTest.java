package com.scf.document;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentCenterIntegrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String OTHER_PROJECT_ID = "PJ_TEST_OTHER";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void ea038RegisterAndListDocument() throws Exception {
        String fileId = uploadFile(platformToken(), PROJECT_ID);
        String body = """
                {
                  "business_type": "TRADE_ORDER",
                  "business_id": "ORD001",
                  "document_type": "INVOICE",
                  "file_id": "%s",
                  "document_no": "INV-EA038-001"
                }
                """.formatted(fileId);

        MvcResult created = mvc.perform(post("/documents/center")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        String docId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(get("/documents/center")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .param("business_id", "ORD001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber());

        mvc.perform(get("/documents/center/" + docId)
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.document_no").value("INV-EA038-001"));
    }

    @Test
    void ea038OcrWritesJobId() throws Exception {
        String fileId = uploadFile(platformToken(), PROJECT_ID);
        String docId = registerDocument(fileId, "PACKING_LIST");

        mvc.perform(post("/documents/center/" + docId + "/ocr")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ocr_job_id").exists())
                .andExpect(jsonPath("$.data.document_status").value("OCR_COMPLETED"));
    }

    @Test
    void ea038ValidateMissingRequiredDocuments() throws Exception {
        seedFinance("FIN_EA038_MISSING");
        mvc.perform(post("/documents/validate")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_id": "FIN_EA038_MISSING",
                                  "business_stage": "DISBURSE",
                                  "product_type": "AGENCY_PURCHASE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.missing.length()").value(2));
    }

    @Test
    void ea038ValidateLowConfidenceWarning() throws Exception {
        seedFinance("FIN_EA038_WARN");
        String fileId = uploadFile(platformToken(), PROJECT_ID);
        String docId = registerFinanceDocument(fileId, "FIN_EA038_WARN", "INVOICE");
        mvc.perform(post("/documents/center/" + docId + "/ocr")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE scf.tr_document SET ocr_confidence = 0.7000, review_status = 'PENDING' WHERE id = ?",
                docId);

        mvc.perform(post("/documents/validate")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_id": "FIN_EA038_WARN",
                                  "business_stage": "DISBURSE",
                                  "product_type": "AGENCY_PURCHASE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnings.length()").value(1));
    }

    @Test
    void ea038ApproveWritesReviewLog() throws Exception {
        String docId = registerDocument(uploadFile(platformToken(), PROJECT_ID), "INVOICE");
        mvc.perform(post("/documents/center/" + docId + "/submit-review")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk());

        mvc.perform(post("/documents/center/" + docId + "/approve")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"复核通过\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.review_status").value("APPROVED"))
                .andExpect(jsonPath("$.data.review_logs[0].action").value("APPROVE"));
    }

    @Test
    void ea038RejectWithoutReasonReturns400() throws Exception {
        String docId = registerDocument(uploadFile(platformToken(), PROJECT_ID), "INVOICE");
        mvc.perform(post("/documents/center/" + docId + "/submit-review")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isOk());

        mvc.perform(post("/documents/center/" + docId + "/reject")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
    }

    @Test
    void ea038CrossProjectDocumentIsHidden() throws Exception {
        String docId = registerDocument(uploadFile(platformToken(), PROJECT_ID), "INVOICE");
        mvc.perform(get("/documents/center/" + docId)
                        .headers(headers(platformToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea038CrossProjectFinanceValidateDoesNotReadOtherProjectDocuments() throws Exception {
        seedFinance("FIN_EA038_SCOPE");
        String fileId = uploadFile(platformToken(), PROJECT_ID);
        registerFinanceDocument(fileId, "FIN_EA038_SCOPE", "PURCHASE_CONTRACT");

        mvc.perform(post("/documents/validate")
                        .headers(headers(platformToken(), OTHER_PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_id": "FIN_EA038_SCOPE",
                                  "business_stage": "DISBURSE",
                                  "product_type": "AGENCY_PURCHASE"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea038MemberCannotManageRequirements() throws Exception {
        mvc.perform(post("/documents/requirements")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_stage": "DISBURSE",
                                  "document_type": "CUSTOM_DOC"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void ea038ArchivedDocumentCannotBeReviewedAgain() throws Exception {
        String docId = registerDocument(uploadFile(platformToken(), PROJECT_ID), "INVOICE");
        mvc.perform(post("/documents/center/" + docId + "/archive")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"作废\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/documents/center/" + docId + "/submit-review")
                        .headers(headers(platformToken(), PROJECT_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void ea038PlatformCanManageRequirements() throws Exception {
        MvcResult created = mvc.perform(post("/documents/requirements")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "project_id": "PJ001",
                                  "business_type": "FINANCE",
                                  "business_stage": "APPROVE",
                                  "document_type": "CREDIT_MEMO",
                                  "required_flag": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        String reqId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(put("/documents/requirements/" + reqId)
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_stage": "APPROVE",
                                  "document_type": "CREDIT_MEMO",
                                  "required_flag": false,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.required_flag").value(false));
    }

    private String registerFinanceDocument(String fileId, String businessId, String documentType) throws Exception {
        MvcResult result = mvc.perform(post("/documents/center")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "FINANCE",
                                  "business_id": "%s",
                                  "document_type": "%s",
                                  "file_id": "%s"
                                }
                                """.formatted(businessId, documentType, fileId)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String registerDocument(String fileId, String documentType) throws Exception {
        MvcResult result = mvc.perform(post("/documents/center")
                        .headers(headers(platformToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "business_type": "TRADE_ORDER",
                                  "business_id": "ORD001",
                                  "document_type": "%s",
                                  "file_id": "%s"
                                }
                                """.formatted(documentType, fileId)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String uploadFile(String token, String projectId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ea038.pdf", "application/pdf", "ea038-test".getBytes());
        MvcResult result = mvc.perform(multipart("/files/upload")
                        .file(file)
                        .headers(headers(token, projectId))
                        .param("business_type", "TRADE_ORDER")
                        .param("business_id", "ORD001"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.file_id");
    }

    private void seedFinance(String financeId) {
        jdbcTemplate.update("""
                INSERT INTO scf.fn_finance_application (
                  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
                  product_type, source_type, source_id, apply_amount, approved_amount, currency,
                  term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by,
                  disbursed_amount, deleted_flag, version_no
                )
                SELECT ?, 'OP001', 'PJ001', ?, 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
                       'AGENCY_PURCHASE', 'ORDER', 'ORD001', 100000.00, 100000.00, 'CNY',
                       90, 0.080000, 0.00, NULL, 'TO_DISBURSE', 'U003',
                       0.00, 0, 1
                WHERE NOT EXISTS (SELECT 1 FROM scf.fn_finance_application WHERE id = ?)
                """, financeId, "FIN-NO-" + financeId, financeId);
    }

    private HttpHeaders headers(String token, String projectId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "EA038-TEST");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        return headers;
    }

    private String platformToken() {
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
                userId, loginName, OPERATOR_ID, PROJECT_ID, enterpriseId, roleId, identityId));
    }
}
