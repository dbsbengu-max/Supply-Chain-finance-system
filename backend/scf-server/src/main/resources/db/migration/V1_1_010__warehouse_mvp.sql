SET search_path TO scf;

ALTER TABLE wh_warehouse
  ADD COLUMN IF NOT EXISTS project_id varchar(64) REFERENCES sys_project(id);

UPDATE wh_warehouse SET project_id = 'PJ001' WHERE project_id IS NULL AND operator_id = 'OP001';

ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS operator_id varchar(64);
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS project_id varchar(64);
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS location_code varchar(100);
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS stocktake_exception smallint NOT NULL DEFAULT 0;
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS created_by varchar(64);
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS created_at timestamptz;
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS updated_by varchar(64);
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS updated_at timestamptz;
ALTER TABLE wh_inventory ADD COLUMN IF NOT EXISTS deleted_flag smallint NOT NULL DEFAULT 0;

UPDATE wh_inventory i
SET operator_id = w.operator_id,
    project_id = w.project_id,
    created_by = COALESCE(i.created_by, 'system'),
    created_at = COALESCE(i.created_at, now()),
    deleted_flag = 0
FROM wh_warehouse w
WHERE i.warehouse_id = w.id
  AND i.operator_id IS NULL;

CREATE TABLE IF NOT EXISTS wh_inbound (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  inbound_no varchar(64) NOT NULL UNIQUE,
  warehouse_id varchar(64) NOT NULL REFERENCES wh_warehouse(id),
  sku_id varchar(64) NOT NULL REFERENCES md_sku(id),
  batch_no varchar(100) NOT NULL,
  owner_id varchar(64) NOT NULL REFERENCES md_enterprise(id),
  location_code varchar(100),
  quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
  valuation_amount numeric(18,2),
  currency varchar(16),
  inbound_status varchar(32) NOT NULL DEFAULT 'COMPLETED',
  inventory_id varchar(64) REFERENCES wh_inventory(id),
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_flag smallint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wh_inbound_scope ON wh_inbound(operator_id, project_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wh_release_request (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  request_no varchar(64) NOT NULL UNIQUE,
  inventory_id varchar(64) NOT NULL REFERENCES wh_inventory(id),
  quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
  request_status varchar(32) NOT NULL,
  remark varchar(500),
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  approved_by varchar(64),
  approved_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wh_release_inv ON wh_release_request(inventory_id, request_status);

CREATE TABLE IF NOT EXISTS wh_outbound_request (
  id varchar(64) PRIMARY KEY,
  operator_id varchar(64) NOT NULL REFERENCES sys_operator(id),
  project_id varchar(64) NOT NULL REFERENCES sys_project(id),
  request_no varchar(64) NOT NULL UNIQUE,
  inventory_id varchar(64) NOT NULL REFERENCES wh_inventory(id),
  quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
  request_status varchar(32) NOT NULL,
  remark varchar(500),
  created_by varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  confirmed_by varchar(64),
  confirmed_at timestamptz,
  deleted_flag smallint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wh_outbound_inv ON wh_outbound_request(inventory_id, request_status);

INSERT INTO wh_inventory (
  id, warehouse_id, operator_id, project_id, sku_id, batch_no, owner_id, location_code,
  quantity, available_quantity, frozen_quantity, pledged_quantity, outbound_pending_quantity,
  valuation_amount, currency, right_status, stocktake_exception, version_no, created_by, created_at, deleted_flag
)
SELECT
  'INV002', 'WH001', 'OP001', 'PJ001', 'SKU_GARLIC_A', 'BATCH-GARLIC-002', 'ENT_MEMBER_001', 'A-01-02',
  50.000000, 50.000000, 0.000000, 0.000000, 0.000000,
  425000.00, 'CNY', 'IN_STOCK', 0, 1, 'system', now(), 0
WHERE NOT EXISTS (
  SELECT 1 FROM wh_inventory existing WHERE existing.id = 'INV002'
);

UPDATE wh_inventory
SET operator_id = 'OP001', project_id = 'PJ001', location_code = 'A-01-01',
    stocktake_exception = 0, created_by = 'system', created_at = now(), deleted_flag = 0
WHERE id = 'INV001';

WITH perms(id, code, name, type, resource, method, path) AS (
  VALUES
    ('PERM_WAREHOUSE_INBOUND', 'WAREHOUSE_INBOUND', '仓储入库', 'API', 'WAREHOUSE', 'POST', '/warehouse/inbounds'),
    ('PERM_WAREHOUSE_FREEZE', 'WAREHOUSE_FREEZE', '库存冻结', 'API', 'WAREHOUSE', 'POST', '/warehouse/inventories/*/freeze'),
    ('PERM_WAREHOUSE_OUTBOUND', 'WAREHOUSE_OUTBOUND', '出库申请', 'API', 'WAREHOUSE', 'POST', '/warehouse/outbounds')
)
INSERT INTO sys_permission (id, operator_id, permission_code, permission_name, permission_type, resource_code, api_method, api_path, status)
SELECT id, 'OP001', code, name, type, resource, method, path, 'ENABLED'
FROM perms
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission existing
  WHERE existing.operator_id = 'OP001' AND existing.permission_code = perms.code
);

INSERT INTO sys_role_permission (id, role_id, permission_id, granted_by)
SELECT rp.role_id || ':' || p.id, rp.role_id, p.id, 'system'
FROM (
  SELECT 'ROLE_WAREHOUSE' AS role_id, 'WAREHOUSE_INBOUND' AS permission_code
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'WAREHOUSE_FREEZE'
  UNION ALL SELECT 'ROLE_WAREHOUSE', 'WAREHOUSE_OUTBOUND'
  UNION ALL SELECT 'ROLE_MEMBER', 'WAREHOUSE_INBOUND'
  UNION ALL SELECT 'ROLE_MEMBER', 'WAREHOUSE_FREEZE'
  UNION ALL
  SELECT 'ROLE_PLATFORM_ADMIN', permission_code FROM sys_permission WHERE operator_id = 'OP001' AND permission_code LIKE 'WAREHOUSE_%'
) rp
JOIN sys_permission p ON p.operator_id = 'OP001' AND p.permission_code = rp.permission_code
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission existing
  WHERE existing.role_id = rp.role_id AND existing.permission_id = p.id
);
