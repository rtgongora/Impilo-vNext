#!/usr/bin/env bash
# Clinical standards traceability guard (WHO DAK artefacts plus hand-declared baselines).
#
# Asserts that this repository can still answer, for every clinical rule it ships in the
# reproductive/maternal domain, which WHO recommendation it came from and what was changed — and
# that every artefact WHO published is either implemented or consciously excluded.
#
# The last one is the check that earns its keep. A wrong rule tends to get noticed in review. A
# decision table nobody ever opened does not: it is simply absent, and nothing in the running system
# is shaped like its absence. This guard makes that absence fail a build.
#
# Checks:
#   1. The vendored WHO sources still match their manifest hashes.
#   2. The committed extraction is not stale relative to those sources.
#   3. The committed traceability matrix is not stale relative to the extraction and the content.
#   4. No published artefact is UNCOVERED — every one is implemented or in the exclusions register.
#   5. Every shipped citation names a DAK id that actually exists, or declares itself national.
#   6. Every adaptation states its type, its authority, and — unless verbatim — its rationale.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"
FAIL=0

echo "=== WHO DAK traceability guard ==="

DAK_ROOT="docs/reference/who-dak"
if [[ ! -d "$DAK_ROOT" ]]; then
  echo "SKIP: $DAK_ROOT not present"
  exit 0
fi

if ! bash scripts/clinical/dak/fetch-dak-sources.sh >/dev/null 2>&1; then
  echo "FAIL: vendored WHO sources do not match MANIFEST.json"
  echo "      run scripts/clinical/dak/fetch-dak-sources.sh for detail"
  FAIL=1
fi

if ! python3 scripts/clinical/dak/extract-dak-l2.py --check >/dev/null 2>&1; then
  echo "FAIL: the committed DAK extraction is stale"
  echo "      run: python3 scripts/clinical/dak/extract-dak-l2.py  and commit the result"
  FAIL=1
fi

if ! python3 scripts/clinical/dak/build-traceability-matrix.py --check >/dev/null 2>&1; then
  echo "FAIL: the committed traceability matrix is stale"
  echo "      run: python3 scripts/clinical/dak/build-traceability-matrix.py  and commit the result"
  FAIL=1
fi

python3 - <<'PY'
import json, sys
from pathlib import Path

MATRIX = Path("docs/clinical-governance/rmnp/dak-traceability-matrix.json")
GOVERNANCE = Path("docs/clinical-governance")
# Per-domain registers, plus the original rmnp file under its committed name.
EXCLUSION_FILES = ([GOVERNANCE / "rmnp" / "dak-coverage-exclusions.json"]
                   + sorted(GOVERNANCE.glob("*/coverage-exclusions.json")))
CKP = Path("services/clinical-knowledge-platform-service/src/main/resources/clinical")
FORMS = Path("services/forms-service/src/main/resources/seed-forms")

VALID_ADAPTATIONS = {"ADOPTED_VERBATIM", "STRENGTHENED", "NARROWED", "SPLIT", "MERGED",
                     "ADDED_NATIONAL", "DEFERRED", "REJECTED"}
VALID_DECISIONS = {"DEFERRED", "REJECTED", "OUT_OF_SCOPE", "COVERED_ELSEWHERE"}

fail = 0
if not MATRIX.exists():
    print("FAIL: traceability matrix missing — run build-traceability-matrix.py")
    sys.exit(1)

matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
published = {row["standardId"] for row in matrix["rows"]}
# Standards declared by hand carry no dakId; both spellings resolve.
published |= {row["dakId"] for row in matrix["rows"] if row.get("dakId")}

# 4. Nothing published may be undecided.
uncovered = [r["standardId"] for r in matrix["rows"] if r["status"] == "UNCOVERED"]
if uncovered:
    print(f"FAIL: {len(uncovered)} published DAK artefact(s) are neither implemented nor excluded.")
    print("      A table nobody decided about is the failure this guard exists to catch.")
    print("      Add an entry to dak-coverage-exclusions.json, or ship a rule citing it:")
    for dak_id in uncovered[:15]:
        print(f"        - {dak_id}")
    if len(uncovered) > 15:
        print(f"        ... and {len(uncovered) - 15} more")
    fail = 1

