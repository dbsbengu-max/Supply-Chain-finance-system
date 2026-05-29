SET search_path TO scf;

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

UPDATE acct_virtual_account
SET balance = 10000000.00,
    status = 'ACTIVE'
WHERE id = 'ACC_FUNDING_001';

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
SELECT
  'FIN_CLEAR_OK', 'OP001', 'PJ001', 'FIN-CLR-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
  'VOUCHER_FINANCE', 'VOUCHER', 'VOUCHER001', 200000.00, 200000.00, 200000.00,
  'CNY', 90, 0.080000, NULL, NULL, 'DISBURSED', 'U003'
WHERE NOT EXISTS (
  SELECT 1 FROM fn_finance_application existing WHERE existing.id = 'FIN_CLEAR_OK'
);

UPDATE fn_finance_application
SET finance_status = 'DISBURSED',
    disbursed_amount = 200000.00,
    approved_amount = 200000.00
WHERE id = 'FIN_CLEAR_OK';
