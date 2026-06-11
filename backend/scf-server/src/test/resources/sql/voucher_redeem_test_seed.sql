SET search_path TO scf;

DELETE FROM dv_voucher_flow WHERE voucher_id = 'VOUCHER_REDEEM_TEST';
DELETE FROM dv_voucher WHERE id = 'VOUCHER_REDEEM_TEST';
DELETE FROM bpm_task WHERE business_id = 'VOUCHER_REDEEM_TEST';
DELETE FROM bpm_process_instance WHERE business_id = 'VOUCHER_REDEEM_TEST';
DELETE FROM idempotency_record WHERE idempotency_key LIKE 'EA026-REDEEM-%';
DELETE FROM acct_bank_flow WHERE external_flow_no LIKE 'REDEEM-EA026-%';

MERGE INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
KEY(id) VALUES
(
  'ACC_CORE_001', 'OP001', 'PJ001', 'ENT_CORE_001', 'ENT_FACTOR_001',
  'PAYMENT', 'VA-CORE-001', '核心企业兑付户', 'CNY', 1000000.00, 0.00, 'ACTIVE'
),
(
  'ACC_MEMBER_001', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
  'RECEIVE', 'VA-MEM-001', '成员企业收款户', 'CNY', 0.00, 0.00, 'ACTIVE'
);

INSERT INTO dv_voucher (
  id, operator_id, project_id, voucher_no, issuer_id, acceptor_id, holder_id,
  parent_voucher_id, amount, available_amount, locked_amount, currency, issue_date, due_date,
  voucher_status, evidence_status
)
VALUES (
  'VOUCHER_REDEEM_TEST', 'OP001', 'PJ001', 'DV-REDEEM-TEST', 'ENT_CORE_001', 'ENT_CORE_001', 'ENT_MEMBER_001',
  NULL, 100000.00, 100000.00, 0.00, 'CNY', '2026-05-27', '2026-08-25', 'ACCEPTED', 'SUCCESS'
);
