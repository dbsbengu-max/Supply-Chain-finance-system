SET search_path TO scf;

UPDATE sys_permission
SET api_path = REPLACE(api_path, '/dv/vouchers', '/vouchers')
WHERE operator_id = 'OP001'
  AND api_path LIKE '/dv/vouchers%';

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_VOUCHER_REDEEM', 'VOUCHER_REDEEM_APPLY', '凭证兑付申请', 'API', 'VOUCHER', 'POST', '/vouchers/*/redeem-apply')
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
  AND p.permission_code = 'VOUCHER_REDEEM_APPLY'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = 'ROLE_MEMBER' AND existing.permission_id = p.id
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT 'ROLE_FUNDING:' || p.id, 'ROLE_FUNDING', p.id, 'system'
FROM sys_permission p
WHERE p.operator_id = 'OP001'
  AND p.permission_code = 'VOUCHER_REDEEM_APPLY'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = 'ROLE_FUNDING' AND existing.permission_id = p.id
  );
