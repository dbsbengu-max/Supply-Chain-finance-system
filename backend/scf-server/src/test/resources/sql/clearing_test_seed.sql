SET search_path TO scf;

DELETE FROM clearing_result WHERE repayment_id IN (
  SELECT id FROM fn_repayment WHERE finance_id = 'FIN_CLEAR_OK'
);
DELETE FROM fn_repayment WHERE finance_id = 'FIN_CLEAR_OK';
DELETE FROM acct_bank_flow WHERE account_id = 'ACC_REPAY_001' AND external_flow_no LIKE 'CLR-TEST-%';
DELETE FROM idempotency_record WHERE idempotency_key LIKE 'CLR-EXEC-%';
DELETE FROM fn_finance_application WHERE id = 'FIN_CLEAR_OK';

UPDATE acct_virtual_account
SET balance = 10000000.00,
    frozen_balance = 0.00,
    status = 'ACTIVE'
WHERE id = 'ACC_FUNDING_001';
UPDATE acct_virtual_account SET balance = 0.00 WHERE id = 'ACC_REPAY_001';

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
VALUES (
  'FIN_CLEAR_OK', 'OP001', 'PJ001', 'FIN-CLR-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
  'VOUCHER_FINANCE', 'VOUCHER', 'VOUCHER001', 200000.00, 200000.00, 200000.00,
  'CNY', 90, 0.080000, NULL, NULL, 'DISBURSED', 'U003'
);
