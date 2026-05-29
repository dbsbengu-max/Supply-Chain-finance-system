SET search_path TO scf;

MERGE INTO sys_project (id, operator_id, project_code, project_name, countries, currencies, status, created_by)
KEY(id) VALUES
('PJ_TEST_OTHER', 'OP001', 'OTHER-DISB-TEST', '放款验收测试其他项目', 'CN_MAINLAND', 'CNY', 'ACTIVE', 'test');

DELETE FROM fn_disbursement
WHERE finance_id IN (
  'FIN_DISB_OK',
  'FIN_DISB_IDEMP',
  'FIN_DISB_CONFLICT',
  'FIN_DISB_STATE',
  'FIN_DISB_CROSS',
  'FIN_DISB_PARTIAL'
);

DELETE FROM idempotency_record WHERE idempotency_key LIKE 'EA015-%';

DELETE FROM fn_finance_application
WHERE id IN (
  'FIN_DISB_OK',
  'FIN_DISB_IDEMP',
  'FIN_DISB_CONFLICT',
  'FIN_DISB_STATE',
  'FIN_DISB_CROSS',
  'FIN_DISB_PARTIAL'
);

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

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by
)
VALUES
('FIN_DISB_OK', 'OP001', 'PJ001', 'FIN-EA015-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 120000.00, 120000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'TO_DISBURSE', 'U003'),
('FIN_DISB_IDEMP', 'OP001', 'PJ001', 'FIN-EA015-IDEMP', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 130000.00, 130000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'TO_DISBURSE', 'U003'),
('FIN_DISB_CONFLICT', 'OP001', 'PJ001', 'FIN-EA015-CONFLICT', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 140000.00, 140000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'TO_DISBURSE', 'U003'),
('FIN_DISB_STATE', 'OP001', 'PJ001', 'FIN-EA015-STATE', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 150000.00, 150000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'SUBMITTED', 'U003'),
('FIN_DISB_PARTIAL', 'OP001', 'PJ001', 'FIN-EA015-PARTIAL', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 160000.00, 160000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'TO_DISBURSE', 'U003'),
('FIN_DISB_CROSS', 'OP001', 'PJ_TEST_OTHER', 'FIN-EA015-CROSS', 'ENT_MEMBER_001', 'ENT_FACTOR_001', NULL, 'ORDER_FINANCE', 'ORDER', 'ORD001', 170000.00, 170000.00, 0.00, 'CNY', 90, 0.080000, NULL, NULL, 'TO_DISBURSE', 'U003');
