#!/usr/bin/env bash
# EA-046 vendor sandbox evidence — thin wrapper (requires PowerShell 7+ on jump box)
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-$(dirname "$SCRIPT_DIR")/.env.esign-sandbox}"
pwsh -File "$SCRIPT_DIR/run-ea046-sandbox-evidence.ps1" -EnvFile "$ENV_FILE" "${@:2}"
