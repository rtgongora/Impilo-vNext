#!/usr/bin/env bash
# Consent source-of-record gate (Checkpoint 5).
#
# Checkpoint 5 designates `tshepo-consent-service` (schema `tshepo_consent`, table
# `consent_directive`) as the single authoritative store for consent/lawful-basis GRANT and
# REVOCATION state, per docs/doctrine/tshepo-trust-plane-doctrine.md sections 4 and 7.
# The designation is recorded in:
#   docs/security/trust-audit/checkpoint-5/CONSENT_SOURCE_OF_RECORD.md
#
# A designation nobody can violate by accident is worth something; one that can be violated by
# adding a migration nobody reads is worth nothing. Consent state already lives in NINE places
# in this estate -- it got there one reasonable-looking migration at a time, and each of those
# migrations was individually defensible. This gate makes the tenth one a decision instead of
# an accident: every CREATE TABLE whose schema-or-table reference contains "consent" must
# appear in the allowlist below with a note saying which role it plays.
#
# It fails in BOTH directions:
#   * a consent table with no allowlist row  -- a new store appeared unannounced;
#   * an allowlist row with no such table    -- the allowlist has rotted into fiction, which is
#                                               how a register stops being evidence.
#
# Source-only: no cluster, no network, so it can never "skip to green".
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

DESIGNATION="docs/security/trust-audit/checkpoint-5/CONSENT_SOURCE_OF_RECORD.md"
FAIL=0

# ---------------------------------------------------------------------------
# The allowlist. Format: <service>|<table-ref>   # role -- one line, mandatory.
#
# Adding a line here is the conscious act the gate exists to force. Before you add one, say
# which role in CONSENT_SOURCE_OF_RECORD.md section 3 the table has. If the answer is
# "authoritative grant state", the answer is wrong: there is already a designated source.
# ---------------------------------------------------------------------------
read -r -d '' ALLOWLIST <<'EOF' || true
tshepo-consent-service|tshepo_consent.consent_directive   # SOURCE OF RECORD -- FHIR R4 Consent directives; the only authoritative grant/revocation state.
tshepo-consent-service|tshepo_consent.consent_audit       # Immutable mutation trail for the source of record.
tshepo-consent-service|tshepo_consent.share_link          # Patient-initiated share links issued against a directive; listed because its schema is tshepo_consent.
tshepo-consent-service|tshepo_consent.event_outbox        # Standard outbox that publishes revocation to platform.consent.events; listed because its schema is tshepo_consent.
mvumo-service|mvumo.consent_template                      # EXPERIENCE -- the consent text/version presented to a person.
mvumo-service|mvumo.consent_request                       # EXPERIENCE -- a solicitation; carries tshepo_consent_id (V003) pointing at the materialised directive.
mvumo-service|mvumo.remote_consent_session                # EXPERIENCE -- remote/assisted capture session state.
mvumo-service|mvumo.consent_event                         # EXPERIENCE -- product-level trail of how consent was obtained, not whether it holds.
tshepo-service|tshepo.consent_directive                   # LEGACY -- second, non-FHIR directive table read only by the monolith PolicyEngine Step 5; retirement criterion in CONSENT_SOURCE_OF_RECORD.md section 4. Must gain no new readers, writers or columns.
experience-bff|policy_consent                             # DIFFERENT CONCEPT -- privacy-policy / Terms-of-Use acceptance. NOT a clinical consent directive; do not converge it.
experience-bff|consent_request                            # DIFFERENT CONCEPT -- outbound solicitation of ToU acceptance over SMS/USSD; name-collides with mvumo.consent_request.
experience-bff|consent_preferences                        # ORPHANED former shadow -- both callers repointed to tshepo-consent-service; no live reader or writer remains.
inpatient-service|inpatient.procedure_consent             # WORKFLOW ARTEFACT -- per-episode PROCEDURE/ANAESTHESIA/TRANSFUSION readiness gate; references mvumo_consent_request_id rather than restating grant state.
simba-service|simba.simba_consent_preference              # WELLNESS PLANE -- peer-wellness sharing preferences (caregiver/provider/programme scope), not a clinical consent directive.
EOF

allowed=$(mktemp); found=$(mktemp); notes=$(mktemp)
trap 'rm -f "$allowed" "$found" "$notes"' EXIT

printf '%s\n' "$ALLOWLIST" \
  | sed 's/[[:space:]]*#.*$//' \
  | sed 's/[[:space:]]*$//' \
  | grep -v '^[[:space:]]*$' \
  | sort -u > "$allowed"

