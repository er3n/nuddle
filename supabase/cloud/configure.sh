#!/usr/bin/env bash
# Cloud Supabase settings that would otherwise live only in the dashboard (CLAUDE.md hard rule 5).
# The human runs it from the repo root with `mise exec -- supabase/cloud/configure.sh <step>`
# (supabase/README.md, Cloud project). Secrets are read from and written to ~/.nuddle/prod, one
# file per value, and never printed.
#
#   secrets  generate TICK_SECRET and SEND_SMS_HOOK_SECRET (only the missing ones)
#   auth     apply auth.json plus the Send SMS hook secret through the Management API
#            (needs ~/.nuddle/prod/SUPABASE_ACCESS_TOKEN). Never `supabase config push`: it would
#            enable the local test numbers in the cloud (ADR 0011).
#   tick     store tick_url and tick_secret in Vault, which switches the nuddle-tick job on
#            (needs `supabase link`; run it once production health is green)
set -euo pipefail
umask 077

ref=hksqgdkbxalaixjghlvi
host=https://nuddle.app
dir="${NUDDLE_PROD_DIR:-$HOME/.nuddle/prod}"
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

need() {
  if [[ ! -s "$dir/$1" ]]; then
    echo "missing $dir/$1 (docs/human-checklist.md, Before stage 7)" >&2
    exit 1
  fi
}

management_api() { # method path [body file]
  printf 'Authorization: Bearer %s\n' "$(cat "$dir/SUPABASE_ACCESS_TOKEN")" >"$tmp/headers"
  local args=(-sS -o "$tmp/response" -w '%{http_code}' -X "$1" -H @"$tmp/headers")
  if [[ $# -gt 2 ]]; then args+=(-H 'Content-Type: application/json' --data-binary @"$3"); fi
  curl "${args[@]}" "https://api.supabase.com/v1/projects/$ref$2"
}

case "${1:-}" in
  secrets)
    mkdir -p "$dir"
    chmod 700 "$dir"
    if [[ ! -s "$dir/TICK_SECRET" ]]; then
      openssl rand -hex 32 | tr -d '\n' >"$dir/TICK_SECRET"
    fi
    if [[ ! -s "$dir/SEND_SMS_HOOK_SECRET" ]]; then
      printf 'v1,whsec_%s' "$(openssl rand -base64 32 | tr -d '\n')" >"$dir/SEND_SMS_HOOK_SECRET"
    fi
    echo "TICK_SECRET and SEND_SMS_HOOK_SECRET are in $dir"
    ;;

  auth)
    need SUPABASE_ACCESS_TOKEN
    need SEND_SMS_HOOK_SECRET
    jq --rawfile secret "$dir/SEND_SMS_HOOK_SECRET" '. + {hook_send_sms_secrets: $secret}' \
      "$here/auth.json" >"$tmp/body"
    status="$(management_api PATCH /config/auth "$tmp/body")"
    if [[ "$status" != 2* ]]; then
      # The error body never echoes the config; only its message is shown.
      echo "auth config failed: HTTP $status: $(jq -r '.message // "no message"' "$tmp/response")" >&2
      exit 1
    fi
    status="$(management_api GET /config/auth)"
    echo "auth config applied (HTTP $status). Read back:"
    jq '{site_url, disable_signup, external_phone_enabled, external_email_enabled,
      sms_test_otp, hook_send_sms_enabled, hook_send_sms_uri,
      hook_send_sms_secret_set: ((.hook_send_sms_secrets // "") != "")}' "$tmp/response"
    ;;

  tick)
    need TICK_SECRET
    secret="$(cat "$dir/TICK_SECRET")"
    if [[ ! "$secret" =~ ^[0-9a-f]{64}$ ]]; then
      echo "TICK_SECRET is not 64 hex characters" >&2
      exit 1
    fi
    cat >"$tmp/vault.sql" <<SQL
do \$\$
declare
  wanted constant jsonb := jsonb_build_object(
    'tick_url', '$host/api/internal/tick',
    'tick_secret', '$secret'
  );
  entry record;
  existing uuid;
begin
  for entry in select key, value #>> '{}' as value from jsonb_each(wanted) loop
    select id into existing from vault.secrets where name = entry.key;
    if existing is null then
      perform vault.create_secret(entry.value, entry.key);
    else
      perform vault.update_secret(existing, entry.value);
    end if;
  end loop;
end
\$\$;
SQL
    supabase db query --linked -f "$tmp/vault.sql" >/dev/null
    echo "Vault tick_url and tick_secret stored. The nuddle-tick job calls $host every minute:"
    supabase db query --linked \
      "select jobname, schedule, active, (select count(*) from vault.secrets where name in ('tick_url', 'tick_secret')) as vault_secrets from cron.job where jobname = 'nuddle-tick'"
    ;;

  *)
    echo "usage: $0 secrets|auth|tick" >&2
    exit 2
    ;;
esac
