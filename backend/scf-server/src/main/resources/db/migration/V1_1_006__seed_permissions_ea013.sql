SET search_path TO scf;

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_CUSTOMER_VIEW', 'CUSTOMER_VIEW', '客户查看', 'API', 'CUSTOMER', 'GET', '/customers/**'),
    ('PERM_CUSTOMER_CREATE', 'CUSTOMER_CREATE', '客户创建', 'API', 'CUSTOMER', 'POST', '/customers/enterprises'),
    ('PERM_CUSTOMER_UPDATE', 'CUSTOMER_UPDATE', '客户更新', 'API', 'CUSTOMER', 'PUT', '/customers/enterprises/*'),
    ('PERM_CUSTOMER_KYC_SUBMIT', 'CUSTOMER_KYC_SUBMIT', 'KYC提交', 'API', 'CUSTOMER', 'POST', '/customers/enterprises/*/submit-kyc'),
    ('PERM_CUSTOMER_KYC_APPROVE', 'CUSTOMER_KYC_APPROVE', 'KYC通过', 'API', 'CUSTOMER', 'POST', '/customers/enterprises/*/approve-kyc'),
    ('PERM_CUSTOMER_KYC_REJECT', 'CUSTOMER_KYC_REJECT', 'KYC驳回', 'API', 'CUSTOMER', 'POST', '/customers/enterprises/*/reject-kyc'),
    ('PERM_CUSTOMER_CERT_VIEW', 'CUSTOMER_CERT_VIEW', '客户证照查看', 'API', 'CUSTOMER', 'GET', '/customers/enterprises/*/certs'),
    ('PERM_CUSTOMER_CERT_UPLOAD', 'CUSTOMER_CERT_UPLOAD', '客户证照上传', 'API', 'CUSTOMER', 'POST', '/customers/enterprises/*/certs'),
    ('PERM_CUSTOMER_ACCOUNT_VIEW', 'CUSTOMER_ACCOUNT_VIEW', '客户账户查看', 'API', 'CUSTOMER', 'GET', '/customers/enterprises/*/bank-accounts'),
    ('PERM_CUSTOMER_ACCOUNT_CREATE', 'CUSTOMER_ACCOUNT_CREATE', '客户账户创建', 'API', 'CUSTOMER', 'POST', '/customers/enterprises/*/bank-accounts'),
    ('PERM_PROJECT_VIEW', 'PROJECT_VIEW', '项目查看', 'API', 'PROJECT', 'GET', '/projects/**'),
    ('PERM_PROJECT_CREATE', 'PROJECT_CREATE', '项目创建', 'API', 'PROJECT', 'POST', '/projects'),
    ('PERM_PROJECT_UPDATE', 'PROJECT_UPDATE', '项目更新', 'API', 'PROJECT', 'PUT', '/projects/*'),
    ('PERM_ORDER_VIEW', 'ORDER_VIEW', '订单查看', 'API', 'TRADE_ORDER', 'GET', '/trade/orders/**'),
    ('PERM_ORDER_CREATE', 'ORDER_CREATE', '订单创建', 'API', 'TRADE_ORDER', 'POST', '/trade/orders'),
    ('PERM_ORDER_UPDATE', 'ORDER_UPDATE', '订单更新', 'API', 'TRADE_ORDER', 'PUT', '/trade/orders/*'),
    ('PERM_ORDER_SUBMIT', 'ORDER_SUBMIT', '订单提交', 'API', 'TRADE_ORDER', 'POST', '/trade/orders/*/submit'),
    ('PERM_ORDER_CONFIRM', 'ORDER_CONFIRM', '订单确认', 'API', 'TRADE_ORDER', 'POST', '/trade/orders/*/confirm'),
    ('PERM_ORDER_CANCEL', 'ORDER_CANCEL', '订单取消', 'API', 'TRADE_ORDER', 'POST', '/trade/orders/*/cancel'),
    ('PERM_ORDER_VALIDATE', 'ORDER_VALIDATE', '贸易背景校验', 'API', 'TRADE_ORDER', 'POST', '/trade/orders/*/validate-background'),
    ('PERM_DOCUMENT_VIEW', 'DOCUMENT_VIEW', '单据查看', 'API', 'DOCUMENT', 'GET', '/trade/orders/*/documents'),
    ('PERM_DOCUMENT_UPLOAD', 'DOCUMENT_UPLOAD', '单据登记', 'API', 'DOCUMENT', 'POST', '/trade/orders/*/documents'),
    ('PERM_FILE_VIEW', 'FILE_VIEW', '文件查看', 'API', 'FILE', 'GET', '/files/**'),
    ('PERM_FILE_UPLOAD', 'FILE_UPLOAD', '文件上传', 'API', 'FILE', 'POST', '/files/upload'),
    ('PERM_FILE_DELETE', 'FILE_DELETE', '文件删除', 'API', 'FILE', 'DELETE', '/files/*'),
    ('PERM_AI_OCR_EXECUTE', 'AI_OCR_EXECUTE', 'OCR识别执行', 'API', 'AI_OCR', 'POST', '/ai/ocr/jobs'),
    ('PERM_AI_OCR_VIEW', 'AI_OCR_VIEW', 'OCR结果查看', 'API', 'AI_OCR', 'GET', '/ai/ocr/jobs/*'),
    ('PERM_OCR_RESULT_CONFIRM', 'OCR_RESULT_CONFIRM', 'OCR结果确认', 'API', 'AI_OCR', 'POST', '/ai/ocr/jobs/*/confirm'),
    ('PERM_CUSTOMER_CERT_CONFIRM', 'CUSTOMER_CERT_CONFIRM', '客户证照OCR确认', 'API', 'CUSTOMER', 'POST', '/customers/certs/*/confirm-ocr'),
    ('PERM_CUSTOMER_ACCOUNT_VERIFY', 'CUSTOMER_ACCOUNT_VERIFY', '客户账户认证', 'API', 'CUSTOMER', 'POST', '/customers/bank-accounts/*/verify'),
    ('PERM_EXCEL_IMPORT', 'EXCEL_IMPORT', 'Excel导入', 'API', 'IMPORT', 'POST', '/imports/excel/jobs'),
    ('PERM_EXCEL_IMPORT_CONFIRM', 'EXCEL_IMPORT_CONFIRM', 'Excel导入确认', 'API', 'IMPORT', 'POST', '/imports/excel/jobs/*/confirm'),
    ('PERM_PRICE_VIEW', 'PRICE_VIEW', '价格查看', 'API', 'PRICE', 'GET', '/pricing/**'),
    ('PERM_PRICE_CREATE', 'PRICE_CREATE', '价格创建', 'API', 'PRICE', 'POST', '/pricing/prices'),
    ('PERM_PRICE_SUBMIT', 'PRICE_SUBMIT', '价格提交', 'API', 'PRICE', 'POST', '/pricing/prices/*/submit'),
    ('PERM_PRICE_APPROVE', 'PRICE_APPROVE', '价格审批通过', 'API', 'PRICE', 'POST', '/pricing/prices/*/approve'),
    ('PERM_PRICE_REJECT', 'PRICE_REJECT', '价格审批驳回', 'API', 'PRICE', 'POST', '/pricing/prices/*/reject'),
    ('PERM_PRICE_IMPORT', 'PRICE_IMPORT', '价格Excel导入', 'API', 'PRICE', 'POST', '/pricing/prices/import'),
    ('PERM_PRICE_CATEGORY_CREATE', 'PRICE_CATEGORY_CREATE', '品类创建', 'API', 'PRICE', 'POST', '/pricing/categories'),
    ('PERM_PRICE_SKU_CREATE', 'PRICE_SKU_CREATE', 'SKU创建', 'API', 'PRICE', 'POST', '/pricing/skus'),
    ('PERM_PRICE_VALUATION_VIEW', 'PRICE_VALUATION_VIEW', '估值查看', 'API', 'VALUATION', 'GET', '/pricing/valuations/**'),
    ('PERM_PRICE_VALUATION', 'PRICE_VALUATION', '估值计算', 'API', 'VALUATION', 'POST', '/pricing/valuations/**'),
    ('PERM_FINANCE_VIEW', 'FINANCE_VIEW', '融资查看', 'API', 'FINANCE', 'GET', '/finance/**'),
    ('PERM_FINANCE_CREATE', 'FINANCE_CREATE', '融资申请创建', 'API', 'FINANCE', 'POST', '/finance/applications'),
    ('PERM_FINANCE_APPROVE', 'FINANCE_APPROVE', '融资审批', 'API', 'FINANCE', 'POST', '/finance/applications/*/approve'),
    ('PERM_FINANCE_DISBURSE', 'FINANCE_DISBURSE', '放款执行', 'API', 'FINANCE', 'POST', '/finance/applications/*/disburse'),
    ('PERM_CLEARING_VIEW', 'CLEARING_VIEW', '清分查看', 'API', 'CLEARING', 'GET', '/accounts/clearing/**'),
    ('PERM_CLEARING_RULE_CREATE', 'CLEARING_RULE_CREATE', '清分规则创建', 'API', 'CLEARING', 'POST', '/accounts/clearing-rules'),
    ('PERM_CLEARING_CALCULATE', 'CLEARING_CALCULATE', '清分试算', 'API', 'CLEARING', 'POST', '/accounts/clearing/calculate'),
    ('PERM_CLEARING_EXECUTE', 'CLEARING_EXECUTE', '清分执行', 'API', 'CLEARING', 'POST', '/accounts/clearing/execute'),
    ('PERM_VOUCHER_VIEW', 'VOUCHER_VIEW', '凭证查看', 'API', 'VOUCHER', 'GET', '/dv/vouchers/**'),
    ('PERM_VOUCHER_TRANSFER', 'VOUCHER_TRANSFER', '凭证转让', 'API', 'VOUCHER', 'POST', '/dv/vouchers/*/transfer'),
    ('PERM_VOUCHER_SPLIT', 'VOUCHER_SPLIT', '凭证拆分', 'API', 'VOUCHER', 'POST', '/dv/vouchers/*/split'),
    ('PERM_WAREHOUSE_VIEW', 'WAREHOUSE_VIEW', '仓储查看', 'API', 'WAREHOUSE', 'GET', '/warehouse/**'),
    ('PERM_WAREHOUSE_PLEDGE', 'WAREHOUSE_PLEDGE', '库存质押', 'API', 'WAREHOUSE', 'POST', '/warehouse/inventories/*/pledge'),
    ('PERM_WAREHOUSE_RELEASE', 'WAREHOUSE_RELEASE', '质押释放', 'API', 'WAREHOUSE', 'POST', '/warehouse/inventories/*/release'),
    ('PERM_BPM_TASK_VIEW', 'BPM_TASK_VIEW', '待办查看', 'API', 'BPM', 'GET', '/bpm/tasks/**'),
    ('PERM_BPM_APPROVE', 'BPM_APPROVE', '审批处理', 'API', 'BPM', 'POST', '/bpm/tasks/*/approve'),
    ('PERM_BI_VIEW', 'BI_VIEW', 'BI查看', 'API', 'BI', 'GET', '/bi/**'),
    ('PERM_BI_EXPORT', 'BI_EXPORT', 'BI导出', 'API', 'BI', 'POST', '/bi/**/export'),
    ('PERM_BI_DRILLDOWN', 'BI_DRILLDOWN', 'BI下钻', 'API', 'BI', 'GET', '/bi/**/drilldown'),
    ('PERM_ORDER_EXPORT', 'ORDER_EXPORT', '订单导出', 'API', 'TRADE_ORDER', 'POST', '/trade/orders/export')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission p
  WHERE p.operator_id = 'OP001' AND p.permission_code = perms.code
);

