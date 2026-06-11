SET search_path TO scf;

ALTER TABLE tr_contract_sign_task
  ADD COLUMN IF NOT EXISTS platform_trace_id varchar(128);

ALTER TABLE tr_contract_sign_task
  ADD COLUMN IF NOT EXISTS provider_request_id varchar(128);

ALTER TABLE tr_contract_sign_task
  ADD COLUMN IF NOT EXISTS provider_trace_id varchar(128);

ALTER TABLE tr_contract_sign_task
  ADD COLUMN IF NOT EXISTS provider_exchange_json text;

CREATE INDEX IF NOT EXISTS idx_contract_sign_task_provider_trace
  ON tr_contract_sign_task(provider_trace_id);
