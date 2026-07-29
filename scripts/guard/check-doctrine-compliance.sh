#!/usr/bin/env bash
# Doctrine compliance gate — automatable checks + advisory findings.
set -uo pipefail
source "$(dirname "$0")/_guard-common.sh"
source "$(dirname "$0")/../full-boot/_full-boot-common.sh"
cd "$REPO_PATH"
full_boot_ensure_artifacts

FAIL=0
ADVISORY=0

echo "=== Doctrine compliance gate ==="

# Blocking: plane doctrine doc exists
for f in docs/architecture/planes/0{0,1,2,3,4,5,6,7}*.md; do
  [[ -f "$f" ]] || { guard_fail "missing plane doc: $f"; FAIL=1; }
done
[[ $FAIL -eq 0 ]] && guard_pass "seven plane doctrine documents present"

# Blocking: health-os doctrine
[[ -f docs/doctrine/health-os-doctrine.md ]] && guard_pass "health-os-doctrine.md present" \
  || { guard_fail "missing docs/doctrine/health-os-doctrine.md"; FAIL=1; }

# Blocking: core transaction doctrine
[[ -f docs/doctrine/CORE_TRANSACTION_DOCTRINE.md ]] && guard_pass "core transaction doctrine present" \
  || { guard_fail "missing CORE_TRANSACTION_DOCTRINE.md"; FAIL=1; }

# Advisory: compliance matrix exists
[[ -f docs/architecture/VNEXT_DOCTRINE_COMPLIANCE_MATRIX.md ]] && guard_pass "doctrine compliance matrix generated" \
  || { guard_warn "run scripts/full-boot/generate-artifacts.sh"; ADVISORY=1; }

# Advisory: experience shell canonical (no ui/experience fork)
if [[ -d ui/experience ]]; then
  guard_fail "deprecated ui/experience fork must not exist"
  FAIL=1
else
  guard_pass "no parallel ui/experience fork"
fi

# Advisory: required services missing from slice
python3 <<'PY' || ADVISORY=1
import yaml, pathlib
doc = yaml.safe_load(pathlib.Path("config/full-boot-service-classification.yml").read_text())
req = [e["id"] for e in doc["classifications"] if e["classification"] == "required_full_boot"]
slice = {"one-ui-shell", "experience-bff", "postgres", "redis"}
missing = [r for r in req if r not in slice and not r.startswith("tshepo")]
if len(missing) > 15:
    print(f"GUARD WARN  {len(missing)} required_full_boot services not in slice preview")
    for m in missing[:10]:
        print(f"  - {m}")
    print("  …")
PY

echo ""
if [[ $FAIL -ne 0 ]]; then
  echo "Doctrine gate: FAIL (blocking)"
  exit 1
fi
if [[ $ADVISORY -ne 0 ]]; then
  echo "Doctrine gate: PASS WITH ADVISORY"
  exit 0
fi
echo "Doctrine gate: PASS"
exit 0
