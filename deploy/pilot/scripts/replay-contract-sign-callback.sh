#!/usr/bin/env bash
# Replay contract sign callback with TIMESTAMP_NONCE_SIGNATURE (EA-045 联调包)
set -euo pipefail

EXTERNAL_SIGN_REF="${1:?usage: replay-contract-sign-callback.sh <external_sign_ref> [SUCCESS|FAILED]}"
CALLBACK_STATUS="${2:-SUCCESS}"
BASE_URL="${SCF_BASE_URL:-http://127.0.0.1:8080/api/v1}"
BASE_URL="${BASE_URL%/}"
case "$BASE_URL" in
  */api/v1) ;;
  *) BASE_URL="${BASE_URL}/api/v1" ;;
esac
CALLBACK_SECRET="${SCF_CONTRACT_SIGN_CALLBACK_TOKEN:-mock-contract-sign-callback-token}"
PROVIDER_CODE="${SCF_CONTRACT_SIGN_PROVIDER_CODE:-ESIGN_HTTP}"
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-REPLAY-${EXTERNAL_SIGN_REF}-${CALLBACK_STATUS}}"
NONCE="${NONCE:-$(uuidgen 2>/dev/null | tr -d '-' || date +%s%N)}"
SIGNED_AT="${SIGNED_AT:-2026-06-01T10:00:00Z}"

BODY=$(printf '{"external_sign_ref":"%s","callback_status":"%s","signed_at":"%s","failure_reason":null,"provider_code":"%s"}' \
  "$EXTERNAL_SIGN_REF" "$CALLBACK_STATUS" "$SIGNED_AT" "$PROVIDER_CODE")
TIMESTAMP=$(date +%s)
CANONICAL="${TIMESTAMP}
${NONCE}
${BODY}"

if command -v openssl >/dev/null 2>&1; then
  SIGNATURE=$(printf '%s' "$CANONICAL" | openssl dgst -sha256 -hmac "$CALLBACK_SECRET" | awk '{print $2}')
else
  echo "openssl required for HMAC-SHA256" >&2
  exit 1
fi

echo "POST ${BASE_URL}/integrations/contracts/sign-callback"
echo "Body: ${BODY}"

curl -sS -X POST "${BASE_URL}/integrations/contracts/sign-callback" \
  -H "Content-Type: application/json" \
  -H "X-Contract-Sign-Timestamp: ${TIMESTAMP}" \
  -H "X-Contract-Sign-Nonce: ${NONCE}" \
  -H "X-Contract-Sign-Signature: ${SIGNATURE}" \
  -H "X-Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -d "${BODY}"
echo
