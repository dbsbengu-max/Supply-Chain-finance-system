SET search_path TO scf;

DELETE FROM acct_bank_flow WHERE source_id IN (
  SELECT id FROM fn_disbursement WHERE finance_id = 'FIN_BANK_OK'
);
DELETE FROM fn_disbursement WHERE finance_id = 'FIN_BANK_OK';
DELETE FROM idempotency_record WHERE idempotency_key LIKE 'BANK-CB-%' OR idempotency_key LIKE 'BANK-DISB-%';
DELETE FROM fn_finance_application WHERE id = 'FIN_BANK_OK';

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
VALUES (
  'FIN_BANK_OK', 'OP001', 'PJ001', 'FIN-BANK-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL,
  'ORDER_FINANCE', 'ORDER', 'ORD001', 200000.00, 200000.00, 0.00, 'CNY', 90, 0.080000,
  NULL, NULL, 'TO_DISBURSE', 'U003'
);

UPDATE acct_virtual_account SET balance = 10000000.00 WHERE id = 'ACC_FUNDING_001';
UPDATE acct_virtual_account SET balance = 0.00 WHERE id = 'ACC_MEMBER_001';