WITH explicit_role_perm AS (
  SELECT 'ROLE_FUNDING' AS role_id, 'CUSTOMER_VIEW' AS permission_code
  UNION ALL SELECT 'ROLE_FUNDING', 'CUSTOMER_KYC_APPROVE'
  UNION ALL SELECT 'ROLE_FUNDING', 'CUSTOMER_KYC_REJECT'
  UNION ALL SELECT 'ROLE_FUNDING', 'CUSTOMER_CERT_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'CUSTOMER_ACCOUNT_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'PROJECT_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'ORDER_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'ORDER_VALIDATE'
  UNION ALL SELECT 'ROLE_FUNDING', 'DOCUMENT_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'FILE_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'PRICE_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'PRICE_VALUATION_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'PRICE_VALUATION'
  UNION ALL SELECT 'ROLE_FUNDING', 'FINANCE_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'FINANCE_APPROVE'
  UNION ALL SELECT 'ROLE_FUNDING', 'FINANCE_DISBURSE'
  UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_CALCULATE'
  UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_EXECUTE'
  UNION ALL SELECT 'ROLE_FUNDING', 'VOUCHER_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'WAREHOUSE_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'BPM_TASK_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'BPM_APPROVE'
  UNION ALL SELECT 'ROLE_FUNDING', 'BI_VIEW'
  UNION ALL SELECT 'ROLE_FUNDING', 'BI_DRILLDOWN'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_CREATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_UPDATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_KYC_SUBMIT'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_CERT_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_CERT_UPLOAD'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_ACCOUNT_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_ACCOUNT_CREATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'PROJECT_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'ORDER_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'ORDER_CREATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'ORDER_UPDATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'ORDER_SUBMIT'
  UNION ALL SELECT 'ROLE_MEMBER', 'ORDER_CANCEL'
  UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_UPLOAD'
  UNION ALL SELECT 'ROLE_MEMBER', 'FILE_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'FILE_UPLOAD'
  UNION ALL SELECT 'ROLE_MEMBER', 'AI_OCR_EXECUTE'
  UNION ALL SELECT 'ROLE_MEMBER', 'AI_OCR_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'OCR_RESULT_CONFIRM'
  UNION ALL SELECT 'ROLE_MEMBER', 'CUSTOMER_CERT_CONFIRM'
  UNION ALL SELECT 'ROLE_MEMBER', 'EXCEL_IMPORT'
  UNION ALL SELECT 'ROLE_MEMBER', 'EXCEL_IMPORT_CONFIRM'
  UNION ALL SELECT 'ROLE_MEMBER', 'PRICE_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'PRICE_VALUATION_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'PRICE_VALUATION'
  UNION ALL SELECT 'ROLE_MEMBER', 'FINANCE_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'FINANCE_CREATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'VOUCHER_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'VOUCHER_TRANSFER'
  UNION ALL SELECT 'ROLE_MEMBER', 'WAREHOUSE_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'BPM_TASK_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'BI_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'PROJECT_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'ORDER_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'DOCUMENT_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'FILE_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'PRICE_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'PRICE_VALUATION_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'PRICE_VALUATION'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'WAREHOUSE_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'WAREHOUSE_PLEDGE'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'WAREHOUSE_RELEASE'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'BPM_TASK_VIEW'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'BI_VIEW'
),
role_perm AS (
  SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, permission_code FROM sys_permission WHERE operator_id = 'OP001'
  UNION ALL
  SELECT role_id, permission_code FROM explicit_role_perm
)
INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT role_perm.role_id || ':' || p.id, role_perm.role_id, p.id, 'system'
FROM role_perm
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = role_perm.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = role_perm.role_id AND existing.permission_id = p.id
);

