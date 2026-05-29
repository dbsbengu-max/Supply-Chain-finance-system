SET search_path TO scf;

ALTER TABLE fn_finance_application
  ADD COLUMN IF NOT EXISTS disbursed_amount numeric(18,2) NOT NULL DEFAULT 0;

ALTER TABLE fn_disbursement ADD COLUMN IF NOT EXISTS value_date date;
ALTER TABLE fn_disbursement ADD COLUMN IF NOT EXISTS remark varchar(500);

INSERT INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
SELECT
  'ACC_FUNDING_001', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ENT_FACTOR_001',
  'DISBURSE', 'VA-FUND-001', '资金方放款户', 'CNY', 10000000.00, 0.00, 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM acct_virtual_account existing WHERE existing.id = 'ACC_FUNDING_001'
);

INSERT INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
SELECT
  'ACC_MEMBER_001', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
  'RECEIVE', 'VA-MEM-001', '成员企业收款户', 'CNY', 0.00, 0.00, 'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM acct_virtual_account existing WHERE existing.id = 'ACC_MEMBER_001'
);
