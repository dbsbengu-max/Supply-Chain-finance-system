SET search_path TO scf;

ALTER TABLE sys_file ADD COLUMN IF NOT EXISTS business_type varchar(64);
ALTER TABLE sys_file ADD COLUMN IF NOT EXISTS business_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_sys_file_business ON sys_file(operator_id, project_id, business_type, business_id);

CREATE TABLE IF NOT EXISTS ai_ocr_job (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL,
  project_id varchar(64) NOT NULL,
  file_id varchar(64) NOT NULL REFERENCES sys_file(id),
  business_type varchar(64) NOT NULL,
  business_id varchar(64),
  recognition_type varchar(64) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'COMPLETED',
  model_version varchar(64) NOT NULL DEFAULT 'mock-v1',
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  confirmed_by varchar(64),
  confirmed_at timestamptz
);

CREATE TABLE IF NOT EXISTS ai_ocr_field (
  id varchar(64) PRIMARY KEY,
  job_id varchar(64) NOT NULL REFERENCES ai_ocr_job(id),
  field_name varchar(100) NOT NULL,
  suggested_value text,
  confidence numeric(5,4) NOT NULL DEFAULT 0,
  source_text text,
  page_no int,
  bbox varchar(200),
  confirm_status varchar(32) NOT NULL DEFAULT 'PENDING',
  confirmed_value text
);

CREATE INDEX IF NOT EXISTS idx_ai_ocr_job_scope ON ai_ocr_job(operator_id, project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_ocr_field_job ON ai_ocr_field(job_id);

CREATE TABLE IF NOT EXISTS excel_import_job (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL,
  project_id varchar(64) NOT NULL,
  file_id varchar(64) NOT NULL REFERENCES sys_file(id),
  import_type varchar(64) NOT NULL,
  batch_id varchar(64) NOT NULL,
  dry_run boolean NOT NULL DEFAULT true,
  status varchar(32) NOT NULL DEFAULT 'PREVIEW',
  total_rows int NOT NULL DEFAULT 0,
  ok_rows int NOT NULL DEFAULT 0,
  error_rows int NOT NULL DEFAULT 0,
  warning_rows int NOT NULL DEFAULT 0,
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  confirmed_by varchar(64),
  confirmed_at timestamptz
);

CREATE TABLE IF NOT EXISTS excel_import_row (
  id varchar(64) PRIMARY KEY,
  job_id varchar(64) NOT NULL REFERENCES excel_import_job(id),
  row_no int NOT NULL,
  row_status varchar(32) NOT NULL,
  row_data text NOT NULL,
  error_message text,
  warning_message text
);

CREATE INDEX IF NOT EXISTS idx_excel_import_job_scope ON excel_import_job(operator_id, project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_excel_import_row_job ON excel_import_row(job_id, row_no);
