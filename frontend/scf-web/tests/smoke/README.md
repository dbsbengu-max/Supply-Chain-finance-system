# Browser smoke (EA-031 / EA-049)

## Prerequisites

1. Backend API running at `http://localhost:8080`
2. Node dependencies installed (`npm install`)
3. Playwright browser: `npm run smoke:install`

## Run

```bash
npm run smoke
```

Playwright starts Vite dev server (or reuses an existing one on :5173) and runs **nine** serial checks:

| ID | Flow |
|---|---|
| SMOKE-01 | Login (`platform_admin` / `Admin@123`) |
| SMOKE-02 | 试点闭环 |
| SMOKE-03 | 补偿池 |
| SMOKE-04 | 融资管理 |
| SMOKE-05 | 清分中心 |
| SMOKE-06 | 经营看板 |
| SMOKE-07 | 功能上线收口 + UAT 验收入口 |
| SMOKE-08 | 签章中心 |
| SMOKE-09 | 客户/KYC |

Final line: `>>> SMOKE: PASS <<<` or `>>> SMOKE: FAIL <<<`.

On Windows PowerShell (recommended — exits cleanly after tests):

```powershell
npm run smoke
```

If dev server is already running:

```powershell
$env:SMOKE_SKIP_WEBSERVER=1; npm run smoke
```

Direct Playwright (may hang on Windows if Vite child process stays open):

```powershell
npm run smoke:direct
```
