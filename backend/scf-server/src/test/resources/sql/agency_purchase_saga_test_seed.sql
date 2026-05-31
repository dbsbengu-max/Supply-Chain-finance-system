SET search_path TO scf;

DELETE FROM ap_agency_purchase_saga_step WHERE application_id IN (
  SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%'
);
DELETE FROM biz_compensation_task WHERE business_type = 'AGENCY_PURCHASE'
  AND business_id IN (SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%');
DELETE FROM biz_event_outbox WHERE business_type = 'AGENCY_PURCHASE'
  AND business_id IN (SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%');
DELETE FROM bpm_task WHERE business_id IN (SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%');
DELETE FROM bpm_process_instance WHERE business_id IN (SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%');
DELETE FROM fn_finance_application WHERE source_type = 'AGENCY_PURCHASE'
  AND source_id IN (SELECT id FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%');
DELETE FROM ap_agency_purchase_application WHERE remark LIKE 'ea027-%'
   OR remark = 'ea027-stock-prepare-fail';

MERGE INTO acct_virtual_account (
  id, operator_id, project_id, enterprise_id, funding_party_id,
  account_type, account_no, account_name, currency, balance, frozen_balance, status
)
KEY(id) VALUES
(
  'ACC_MARGIN_SAGA', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
  'MARGIN', 'VA-MARGIN-SAGA', 'Saga Margin Account', 'CNY', 200000.00, 0.00, 'ACTIVE'
),
(
  'ACC_MARGIN_LOW', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001',
  'MARGIN', 'VA-MARGIN-LOW', 'Saga Low Margin Account', 'CNY', 1000.00, 0.00, 'ACTIVE'
);

MERGE INTO tr_order (
  id, operator_id, project_id, order_no, order_type, buyer_id, seller_id, trade_company_id,
  total_amount, currency, country_from, country_to, order_status, signed_at, created_by, deleted_flag
)
KEY(id) VALUES
(
  'ORD_SAGA_SUBMITTED', 'OP001', 'PJ001', 'ORD-SAGA-SUBMIT', 'AGENCY_PURCHASE',
  'ENT_MEMBER_001', 'ENT_CORE_001', 'ENT_TRADE_001', 100000.00, 'CNY', 'CN_MAINLAND', 'MY',
  'SUBMITTED', NULL, 'U003', 0
);

MERGE INTO wh_inventory (
  id, warehouse_id, operator_id, project_id, sku_id, batch_no, owner_id,
  quantity, available_quantity, frozen_quantity, pledged_quantity, outbound_pending_quantity,
  valuation_amount, currency, right_status, stocktake_exception, deleted_flag, version_no
)
KEY(id) VALUES
(
  'INV_SAGA', 'WH001', 'OP001', 'PJ001', 'SKU_GARLIC_A', 'BATCH-SAGA-001', 'ENT_MEMBER_001',
  100.000000, 100.000000, 0.000000, 0.000000, 0.000000, 500000.00, 'CNY', 'IN_STOCK', 0, 0, 1
);
