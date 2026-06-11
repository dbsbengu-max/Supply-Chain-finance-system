-- EA-046: export contract-sign sandbox evidence by external_sign_ref
-- Usage (psql):
--   psql ... -v ref="'HTTP-FLOW-xxx'" -f export-contract-sign-evidence.sql -o evidence.sql.out

\set ON_ERROR_STOP on
SET search_path TO scf;

\echo '=== tr_contract_sign_task ==='
SELECT id, document_id, provider_code, external_sign_ref, task_status, callback_status,
       platform_trace_id, provider_request_id, provider_trace_id,
       left(provider_exchange_json, 500) AS provider_exchange_preview,
       retry_count, signed_at, created_at, updated_at
FROM tr_contract_sign_task
WHERE external_sign_ref = :ref
   OR platform_trace_id = :ref
ORDER BY created_at DESC;

\echo '=== tr_document (linked) ==='
SELECT d.id, d.business_id, d.sign_status, d.contract_status, d.sign_provider, d.external_sign_ref
FROM tr_document d
JOIN tr_contract_sign_task t ON t.document_id = d.id
WHERE t.external_sign_ref = :ref
ORDER BY t.created_at DESC
LIMIT 5;

\echo '=== biz_compensation_task (callback pool) ==='
SELECT id, compensation_type, business_type, business_id, compensation_status,
       last_error, created_at, updated_at
FROM biz_compensation_task
WHERE business_type = 'CONTRACT_SIGN_CALLBACK'
  AND (business_id = :ref OR business_id LIKE '%' || trim(both '''' from :'ref') || '%')
ORDER BY created_at DESC
LIMIT 10;

\echo '=== audit_operation_log (sign actions) ==='
SELECT id, action, object_type, object_id, user_id, operation_at,
       left(after_value, 800) AS after_value_preview
FROM audit_operation_log
WHERE action IN (
  'CONTRACT_SIGN_INITIATED',
  'CONTRACT_SIGN_COMPLETED',
  'CONTRACT_SIGN_SUBMIT_FAILED',
  'CONTRACT_SIGN_STATUS_QUERY',
  'SAGA_CONTRACT_SIGN_STATUS_QUERY'
)
AND (
  after_value LIKE '%' || trim(both '''' from :'ref') || '%'
  OR object_id IN (
    SELECT id FROM tr_contract_sign_task WHERE external_sign_ref = :ref
  )
)
ORDER BY operation_at DESC
LIMIT 20;
