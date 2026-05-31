# Staging seed verification evidence

Archive outputs from `verify-pilot-seed.ps1 -ArchiveDir` for audit trail.

## Run on staging PostgreSQL

```powershell
cd deploy\pilot
# Copy .env.example → .env and set SCF_DB_* to **staging** credentials
.\scripts\verify-pilot-seed.ps1 -ArchiveDir .\evidence\staging
```

Commit the generated `seed-verify-YYYYMMDD-HHmmss.log` after staging sign-off, or attach to release ticket.

## Local dev (optional)

```powershell
# docker compose up -d postgres  (when Docker available)
$env:SCF_DB_PASSWORD = "scf_dev_pass"
.\scripts\verify-pilot-seed.ps1 -ArchiveDir .\evidence\staging
```

## Status

| Environment | Last run | Log file |
|---|---|---|
| staging | _pending — no psql/staging DB in CI agent_ | — |
| local dev | _optional_ | — |
