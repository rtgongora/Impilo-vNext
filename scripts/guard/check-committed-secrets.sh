#!/usr/bin/env bash
#
# P0 secrets guardrail (docs/security/secrets-management-migration-plan.md).
#
# Fails when a NEW committed placeholder/secret token appears in tracked source
# (helm values, the runtime-values generator, realm JSON, service config, env
# files). The known existing occurrences are baselined in
# committed-secrets-baseline.txt and shrink as the migration phases land — this
# guard stops the bleeding (no new committed secrets) without going red on the
# already-catalogued debt.
#
# Usage:
#   scripts/guard/check-committed-secrets.sh                 # check (CI)
#   scripts/guard/check-committed-secrets.sh --update-baseline  # re-record after a phase
#
# For REAL secrets: never commit — provision out-of-band (secretKeyRef). For an
# intentional non-secret placeholder: re-run with --update-baseline and justify
# the addition in review.
set -uo pipefail

cd "$(git rev-parse --show-toplevel)"
BASELINE="scripts/guard/committed-secrets-baseline.txt"
PATTERN='[A-Za-z0-9_.:/-]*([Cc]hange[_-][Mm]e|CHANGE_ME)[A-Za-z0-9_-]*'

# Emit "path<TAB>token" for every placeholder/secret hit in the tracked,
# non-generated-doc scope. Kept in one function so baseline and check never drift.
scan() {
  git ls-files -z \
    | grep -zvE '(^|/)(node_modules|target|dist|build|reports|\.git)/' \
    | grep -zvE '\.(md|example)$|example\.env$|\.example\.' \
    | grep -zvE '^scripts/guard/(committed-secrets-baseline\.txt|check-committed-secrets\.sh)$' \
    | grep -zvE '^\.gitleaks\.toml$' \
    | xargs -0 grep -HoIE "$PATTERN" 2>/dev/null \
    | sed -E 's/^([^:]+):(.*)$/\1\t\2/' \
    | sort -u
}

if [[ "${1:-}" == "--update-baseline" ]]; then
  scan > "$BASELINE"
  echo "Baseline updated: $(grep -c . "$BASELINE") entries in $BASELINE"
  exit 0
fi

[[ -f "$BASELINE" ]] || { echo "::error::missing $BASELINE (run --update-baseline)"; exit 2; }

current="$(scan)"
# comm needs sorted, non-empty-line inputs; scan already sorts -u.
new="$(comm -23 <(printf '%s\n' "$current" | grep -v '^$') <(sort -u "$BASELINE" | grep -v '^$'))"
stale="$(comm -13 <(printf '%s\n' "$current" | grep -v '^$') <(sort -u "$BASELINE" | grep -v '^$'))"

if [[ -n "${stale//[$'\n\t ']/}" ]]; then
  echo "note: baseline entries no longer present (a migration phase removed them — prune via --update-baseline):"
  printf '%s\n' "$stale" | sed 's/^/  - /'
fi

if [[ -n "${new//[$'\n\t ']/}" ]]; then
  echo "::error::NEW committed placeholder/secret token(s) detected (not in baseline):"
  printf '%s\n' "$new" | sed 's/^/  + /'
  echo ""
  echo "Real secret? Do NOT commit it — provision out-of-band via secretKeyRef"
  echo "(docs/security/secrets-management-migration-plan.md). Intentional non-secret"
  echo "placeholder? Re-run: scripts/guard/check-committed-secrets.sh --update-baseline"
  exit 1
fi

echo "committed-secrets guard OK: $(grep -c . "$BASELINE") known baselined, 0 new."
