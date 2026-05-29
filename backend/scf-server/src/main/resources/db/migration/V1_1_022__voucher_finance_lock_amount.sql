SET search_path TO scf;

ALTER TABLE dv_voucher
  ADD COLUMN IF NOT EXISTS locked_amount numeric(18,2) NOT NULL DEFAULT 0;

ALTER TABLE dv_voucher
  ADD CONSTRAINT chk_dv_voucher_locked_amount CHECK (locked_amount >= 0);

ALTER TABLE dv_voucher
  ADD CONSTRAINT chk_dv_voucher_available_locked
  CHECK (available_amount + locked_amount <= amount);
