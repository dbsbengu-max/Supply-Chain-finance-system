SET search_path TO scf;

ALTER TABLE dv_voucher
  ADD COLUMN IF NOT EXISTS bpm_instance_id varchar(32);

ALTER TABLE dv_voucher
  ADD COLUMN IF NOT EXISTS redeem_restore_status varchar(32);

ALTER TABLE dv_voucher
  ADD COLUMN IF NOT EXISTS redeem_amount numeric(18,2);

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_VOUCHER_REDEEM_EXECUTE', 'VOUCHER_REDEEM_EXECUTE', '凭证兑付执行', 'API', 'VOUCHER', 'POST', '/vouchers/*/redeem-execute')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT 'ROLE_FUNDING:' || p.id, 'ROLE_FUNDING', p.id, 'system'
FROM sys_permission p
WHERE p.operator_id = 'OP001'
  AND p.permission_code = 'VOUCHER_REDEEM_EXECUTE'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = 'ROLE_FUNDING' AND existing.permission_id = p.id
  );

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT 'ROLE_PLATFORM_ADMIN:' || p.id, 'ROLE_PLATFORM_ADMIN', p.id, 'system'
FROM sys_permission p
WHERE p.operator_id = 'OP001'
  AND p.permission_code = 'VOUCHER_REDEEM_EXECUTE'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing
    WHERE existing.role_id = 'ROLE_PLATFORM_ADMIN' AND existing.permission_id = p.id
  );
