SET search_path TO scf;

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_FINANCE_PRECHECK', 'FINANCE_PRECHECK', '放款前置校验', 'POST', '/finance/applications/*/pre-check')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'FINANCE', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'FINANCE_PRECHECK' AS permission_code
    UNION ALL SELECT 'ROLE_FUNDING', 'FINANCE_PRECHECK'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
