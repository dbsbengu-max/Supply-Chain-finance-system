SET search_path TO scf;

DELETE FROM tr_contract_sign_task WHERE document_id = 'DOC_EA040_SIGN_OK';
DELETE FROM tr_document_review_log WHERE document_id = 'DOC_EA040_SIGN_OK';
DELETE FROM tr_document WHERE id = 'DOC_EA040_SIGN_OK';
DELETE FROM fn_finance_application WHERE id = 'FIN_PRECHECK_OK';

INSERT INTO fn_finance_application (
  id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id,
  product_type, source_type, source_id, apply_amount, approved_amount, disbursed_amount,
  currency, term_days, annual_rate, finance_status, created_by, deleted_flag
)
VALUES (
  'FIN_PRECHECK_OK', 'OP001', 'PJ001', 'FIN-EA046-OK', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001',
  'AGENCY_PURCHASE', 'AGENCY_PURCHASE', 'AGP001', 80000.00, 80000.00, 0.00, 'CNY', 90, 0.080000,
  'TO_DISBURSE', 'U003', 0
)
ON CONFLICT (id) DO UPDATE SET
  finance_status = 'TO_DISBURSE',
  deleted_flag = 0;

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
VALUES (
  'DOC_EA040_SIGN_OK', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_OK', 'PURCHASE_CONTRACT', 'PC-EA040-OK', 'FILE_EA040_1',
  'COMPLETED', 'APPROVED', 'APPROVED', 'APPROVED', 'PENDING_SIGN', 'PENDING', 0.9100, 'U001', CURRENT_TIMESTAMP, 0
)
ON CONFLICT (id) DO UPDATE SET
  sign_status = 'PENDING',
  contract_status = 'PENDING_SIGN',
  sign_provider = NULL,
  external_sign_ref = NULL,
  review_status = 'APPROVED',
  business_id = 'FIN_PRECHECK_OK',
  deleted_flag = 0;
