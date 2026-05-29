SET search_path TO scf;

MERGE INTO sys_project (id, operator_id, project_code, project_name, countries, currencies, status, created_by)
KEY(id) VALUES
('PJ_TEST_OTHER', 'OP001', 'OTHER-PERM-TEST', '权限穿透测试其他项目', 'CN_MAINLAND', 'CNY', 'ACTIVE', 'test');

MERGE INTO md_enterprise (id, operator_id, enterprise_code, enterprise_name, enterprise_type, country_region, registration_no, unified_credit_code, legal_person, kyc_status, risk_level, status, created_by)
KEY(id) VALUES
('ENT_MEMBER_002', 'OP001', 'MEM002', '权限穿透测试成员企业二', 'MEMBER_ENTERPRISE', 'MY', 'MY-MEM-002', NULL, 'Test Lee', 'APPROVED', 'MEDIUM', 'ACTIVE', 'test'),
('ENT_MEMBER_DRAFT', 'OP001', 'MEMDRAFT', '权限穿透测试草稿成员企业', 'MEMBER_ENTERPRISE', 'MY', 'MY-MEM-DRAFT', NULL, 'Draft Lim', 'DRAFT', 'MEDIUM', 'ACTIVE', 'test'),
('ENT_KYC_PENDING', 'OP001', 'MEMPENDING', '权限穿透测试待审企业', 'MEMBER_ENTERPRISE', 'MY', 'MY-MEM-PENDING', NULL, 'Pending Lim', 'PENDING', 'MEDIUM', 'ACTIVE', 'test');

MERGE INTO sys_user (id, login_name, mobile, email, user_name, password_hash, mfa_enabled, status, created_by)
KEY(id) VALUES
('U005', 'member_b_user', '+60-100000002', 'member-b@example.com', '成员企业B用户', 'mock_hash', 1, 'ACTIVE', 'test');

MERGE INTO sys_user_identity (id, user_id, operator_id, project_id, enterprise_id, role_id, is_default, status)
KEY(id) VALUES
('ID005', 'U005', 'OP001', 'PJ001', 'ENT_MEMBER_002', 'ROLE_MEMBER', 1, 'ACTIVE');

MERGE INTO md_bank_account (id, enterprise_id, account_type, bank_name, account_name, account_no, currency, is_repayment_account, verification_status, created_by)
KEY(id) VALUES
('BANK_PERM_001', 'ENT_MEMBER_001', 'BASIC', 'Mock Bank', '百农汇回款账户', '6222000000000001', 'CNY', 1, 'VERIFIED', 'test');

MERGE INTO tr_order (id, operator_id, project_id, order_no, order_type, buyer_id, seller_id, trade_company_id, total_amount, currency, country_from, country_to, order_status, signed_at, created_by)
KEY(id) VALUES
('ORD_CANCEL_PLATFORM', 'OP001', 'PJ001', 'ORD-PERM-CANCEL-PLATFORM', 'AGENCY_PURCHASE', 'ENT_MEMBER_001', 'ENT_CORE_001', 'ENT_TRADE_001', 1000.00, 'CNY', 'CN_MAINLAND', 'MY', 'DRAFT', NULL, 'U001'),
('ORD_SUBMITTED_PLATFORM', 'OP001', 'PJ001', 'ORD-PERM-CONFIRM-PLATFORM', 'AGENCY_PURCHASE', 'ENT_MEMBER_001', 'ENT_CORE_001', 'ENT_TRADE_001', 1000.00, 'CNY', 'CN_MAINLAND', 'MY', 'SUBMITTED', NULL, 'U003'),
('ORD_CANCEL_OTHER_CREATOR', 'OP001', 'PJ001', 'ORD-PERM-CANCEL-OTHER', 'AGENCY_PURCHASE', 'ENT_MEMBER_001', 'ENT_CORE_001', 'ENT_TRADE_001', 1000.00, 'CNY', 'CN_MAINLAND', 'MY', 'DRAFT', NULL, 'U001');

MERGE INTO tr_order_item (id, order_id, sku_id, quantity, unit, unit_price, amount, delivery_date)
KEY(id) VALUES
('ORDITEM_CANCEL_PLATFORM', 'ORD_CANCEL_PLATFORM', 'SKU_GARLIC_A', 1.000000, 'ton', 1000.000000, 1000.00, '2026-06-15'),
('ORDITEM_SUBMITTED_PLATFORM', 'ORD_SUBMITTED_PLATFORM', 'SKU_GARLIC_A', 1.000000, 'ton', 1000.000000, 1000.00, '2026-06-15'),
('ORDITEM_CANCEL_OTHER', 'ORD_CANCEL_OTHER_CREATOR', 'SKU_GARLIC_A', 1.000000, 'ton', 1000.000000, 1000.00, '2026-06-15');

MERGE INTO tr_document (id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id, ocr_status, validation_status, created_by)
KEY(id) VALUES
('DOC_PERM_ORD001', 'OP001', 'PJ001', 'TRADE_ORDER', 'ORD001', 'INVOICE', 'INV-PERM-001', 'FILE-PERM-ORD001', 'PENDING', 'PENDING', 'U003');