WITH field_perm(id, role_id, object_type, field_name, field_policy) AS (
  VALUES
  ('FIELD_FUNDING_ENTERPRISE_CREDIT_MASK', 'ROLE_FUNDING', 'ENTERPRISE', 'unified_credit_code', 'MASK'),
  ('FIELD_FUNDING_ENTERPRISE_LEGAL_MASK', 'ROLE_FUNDING', 'ENTERPRISE', 'legal_person', 'MASK'),
  ('FIELD_FUNDING_CERT_NO_MASK', 'ROLE_FUNDING', 'ENTERPRISE_CERT', 'cert_no', 'MASK'),
  ('FIELD_FUNDING_BANK_ACCOUNT_MASK', 'ROLE_FUNDING', 'BANK_ACCOUNT', 'account_no', 'MASK'),
  ('FIELD_MEMBER_RISK_SCORE_HIDE', 'ROLE_MEMBER', 'CUSTOMER_RISK', 'risk_score', 'HIDE'),
  ('FIELD_WAREHOUSE_FINANCE_RATE_HIDE', 'ROLE_WAREHOUSE', 'FINANCE', 'annual_rate', 'HIDE')
)
INSERT INTO sys_field_permission (id, role_id, object_type, field_name, field_policy)
SELECT id, role_id, object_type, field_name, field_policy
FROM field_perm
WHERE NOT EXISTS (
  SELECT 1 FROM sys_field_permission existing
  WHERE existing.role_id = field_perm.role_id
    AND existing.object_type = field_perm.object_type
    AND existing.field_name = field_perm.field_name
);

