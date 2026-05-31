-- EA-034 Seed profile manifest (optional apply via apply-seed-profile.ps1)
-- Flyway V1_1_004 remains immutable for existing environments.

SET search_path TO scf;

CREATE TABLE IF NOT EXISTS sys_seed_manifest (
    id              VARCHAR(64) PRIMARY KEY,
    seed_profile    VARCHAR(32) NOT NULL,
    source_file     VARCHAR(128) NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_by      VARCHAR(128)
);

COMMENT ON TABLE sys_seed_manifest IS 'Tracks IAM/DEMO seed profiles applied outside or alongside Flyway V1_1_004';

-- Backfill marker when V1_1_004 already ran (Flyway version present)
INSERT INTO sys_seed_manifest (id, seed_profile, source_file, applied_by)
SELECT 'V1_1_004', 'FULL', 'V1_1_004__seed_mock_data.sql', 'flyway'
WHERE EXISTS (
    SELECT 1 FROM flyway_schema_history WHERE version = '1.1.004' AND success = true
)
ON CONFLICT (id) DO NOTHING;
