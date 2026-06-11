#!/usr/bin/env bash
# Wrapper for EA-048 on Unix — requires pwsh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/../.env.ea048-real-vendor}"
exec pwsh -NoProfile -File "${SCRIPT_DIR}/run-ea048-real-vendor-gonogo.ps1" -EnvFile "$ENV_FILE" "${@:2}"
