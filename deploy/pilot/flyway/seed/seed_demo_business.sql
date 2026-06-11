-- EA-034 Demo business seed (extracted from V1_1_004 lines 46+)
-- Requires IAM seed first. Apply: .\scripts\apply-seed-profile.ps1 -Profile demo
-- Idempotent: ON CONFLICT DO NOTHING

SET search_path TO scf;

INSERT INTO md_category (id, category_code, category_name, category_type, default_unit, status)
VALUES
('CAT_GARLIC', 'GARLIC', '大蒜', 'AGRICULTURE', 'ton', 'ENABLED'),
('CAT_GINGER', 'GINGER', '生姜', 'AGRICULTURE', 'ton', 'ENABLED'),
('CAT_DISPLAY', 'DISPLAY', '显示屏', 'ELECTRONICS', 'piece', 'ENABLED'),
('CAT_CHIP', 'MEMORY_CHIP', '存储芯片', 'ELECTRONICS', 'piece', 'ENABLED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO md_sku (id, category_id, sku_code, spec, grade, origin, package_type, unit, status)
VALUES
('SKU_GARLIC_A', 'CAT_GARLIC', 'GARLIC-A-SD', '山东大蒜 A 级', 'A', '山东', '袋装', 'ton', 'ENABLED'),
('SKU_DISPLAY_55', 'CAT_DISPLAY', 'DISPLAY-55', '55寸显示屏', 'A', '深圳', '箱装', 'piece', 'ENABLED'),
('SKU_CHIP_128', 'CAT_CHIP', 'CHIP-128G', '128G 存储芯片', 'A', '深圳', '盒装', 'piece', 'ENABLED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fx_rate (id, base_currency, quote_currency, rate, rate_date, source_type, source_name, review_status, version_no, created_by)
VALUES
('FX_USD_CNY_20260527', 'USD', 'CNY', 7.12000000, '2026-05-27', 'MARKET', 'Mock Market', 'APPROVED', 1, 'system'),
('FX_MYR_CNY_20260527', 'MYR', 'CNY', 1.53000000, '2026-05-27', 'MARKET', 'Mock Market', 'APPROVED', 1, 'system'),
('FX_HKD_CNY_20260527', 'HKD', 'CNY', 0.91000000, '2026-05-27', 'MARKET', 'Mock Market', 'APPROVED', 1, 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO pr_price_record (id, sku_id, price_date, price, currency, unit, source_type, source_name, trust_level, review_status, version_no, abnormal_flag, approved_by, approved_at, created_by)
VALUES
('PR_GARLIC_20260527', 'SKU_GARLIC_A', '2026-05-27', 8500.000000, 'CNY', 'ton', 'EXTERNAL_MARKET', 'Mock Price Feed', 'A', 'APPROVED', 1, 0, 'U001', now(), 'system'),
('PR_DISPLAY_20260527', 'SKU_DISPLAY_55', '2026-05-27', 1200.000000, 'CNY', 'piece', 'INTERNAL_DEAL', 'Mock Internal Deal', 'B', 'APPROVED', 1, 0, 'U001', now(), 'system'),
('PR_CHIP_20260527', 'SKU_CHIP_128', '2026-05-27', 80.000000, 'CNY', 'piece', 'MANUAL', 'Mock Manual', 'B', 'APPROVED', 1, 0, 'U001', now(), 'system')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tr_order (id, operator_id, project_id, order_no, order_type, buyer_id, seller_id, trade_company_id, total_amount, currency, country_from, country_to, order_status, signed_at, created_by)
VALUES
('ORD001', 'OP001', 'PJ001', 'ORD-CNMY-0001', 'AGENCY_PURCHASE', 'ENT_MEMBER_001', 'ENT_CORE_001', 'ENT_TRADE_001', 850000.00, 'CNY', 'CN_MAINLAND', 'MY', 'CONFIRMED', now(), 'U003')
ON CONFLICT (id) DO NOTHING;

INSERT INTO tr_order_item (id, order_id, sku_id, quantity, unit, unit_price, amount, delivery_date)
VALUES
('ORDITEM001', 'ORD001', 'SKU_GARLIC_A', 100.000000, 'ton', 8500.000000, 850000.00, '2026-06-15')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ar_receivable (id, operator_id, project_id, ar_no, creditor_id, debtor_id, order_id, amount, available_amount, currency, due_date, confirm_status, finance_status, evidence_status)
VALUES
('AR001', 'OP001', 'PJ001', 'AR-0001', 'ENT_MEMBER_001', 'ENT_CORE_001', 'ORD001', 850000.00, 850000.00, 'CNY', '2026-08-25', 'CONFIRMED', 'NONE', 'SUCCESS')
ON CONFLICT (id) DO NOTHING;

INSERT INTO dv_voucher (id, operator_id, project_id, voucher_no, issuer_id, acceptor_id, holder_id, parent_voucher_id, amount, available_amount, currency, issue_date, due_date, voucher_status, evidence_status)
VALUES
('VOUCHER001', 'OP001', 'PJ001', 'DV-0001', 'ENT_CORE_001', 'ENT_CORE_001', 'ENT_MEMBER_001', NULL, 850000.00, 850000.00, 'CNY', '2026-05-27', '2026-08-25', 'ACCEPTED', 'SUCCESS')
ON CONFLICT (id) DO NOTHING;

INSERT INTO dv_voucher_flow (id, voucher_id, flow_type, from_holder_id, to_holder_id, amount, before_available_amount, after_available_amount, related_voucher_id, operated_by)
VALUES
('DVFLOW001', 'VOUCHER001', 'ISSUE', NULL, 'ENT_MEMBER_001', 850000.00, 0.00, 850000.00, NULL, 'U001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cr_credit (id, operator_id, project_id, credit_no, customer_id, funding_party_id, credit_limit, used_limit, frozen_limit, available_limit, currency, start_date, end_date, credit_status)
VALUES
('CR001', 'OP001', 'PJ001', 'CR-0001', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 1000000.00, 0.00, 0.00, 1000000.00, 'CNY', '2026-05-27', '2027-05-26', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO fn_finance_application (id, operator_id, project_id, finance_no, customer_id, funding_party_id, credit_id, product_type, source_type, source_id, apply_amount, approved_amount, currency, term_days, annual_rate, guarantee_amount, pledge_rate, finance_status, created_by)
VALUES
('FIN001', 'OP001', 'PJ001', 'FIN-0001', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'CR001', 'VOUCHER_FINANCE', 'VOUCHER', 'VOUCHER001', 500000.00, 500000.00, 'CNY', 90, 0.085000, 50000.00, NULL, 'TO_DISBURSE', 'U003')
ON CONFLICT (id) DO NOTHING;

INSERT INTO acct_virtual_account (id, operator_id, project_id, enterprise_id, funding_party_id, account_type, account_no, account_name, currency, balance, frozen_balance, status)
VALUES
('ACC_REPAY_001', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'REPAYMENT', 'VA-REPAY-001', '百农汇回款监管户', 'CNY', 0.00, 0.00, 'ACTIVE'),
('ACC_MARGIN_001', 'OP001', 'PJ001', 'ENT_MEMBER_001', 'ENT_FACTOR_001', 'MARGIN', 'VA-MARGIN-001', '百农汇保证金户', 'CNY', 50000.00, 50000.00, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO clearing_rule (id, operator_id, project_id, funding_party_id, product_type, rule_name, priority_json, fee_formula_json, currency_rule, effective_from, effective_to, review_status, version_no)
VALUES
('CLR_RULE_001', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'VOUCHER_FINANCE', '凭证融资标准清分规则', '{"priority":["penalty","fee","interest","principal","platform_fee","residual"]}', '{"interest":"principal*rate*days/360"}', 'ORIGINAL_CURRENCY', '2026-05-27', NULL, 'APPROVED', 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO wh_warehouse (id, operator_id, warehouse_company_id, warehouse_code, warehouse_name, country_region, address, warehouse_type, status)
VALUES
('WH001', 'OP001', 'ENT_WH_001', 'SZ-BONDED-001', '深圳监管仓一号', 'CN_MAINLAND', '深圳前海监管仓', 'REGULATORY', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO wh_inventory (id, warehouse_id, sku_id, batch_no, owner_id, quantity, available_quantity, frozen_quantity, pledged_quantity, valuation_amount, currency, right_status)
VALUES
('INV001', 'WH001', 'SKU_GARLIC_A', 'BATCH-GARLIC-001', 'ENT_MEMBER_001', 100.000000, 0.000000, 0.000000, 100.000000, 850000.00, 'CNY', 'PLEDGED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO wh_inventory_lock (id, inventory_id, finance_id, lock_type, quantity, lock_status, locked_by)
VALUES
('LOCK001', 'INV001', 'FIN001', 'PLEDGED', 100.000000, 'ACTIVE', 'U002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO lg_order (id, operator_id, project_id, logistics_no, order_id, carrier_id, transport_mode, origin, destination, etd, eta, logistics_status)
VALUES
('LG001', 'OP001', 'PJ001', 'LG-0001', 'ORD001', 'ENT_LOG_001', 'SEA', '深圳', '巴生港', '2026-06-01 10:00:00+08', '2026-06-08 10:00:00+08', 'CUSTOMS_DECLARED')
ON CONFLICT (id) DO NOTHING;

INSERT INTO lg_node (id, logistics_id, node_type, node_time, location, exception_flag)
VALUES
('LGNODE001', 'LG001', 'WAREHOUSE_OUTBOUND', '2026-06-01 09:00:00+08', '深圳监管仓一号', 0),
('LGNODE002', 'LG001', 'CUSTOMS_DECLARED', '2026-06-01 15:00:00+08', '深圳海关', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bi_metric (id, metric_code, metric_name, metric_domain, formula, source_tables, refresh_frequency, permission_scope, status)
VALUES
('BI001', 'total_finance_amount', '累计融资金额', 'FINANCE', 'sum(fn_finance_application.approved_amount)', 'fn_finance_application', 'MINUTE_10', 'PROJECT', 'ENABLED'),
('BI002', 'inventory_value', '库存货值', 'WAREHOUSE', 'sum(wh_inventory.valuation_amount)', 'wh_inventory', 'MINUTE_10', 'PROJECT', 'ENABLED'),
('BI003', 'voucher_balance', '凭证余额', 'VOUCHER', 'sum(dv_voucher.available_amount)', 'dv_voucher', 'MINUTE_10', 'PROJECT', 'ENABLED')
ON CONFLICT (id) DO NOTHING;
