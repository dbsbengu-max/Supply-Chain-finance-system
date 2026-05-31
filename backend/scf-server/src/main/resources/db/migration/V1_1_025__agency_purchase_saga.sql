-- EA-027: agency purchase cross-domain saga fields and step timeline

ALTER TABLE ap_agency_purchase_application ADD COLUMN inventory_id varchar(64);
ALTER TABLE ap_agency_purchase_application ADD COLUMN margin_account_id varchar(64);
ALTER TABLE ap_agency_purchase_application ADD COLUMN margin_amount numeric(18,2);
ALTER TABLE ap_agency_purchase_application ADD COLUMN margin_frozen_amount numeric(18,2);
ALTER TABLE ap_agency_purchase_application ADD COLUMN inventory_freeze_quantity numeric(18,6);
ALTER TABLE ap_agency_purchase_application ADD COLUMN finance_application_id varchar(64);
ALTER TABLE ap_agency_purchase_application ADD COLUMN saga_status varchar(32);
ALTER TABLE ap_agency_purchase_application ADD COLUMN saga_last_error varchar(1000);

CREATE TABLE ap_agency_purchase_saga_step (
  id varchar(64) PRIMARY KEY,
  application_id varchar(64) NOT NULL REFERENCES ap_agency_purchase_application(id),
  step_code varchar(64) NOT NULL,
  step_status varchar(32) NOT NULL,
  detail_json text,
  executed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_ap_saga_step UNIQUE (application_id, step_code)
);

CREATE INDEX idx_ap_saga_step_app ON ap_agency_purchase_saga_step(application_id);
