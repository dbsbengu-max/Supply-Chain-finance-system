SET search_path TO scf;

DELETE FROM dv_voucher_flow WHERE voucher_id = 'VOUCHER_REPAY_REL';
DELETE FROM dv_voucher WHERE id = 'VOUCHER_REPAY_REL';
DELETE FROM clearing_result WHERE repayment_id IN (
  SELECT id FROM fn_repayment WHERE finance_id = 'FIN_REPAY_RELEASE'
);
DELETE FROM fn_repayment WHERE finance_id = 'FIN_REPAY_RELEASE';
DELETE FROM acct_bank_flow WHERE account_id = 'ACC_REPAY_001' AND external_flow_no LIKE 'EA025-%';
DELETE FROM biz_event_outbox WHERE idempotency_key LIKE 'REPAYMENT-SETTLED-%'
  OR business_id IN (SELECT id FROM fn_repayment WHERE finance_id = 'FIN_REPAY_RELEASE');
DELETE FROM idempotency_record WHERE idempotency_key LIKE 'EA025-CLR-%';
DELETE FROM fn_finance_application WHERE id = 'FIN_REPAY_RELEASE';

MERGE INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
KEY(id) VALUES
(
  'ACC_FUNDING_001', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ENT_FACTOR_001',
  'DISBURSE', 'VA-FUND-001', '资金方放款户', 'CNY', 10000000.00, 0.00, 'ACTIVE'
);

UPDATE acct_virtual_account SET balance = 0.00 WHERE id = 'ACC_REPAY_001';

INSERT INTO dv_voucher (
  id, operator_id, project_id, voucher_no, issuer_id, acceptor_id, holder_id,
  parent_voucher_id, amount, available_amount, locked_amount, currency, issue_date, due_date,
  voucher_status, evidence_status
)
VALUES (
  'VOUCHER_REPAY_REL', 'OP001', 'PJ001', 'DV-REPAY-REL', 'ENT_CORE_001', 'ENT_CORE_001', 'ENT_MEMBER_001',
  NULL, 500000.00, 300000.00, 200000.00, 'CNY', '2026-05-27', '2026-08-25', 'FINANCING', 'SUCCESS'
);

INSERT INTO dv_voucher_flow (
  id, voucher_id, flow_type, from_holder_id, to_holder_id, amount,
  before_available_amount, after_available_amount, related_voucher_id, operated_by, operated_at
)
VALUES (
  'DVFLOW_REPAY_LOCK', 'VOUCHER_REPAY_REL', 'FINANCE_LOCK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 200000.00,
  500000.00, 300000.00, 'FIN_REPAY_RELEASE', 'system', '2026-06-01 10:00:00+08'
);

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
VALUES (
  'FIN_REPAY_RELEASE', 'OP001', 'PJ001', 'FIN-EA025-REL', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
  'VOUCHER_FINANCE', 'VOUCHER', 'VOUCHER_REPAY_REL', 200000.00, 200000.00, 200000.00,
  'CNY', 90, 0.080000, NULL, NULL, 'DISBURSED', 'U003'
);
