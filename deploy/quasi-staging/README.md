# EA-036 Quasi Staging

This package creates a reproducible quasi-staging environment when there is no jump host and no existing staging backend machine.

It is not production. It is a controlled validation target for EA-035 scripts and UAT rehearsal:

- PostgreSQL 14
- Spring Boot backend using `prod` profile
- Nginx-hosted Vue frontend
- Demo password reset for four pilot users
- Reuses EA-035 gate scripts through an isolated env file

## Start

```powershell
cd deploy\quasi-staging
copy .env.example .env
.\scripts\up.ps1 -Rebuild
```

URLs:

- Web: `http://localhost:15173`
- Backend health: `http://localhost:18080/api/v1/actuator/health`
- PostgreSQL: `localhost:15432/scf`

Demo users:

| User | Password |
|---|---|
| platform_admin | Admin@123 |
| funding_user | Fund@123 |
| member_user | Member@123 |
| warehouse_user | Wh@123 |

## Verify

```powershell
.\scripts\verify.ps1 -WatchMinutes 30 -WatchIntervalMinutes 5
```

Evidence is written to:

```text
deploy/quasi-staging/evidence/
```

## Stop

```powershell
.\scripts\down.ps1
```

Remove data volumes:

```powershell
.\scripts\down.ps1 -Volumes
```

## Promotion Rule

Quasi staging can prove that the deployment package and EA-035 automation are coherent. It cannot replace final staging/prod sign-off. Before go-live, repeat EA-035 against a real staging or pre-production host with rotated secrets and non-demo user passwords.

## Dockerless Fallback

If Docker is unavailable but PostgreSQL and Java are available locally, use the pilot local prod rehearsal instead:

```powershell
cd deploy\pilot
copy .env.local-prod-rehearsal.example .env.local-prod-rehearsal
.\scripts\run-local-prod-rehearsal.ps1 -WatchMinutes 5 -WatchIntervalMinutes 1
```

This runs the backend with the `prod` profile and reuses the EA-035 gate. It is still a local rehearsal, not staging Go/No-Go evidence.
