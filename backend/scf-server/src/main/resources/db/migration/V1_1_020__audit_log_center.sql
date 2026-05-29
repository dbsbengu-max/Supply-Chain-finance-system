SET search_path TO scf;

CREATE INDEX IF NOT EXISTS idx_audit_log_operator_project_time
  ON audit_operation_log(operator_id, project_id, operation_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_object
  ON audit_operation_log(object_type, object_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_enterprise_time
  ON audit_operation_log(enterprise_id, operation_at DESC);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_AUDIT_VIEW', 'AUDIT_VIEW', '审计日志查看', 'GET', '/audit/**')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'AUDIT', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'AUDIT_VIEW' AS permission_code
    UNION ALL SELECT 'ROLE_FUNDING', 'AUDIT_VIEW'
    UNION ALL SELECT 'ROLE_MEMBER', 'AUDIT_VIEW'
    UNION ALL SELECT 'ROLE_WAREHOUSE', 'AUDIT_VIEW'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
