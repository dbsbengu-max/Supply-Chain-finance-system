-- EA-034 IAM seed (extracted from V1_1_004 — do not edit V1_1_004 in place)
-- Apply: .\scripts\apply-seed-profile.ps1 -Profile iam
-- Idempotent: safe to re-run on staging

SET search_path TO scf;

INSERT INTO sys_operator (id, operator_code, operator_name, country_region, contact_name, contact_mobile, status, created_by)
VALUES ('OP001', 'YLT', '易链通运营平台', 'CN_MAINLAND', '平台管理员', '+86-13800000000', 'ENABLED', 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_project (id, operator_id, project_code, project_name, countries, currencies, status, created_by)
VALUES ('PJ001', 'OP001', 'CN-HK-MY-001', '中国-香港-马来西亚供应链金融试点', 'CN_MAINLAND,HK,MY', 'CNY,HKD,USD,MYR', 'ACTIVE', 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user (id, login_name, mobile, email, user_name, password_hash, mfa_enabled, status, created_by)
VALUES
('U001', 'platform_admin', '+86-13800000001', 'admin@example.com', '平台管理员', 'mock_hash', 1, 'ACTIVE', 'system'),
('U002', 'funding_user', '+86-13800000002', 'fund@example.com', '资金方用户', 'mock_hash', 1, 'ACTIVE', 'system'),
('U003', 'member_user', '+60-100000001', 'member@example.com', '融资客户用户', 'mock_hash', 1, 'ACTIVE', 'system'),
('U004', 'warehouse_user', '+86-13800000004', 'wh@example.com', '仓库用户', 'mock_hash', 1, 'ACTIVE', 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_role (id, operator_id, role_code, role_name, role_type, data_scope, status, created_by)
VALUES
('ROLE_PLATFORM_ADMIN', 'OP001', 'PLATFORM_ADMIN', '平台运营管理员', 'PLATFORM', 'OPERATOR', 'ENABLED', 'system'),
('ROLE_FUNDING', 'OP001', 'FUNDING_PARTY', '资金方用户', 'FUNDING', 'FUNDING_PARTY', 'ENABLED', 'system'),
('ROLE_MEMBER', 'OP001', 'MEMBER_ENTERPRISE', '成员企业用户', 'ENTERPRISE', 'ENTERPRISE', 'ENABLED', 'system'),
('ROLE_WAREHOUSE', 'OP001', 'WAREHOUSE_OPERATOR', '仓库方用户', 'WAREHOUSE', 'WAREHOUSE', 'ENABLED', 'system')
ON CONFLICT (id) DO NOTHING;

-- Minimal enterprises required for sys_user_identity FK
INSERT INTO md_enterprise (id, operator_id, enterprise_code, enterprise_name, enterprise_type, country_region, registration_no, unified_credit_code, legal_person, kyc_status, risk_level, status, created_by)
VALUES
('ENT_CORE_001', 'OP001', 'CORE001', '芯星链核心企业有限公司', 'CORE_ENTERPRISE', 'CN_MAINLAND', NULL, '91310000CORE001', '张三', 'APPROVED', 'LOW', 'ACTIVE', 'system'),
('ENT_MEMBER_001', 'OP001', 'MEM001', '百农汇马来西亚贸易有限公司', 'MEMBER_ENTERPRISE', 'MY', 'MY-MEM-001', NULL, 'Lim', 'APPROVED', 'MEDIUM', 'ACTIVE', 'system'),
('ENT_TRADE_001', 'OP001', 'TRADE001', '香港代采贸易有限公司', 'TRADE_COMPANY', 'HK', 'HK-TRADE-001', NULL, 'Chan', 'APPROVED', 'LOW', 'ACTIVE', 'system'),
('ENT_BANK_001', 'OP001', 'BANK001', '试点银行', 'BANK', 'CN_MAINLAND', NULL, '91310000BANK001', '李四', 'APPROVED', 'LOW', 'ACTIVE', 'system'),
('ENT_FACTOR_001', 'OP001', 'FACTOR001', '试点保理公司', 'FACTORING', 'CN_MAINLAND', NULL, '91310000FACTOR1', '王五', 'APPROVED', 'LOW', 'ACTIVE', 'system'),
('ENT_WH_001', 'OP001', 'WH001', '深圳监管仓', 'WAREHOUSE', 'CN_MAINLAND', NULL, '91310000WH001', '赵六', 'APPROVED', 'LOW', 'ACTIVE', 'system'),
('ENT_LOG_001', 'OP001', 'LOG001', '跨境货代公司', 'LOGISTICS', 'HK', 'HK-LOG-001', NULL, 'Wong', 'APPROVED', 'LOW', 'ACTIVE', 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_identity (id, user_id, operator_id, project_id, enterprise_id, role_id, is_default, status)
VALUES
('ID001', 'U001', 'OP001', 'PJ001', 'ENT_CORE_001', 'ROLE_PLATFORM_ADMIN', 1, 'ACTIVE'),
('ID002', 'U002', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ROLE_FUNDING', 1, 'ACTIVE'),
('ID003', 'U003', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ROLE_MEMBER', 1, 'ACTIVE'),
('ID004', 'U004', 'OP001', 'PJ001', 'ENT_WH_001', 'ROLE_WAREHOUSE', 1, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
