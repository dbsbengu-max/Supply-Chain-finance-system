SET search_path TO scf;

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_VOUCHER_CREATE', 'VOUCHER_CREATE', '凭证创建', 'API', 'VOUCHER', 'POST', '/dv/vouchers'),
    ('PERM_VOUCHER_ISSUE', 'VOUCHER_ISSUE', '凭证签发', 'API', 'VOUCHER', 'POST', '/dv/vouchers/*/issue'),
    ('PERM_VOUCHER_CANCEL', 'VOUCHER_CANCEL', '凭证作废', 'API', 'VOUCHER', 'POST', '/dv/vouchers/*/cancel')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT 'ROLE_MEMBER:' || p.id, 'ROLE_MEMBER', p.id, 'system'
FROM sys_permission p
WHERE p.operator_id = 'OP001'
  AND p.permission_code IN ('VOUCHER_CREATE', 'VOUCHER_ISSUE', 'VOUCHER_CANCEL')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = 'ROLE_MEMBER' AND existing.permission_id = p.id
  );