# Exclusions must be decisions, not placeholders.
for exclusion_file in EXCLUSION_FILES:
    if not exclusion_file.exists():
        continue
    for entry in json.loads(exclusion_file.read_text(encoding="utf-8")).get("exclusions", []):
        # Hand-declared standards carry no dakId, so either spelling identifies the standard.
        dak_id = entry.get("standardId") or entry.get("dakId") or "?"
        if entry.get("decision") not in VALID_DECISIONS:
            print(f"FAIL: exclusion {dak_id} has decision {entry.get('decision')!r}; "
                  f"expected one of {sorted(VALID_DECISIONS)}")
            fail = 1
        if not (entry.get("reason") or "").strip():
            print(f"FAIL: exclusion {dak_id} states no reason. "
                  f"'Not done yet' is a status, not a decision.")
            fail = 1
        if not (entry.get("revisitCondition") or "").strip():
            print(f"FAIL: exclusion {dak_id} states no revisit condition — "
                  f"a deferral with no trigger to revisit it is a quiet abandonment.")
            fail = 1
        if dak_id not in published:
            print(f"FAIL: exclusion {dak_id} refers to an artefact no extracted DAK publishes.")
            fail = 1

# 5 + 6. Every shipped citation must resolve, and every adaptation must be reviewable.
def walk(node, found, code=None, path=""):
    if isinstance(node, dict):
        code = node.get("code") or node.get("tableId") or node.get("serviceCode") or code
        if "dakRef" in node or "adaptation" in node:
            found.append((node.get("dakRef") or {}, node.get("adaptation") or {}, code or "?"))
        for key, value in node.items():
            walk(value, found, code, f"{path}.{key}")
    elif isinstance(node, list):
        for item in node:
            walk(item, found, code, path)

for content_file in sorted(list(CKP.glob("*.json")) + list(FORMS.glob("*.json"))):
    try:
        payload = json.loads(content_file.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"FAIL: {content_file} is not valid JSON ({exc})")
        fail = 1
        continue
    found = []
    walk(payload, found)
    for dak_ref, adaptation, code in found:
        where = f"{content_file.name}:{code}"
        adaptation_type = adaptation.get("type")
        if adaptation_type not in VALID_ADAPTATIONS:
            print(f"FAIL: {where} adaptation type {adaptation_type!r} is not one of "
                  f"{sorted(VALID_ADAPTATIONS)}")
            fail = 1
        if adaptation_type == "ADDED_NATIONAL":
            if not adaptation.get("evidenceRefs"):
                print(f"FAIL: {where} is ADDED_NATIONAL but cites no evidence. A rule with no WHO "
                      f"source and no national source is not traceable to anything.")
                fail = 1
        else:
            dak_id = dak_ref.get("id")
            if not dak_id:
                print(f"FAIL: {where} has no dakRef.id and is not ADDED_NATIONAL.")
                fail = 1
            elif dak_id not in published:
                print(f"FAIL: {where} cites {dak_id}, which no extracted DAK publishes. "
                      f"Check the identifier, or mark the rule ADDED_NATIONAL.")
                fail = 1
        if adaptation_type not in (None, "ADOPTED_VERBATIM") and not (
                adaptation.get("rationale") or "").strip():
            print(f"FAIL: {where} changes the WHO text ({adaptation_type}) but states no rationale.")
            fail = 1
        if not (adaptation.get("approvingAuthority") or "").strip():
            print(f"FAIL: {where} names no approving authority. Unratified content must say so "
                  f"explicitly (PENDING_MOHCC_RATIFICATION), never leave it blank.")
            fail = 1

counts = matrix.get("statusCounts", {})
print(f"  {matrix['publishedArtefacts']} published artefacts, "
      f"{matrix['shippedCitations']} shipped citations, "
      f"{matrix['publishedDataElements']} data elements indexed")
for status, count in sorted(counts.items()):
    print(f"    {status}: {count}")
sys.exit(fail)
PY
[[ $? -ne 0 ]] && FAIL=1

if [[ $FAIL -eq 0 ]]; then
  echo "DAK traceability guard: OK"
else
  echo "DAK traceability guard: FAILED"
fi
exit $FAIL
