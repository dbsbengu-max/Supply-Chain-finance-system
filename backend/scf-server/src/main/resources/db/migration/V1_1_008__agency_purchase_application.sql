SET search_path TO scf;

CREATE TABLE IF NOT EXISTS ap_agency_purchase_application (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL,
  project_id varchar(64) NOT NULL,
  application_no varchar(64) NOT NULL,
  order_mode varchar(32) NOT NULL,
  fund_source varchar(32) NOT NULL,
  pickup_type varchar(32) NOT NULL,
  mode_key varchar(64) NOT NULL,
  customer_id varchar(64) NOT NULL,
  trade_company_id varchar(64) NOT NULL,
  order_id varchar(64),
  currency varchar(16) NOT NULL DEFAULT 'CNY',
  total_amount numeric(20,2) NOT NULL,
  application_status varchar(32) NOT NULL DEFAULT 'DRAFT',
  remark varchar(500),
  bpm_instance_id varchar(64),
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1,
  UNIQUE(operator_id, project_id, application_no)
);

CREATE INDEX IF NOT EXISTS idx_ap_agency_app_scope
  ON ap_agency_purchase_application(operator_id, project_id, application_status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ap_agency_app_customer
  ON ap_agency_purchase_application(operator_id, project_id, customer_id);

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_AGENCY_PURCHASE_VIEW', 'AGENCY_PURCHASE_VIEW', '代采申请查看', 'API', 'AGENCY_PURCHASE', 'GET', '/agency-purchase/applications/**'),
    ('PERM_AGENCY_PURCHASE_CREATE', 'AGENCY_PURCHASE_CREATE', '代采申请创建', 'API', 'AGENCY_PURCHASE', 'POST', '/agency-purchase/applications'),
    ('PERM_AGENCY_PURCHASE_SUBMIT', 'AGENCY_PURCHASE_SUBMIT', '代采申请提交', 'API', 'AGENCY_PURCHASE', 'POST', '/agency-purchase/applications/*/submit'),
    ('PERM_AGENCY_PURCHASE_CANCEL', 'AGENCY_PURCHASE_CANCEL', '代采申请取消', 'API', 'AGENCY_PURCHASE', 'POST', '/agency-purchase/applications/*/cancel')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

WITH role_perm AS (
  SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, permission_code FROM sys_permission
  WHERE operator_id = 'OP001' AND permission_code LIKE 'AGENCY_PURCHASE_%'
  UNION ALL
  SELECT 'ROLE_MEMBER', 'AGENCY_PURCHASE_VIEW'
  UNION ALL SELECT 'ROLE_MEMBER', 'AGENCY_PURCHASE_CREATE'
  UNION ALL SELECT 'ROLE_MEMBER', 'AGENCY_PURCHASE_SUBMIT'
  UNION ALL SELECT 'ROLE_MEMBER', 'AGENCY_PURCHASE_CANCEL'
  UNION ALL
  SELECT 'ROLE_FUNDING', 'AGENCY_PURCHASE_VIEW'
)
INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM role_perm rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