# Every line must carry a note. A bare entry is a row nobody had to justify.
missing_note=$(printf '%s\n' "$ALLOWLIST" | grep -v '^[[:space:]]*$' | grep -v '#' || true)
if [[ -n "$missing_note" ]]; then
  guard_fail "allowlist rows with no role note -- say what the table is for:" || true
  printf '%s\n' "$missing_note" | sed 's/^/    /'
  FAIL=1
fi

# ---------------------------------------------------------------------------
# Extract every consent-bearing CREATE TABLE from service migrations.
#
# Comments are stripped first (an example inside a -- comment is not a table), and newlines are
# folded so `CREATE TABLE\n  foo (` is still matched. target/ is excluded: build output is a
# copy, and counting it would report phantom duplicates.
# ---------------------------------------------------------------------------
mapfile -t SQL_FILES < <(
  find services -path '*/src/main/resources/*' -name '*.sql' -not -path '*/target/*' 2>/dev/null | sort
)
guard_assert_scanned "${#SQL_FILES[@]}" "service migration .sql files" || exit 1

for f in "${SQL_FILES[@]}"; do
  svc="${f#services/}"; svc="${svc%%/*}"
  # `|| true` because most migrations create no table at all, and a no-match grep must not be
  # mistaken for a failure -- under pipefail it would abort the sweep partway through and the
  # guard would report a clean tree it never finished reading.
  stmts="$(
    sed 's/--.*//' "$f" \
      | tr '\n' ' ' \
      | grep -oiE 'CREATE[[:space:]]+TABLE[[:space:]]+(IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+)?[A-Za-z0-9_."]+' \
      || true
  )"
  [[ -z "$stmts" ]] && continue
  printf '%s\n' "$stmts" \
    | sed -E 's/.*[[:space:]]//' \
    | tr -d '"' \
    | tr 'A-Z' 'a-z' \
    | while read -r tbl; do
        case "$tbl" in
          *consent*) printf '%s|%s\t%s\n' "$svc" "$tbl" "$f" ;;
        esac
      done
done | sort -u > "$notes"

cut -f1 "$notes" | sort -u > "$found"

guard_assert_scanned "$(wc -l < "$found")" "consent tables in migrations" || exit 1

# ---------------------------------------------------------------------------
# Direction 1: a consent store that nobody declared.
# ---------------------------------------------------------------------------
undeclared=$(comm -23 "$found" "$allowed" || true)
if [[ -n "$undeclared" ]]; then
  guard_fail "NEW consent store(s) with no allowlist row -- consent already lives in too many places:" || true
  while IFS= read -r key; do
    [[ -z "$key" ]] && continue
    echo "    $key"
    grep -F "$key	" "$notes" | cut -f2 | sed 's/^/        declared in /'
  done <<< "$undeclared"
  echo "    ---"
  echo "    tshepo-consent-service (tshepo_consent.consent_directive) is the designated source of"
  echo "    record for consent grant/revocation state -- see $DESIGNATION."
  echo "    If the new table is NOT authoritative grant state, add it to the allowlist in"
  echo "    $(basename "$0") with a one-line note saying which role it plays."
  echo "    If it IS meant to hold grant state, it must not exist: write to the source of record."
  FAIL=1
else
  guard_pass "no undeclared consent store ($(wc -l < "$found") consent table(s), all allowlisted)"
fi

# ---------------------------------------------------------------------------
# Direction 2: an allowlist row describing a table that is no longer there.
# A register that names things which do not exist stops being evidence of anything.
# ---------------------------------------------------------------------------
stale=$(comm -13 "$found" "$allowed" || true)
if [[ -n "$stale" ]]; then
  guard_fail "allowlist row(s) for consent tables that no longer exist -- update the allowlist and $DESIGNATION:" || true
  printf '%s\n' "$stale" | sed 's/^/    /'
  FAIL=1
else
  guard_pass "no stale allowlist rows"
fi

# ---------------------------------------------------------------------------
# The designation itself must survive. A guard enforcing a document that has been deleted is a
# check that has outlived what it guards.
# ---------------------------------------------------------------------------
if [[ ! -f "$DESIGNATION" ]]; then
  guard_fail "designation document missing: $DESIGNATION" || true
  FAIL=1
elif ! grep -q 'tshepo_consent.consent_directive' "$DESIGNATION" \
   || ! grep -qi 'system of record\|source of record' "$DESIGNATION"; then
  guard_fail "$DESIGNATION no longer designates tshepo-consent-service as the source of record" || true
  FAIL=1
else
  guard_pass "designation recorded in $DESIGNATION"
fi

exit "$FAIL"
