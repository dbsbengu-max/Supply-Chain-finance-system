SET search_path TO scf;

-- EA-001-FIX: P0 schema patches from ddl_v1_1_core_rev2.sql (Codex)
-- Skips bpm/idempotency tables already created in V1_1_003

CREATE TABLE IF NOT EXISTS md_enterprise_cert (
  id varchar(64) PRIMARY KEY,
  enterprise_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  cert_type varchar(64) NOT NULL,
  cert_no varchar(100),
  valid_from date,
  valid_to date,
  file_id varchar(64) NOT NULL,
  ocr_status varchar(32) NOT NULL DEFAULT 'PENDING',
  confidence numeric(5,2),
  confirmed_by varchar(64),
  confirmed_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_enterprise_cert_ent_type
  ON md_enterprise_cert(enterprise_id, cert_type, ocr_status);

CREATE TABLE IF NOT EXISTS tr_document (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  document_type varchar(64) NOT NULL,
  document_no varchar(100),
  file_id varchar(64) NOT NULL,
  ocr_status varchar(32) NOT NULL DEFAULT 'PENDING',
  validation_status varchar(32) NOT NULL DEFAULT 'PENDING',
  confirmed_by varchar(64),
  confirmed_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_tr_document_business
  ON tr_document(operator_id, project_id, business_type, business_id, document_type);

CREATE TABLE IF NOT EXISTS pr_valuation_record (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  sku_id varchar(64) NOT NULL REFERENCES md_sku(id),
  quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
  price_id varchar(64) NOT NULL REFERENCES pr_price_record(id),
  valuation_amount numeric(18,2) NOT NULL CHECK (valuation_amount >= 0),
  currency varchar(16) NOT NULL,
  fx_rate_id varchar(64) REFERENCES fx_rate(id),
  converted_amount numeric(18,2),
  converted_currency varchar(16),
  calculated_at timestamptz NOT NULL DEFAULT now(),
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_valuation_business
  ON pr_valuation_record(operator_id, project_id, business_type, business_id);

CREATE TABLE IF NOT EXISTS sys_menu (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) REFERENCES sys_operator(id),
  parent_id varchar(64),
  menu_code varchar(100) NOT NULL,
  menu_name varchar(200) NOT NULL,
  route_path varchar(300),
  component_path varchar(300),
  sort_no int NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL DEFAULT 'ENABLED',
  UNIQUE(operator_id, menu_code)
);

CREATE TABLE IF NOT EXISTS sys_permission (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) REFERENCES sys_operator(id),
  permission_code varchar(100) NOT NULL,
  permission_name varchar(200) NOT NULL,
  permission_type varchar(32) NOT NULL,
  resource_code varchar(100),
  api_method varchar(16),
  api_path varchar(300),
  status varchar(32) NOT NULL DEFAULT 'ENABLED',
  UNIQUE(operator_id, permission_code)
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
  id varchar(64) PRIMARY KEY,
  role_id varchar(64) NOT NULL REFERENCES sys_role(id),
  permission_id varchar(64) NOT NULL REFERENCES sys_permission(id),
  granted_by varchar(64) NOT NULL,
  granted_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS sys_field_permission (
  id varchar(64) PRIMARY KEY,
  role_id varchar(64) NOT NULL REFERENCES sys_role(id),
  object_type varchar(64) NOT NULL,
  field_name varchar(100) NOT NULL,
  field_policy varchar(32) NOT NULL,
  UNIQUE(role_id, object_type, field_name)
);

CREATE TABLE IF NOT EXISTS sys_data_scope_rule (
  id varchar(64) PRIMARY KEY,
  role_id varchar(64) NOT NULL REFERENCES sys_role(id),
  scope_type varchar(64) NOT NULL,
  scope_expression jsonb,
  status varchar(32) NOT NULL DEFAULT 'ACTIVE',
  UNIQUE(role_id, scope_type)
);

CREATE TABLE IF NOT EXISTS sys_file (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) REFERENCES sys_operator(id),
  project_id varchar(64) REFERENCES sys_project(id),
  file_name varchar(300) NOT NULL,
  file_ext varchar(32),
  mime_type varchar(100),
  file_size bigint NOT NULL DEFAULT 0,
  storage_bucket varchar(100),
  storage_key varchar(500) NOT NULL,
  checksum varchar(128),
  uploaded_by varchar(64) NOT NULL,
  uploaded_at timestamptz NOT NULL DEFAULT now(),
  status varchar(32) NOT NULL DEFAULT 'ACTIVE'
);

CREATE TABLE IF NOT EXISTS sys_dictionary (
  id varchar(64) PRIMARY KEY,
  dict_type varchar(100) NOT NULL,
  dict_code varchar(100) NOT NULL,
  dict_label varchar(200) NOT NULL,
  sort_no int NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL DEFAULT 'ENABLED',
  UNIQUE(dict_type, dict_code)
);

ALTER TABLE wh_inventory
  ADD COLUMN IF NOT EXISTS outbound_pending_quantity numeric(18,6) NOT NULL DEFAULT 0;

ALTER TABLE audit_operation_log
  ADD COLUMN IF NOT EXISTS project_id varchar(64);

ALTER TABLE dv_voucher ADD CONSTRAINT chk_voucher_available_amount
  CHECK (available_amount >= 0 AND available_amount <= amount);

ALTER TABLE cr_credit ADD CONSTRAINT chk_credit_limits_nonnegative
  CHECK (credit_limit >= 0 AND used_limit >= 0 AND frozen_limit >= 0 AND available_limit >= 0
    AND used_limit + frozen_limit + available_limit <= credit_limit);

ALTER TABLE acct_virtual_account ADD CONSTRAINT chk_account_balance_nonnegative
  CHECK (balance >= 0 AND frozen_balance >= 0 AND frozen_balance <= balance);

ALTER TABLE wh_inventory ADD CONSTRAINT chk_inventory_quantities_nonnegative
  CHECK (quantity >= 0 AND available_quantity >= 0 AND frozen_quantity >= 0
    AND pledged_quantity >= 0 AND outbound_pending_quantity >= 0);

CREATE INDEX IF NOT EXISTS idx_outbox_status_retry ON biz_event_outbox(event_status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_finance_scope ON fn_finance_application(project_id, funding_party_id, finance_status, created_at);
CREATE INDEX IF NOT EXISTS idx_voucher_holder ON dv_voucher(project_id, holder_id, voucher_status, due_date);
