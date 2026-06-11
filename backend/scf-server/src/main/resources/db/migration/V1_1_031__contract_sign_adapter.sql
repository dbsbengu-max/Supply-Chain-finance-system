SET search_path TO scf;

CREATE TABLE IF NOT EXISTS tr_contract_sign_task (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  document_id varchar(64) NOT NULL REFERENCES tr_document(id),
  provider_code varchar(64) NOT NULL,
  external_sign_ref varchar(128),
  task_status varchar(32) NOT NULL,
  callback_status varchar(32),
  signers_json text,
  callback_payload_json text,
  failure_reason varchar(512),
  retry_count int NOT NULL DEFAULT 0,
  last_retry_at timestamptz,
  signed_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz
);

CREATE INDEX IF NOT EXISTS idx_contract_sign_task_doc
  ON tr_contract_sign_task(document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_contract_sign_task_ext
  ON tr_contract_sign_task(external_sign_ref);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_DOCUMENT_CONTRACT_SIGN', 'DOCUMENT_CONTRACT_SIGN', '合同发起签署', 'POST', '/documents/center/*/sign'),
    ('PERM_DOCUMENT_CONTRACT_SIGN_RETRY', 'DOCUMENT_CONTRACT_SIGN_RETRY', '合同签署失败重试', 'POST', '/documents/center/*/sign/retry')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, 'API', 'DOCUMENT', method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'DOCUMENT_CONTRACT_SIGN' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'DOCUMENT_CONTRACT_SIGN_RETRY'
    UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_CONTRACT_SIGN'
    UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_CONTRACT_SIGN_RETRY'
    UNION ALL SELECT 'ROLE_FUNDING', 'DOCUMENT_CONTRACT_SIGN'
    UNION ALL SELECT 'ROLE_FUNDING', 'DOCUMENT_CONTRACT_SIGN_RETRY'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
