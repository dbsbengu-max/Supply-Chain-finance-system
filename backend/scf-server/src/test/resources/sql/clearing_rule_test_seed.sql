SET search_path TO scf;

DELETE FROM clearing_rule WHERE id LIKE 'CLR_RULE_EA017_%';

INSERT INTO clearing_rule (
  id, operator_id, project_id, funding_party_id, product_type, rule_name,
  priority_json, fee_formula_json, currency_rule, effective_from, effective_to,
  review_status, version_no
)
VALUES
(
  'CLR_RULE_EA017_APPROVED', 'OP001', 'PJ001', 'ENT_FACTOR_001', 'ORDER_FINANCE', 'EA017 已批准规则',
  '{"priority":["interest","principal"]}', '{"interest":"principal*rate*days/360"}',
  'ORIGINAL_CURRENCY', '2026-06-01', NULL, 'APPROVED', 1
),
(
  'CLR_RULE_EA017_OTHER_FACTOR', 'OP001', 'PJ001', 'ENT_CORE_001', 'ORDER_FINANCE', 'EA017 其他资方规则',
  '{"priority":["interest","principal"]}', '{"interest":"principal*rate*days/360"}',
  'ORIGINAL_CURRENCY', '2026-06-01', NULL, 'DRAFT', 1
);
