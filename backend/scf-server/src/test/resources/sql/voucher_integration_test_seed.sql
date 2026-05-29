SET search_path TO scf;

DELETE FROM dv_voucher_flow WHERE voucher_id IN ('VOUCHER_SAGA_TEST', 'VOUCHER_EA024');
DELETE FROM dv_voucher WHERE id IN ('VOUCHER_SAGA_TEST', 'VOUCHER_EA024');

DELETE FROM fn_disbursement WHERE finance_id = 'FIN_VOUCHER_SAGA';
DELETE FROM biz_event_outbox WHERE idempotency_key = 'FINANCE-DISBURSED-FIN_VOUCHER_SAGA';
DELETE FROM fn_finance_application WHERE id = 'FIN_VOUCHER_SAGA';

MERGE INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
KEY(id) VALUES
(
  'ACC_FUNDING_001', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ENT_FACTOR_001',
  'DISBURSE', 'VA-FUND-001', '资金方放款户', 'CNY', 10000000.00, 0.00, 'ACTIVE'
),
(
  'ACC_MEMBER_001', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
  'RECEIVE', 'VA-MEM-001', '成员企业收款户', 'CNY', 0.00, 0.00, 'ACTIVE'
);

INSERT INTO dv_voucher (
  id, operator_id, project_id, voucher_no, issuer_id, acceptor_id, holder_id,
  parent_voucher_id, amount, available_amount, currency, issue_date, due_date,
  voucher_status, evidence_status
)
VALUES
(
  'VOUCHER_SAGA_TEST', 'OP001', 'PJ001', 'DV-SAGA-TEST', 'ENT_CORE_001', 'ENT_CORE_001', 'ENT_MEMBER_001',
  NULL, 600000.00, 600000.00, 'CNY', '2026-05-27', '2026-08-25', 'ACCEPTED', 'SUCCESS'
);

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
VALUES
(
  'FIN_VOUCHER_SAGA', 'OP001', 'PJ001', 'FIN-EA024-SAGA', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
  'VOUCHER_FINANCE', 'VOUCHER', 'VOUCHER_SAGA_TEST', 300000.00, 300000.00, 0.00,
  'CNY', 90, 0.085000, NULL, NULL, 'TO_DISBURSE', 'U003'
);
