SET search_path TO scf;

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'CLEARING_RULE_CREATE' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'CLEARING_RULE_LIST'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'CLEARING_RULE_UPDATE'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'CLEARING_RULE_SUBMIT'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'CLEARING_RULE_APPROVE'
    UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_RULE_CREATE'
    UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_RULE_LIST'
    UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_RULE_UPDATE'
    UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_RULE_SUBMIT'
    UNION ALL SELECT 'ROLE_FUNDING', 'CLEARING_RULE_APPROVE'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
