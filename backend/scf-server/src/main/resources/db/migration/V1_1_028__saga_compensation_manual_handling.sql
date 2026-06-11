SET search_path TO scf;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS high_risk smallint NOT NULL DEFAULT 0;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS claimed_by varchar(64);

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS claimed_at timestamptz;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS submitted_by varchar(64);

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS submitted_at timestamptz;

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS handle_reason varchar(500);

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS closed_by varchar(64);

ALTER TABLE biz_compensation_task
  ADD COLUMN IF NOT EXISTS closed_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_compensation_claimed
  ON biz_compensation_task(compensation_status, claimed_by, created_at);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_SAGA_OPS_HANDLE', 'SAGA_OPS_HANDLE', 'Saga 补偿认领与处置', 'POST', '/saga/ops/compensation-tasks/*/claim'),
    ('PERM_SAGA_OPS_RETRY', 'SAGA_OPS_RETRY', 'Saga 补偿人工重试', 'POST', '/saga/ops/compensation-tasks/*/retry'),
    ('PERM_SAGA_OPS_APPROVE', 'SAGA_OPS_APPROVE', 'Saga 补偿审批执行', 'POST', '/saga/ops/compensation-tasks/*/approve-execute')
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
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'SAGA_OPS_HANDLE' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'SAGA_OPS_RETRY'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'SAGA_OPS_APPROVE'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
