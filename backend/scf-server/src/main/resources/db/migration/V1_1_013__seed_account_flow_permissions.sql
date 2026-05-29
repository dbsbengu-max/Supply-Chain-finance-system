SET search_path TO scf;

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_ACCOUNT_FLOW_VIEW', 'ACCOUNT_FLOW_VIEW', '银行流水查看', 'API', 'ACCOUNT_FLOW', 'GET', '/accounts/bank-flows/**'),
    ('PERM_ACCOUNT_FLOW_IMPORT', 'ACCOUNT_FLOW_IMPORT', '银行流水导入', 'API', 'ACCOUNT_FLOW', 'POST', '/accounts/bank-flows/import'),
    ('PERM_ACCOUNT_FLOW_MATCH', 'ACCOUNT_FLOW_MATCH', '银行流水匹配', 'API', 'ACCOUNT_FLOW', 'POST', '/accounts/bank-flows/*/match')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ACTIVE'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

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
