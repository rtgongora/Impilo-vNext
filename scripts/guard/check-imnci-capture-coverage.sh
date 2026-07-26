#!/usr/bin/env bash
# IMNCI capture coverage guard.
#
# The paediatric danger-sign engine evaluates named facts; the IMNCI assessment form is what
# a clinician actually fills in. If a rule needs a sign the form cannot capture, the rule can
# never fire in practice — it will sit in the content looking implemented while the finding it
# depends on is never recorded. That failure is silent from both ends: the form looks complete
# and the engine looks complete.
#
# This asserts every requiredInput of every danger-sign rule has a matching linkId in the IMNCI
# form. It caught two real gaps when first written (convulsingNow and movesOnlyWhenStimulated).
set -euo pipefail
REPO_PATH="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
cd "$REPO_PATH"

FORM=services/forms-service/src/main/resources/seed-forms/08-imci-child-assessment.json
RULES=services/clinical-knowledge-platform-service/src/main/resources/clinical/paediatric-danger-signs.json

echo "=== IMNCI capture coverage guard ==="

for f in "$FORM" "$RULES"; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: missing $f"
    exit 1
  fi
done

python3 - "$FORM" "$RULES" <<'PY'
import json, sys

form_path, rules_path = sys.argv[1], sys.argv[2]

try:
    form = json.load(open(form_path))
    rules = json.load(open(rules_path))
except json.JSONDecodeError as exc:
    print(f"FAIL: could not parse content: {exc}")
    raise SystemExit(1)

captured = {
    field["linkId"]
    for section in form["definition"]["sections"]
    for field in section["fields"]
    if "linkId" in field
}

required = {}
for rule in rules.get("rules", []):
    for fact in rule.get("requiredInputs", []):
        required.setdefault(fact, []).append(rule["code"])

# ageDays is derived from the patient record rather than asked on the form.
derived = {"ageDays"}

missing = {fact: codes for fact, codes in required.items() if fact not in captured and fact not in derived}

if missing:
    print("FAIL: danger-sign rules need facts the IMNCI form cannot capture:")
    for fact, codes in sorted(missing.items()):
        print(f"  - {fact}  (needed by {', '.join(sorted(set(codes)))})")
    raise SystemExit(1)

print(f"IMNCI capture coverage: OK ({len(required)} engine inputs, {len(captured)} form fields)")
PY