WITH scope_rule(id, role_id, scope_type, scope_expression, status) AS (
  VALUES
  ('SCOPE_PLATFORM_OPERATOR', 'ROLE_PLATFORM_ADMIN', 'OPERATOR_PROJECT', '{"operator":"current_operator","project":"current_project"}', 'ACTIVE'),
  ('SCOPE_FUNDING_PARTY', 'ROLE_FUNDING', 'FUNDING_PARTY', '{"operator":"current_operator","project":"current_project","funding_party":"current_enterprise"}', 'ACTIVE'),
  ('SCOPE_MEMBER_ENTERPRISE', 'ROLE_MEMBER', 'ENTERPRISE', '{"operator":"current_operator","project":"current_project","enterprise":"current_enterprise"}', 'ACTIVE'),
  ('SCOPE_WAREHOUSE', 'ROLE_WAREHOUSE', 'WAREHOUSE', '{"operator":"current_operator","project":"current_project","warehouse_company":"current_enterprise"}', 'ACTIVE')
)
INSERT INTO sys_data_scope_rule (id, role_id, scope_type, scope_expression, status)
SELECT id, role_id, scope_type, scope_expression, status
FROM scope_rule
WHERE NOT EXISTS (
  SELECT 1 FROM sys_data_scope_rule existing
  WHERE existing.role_id = scope_rule.role_id AND existing.scope_type = scope_rule.scope_type
);

