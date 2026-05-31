SET search_path TO scf;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS retry_count int NOT NULL DEFAULT 0;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS next_retry_at timestamptz;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS last_error varchar(1000);

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS updated_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_compensation_status_retry
  ON biz_compensation_task(compensation_status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_status_retry
  ON biz_event_outbox(event_status, next_retry_at, created_at);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_SAGA_OPS_VIEW', 'SAGA_OPS_VIEW', 'Saga 运营监控查看', 'GET', '/saga/ops/**'),
    ('PERM_SAGA_OPS_MANAGE', 'SAGA_OPS_MANAGE', 'Saga 运营人工介入', 'POST', '/saga/ops/**')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'SAGA', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'SAGA_OPS_VIEW' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'SAGA_OPS_MANAGE'
    UNION ALL SELECT 'ROLE_FUNDING', 'SAGA_OPS_VIEW'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
