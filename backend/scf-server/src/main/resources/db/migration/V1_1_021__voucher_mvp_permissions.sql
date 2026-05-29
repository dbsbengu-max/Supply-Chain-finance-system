SET search_path TO scf;

CREATE INDEX IF NOT EXISTS idx_voucher_scope_status
  ON dv_voucher(operator_id, project_id, voucher_status, issue_date DESC);

CREATE INDEX IF NOT EXISTS idx_voucher_flow_voucher_time
  ON dv_voucher_flow(voucher_id, operated_at DESC);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_VOUCHER_REDEEM', 'VOUCHER_REDEEM', '凭证兑付申请', 'POST', '/dv/vouchers/*/redeem-apply')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'VOUCHER', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

WITH role_perm(role_id, permission_code) AS (
  VALUES
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_VIEW'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_CREATE'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_ISSUE'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_TRANSFER'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_SPLIT'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_REDEEM'),
    ('ROLE_PLATFORM_ADMIN', 'VOUCHER_CANCEL'),
    ('ROLE_FUNDING', 'VOUCHER_VIEW'),
    ('ROLE_MEMBER', 'VOUCHER_VIEW'),
    ('ROLE_MEMBER', 'VOUCHER_CREATE'),
    ('ROLE_MEMBER', 'VOUCHER_ISSUE'),
    ('ROLE_MEMBER', 'VOUCHER_TRANSFER'),
    ('ROLE_MEMBER', 'VOUCHER_SPLIT'),
    ('ROLE_MEMBER', 'VOUCHER_REDEEM'),
    ('ROLE_MEMBER', 'VOUCHER_CANCEL')
)
INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM role_perm rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
