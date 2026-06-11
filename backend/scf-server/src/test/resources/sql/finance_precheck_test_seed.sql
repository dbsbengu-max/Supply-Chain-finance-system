SET search_path TO scf;

DELETE FROM tr_contract_sign_task
WHERE document_id IN (
  SELECT id FROM tr_document
  WHERE business_id IN ('FIN_PRECHECK_OK', 'FIN_PRECHECK_NO_DOC', 'FIN_PRECHECK_LOW_CREDIT', 'FIN_PRECHECK_LOW_BAL')
);
DELETE FROM tr_document_review_log
WHERE document_id IN (
  SELECT id FROM tr_document
  WHERE business_id IN ('FIN_PRECHECK_OK', 'FIN_PRECHECK_NO_DOC', 'FIN_PRECHECK_LOW_CREDIT', 'FIN_PRECHECK_LOW_BAL')
);
DELETE FROM tr_document
WHERE business_id IN ('FIN_PRECHECK_OK', 'FIN_PRECHECK_NO_DOC', 'FIN_PRECHECK_LOW_CREDIT', 'FIN_PRECHECK_LOW_BAL');
DELETE FROM fn_finance_application
WHERE id IN ('FIN_PRECHECK_OK', 'FIN_PRECHECK_NO_DOC', 'FIN_PRECHECK_LOW_CREDIT', 'FIN_PRECHECK_LOW_BAL');

MERGE INTO cr_credit (
  id, operator_id, project_id, credit_no, customer_id, funding_party_id,
  credit_limit, used_limit, frozen_limit, available_limit, currency, start_date, end_date, credit_status
)
KEY(id) VALUES
('CR_PRECHECK_TIGHT', 'OP001', 'PJ001', 'CR-EA039-TIGHT', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
 100000.00, 0.00, 0.00, 10000.00, 'CNY', DATE '2026-01-01', DATE '2027-12-31', 'ACTIVE');

MERGE INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
KEY(id) VALUES
(
  'ACC_FUNDING_LOW', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ENT_FACTOR_001',
  'DISBURSE', 'VA-FUND-LOW', '资金方低余额户', 'CNY', 5000.00, 0.00, 'ACTIVE'
);

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, finance_status, created_by
)
VALUES
('FIN_PRECHECK_OK', 'OP001', 'PJ001', 'FIN-EA039-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
 'AGENCY_PURCHASE', 'AGENCY_PURCHASE', 'AGP001', 80000.00, 80000.00, 0.00, 'CNY', 90, 0.080000, 'TO_DISBURSE', 'U003'),
('FIN_PRECHECK_NO_DOC', 'OP001', 'PJ001', 'FIN-EA039-NODOC', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
 'AGENCY_PURCHASE', 'AGENCY_PURCHASE', 'AGP002', 90000.00, 90000.00, 0.00, 'CNY', 90, 0.080000, 'TO_DISBURSE', 'U003'),
('FIN_PRECHECK_LOW_CREDIT', 'OP001', 'PJ001', 'FIN-EA039-CREDIT', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR_PRECHECK_TIGHT',
 'AGENCY_PURCHASE', 'AGENCY_PURCHASE', 'AGP003', 500000.00, 500000.00, 0.00, 'CNY', 90, 0.080000, 'TO_DISBURSE', 'U003'),
('FIN_PRECHECK_LOW_BAL', 'OP001', 'PJ001', 'FIN-EA039-BAL', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
 'AGENCY_PURCHASE', 'AGENCY_PURCHASE', 'AGP004', 70000.00, 70000.00, 0.00, 'CNY', 90, 0.080000, 'TO_DISBURSE', 'U003');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_INV_OK', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_OK', 'INVOICE', 'INV-OK', 'FILE_EA039_1',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'NOT_CONTRACT', 'NOT_REQUIRED', 0.9200, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_INV_OK');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_PC_OK', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_OK', 'PURCHASE_CONTRACT', 'PC-OK', 'FILE_EA039_2',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'SIGNED', 'SIGNED', 0.9100, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_PC_OK');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_INV_CREDIT', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_LOW_CREDIT', 'INVOICE', 'INV-CREDIT', 'FILE_EA039_5',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'NOT_CONTRACT', 'NOT_REQUIRED', 0.9200, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_INV_CREDIT');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_PC_CREDIT', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_LOW_CREDIT', 'PURCHASE_CONTRACT', 'PC-CREDIT', 'FILE_EA039_6',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'SIGNED', 'SIGNED', 0.9100, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_PC_CREDIT');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_INV_BAL', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_LOW_BAL', 'INVOICE', 'INV-BAL', 'FILE_EA039_3',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'NOT_CONTRACT', 'NOT_REQUIRED', 0.9200, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_INV_BAL');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
SELECT 'DOC_EA039_PC_BAL', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_LOW_BAL', 'PURCHASE_CONTRACT', 'PC-BAL', 'FILE_EA039_4',
       'COMPLETED', 'PASSED', 'OCR_COMPLETED', 'APPROVED', 'SIGNED', 'SIGNED', 0.9100, 'U001', CURRENT_TIMESTAMP, 0
WHERE NOT EXISTS (SELECT 1 FROM tr_document WHERE id = 'DOC_EA039_PC_BAL');
