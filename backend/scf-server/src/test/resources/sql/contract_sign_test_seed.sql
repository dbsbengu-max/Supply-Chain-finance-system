SET search_path TO scf;

DELETE FROM tr_contract_sign_task WHERE document_id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL', 'DOC_EA040_UNSIGNED');
DELETE FROM tr_document_review_log WHERE document_id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL', 'DOC_EA040_UNSIGNED');
DELETE FROM tr_document WHERE id IN ('DOC_EA040_SIGN_OK', 'DOC_EA040_SIGN_FAIL', 'DOC_EA040_UNSIGNED');

INSERT INTO tr_document (
  id, operator_id, project_id, business_type, business_id, document_type, document_no, file_id,
  ocr_status, validation_status, document_status, review_status, contract_status, sign_status,
  ocr_confidence, created_by, created_at, deleted_flag
)
VALUES
('DOC_EA040_SIGN_OK', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_OK', 'PURCHASE_CONTRACT', 'PC-EA040-OK', 'FILE_EA040_1',
 'COMPLETED', 'APPROVED', 'APPROVED', 'APPROVED', 'PENDING_SIGN', 'PENDING', 0.9100, 'U001', CURRENT_TIMESTAMP, 0),
('DOC_EA040_SIGN_FAIL', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_OK', 'PURCHASE_CONTRACT', 'PC-EA040-FAIL', 'FILE_EA040_2',
 'COMPLETED', 'APPROVED', 'APPROVED', 'APPROVED', 'PENDING_SIGN', 'PENDING', 0.9100, 'U001', CURRENT_TIMESTAMP, 0),
('DOC_EA040_UNSIGNED', 'OP001', 'PJ001', 'FINANCE', 'FIN_PRECHECK_NO_DOC', 'PURCHASE_CONTRACT', 'PC-EA040-UNSIGNED', 'FILE_EA040_3',
 'COMPLETED', 'APPROVED', 'APPROVED', 'APPROVED', 'PENDING_SIGN', 'PENDING', 0.9100, 'U001', CURRENT_TIMESTAMP, 0);
