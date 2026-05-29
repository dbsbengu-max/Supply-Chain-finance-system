package com.scf.security;

import com.jayway.jsonpath.JsonPath;
import com.scf.common.security.DataScopeHelper;
import com.scf.common.security.JwtService;
import com.scf.common.security.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/sql/permission_test_seed.sql")
class PermissionPenetrationTest {

    private static final String OPERATOR_ID = "OP001";
    private static final String PROJECT_ID = "PJ001";
    private static final String OTHER_PROJECT_ID = "PJ_TEST_OTHER";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @Autowired
    DataScopeHelper dataScopeHelper;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetMutableFixtures() {
        jdbcTemplate.update("UPDATE scf.md_enterprise SET kyc_status = 'PENDING' WHERE id = 'ENT_KYC_PENDING'");
        jdbcTemplate.update("UPDATE scf.md_enterprise SET kyc_status = 'DRAFT' WHERE id = 'ENT_MEMBER_DRAFT'");
        jdbcTemplate.update("UPDATE scf.tr_order SET order_status = 'DRAFT', signed_at = NULL WHERE id = 'ORD_CANCEL_PLATFORM'");
        jdbcTemplate.update("UPDATE scf.tr_order SET order_status = 'SUBMITTED', signed_at = NULL WHERE id = 'ORD_SUBMITTED_PLATFORM'");
        jdbcTemplate.update("UPDATE scf.tr_order SET order_status = 'DRAFT', signed_at = NULL WHERE id = 'ORD_CANCEL_OTHER_CREATOR'");
    }

