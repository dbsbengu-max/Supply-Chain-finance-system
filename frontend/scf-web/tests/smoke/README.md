# Browser smoke (EA-031)

## Prerequisites

1. Backend API running at `http://localhost:8080`
2. Node dependencies installed (`npm install`)
3. Playwright browser: `npm run smoke:install`

## Run

```bash
npm run smoke
```

Playwright starts Vite dev server (or reuses an existing one on :5173) and runs six serial checks:

| ID | Flow |
|---|---|
| SMOKE-01 | Login (`platform_admin` / `Admin@123`) |
| SMOKE-02 | 试点闭环 |
| SMOKE-03 | Saga 监控 |
| SMOKE-04 | 融资管理 |
| SMOKE-05 | 清分中心 |
| SMOKE-06 | 经营看板 |

Final line: `>>> SMOKE: PASS <<<` or `>>> SMOKE: FAIL <<<`.

If dev server is already running:

```bash
SMOKE_SKIP_WEBSERVER=1 npm run smoke
```

On Windows PowerShell:

```powershell
$env:SMOKE_SKIP_WEBSERVER=1; npm run smoke
```
