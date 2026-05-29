SET search_path TO scf;

UPDATE sys_permission
SET api_path = '/accounts/clearing/**'
WHERE operator_id = 'OP001' AND permission_code = 'CLEARING_VIEW';

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_CLEARING_RULE_LIST', 'CLEARING_RULE_LIST', '清分规则列表', 'GET', '/accounts/clearing-rules/**'),
    ('PERM_CLEARING_RULE_UPDATE', 'CLEARING_RULE_UPDATE', '清分规则更新', 'PUT', '/accounts/clearing-rules/*'),
    ('PERM_CLEARING_RULE_SUBMIT', 'CLEARING_RULE_SUBMIT', '清分规则提交', 'POST', '/accounts/clearing-rules/*/submit'),
    ('PERM_CLEARING_RULE_APPROVE', 'CLEARING_RULE_APPROVE', '清分规则审批', 'POST', '/accounts/clearing-rules/*/approve')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'CLEARING', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_FUNDING' AS role_id, 'CLEARING_RULE_CREATE' AS permission_code
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
