-- EA-032 Pilot seed verification queries
-- Usage: psql -h $SCF_DB_HOST -U $SCF_DB_USER -d $SCF_DB_NAME -f verify-pilot-seed.sql

SET search_path TO scf;

\echo '=== 1. Operator / Project ==='
SELECT id, operator_code, status FROM sys_operator WHERE id = 'OP001';
SELECT id, project_code, status FROM sys_project WHERE id = 'PJ001';

\echo '=== 2. Pilot users (must be ACTIVE) ==='
SELECT id, login_name, status,
       CASE WHEN password_hash = 'mock_hash' THEN 'WARN_MOCK_HASH' ELSE 'OK_HASH' END AS password_state
FROM sys_user
WHERE login_name IN ('platform_admin', 'funding_user', 'member_user', 'warehouse_user')
ORDER BY login_name;

\echo '=== 3. Roles ==='
SELECT id, role_code, status FROM sys_role
WHERE id IN ('ROLE_PLATFORM_ADMIN', 'ROLE_FUNDING', 'ROLE_MEMBER', 'ROLE_WAREHOUSE');

\echo '=== 4. User identities (default project PJ001) ==='
SELECT u.login_name, ui.project_id, ui.status, r.role_code
FROM sys_user_identity ui
JOIN sys_user u ON u.id = ui.user_id
JOIN sys_role r ON r.id = ui.role_id
WHERE u.login_name IN ('platform_admin', 'funding_user', 'member_user', 'warehouse_user');

\echo '=== 5. platform_admin critical permissions (pilot closure) ==='
SELECT p.permission_code
FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = 'ROLE_PLATFORM_ADMIN'
  AND p.permission_code IN (
    'SAGA_OPS_VIEW', 'SAGA_OPS_MANAGE',
    'FINANCE_VIEW', 'FINANCE_CREATE', 'FINANCE_APPROVE', 'FINANCE_DISBURSE',
    'CLEARING_VIEW', 'CLEARING_EXECUTE',
    'BI_VIEW', 'BI_EXPORT', 'BI_DRILLDOWN',
    'AUDIT_VIEW',
    'AGENCY_PURCHASE_VIEW', 'AGENCY_PURCHASE_CREATE',
    'VOUCHER_VIEW', 'ACCOUNT_FLOW_VIEW',
    'RISK_ALERT_VIEW', 'INBOX_VIEW'
  )
ORDER BY p.permission_code;

\echo '=== 6. Permission count by role ==='
SELECT r.role_code, COUNT(p.id) AS perm_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
LEFT JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.id IN ('ROLE_PLATFORM_ADMIN', 'ROLE_FUNDING', 'ROLE_MEMBER', 'ROLE_WAREHOUSE')
GROUP BY r.role_code
ORDER BY r.role_code;

\echo '=== 7. Flyway version ==='
SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

\echo '=== 8. Seed manifest (EA-034, if V1_1_027 applied) ==='
SELECT id, seed_profile, source_file, applied_at
FROM scf.sys_seed_manifest
ORDER BY applied_at DESC;

\echo '=== DONE ==='
