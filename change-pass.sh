#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://queue.kryptikk.de}"

USER_ID="${1:-}"
NEW_PASSWORD="${2:-}"

if [[ -z "$USER_ID" ]]; then
  echo "Usage: $0 <user-id> [new-password]"
  exit 1
fi

if [[ -z "$NEW_PASSWORD" ]]; then
  read -rsp "New password: " NEW_PASSWORD
  echo
fi

read -rp "Admin email [admin@queuedos.local]: " ADMIN_EMAIL
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@queuedos.local}"
read -rsp "Admin password: " ADMIN_PASSWORD
echo

TOKEN="$(
  curl -fsS "$BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" \
    | jq -r '.token'
)"

curl -fsS -X PUT "$BASE_URL/api/users/$USER_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"password\":\"$NEW_PASSWORD\"}"

echo
echo "Password changed for user: $USER_ID"