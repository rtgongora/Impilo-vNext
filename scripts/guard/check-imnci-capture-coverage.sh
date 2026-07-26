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

YI_FORM=services/forms-service/src/main/resources/seed-forms/12-imci-young-infant-assessment.json
CHILD_TABLES=services/clinical-knowledge-platform-service/src/main/resources/clinical/imnci-classification-tables.json
YI_TABLES=services/clinical-knowledge-platform-service/src/main/resources/clinical/imnci-young-infant-tables.json

for f in "$FORM" "$RULES" "$YI_FORM" "$CHILD_TABLES" "$YI_TABLES"; do
  if [[ ! -f "$f" ]]; then
    echo "FAIL: missing $f"
    exit 1
  fi
done

python3 - "$FORM" "$RULES" "$YI_FORM" "$CHILD_TABLES" "$YI_TABLES" <<'PY'
import json, sys

form_path, rules_path, yi_form_path, child_tables_path, yi_tables_path = sys.argv[1:6]

def load(p):
    try:
        return json.load(open(p))
    except json.JSONDecodeError as exc:
        print(f"FAIL: could not parse {p}: {exc}")
        raise SystemExit(1)

def captured(doc):
    return {field["linkId"]
            for section in doc["definition"]["sections"]
            for field in section["fields"]
            if "linkId" in field}

def rule_inputs(doc):
    out = {}
    for rule in doc.get("rules", []):
        for fact in rule.get("requiredInputs", []):
            out.setdefault(fact, []).append(rule["code"])
    return out

def table_inputs(doc):
    """Every fact a classification table can read: its declared requiredInputs and every input or
    finding named anywhere in its screening predicate or its rows. Walking the logic matters — a
    sign referenced only inside a tier is still a question somebody has to be able to answer."""
    out = {}
    def walk(node, table_id):
        if isinstance(node, dict):
            for key in ("input", "finding"):
                if key in node and isinstance(node[key], str):
                    out.setdefault(node[key], []).append(table_id)
            for value in node.values():
                walk(value, table_id)
        elif isinstance(node, list):
            for value in node:
                walk(value, table_id)
    for table in doc.get("tables", []):
        tid = table["tableId"]
        for fact in table.get("requiredInputs", []):
            out.setdefault(fact, []).append(tid)
        walk(table.get("appliesWhen"), tid)
        for tier in table.get("tiers", []):
            walk(tier.get("logic"), tid)
    return out

# Derived from the patient record or stamped by another service, never asked on a form.
DERIVED = {"ageDays", "weightForHeightZ", "weightForAgeZ", "lengthHeightForAgeZ",
           "headCircumferenceForAgeZ"}

child_form = captured(load(form_path))
yi_form = captured(load(yi_form_path))

# The danger-sign pack spans both age groups, so either form may satisfy it.
checks = [
    ("danger-sign rules", rule_inputs(load(rules_path)), child_form | yi_form),
    ("IMNCI child classification tables", table_inputs(load(child_tables_path)), child_form),
    ("young-infant classification tables", table_inputs(load(yi_tables_path)), yi_form),
]

failed = False
total_required = 0
for label, required, available in checks:
    total_required += len(required)
    missing = {fact: srcs for fact, srcs in required.items()
               if fact not in available and fact not in DERIVED}
    if missing:
        failed = True
        print(f"FAIL: {label} need facts no form can capture:")
        for fact, srcs in sorted(missing.items()):
            print(f"  - {fact}  (needed by {', '.join(sorted(set(srcs)))})")

if failed:
    print()
    print("An engine that asks a question the form cannot answer does not fail loudly: it reports")
    print("the sign as unassessed forever, and the classification never resolves.")
    raise SystemExit(1)
print(f"IMNCI capture coverage: OK ({total_required} engine inputs across 3 packs, "
      f"{len(child_form)} child + {len(yi_form)} young-infant form fields)")
PY