    @Test
    void perm001MemberBCannotReadEnterpriseAOrder() throws Exception {
        mvc.perform(get("/trade/orders/ORD001")
                        .headers(headers(memberBToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm002WarehouseCannotCreateFinance() throws Exception {
        mvc.perform(post("/finance/applications")
                        .headers(headers(warehouseToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(financeBody("ENT_MEMBER_001", "ENT_FACTOR_001", "ORD001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm003FundingCanApproveKyc() throws Exception {
        mvc.perform(post("/customers/enterprises/ENT_KYC_PENDING/approve-kyc")
                        .headers(headers(fundingToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kyc_status").value("APPROVED"));
    }

    @Test
    void perm004MemberCanSubmitOwnKyc() throws Exception {
        mvc.perform(post("/customers/enterprises/ENT_MEMBER_DRAFT/submit-kyc")
                        .headers(headers(memberDraftToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kyc_status").value("PENDING"));
    }

    @Test
    void perm005CrossProjectOrderReadIsRejectedByHeaderContext() throws Exception {
        mvc.perform(get("/trade/orders/ORD001")
                        .headers(headers(memberToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm006AnonymousCannotReadOrders() throws Exception {
        mvc.perform(get("/trade/orders")
                        .header("X-Request-Id", "PERM-006")
                        .header("X-Operator-Id", OPERATOR_ID)
                        .header("X-Project-Id", PROJECT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void perm007MemberCannotExportBiDashboard() throws Exception {
        mvc.perform(post("/bi/dashboard/export")
                        .headers(headers(memberToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm008FundingSeesMaskedBankAccount() throws Exception {
        mvc.perform(get("/customers/enterprises/ENT_MEMBER_001/bank-accounts")
                        .headers(headers(fundingToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].account_no").value(containsString("****")))
                .andExpect(jsonPath("$.data[0].account_no").value(not("6222000000000001")));
    }

    @Test
    void perm009PlatformAdminCanCancelOrder() throws Exception {
        mvc.perform(post("/trade/orders/ORD_CANCEL_PLATFORM/cancel")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_status").value("CANCELLED"));
    }

    @Test
    void perm010MemberCannotCancelOrderCreatedByOtherUser() throws Exception {
        mvc.perform(post("/trade/orders/ORD_CANCEL_OTHER_CREATOR/cancel")
                        .headers(headers(memberToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm011MemberCannotConfirmOrder() throws Exception {
        mvc.perform(post("/trade/orders/ORD_SUBMITTED_PLATFORM/confirm")
                        .headers(headers(memberToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm012PlatformAdminCanConfirmOrder() throws Exception {
        mvc.perform(post("/trade/orders/ORD_SUBMITTED_PLATFORM/confirm")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order_status").value("CONFIRMED"));
    }

    @Test
    void perm013FundingCanValidateTradeBackground() throws Exception {
        mvc.perform(post("/trade/orders/ORD001/validate-background")
                        .headers(headers(fundingToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").exists())
                .andExpect(jsonPath("$.data.risk_level").exists());
    }

    @Test
    void perm014WarehouseCannotUploadTradeDocument() throws Exception {
        mvc.perform(post("/trade/orders/ORD001/documents")
                        .headers(headers(warehouseToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "document_type": "INVOICE",
                                  "document_no": "INV-PERM-014",
                                  "file_id": "FILE-PERM-014"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm015MemberCannotUpdateConfirmedOrder() throws Exception {
        mvc.perform(put("/trade/orders/ORD001")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ENT_MEMBER_001", "ENT_CORE_001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_409"));
    }

    @Test
    void perm016CrossOperatorOrderReadIsRejectedByHeaderContext() throws Exception {
        mvc.perform(get("/trade/orders/ORD001")
                        .headers(headersWithOperator(memberToken(), "OP_TEST_OTHER", PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm017MissingProjectHeaderBlocksOrderCreate() throws Exception {
        mvc.perform(post("/trade/orders")
                        .headers(headersWithoutProject(memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ENT_MEMBER_001", "ENT_CORE_001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALID_400"));
    }

    @Test
    void perm018MemberBCannotOcrEnterpriseADocument() throws Exception {
        mvc.perform(post("/trade/documents/DOC_PERM_ORD001/ocr")
                        .headers(headers(memberBToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm019FundingCannotCreateOrder() throws Exception {
        mvc.perform(post("/trade/orders")
                        .headers(headers(fundingToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("ENT_MEMBER_001", "ENT_CORE_001")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm020PlatformAdminCanListOrders() throws Exception {
        mvc.perform(get("/trade/orders")
                        .headers(headers(platformAdminToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    void perm021CrossProjectOrderReadWithOtherProjectTokenIsHidden() throws Exception {
        mvc.perform(get("/trade/orders/ORD001")
                        .headers(headers(memberOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));
    }

    @Test
    void perm022CrossProjectFinanceReadWithOtherProjectTokenIsHidden() throws Exception {
        mvc.perform(get("/finance/applications/FIN001")
                        .headers(headers(memberOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));
    }

    @Test
    void perm023CrossProjectWarehouseReadWithOtherProjectTokenIsHidden() throws Exception {
        mvc.perform(get("/warehouse/inventories/INV001")
                        .headers(headers(warehouseOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void perm024FileOcrExcelAreHiddenAcrossProjects() throws Exception {
        String pdfFileId = uploadFile(memberToken(), PROJECT_ID, "perm.pdf", "application/pdf");
        String xlsxFileId = uploadFile(
                memberToken(),
                PROJECT_ID,
                "perm.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        mvc.perform(get("/files/" + pdfFileId)
                        .headers(headers(memberOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_404"));

        String ocrJobId = createOcrJob(pdfFileId);
        mvc.perform(get("/ai/ocr/jobs/" + ocrJobId)
                        .headers(headers(memberOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));

        String excelJobId = createExcelJob(xlsxFileId);
        mvc.perform(get("/imports/excel/jobs/" + excelJobId)
                        .headers(headers(memberOtherProjectToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DATA_404"));
    }

    @Test
    void perm025HeaderProjectMustMatchJwtProject() throws Exception {
        mvc.perform(get("/finance/applications/FIN001")
                        .headers(headers(memberToken(), OTHER_PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm026DataScopeHelperFourRoleBaseline() {
        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.tradeOrderScope(platformAdminContext()));
        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.tradeOrderScope(fundingContext()));
        assertEquals(DataScopeHelper.ScopeType.ENTERPRISE, dataScopeHelper.tradeOrderScope(memberContext()));
        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.tradeOrderScope(warehouseContext()));

        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.financeScope(platformAdminContext()));
        assertEquals(DataScopeHelper.ScopeType.FUNDING_PARTY, dataScopeHelper.financeScope(fundingContext()));
        assertEquals(DataScopeHelper.ScopeType.ENTERPRISE, dataScopeHelper.financeScope(memberContext()));
        assertEquals(DataScopeHelper.ScopeType.NONE, dataScopeHelper.financeScope(warehouseContext()));

        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.warehouseInventoryScope(platformAdminContext()));
        assertEquals(DataScopeHelper.ScopeType.OPERATOR_PROJECT, dataScopeHelper.warehouseInventoryScope(fundingContext()));
        assertEquals(DataScopeHelper.ScopeType.ENTERPRISE, dataScopeHelper.warehouseInventoryScope(memberContext()));
        assertEquals(DataScopeHelper.ScopeType.WAREHOUSE_COMPANY, dataScopeHelper.warehouseInventoryScope(warehouseContext()));
    }

    @Test
    void perm027MemberCannotListBankFlows() throws Exception {
        mvc.perform(get("/accounts/bank-flows")
                        .headers(headers(memberToken(), PROJECT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm028MemberCannotExecuteClearing() throws Exception {
        mvc.perform(post("/accounts/clearing/execute")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .header("X-Idempotency-Key", "PERM-028")
                        .header("X-Secondary-Auth-Token", "MOCK-APPROVED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "finance_id": "FIN001",
                                  "bank_flow_id": "FLOW_PERM_028",
                                  "clearing_rule_id": "CLR_RULE_001"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403"));
    }

    @Test
    void perm029FundingCanViewClearingEntry() throws Exception {
        mvc.perform(get("/accounts/clearing/entry")
                        .headers(headers(fundingToken(), PROJECT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unmatched_flows").isArray());
    }

    private String uploadFile(String token, String projectId, String fileName, String contentType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, contentType, "hello".getBytes());
        MvcResult result = mvc.perform(multipart("/files/upload")
                        .file(file)
                        .headers(headers(token, projectId))
                        .param("business_type", "PERM_TEST")
                        .param("business_id", "BIZ-PERM-TEST"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.file_id");
    }

    private String createOcrJob(String fileId) throws Exception {
        MvcResult result = mvc.perform(post("/ai/ocr/jobs")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "file_id": "%s",
                                  "business_type": "TRADE_DOCUMENT",
                                  "business_id": "ORD001",
                                  "recognition_type": "TABLE_OCR"
                                }
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createExcelJob(String fileId) throws Exception {
        MvcResult result = mvc.perform(post("/imports/excel/jobs")
                        .headers(headers(memberToken(), PROJECT_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "file_id": "%s",
                                  "import_type": "GENERIC",
                                  "dry_run": true
                                }
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private org.springframework.http.HttpHeaders headers(String token, String projectId) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-PERM-TEST");
        headers.add("X-Operator-Id", OPERATOR_ID);
        headers.add("X-Project-Id", projectId);
        return headers;
    }

    private org.springframework.http.HttpHeaders headersWithOperator(String token, String operatorId, String projectId) {
        org.springframework.http.HttpHeaders headers = headers(token, projectId);
        headers.set("X-Operator-Id", operatorId);
        return headers;
    }

    private org.springframework.http.HttpHeaders headersWithoutProject(String token) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.add("X-Request-Id", "REQ-PERM-TEST");
        headers.add("X-Operator-Id", OPERATOR_ID);
        return headers;
    }

    private String platformAdminToken() {
        return token("U001", "platform_admin", PROJECT_ID, "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private String fundingToken() {
        return token("U002", "funding_user", PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private String memberToken() {
        return token("U003", "member_user", PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String memberDraftToken() {
        return token("U003", "member_user", PROJECT_ID, "ENT_MEMBER_DRAFT", "ROLE_MEMBER", "ID003");
    }

    private String memberBToken() {
        return token("U005", "member_b_user", PROJECT_ID, "ENT_MEMBER_002", "ROLE_MEMBER", "ID005");
    }

    private String warehouseToken() {
        return token("U004", "warehouse_user", PROJECT_ID, "ENT_WH_001", "ROLE_WAREHOUSE", "ID004");
    }

    private String memberOtherProjectToken() {
        return token("U003", "member_user", OTHER_PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private String warehouseOtherProjectToken() {
        return token("U004", "warehouse_user", OTHER_PROJECT_ID, "ENT_WH_001", "ROLE_WAREHOUSE", "ID004");
    }

    private String token(String userId, String loginName, String projectId, String enterpriseId, String roleId, String identityId) {
        return jwtService.generateToken(new UserContext(
                userId,
                loginName,
                OPERATOR_ID,
                projectId,
                enterpriseId,
                roleId,
                identityId
        ));
    }

    private UserContext platformAdminContext() {
        return context("U001", "platform_admin", PROJECT_ID, "ENT_CORE_001", "ROLE_PLATFORM_ADMIN", "ID001");
    }

    private UserContext fundingContext() {
        return context("U002", "funding_user", PROJECT_ID, "ENT_FACTOR_001", "ROLE_FUNDING", "ID002");
    }

    private UserContext memberContext() {
        return context("U003", "member_user", PROJECT_ID, "ENT_MEMBER_001", "ROLE_MEMBER", "ID003");
    }

    private UserContext warehouseContext() {
        return context("U004", "warehouse_user", PROJECT_ID, "ENT_WH_001", "ROLE_WAREHOUSE", "ID004");
    }

    private UserContext context(String userId, String loginName, String projectId, String enterpriseId, String roleId, String identityId) {
        return new UserContext(userId, loginName, OPERATOR_ID, projectId, enterpriseId, roleId, identityId);
    }

    private String orderBody(String buyerId, String sellerId) {
        return """
                {
                  "order_type": "AGENCY_PURCHASE",
                  "buyer_id": "%s",
                  "seller_id": "%s",
                  "trade_company_id": "ENT_TRADE_001",
                  "currency": "CNY",
                  "country_from": "CN_MAINLAND",
                  "country_to": "MY",
                  "items": [
                    {
                      "sku_id": "SKU_GARLIC_A",
                      "quantity": "10",
                      "unit": "ton",
                      "unit_price": "8500"
                    }
                  ]
                }
                """.formatted(buyerId, sellerId);
    }

    private String financeBody(String customerId, String fundingPartyId, String sourceId) {
        return """
                {
                  "product_type": "ORDER_FINANCE",
                  "source_type": "ORDER",
                  "source_id": "%s",
                  "customer_id": "%s",
                  "funding_party_id": "%s",
                  "apply_amount": "100000",
                  "currency": "CNY",
                  "term_days": 90,
                  "annual_rate": "0.08"
                }
                """.formatted(sourceId, customerId, fundingPartyId);
    }
}
