#!/usr/bin/env bash
# Cloud Supabase settings that would otherwise live only in the dashboard (CLAUDE.md hard rule 5).
# The human runs it from the repo root with `mise exec -- supabase/cloud/configure.sh <step>`
# (supabase/README.md, Cloud project). Secrets are read from and written to ~/.nuddle/prod, one
# file per value, and never printed.
#
#   secrets  generate TICK_SECRET and SEND_SMS_HOOK_SECRET (only the missing ones)
#   auth     apply auth.json plus the Send SMS hook secret through the Management API, and the
#            home test numbers of ~/.nuddle/prod/TEST_PHONES if that file exists (NUD-57)
#            (needs ~/.nuddle/prod/SUPABASE_ACCESS_TOKEN). Never `supabase config push`: it would
#            enable the local test numbers in the cloud (ADR 0011).
#   tick     store tick_url and tick_secret in Vault, which switches the nuddle-tick job on
#            (needs `supabase link`; run it once production health is green)
#   migrate [--dry-run]
#            apply new migrations (`supabase db push`, never the seed) through the session pooler.
#            Until the beta the agent runs it right after merging a migration (CLAUDE.md rule 6).
set -euo pipefail
umask 077

ref=hksqgdkbxalaixjghlvi
host=https://nuddle.app
dir="${NUDDLE_PROD_DIR:-$HOME/.nuddle/prod}"
# The fixed code and its end date for the home test (NUD-57); removed before the beta.
HOME_TEST_CODE=123456
HOME_TEST_UNTIL=2026-12-01T00:00:00Z
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
    # Home test before the beta (NUD-57): the numbers in TEST_PHONES (one per line, digits with
    # the country code, e.g. 905321234567; never in the repo) sign in with HOME_TEST_CODE without an
    # SMS, until HOME_TEST_UNTIL. No file: no test numbers, as auth.json says.
    test_otp=""
    if [[ -f "$dir/TEST_PHONES" ]]; then
      test_otp="$(tr -d ' +\r' <"$dir/TEST_PHONES" | grep -E '^[0-9]{10,15}$' |
        sed "s/\$/=$HOME_TEST_CODE/" | paste -sd, -)"
    fi
    jq --rawfile secret "$dir/SEND_SMS_HOOK_SECRET" --arg otp "$test_otp" --arg until "$HOME_TEST_UNTIL" \
      '. + {hook_send_sms_secrets: $secret}
         + (if $otp == "" then {} else {sms_test_otp: $otp, sms_test_otp_valid_until: $until} end)' \
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
      test_numbers: ((.sms_test_otp // "") | split(",") | map(select(. != "")) | length),
      sms_test_otp_valid_until, hook_send_sms_enabled, hook_send_sms_uri,
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

  migrate)
    need DATABASE_URL
    if [[ $# -gt 2 || ($# -eq 2 && "$2" != "--dry-run") ]]; then
      echo "usage: $0 migrate [--dry-run]" >&2
      exit 2
    fi
    # Migrations need session semantics: same pooler host and user, port 5432 instead of 6543.
    url="$(sed -E 's|(\.pooler\.supabase\.com):6543/|\1:5432/|' "$dir/DATABASE_URL")"
    if [[ "$url" != *".pooler.supabase.com:5432/"* ]]; then
      echo "DATABASE_URL is not a Supabase pooler URL on port 6543" >&2
      exit 1
    fi
    supabase db push --db-url "$url" --yes ${2:+"$2"}
    ;;

  *)
    echo "usage: $0 secrets|auth|tick|migrate [--dry-run]" >&2
    exit 2
    ;;
esac
