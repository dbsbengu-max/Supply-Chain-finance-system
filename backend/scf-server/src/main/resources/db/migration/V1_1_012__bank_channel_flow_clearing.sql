SET search_path TO scf;

ALTER TABLE acct_bank_flow ADD COLUMN IF NOT EXISTS source_type varchar(32);
ALTER TABLE acct_bank_flow ADD COLUMN IF NOT EXISTS source_id varchar(64);

CREATE INDEX IF NOT EXISTS idx_bank_flow_match
  ON acct_bank_flow(account_id, match_status, flow_time);

CREATE INDEX IF NOT EXISTS idx_bank_flow_source
  ON acct_bank_flow(source_type, source_id);

CREATE INDEX IF NOT EXISTS idx_disbursement_channel_request
  ON fn_disbursement(channel_request_id);
