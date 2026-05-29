SET search_path TO scf;

-- Core DDL from ddl_v1_1_core.sql (V1.1 development baseline)

CREATE TABLE sys_operator (
  id varchar(64) PRIMARY KEY,
  operator_code varchar(64) NOT NULL UNIQUE,
  operator_name varchar(200) NOT NULL,
  country_region varchar(32) NOT NULL,
  contact_name varchar(100),
  contact_mobile varchar(64),
  status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE sys_project (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_code varchar(64) NOT NULL,
  project_name varchar(200) NOT NULL,
  countries varchar(300) NOT NULL,
  currencies varchar(300) NOT NULL,
  status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1,
  UNIQUE(operator_id, project_code)
);

CREATE TABLE sys_user (
  id varchar(64) PRIMARY KEY,
  login_name varchar(100) NOT NULL UNIQUE,
  mobile varchar(64),
  email varchar(200),
  user_name varchar(100) NOT NULL,
  password_hash varchar(200) NOT NULL,
  mfa_enabled smallint NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL,
  last_login_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE sys_role (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  role_code varchar(64) NOT NULL,
  role_name varchar(100) NOT NULL,
  role_type varchar(64) NOT NULL,
  data_scope varchar(64) NOT NULL,
  status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1,
  UNIQUE(operator_id, role_code)
);

CREATE TABLE md_enterprise (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  enterprise_code varchar(64) NOT NULL,
  enterprise_name varchar(200) NOT NULL,
  enterprise_type varchar(64) NOT NULL,
  country_region varchar(32) NOT NULL,
  registration_no varchar(100),
  unified_credit_code varchar(100),
  legal_person varchar(100),
  kyc_status varchar(32) NOT NULL,
  risk_level varchar(32),
  status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1,
  UNIQUE(operator_id, enterprise_code)
);

CREATE INDEX idx_enterprise_operator_type ON md_enterprise(operator_id, enterprise_type, status);

CREATE TABLE sys_user_identity (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL REFERENCES sys_user(id),
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) REFERENCES sys_project(id),
  enterprise_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  role_id varchar(64) NOT NULL REFERENCES sys_role(id),
  is_default smallint NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL
);

CREATE TABLE md_bank_account (
  id varchar(64) PRIMARY KEY,
  enterprise_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  account_type varchar(64) NOT NULL,
  bank_name varchar(200) NOT NULL,
  account_name varchar(200) NOT NULL,
  account_no varchar(200) NOT NULL,
  currency varchar(16) NOT NULL,
  verification_status varchar(32) NOT NULL,
  is_repayment_account smallint NOT NULL DEFAULT 0,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE md_category (
  id varchar(64) PRIMARY KEY,
  category_code varchar(64) NOT NULL UNIQUE,
  category_name varchar(100) NOT NULL,
  category_type varchar(32) NOT NULL,
  default_unit varchar(32) NOT NULL,
  status varchar(32) NOT NULL
);

CREATE TABLE md_sku (
  id varchar(64) PRIMARY KEY,
  category_id varchar(64) NOT NULL REFERENCES md_category(id),
  sku_code varchar(64) NOT NULL,
  spec varchar(200) NOT NULL,
  grade varchar(64),
  origin varchar(100),
  package_type varchar(100),
  unit varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  UNIQUE(category_id, sku_code)
);

CREATE TABLE fx_rate (
  id varchar(64) PRIMARY KEY,
  base_currency varchar(16) NOT NULL,
  quote_currency varchar(16) NOT NULL,
  rate numeric(18,8) NOT NULL CHECK(rate > 0),
  rate_date date NOT NULL,
  source_type varchar(64) NOT NULL,
  source_name varchar(200),
  review_status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(base_currency, quote_currency, rate_date, version_no)
);

CREATE TABLE pr_price_record (
  id varchar(64) PRIMARY KEY,
  sku_id varchar(64) NOT NULL REFERENCES md_sku(id),
  price_date date NOT NULL,
  price numeric(18,6) NOT NULL CHECK(price > 0),
  currency varchar(16) NOT NULL,
  unit varchar(32) NOT NULL,
  source_type varchar(64) NOT NULL,
  source_name varchar(200),
  trust_level varchar(32) NOT NULL,
  review_status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1,
  abnormal_flag smallint NOT NULL DEFAULT 0,
  approved_by varchar(64),
  approved_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  UNIQUE(sku_id, price_date, version_no)
);

CREATE TABLE tr_order (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  order_no varchar(64) NOT NULL UNIQUE,
  order_type varchar(64) NOT NULL,
  buyer_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  seller_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  trade_company_id varchar(64) REFERENCES md_enterprise(id),
  total_amount numeric(18,2) NOT NULL CHECK(total_amount >= 0),
  currency varchar(16) NOT NULL,
  country_from varchar(32),
  country_to varchar(32),
  order_status varchar(32) NOT NULL,
  signed_at timestamptz,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX idx_order_scope ON tr_order(operator_id, project_id, order_status, created_at);

CREATE TABLE tr_order_item (
  id varchar(64) PRIMARY KEY,
  order_id varchar(64) NOT NULL REFERENCES tr_order(id),
  sku_id varchar(64) NOT NULL REFERENCES md_sku(id),
  quantity numeric(18,6) NOT NULL CHECK(quantity > 0),
  unit varchar(32) NOT NULL,
  unit_price numeric(18,6) NOT NULL CHECK(unit_price > 0),
  amount numeric(18,2) NOT NULL CHECK(amount >= 0),
  delivery_date date
);

CREATE TABLE ar_receivable (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  ar_no varchar(64) NOT NULL UNIQUE,
  creditor_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  debtor_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  order_id varchar(64) REFERENCES tr_order(id),
  amount numeric(18,2) NOT NULL CHECK(amount >= 0),
  available_amount numeric(18,2) NOT NULL CHECK(available_amount >= 0),
  currency varchar(16) NOT NULL,
  due_date date NOT NULL,
  confirm_status varchar(32) NOT NULL,
  finance_status varchar(32) NOT NULL,
  evidence_status varchar(32) NOT NULL DEFAULT 'PENDING',
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE dv_voucher (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  voucher_no varchar(64) NOT NULL UNIQUE,
  issuer_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  acceptor_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  holder_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  parent_voucher_id varchar(64) REFERENCES dv_voucher(id),
  amount numeric(18,2) NOT NULL CHECK(amount > 0),
  available_amount numeric(18,2) NOT NULL CHECK(available_amount >= 0),
  currency varchar(16) NOT NULL,
  issue_date date NOT NULL,
  due_date date NOT NULL,
  voucher_status varchar(32) NOT NULL,
  evidence_status varchar(32) NOT NULL DEFAULT 'PENDING',
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE dv_voucher_flow (
  id varchar(64) PRIMARY KEY,
  voucher_id varchar(64) NOT NULL REFERENCES dv_voucher(id),
  flow_type varchar(64) NOT NULL,
  from_holder_id varchar(64),
  to_holder_id varchar(64),
  amount numeric(18,2) NOT NULL CHECK(amount >= 0),
  before_available_amount numeric(18,2) NOT NULL,
  after_available_amount numeric(18,2) NOT NULL,
  related_voucher_id varchar(64),
  operated_by varchar(64) NOT NULL,
  operated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cr_credit (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  credit_no varchar(64) NOT NULL UNIQUE,
  customer_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  funding_party_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  credit_limit numeric(18,2) NOT NULL CHECK(credit_limit >= 0),
  used_limit numeric(18,2) NOT NULL DEFAULT 0,
  frozen_limit numeric(18,2) NOT NULL DEFAULT 0,
  available_limit numeric(18,2) NOT NULL DEFAULT 0,
  currency varchar(16) NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  credit_status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE fn_finance_application (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  finance_no varchar(64) NOT NULL UNIQUE,
  customer_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  funding_party_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  credit_id varchar(64) REFERENCES cr_credit(id),
  product_type varchar(64) NOT NULL,
  source_type varchar(64) NOT NULL,
  source_id varchar(64) NOT NULL,
  apply_amount numeric(18,2) NOT NULL CHECK(apply_amount > 0),
  approved_amount numeric(18,2),
  currency varchar(16) NOT NULL,
  term_days int NOT NULL CHECK(term_days > 0),
  annual_rate numeric(10,6) NOT NULL,
  guarantee_amount numeric(18,2),
  pledge_rate numeric(10,6),
  finance_status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_by varchar(64),
  updated_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE acct_virtual_account (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) REFERENCES sys_project(id),
  enterprise_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  funding_party_id varchar(64) REFERENCES md_enterprise(id),
  account_type varchar(64) NOT NULL,
  account_no varchar(200) NOT NULL UNIQUE,
  account_name varchar(200) NOT NULL,
  currency varchar(16) NOT NULL,
  balance numeric(18,2) NOT NULL DEFAULT 0,
  frozen_balance numeric(18,2) NOT NULL DEFAULT 0,
  status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE acct_bank_flow (
  id varchar(64) PRIMARY KEY,
  account_id varchar(64) NOT NULL REFERENCES acct_virtual_account(id),
  external_flow_no varchar(100) NOT NULL,
  flow_type varchar(32) NOT NULL,
  amount numeric(18,2) NOT NULL CHECK(amount >= 0),
  currency varchar(16) NOT NULL,
  counterparty_name varchar(200),
  counterparty_account varchar(200),
  flow_time timestamptz NOT NULL,
  match_status varchar(32) NOT NULL,
  UNIQUE(account_id, external_flow_no)
);

CREATE TABLE fn_disbursement (
  id varchar(64) PRIMARY KEY,
  finance_id varchar(64) NOT NULL REFERENCES fn_finance_application(id),
  disbursement_no varchar(64) NOT NULL UNIQUE,
  amount numeric(18,2) NOT NULL CHECK(amount > 0),
  currency varchar(16) NOT NULL,
  pay_account_id varchar(64) NOT NULL,
  receive_account_id varchar(64) NOT NULL,
  channel varchar(64) NOT NULL,
  channel_request_id varchar(100),
  channel_response_id varchar(100),
  disbursement_status varchar(32) NOT NULL,
  idempotency_key varchar(128) NOT NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fn_repayment (
  id varchar(64) PRIMARY KEY,
  finance_id varchar(64) NOT NULL REFERENCES fn_finance_application(id),
  repayment_no varchar(64) NOT NULL UNIQUE,
  bank_flow_id varchar(64) REFERENCES acct_bank_flow(id),
  amount numeric(18,2) NOT NULL CHECK(amount >= 0),
  currency varchar(16) NOT NULL,
  repayment_status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE clearing_rule (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  funding_party_id varchar(64) REFERENCES md_enterprise(id),
  product_type varchar(64) NOT NULL,
  rule_name varchar(200) NOT NULL,
  priority_json text NOT NULL,
  fee_formula_json text,
  currency_rule varchar(64) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  review_status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1
);

CREATE TABLE clearing_result (
  id varchar(64) PRIMARY KEY,
  repayment_id varchar(64) NOT NULL REFERENCES fn_repayment(id),
  clearing_rule_id varchar(64) NOT NULL REFERENCES clearing_rule(id),
  principal_amount numeric(18,2) NOT NULL DEFAULT 0,
  interest_amount numeric(18,2) NOT NULL DEFAULT 0,
  fee_amount numeric(18,2) NOT NULL DEFAULT 0,
  penalty_amount numeric(18,2) NOT NULL DEFAULT 0,
  margin_amount numeric(18,2) NOT NULL DEFAULT 0,
  platform_fee_amount numeric(18,2) NOT NULL DEFAULT 0,
  residual_amount numeric(18,2) NOT NULL DEFAULT 0,
  clearing_status varchar(32) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE wh_warehouse (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  warehouse_company_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  warehouse_code varchar(64) NOT NULL UNIQUE,
  warehouse_name varchar(200) NOT NULL,
  country_region varchar(32) NOT NULL,
  address varchar(500) NOT NULL,
  warehouse_type varchar(64) NOT NULL,
  status varchar(32) NOT NULL
);

CREATE TABLE wh_inventory (
  id varchar(64) PRIMARY KEY,
  warehouse_id varchar(64) NOT NULL REFERENCES wh_warehouse(id),
  sku_id varchar(64) NOT NULL REFERENCES md_sku(id),
  batch_no varchar(100) NOT NULL,
  owner_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  quantity numeric(18,6) NOT NULL DEFAULT 0,
  available_quantity numeric(18,6) NOT NULL DEFAULT 0,
  frozen_quantity numeric(18,6) NOT NULL DEFAULT 0,
  pledged_quantity numeric(18,6) NOT NULL DEFAULT 0,
  valuation_amount numeric(18,2),
  currency varchar(16),
  right_status varchar(32) NOT NULL,
  version_no int NOT NULL DEFAULT 1
);

CREATE INDEX idx_inventory_wh_sku ON wh_inventory(warehouse_id, sku_id, batch_no);

CREATE TABLE wh_inventory_lock (
  id varchar(64) PRIMARY KEY,
  inventory_id varchar(64) NOT NULL REFERENCES wh_inventory(id),
  finance_id varchar(64) REFERENCES fn_finance_application(id),
  lock_type varchar(64) NOT NULL,
  quantity numeric(18,6) NOT NULL CHECK(quantity > 0),
  lock_status varchar(32) NOT NULL,
  locked_by varchar(64) NOT NULL,
  locked_at timestamptz NOT NULL DEFAULT now(),
  released_by varchar(64),
  released_at timestamptz
);

CREATE TABLE wh_receipt (
  id varchar(64) PRIMARY KEY,
  receipt_no varchar(64) NOT NULL UNIQUE,
  inventory_id varchar(64) NOT NULL REFERENCES wh_inventory(id),
  owner_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  quantity numeric(18,6) NOT NULL CHECK(quantity > 0),
  receipt_amount numeric(18,2) NOT NULL,
  currency varchar(16) NOT NULL,
  receipt_status varchar(32) NOT NULL,
  evidence_status varchar(32) NOT NULL DEFAULT 'PENDING'
);

CREATE TABLE lg_order (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  logistics_no varchar(64) NOT NULL UNIQUE,
  order_id varchar(64) NOT NULL REFERENCES tr_order(id),
  carrier_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  transport_mode varchar(32) NOT NULL,
  origin varchar(200) NOT NULL,
  destination varchar(200) NOT NULL,
  etd timestamptz,
  eta timestamptz,
  logistics_status varchar(32) NOT NULL
);

CREATE TABLE lg_node (
  id varchar(64) PRIMARY KEY,
  logistics_id varchar(64) NOT NULL REFERENCES lg_order(id),
  node_type varchar(64) NOT NULL,
  node_time timestamptz NOT NULL,
  location varchar(200),
  document_id varchar(64),
  exception_flag smallint NOT NULL DEFAULT 0,
  exception_type varchar(64)
);

CREATE TABLE bpm_process_instance (
  id varchar(64) PRIMARY KEY,
  process_code varchar(100) NOT NULL,
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  process_status varchar(32) NOT NULL,
  started_by varchar(64) NOT NULL,
  started_at timestamptz NOT NULL DEFAULT now(),
  ended_at timestamptz
);

CREATE TABLE bpm_task (
  id varchar(64) PRIMARY KEY,
  process_instance_id varchar(64) NOT NULL REFERENCES bpm_process_instance(id),
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  node_code varchar(64) NOT NULL,
  assignee_id varchar(64) NOT NULL,
  approval_status varchar(32) NOT NULL,
  submitted_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz,
  approval_comment varchar(1000)
);

CREATE TABLE ev_evidence (
  id varchar(64) PRIMARY KEY,
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  evidence_hash varchar(128) NOT NULL,
  evidence_payload_uri varchar(500),
  chain_type varchar(64),
  chain_tx_id varchar(200),
  evidence_status varchar(32) NOT NULL,
  retry_count int NOT NULL DEFAULT 0,
  evidenced_at timestamptz
);

CREATE TABLE audit_operation_log (
  id varchar(64) PRIMARY KEY,
  user_id varchar(64) NOT NULL,
  operator_id varchar(64) NOT NULL,
  enterprise_id varchar(64),
  action varchar(100) NOT NULL,
  object_type varchar(64) NOT NULL,
  object_id varchar(64) NOT NULL,
  before_value text,
  after_value text,
  ip_address varchar(64),
  operation_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE biz_event_outbox (
  id varchar(64) PRIMARY KEY,
  event_type varchar(100) NOT NULL,
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  idempotency_key varchar(128) NOT NULL UNIQUE,
  payload_json text NOT NULL,
  event_status varchar(32) NOT NULL,
  retry_count int NOT NULL DEFAULT 0,
  next_retry_at timestamptz,
  last_error varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz
);

CREATE TABLE biz_compensation_task (
  id varchar(64) PRIMARY KEY,
  source_event_id varchar(64) REFERENCES biz_event_outbox(id),
  compensation_type varchar(100) NOT NULL,
  business_type varchar(64) NOT NULL,
  business_id varchar(64) NOT NULL,
  compensation_status varchar(32) NOT NULL,
  action_json text NOT NULL,
  approved_by varchar(64),
  executed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bi_metric (
  id varchar(64) PRIMARY KEY,
  metric_code varchar(100) NOT NULL UNIQUE,
  metric_name varchar(200) NOT NULL,
  metric_domain varchar(64) NOT NULL,
  formula text NOT NULL,
  source_tables varchar(500) NOT NULL,
  refresh_frequency varchar(32) NOT NULL,
  permission_scope varchar(64) NOT NULL,
  status varchar(32) NOT NULL
);
