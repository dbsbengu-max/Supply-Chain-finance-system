SET search_path TO scf;

UPDATE sys_permission
SET status = 'ENABLED'
WHERE operator_id = 'OP001'
  AND permission_code IN ('ACCOUNT_FLOW_VIEW', 'ACCOUNT_FLOW_IMPORT', 'ACCOUNT_FLOW_MATCH');

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'ACCOUNT_FLOW_VIEW' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'ACCOUNT_FLOW_IMPORT'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'ACCOUNT_FLOW_MATCH'
    UNION ALL SELECT 'ROLE_FUNDING', 'ACCOUNT_FLOW_VIEW'
    UNION ALL SELECT 'ROLE_FUNDING', 'ACCOUNT_FLOW_IMPORT'
    UNION ALL SELECT 'ROLE_FUNDING', 'ACCOUNT_FLOW_MATCH'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
