SET search_path TO scf;

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS document_status varchar(32) NOT NULL DEFAULT 'UPLOADED';

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS review_status varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS review_result varchar(32);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS review_reason varchar(512);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS contract_status varchar(32) NOT NULL DEFAULT 'NOT_CONTRACT';

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS sign_status varchar(32) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS sign_provider varchar(64);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS external_sign_ref varchar(128);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS ocr_job_id varchar(64);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS ocr_confidence numeric(6,4);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS validation_result_json text;

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS issued_at timestamptz;

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS expired_at timestamptz;

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS reviewed_by varchar(64);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS reviewed_at timestamptz;

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS updated_by varchar(64);

ALTER TABLE tr_document
  ADD COLUMN IF NOT EXISTS updated_at timestamptz;

CREATE INDEX IF NOT EXISTS idx_tr_document_center
  ON tr_document(operator_id, project_id, business_type, document_status, review_status, updated_at);

CREATE TABLE IF NOT EXISTS tr_document_requirement (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) REFERENCES sys_project(id),
  business_type varchar(64) NOT NULL,
  business_stage varchar(64) NOT NULL,
  product_type varchar(64),
  document_type varchar(64) NOT NULL,
  required_flag smallint NOT NULL DEFAULT 1,
  ocr_required smallint NOT NULL DEFAULT 0,
  manual_review_required smallint NOT NULL DEFAULT 1,
  min_confidence numeric(6,4) DEFAULT 0.8500,
  enabled smallint NOT NULL DEFAULT 1,
  sort_no int NOT NULL DEFAULT 0,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_doc_requirement_lookup
  ON tr_document_requirement(operator_id, business_type, business_stage, enabled, deleted_flag);

CREATE TABLE IF NOT EXISTS tr_document_review_log (
  id varchar(64) PRIMARY KEY,
  document_id varchar(64) NOT NULL REFERENCES tr_document(id),
  action varchar(32) NOT NULL,
  before_status varchar(32),
  after_status varchar(32),
  operator_id varchar(64) NOT NULL,
  operator_role varchar(64),
  reason varchar(512),
  snapshot_json text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_doc_review_log_doc
  ON tr_document_review_log(document_id, created_at);

WITH perms(id, code, name, method, path) AS (
  VALUES
    ('PERM_DOCUMENT_REVIEW_SUBMIT', 'DOCUMENT_REVIEW_SUBMIT', '单证提交复核', 'POST', '/documents/center/*/submit-review'),
    ('PERM_DOCUMENT_REVIEW_APPROVE', 'DOCUMENT_REVIEW_APPROVE', '单证复核审批', 'POST', '/documents/center/*/approve'),
    ('PERM_DOCUMENT_ARCHIVE', 'DOCUMENT_ARCHIVE', '单证归档作废', 'POST', '/documents/center/*/archive'),
    ('PERM_DOCUMENT_REQUIREMENT_VIEW', 'DOCUMENT_REQUIREMENT_VIEW', '必备单证规则查看', 'GET', '/documents/requirements'),
    ('PERM_DOCUMENT_REQUIREMENT_MANAGE', 'DOCUMENT_REQUIREMENT_MANAGE', '必备单证规则管理', 'POST', '/documents/requirements')
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
    SELECT 'ROLE_PLATFORM_ADMIN' AS role_id, 'DOCUMENT_REVIEW_SUBMIT' AS permission_code
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'DOCUMENT_REVIEW_APPROVE'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'DOCUMENT_ARCHIVE'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'DOCUMENT_REQUIREMENT_VIEW'
    UNION ALL SELECT 'ROLE_PLATFORM_ADMIN', 'DOCUMENT_REQUIREMENT_MANAGE'
    UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_REVIEW_SUBMIT'
    UNION ALL SELECT 'ROLE_MEMBER', 'DOCUMENT_REQUIREMENT_VIEW'
    UNION ALL SELECT 'ROLE_FUNDING', 'DOCUMENT_REVIEW_APPROVE'
    UNION ALL SELECT 'ROLE_FUNDING', 'DOCUMENT_REQUIREMENT_VIEW'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);

INSERT INTO tr_document_requirement (
  id, operator_id, project_id, business_type, business_stage, product_type, document_type,
  required_flag, ocr_required, manual_review_required, min_confidence, enabled, sort_no, created_by
)
SELECT 'DOC_REQ_FIN_CONTRACT', 'OP001', 'PJ001', 'FINANCE', 'DISBURSE', 'AGENCY_PURCHASE', 'PURCHASE_CONTRACT',
       1, 1, 1, 0.8500, 1, 10, 'system'
WHERE NOT EXISTS (SELECT 1 FROM tr_document_requirement WHERE id = 'DOC_REQ_FIN_CONTRACT');

INSERT INTO tr_document_requirement (
  id, operator_id, project_id, business_type, business_stage, product_type, document_type,
  required_flag, ocr_required, manual_review_required, min_confidence, enabled, sort_no, created_by
)
SELECT 'DOC_REQ_FIN_INVOICE', 'OP001', 'PJ001', 'FINANCE', 'DISBURSE', 'AGENCY_PURCHASE', 'INVOICE',
       1, 1, 1, 0.8500, 1, 20, 'system'
WHERE NOT EXISTS (SELECT 1 FROM tr_document_requirement WHERE id = 'DOC_REQ_FIN_INVOICE');

INSERT INTO tr_document_requirement (
  id, operator_id, project_id, business_type, business_stage, product_type, document_type,
  required_flag, ocr_required, manual_review_required, min_confidence, enabled, sort_no, created_by
)
SELECT 'DOC_REQ_ORDER_INVOICE', 'OP001', NULL, 'TRADE_ORDER', 'SUBMIT', NULL, 'INVOICE',
       1, 0, 1, 0.8500, 1, 10, 'system'
WHERE NOT EXISTS (SELECT 1 FROM tr_document_requirement WHERE id = 'DOC_REQ_ORDER_INVOICE');
