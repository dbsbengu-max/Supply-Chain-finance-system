SET search_path TO scf;

-- BPM extended tables (TECH_BPM V1.1)
CREATE TABLE bpm_process_definition (
  id varchar(64) PRIMARY KEY,
  process_code varchar(100) NOT NULL,
  process_name varchar(200) NOT NULL,
  version_no int NOT NULL DEFAULT 1,
  operator_id varchar(64) REFERENCES sys_operator(id),
  project_id varchar(64) REFERENCES sys_project(id),
  funding_party_id varchar(64) REFERENCES md_enterprise(id),
  definition_json text NOT NULL,
  status varchar(32) NOT NULL,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(process_code, version_no, operator_id, project_id, funding_party_id)
);

CREATE TABLE bpm_node_definition (
  id varchar(64) PRIMARY KEY,
  process_definition_id varchar(64) NOT NULL REFERENCES bpm_process_definition(id),
  node_code varchar(64) NOT NULL,
  node_name varchar(200) NOT NULL,
  node_type varchar(64) NOT NULL,
  assignee_role varchar(64),
  timeout_hours int,
  sort_order int NOT NULL,
  UNIQUE(process_definition_id, node_code)
);

CREATE TABLE bpm_transition_definition (
  id varchar(64) PRIMARY KEY,
  process_definition_id varchar(64) NOT NULL REFERENCES bpm_process_definition(id),
  from_node_code varchar(64) NOT NULL,
  to_node_code varchar(64) NOT NULL,
  condition_json text,
  action_type varchar(64) NOT NULL
);

CREATE TABLE bpm_task_comment (
  id varchar(64) PRIMARY KEY,
  task_id varchar(64) NOT NULL REFERENCES bpm_task(id),
  user_id varchar(64) NOT NULL,
  comment_type varchar(64) NOT NULL,
  comment_text varchar(2000) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bpm_task_delegation (
  id varchar(64) PRIMARY KEY,
  task_id varchar(64) NOT NULL REFERENCES bpm_task(id),
  from_user_id varchar(64) NOT NULL,
  to_user_id varchar(64) NOT NULL,
  delegation_type varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_bpm_task_assignee ON bpm_task(assignee_id, approval_status);
CREATE INDEX idx_bpm_instance_business ON bpm_process_instance(business_type, business_id);

-- Idempotency (TECH_SagaOutbox V1.1 / EA-007)
CREATE TABLE idempotency_record (
  id varchar(64) PRIMARY KEY,
  idempotency_key varchar(128) NOT NULL,
  request_hash varchar(128) NOT NULL,
  business_type varchar(64) NOT NULL,
  business_id varchar(64),
  result_json text,
  status varchar(32) NOT NULL,
  http_status int,
  expired_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz,
  UNIQUE(idempotency_key, request_hash)
);

CREATE INDEX idx_idempotency_key ON idempotency_record(idempotency_key);
CREATE INDEX idx_idempotency_expired ON idempotency_record(expired_at);
