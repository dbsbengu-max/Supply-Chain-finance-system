SET search_path TO scf;

CREATE TABLE IF NOT EXISTS inbox_event_read (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  event_key varchar(160) NOT NULL,
  read_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (user_id, operator_id, project_id, event_key)
);

CREATE INDEX IF NOT EXISTS idx_inbox_event_read_user_scope
  ON inbox_event_read(user_id, operator_id, project_id);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_INBOX_VIEW', 'INBOX_VIEW', '统一待办查看', 'GET', '/inbox/**'),
    ('PERM_INBOX_READ', 'INBOX_READ', '统一待办已读', 'PATCH', '/inbox/events/read')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'INBOX', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_FUNDING' AS role_id, 'INBOX_VIEW' AS permission_code
    UNION ALL SELECT 'ROLE_FUNDING', 'INBOX_READ'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'INBOX_VIEW'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'INBOX_READ'
    UNION ALL SELECT 'ROLE_MEMBER', 'INBOX_VIEW'
    UNION ALL SELECT 'ROLE_MEMBER', 'INBOX_READ'
    UNION ALL SELECT 'ROLE_WAREHOUSE', 'INBOX_VIEW'
    UNION ALL SELECT 'ROLE_WAREHOUSE', 'INBOX_READ'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
