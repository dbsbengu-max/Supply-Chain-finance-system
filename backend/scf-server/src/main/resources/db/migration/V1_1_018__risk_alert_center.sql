SET search_path TO scf;

CREATE TABLE IF NOT EXISTS bi_risk_alert_ticket (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  alert_key varchar(160) NOT NULL,
  alert_code varchar(64) NOT NULL,
  severity varchar(16) NOT NULL,
  title varchar(200) NOT NULL,
  message varchar(500) NOT NULL,
  related_id varchar(64) NOT NULL,
  related_type varchar(32) NOT NULL,
  related_label varchar(200),
  amount numeric(18,2),
  currency varchar(16),
  handle_status varchar(32) NOT NULL DEFAULT 'OPEN',
  assignee_user_id varchar(64),
  assignee_name varchar(100),
  remark varchar(500),
  detected_at timestamptz NOT NULL DEFAULT now(),
  handled_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  version_no int NOT NULL DEFAULT 1,
  UNIQUE (operator_id, project_id, alert_key)
);

CREATE INDEX IF NOT EXISTS idx_bi_risk_alert_ticket_scope
  ON bi_risk_alert_ticket(operator_id, project_id, handle_status, severity, alert_code);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_RISK_ALERT_VIEW', 'RISK_ALERT_VIEW', '风险预警查看', 'GET', '/risk/alerts/**'),
    ('PERM_RISK_ALERT_HANDLE', 'RISK_ALERT_HANDLE', '风险预警处理', 'PATCH', '/risk/alerts/*')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'RISK', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_FUNDING' AS role_id, 'RISK_ALERT_VIEW' AS permission_code
    UNION ALL SELECT 'ROLE_FUNDING', 'RISK_ALERT_HANDLE'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'RISK_ALERT_VIEW'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'RISK_ALERT_HANDLE'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
