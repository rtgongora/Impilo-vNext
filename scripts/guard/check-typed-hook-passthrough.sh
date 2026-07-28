#!/usr/bin/env bash
# Fail when a BFF read endpoint pipes an upstream payload straight through while the shell hook
# that reads it declares `attributes`.
#
# WHY THIS EXISTS
# ---------------
# experience-bff grew two populations of read endpoints. One builds a real JSON:API row —
# ConditionsController.toConditionResource, VitalsObservationBridge.composeSetRow,
# ClinicalDocumentsController.documentRow. The other pipes the upstream node straight into "data":
#
#     return ResponseEntity.ok(Map.of("data", pctData, ...));
#
# The TypeScript hooks in ui/one-ui-shell/src/hooks/queries declare `attributes` for BOTH. For the
# passthrough population that declaration is a claim about a payload nothing produces, and the
# consumer's `x.attributes.status` throws.
#
# On 2026-07-28 that was live in ten places. PatientBanner renders on every EHR screen and reads
# `a.attributes.severity`, and PCT's allergy rows are flat snake_case — so the banner crashed
# precisely on the patients who HAVE a severe allergy recorded, the ones it exists to warn about.
# The immunizations tab was worse: PCT returns {items,page,size,total} where the hook declared an
# array, so `?? []` could not rescue it and `.filter is not a function` fired on every load.
#
# Nothing caught any of it. Every consumer test mocks its hook with a TS-shaped fixture, so the
# mocks encode the mismatch and the suite is green by construction.
#
# Point fixes do not hold — every new EHR tab copies the `attributes` idiom from the tab beside it.
# This guard makes the regression fail the build instead of a page.
#
# WHAT IT DOES
#   1. Collects every endpoint path whose hook declares `attributes`.
#   2. Finds the BFF handler serving each path.
#   3. Fails if that handler puts a bare upstream variable into "data" with no mapper.
#
# It asserts self-reach: a run that matched no hooks and no controllers is a broken guard, not a
# clean tree, and fails rather than reporting green. (See the "a check can outlive its guard" law.)

set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
# shellcheck source=/dev/null
[[ -f "$REPO_PATH/scripts/guard/_guard-common.sh" ]] && source "$REPO_PATH/scripts/guard/_guard-common.sh"

GUARD_NAME="typed-hook-passthrough"
HOOKS_DIR="$REPO_PATH/ui/one-ui-shell/src/hooks/queries"
BFF_DIR="$REPO_PATH/services/experience-bff/src/main/java"

if [[ ! -d "$HOOKS_DIR" || ! -d "$BFF_DIR" ]]; then
  echo "GUARD FAIL  $GUARD_NAME: cannot reach hooks ($HOOKS_DIR) or BFF ($BFF_DIR)"
  echo "  A guard that cannot find what it inspects has not passed — it has not run."
  exit 1
fi

python3 - "$HOOKS_DIR" "$BFF_DIR" <<'PY'
import os, re, sys

hooks_dir, bff_dir = sys.argv[1], sys.argv[2]

# ── 1. endpoint paths whose declared resource carries `attributes` ─────────
typed_paths = {}   # path -> hook file
hook_files = 0
for name in sorted(os.listdir(hooks_dir)):
    if not name.endswith(".ts") or name.endswith(".test.ts"):
        continue
    hook_files += 1
    src = open(os.path.join(hooks_dir, name)).read()

    # Resolve each endpoint to the resource type IT declares, not to whatever the file happens to
    # mention. A hook file usually serves several endpoints, and they do not make the same promise:
    # useTrustAdmin declares `attributes` on policy rules but its chain-integrity result is a flat
    # {valid, entriesVerified, …}. Blaming the file would flag the chain check for a promise it
    # never made, and a guard that cries wolf is one people learn to skip.
    #
    # Nor is a mention a promise. useCoverage defensively unwraps either shape
    # (`attributes && typeof attributes === "object" ? attributes : record`) — that tolerates the
    # payload rather than asserting it.
    aliases = dict(re.findall(r'type\s+(\w+)\s*=\s*ApiResponse<\s*([\w\[\]]+)\s*>', src))
    declares_attributes = set()
    for tm in re.finditer(r'(?:interface|type)\s+(\w+)\s*(?:=\s*)?\{(.*?)\n\}', src, re.S):
        if re.search(r'attributes\s*[?]?\s*:\s*\{', tm.group(2)):
            declares_attributes.add(tm.group(1))

    for m in re.finditer(
            r'apiClient\.get<\s*([\w\[\]]+)\s*>\(\s*[`"\']([^`"\'?]+)', src):
        response_type, path = m.group(1), m.group(2)
        if not path.startswith("/internal/v1/"):
            continue
        resource = aliases.get(response_type, response_type).rstrip("[]")
        if resource in declares_attributes:
            typed_paths.setdefault(path.rstrip("/"), name)

# ── 2. BFF handlers, and whether they pass an upstream payload through ─────
# The signature of the defect: "data" followed by a bare identifier that is NOT a mapper call and
# NOT a literal collection. A mapper call (JsonApiRows.rows(...), xRows(...), toXResource(...))
# reshapes; a bare JsonNode variable does not.
PASSTHROUGH = re.compile(
    r'"data"\s*,\s*'
    r'(?!List\.of|Map\.of|java\.util\.List\.of|java\.util\.Map\.of)'
    r'([A-Za-z_][\w.]*)\s*(?:!=\s*null\s*\?[^,]*?:\s*[^,]*)?\s*[,)]'
)
# Case-insensitive on purpose: the call is `JsonApiRows.rows(...)` — the class is capitalised and
# the method is not, so a case-sensitive `Rows?\(` matches neither and clears nothing.
# An upstream payload arrives from a downstream service client. That is the passthrough signature.
CLIENT_CALL = re.compile(r'\b\w*[Cc]lient\.\w+\s*\(')
MAPPER_HINT = re.compile(
    r'(\brows?\(|toResource|Resource\(|entries\(|attributesOf|'
    r'JsonApiRows|PctTimelineRows|\bmap[A-Z]\w*\()', re.I)

controller_files = 0
violations = []
for root, _dirs, files in os.walk(bff_dir):
    for fname in files:
        if not fname.endswith("Controller.java"):
            continue
        controller_files += 1
        full = os.path.join(root, fname)
        src = open(full).read()

        base = ""
        bm = re.search(r'@RequestMapping\(\s*"([^"]+)"', src)
        if bm:
            base = bm.group(1).rstrip("/")

        for m in re.finditer(r'@GetMapping(?:\(\s*"([^"]*)"\s*\))?', src):
            sub = (m.group(1) or "").rstrip("/")
            path = (base + ("/" + sub.lstrip("/") if sub else "")).rstrip("/")
            if path not in typed_paths:
                continue
            # body of this handler: to the next mapping annotation or EOF
            start = m.end()
            nxt = re.search(r'@(Get|Post|Put|Patch|Delete)Mapping', src[start:])
            body = src[start:start + (nxt.start() if nxt else len(src) - start)]

            for pm in PASSTHROUGH.finditer(body):
                expr = pm.group(1)

                # A mapper call on the same statement clears it outright.
                stmt_start = body.rfind("\n", 0, pm.start())
                if MAPPER_HINT.search(body[stmt_start:pm.end() + 80]):
                    continue

                # Otherwise trace where the identifier came from. The defect has one signature:
                # the value handed to "data" IS the upstream service's payload. So the question is
                # not what the variable is named but whether it was assigned from a downstream
                # client call. A locally-built collection — new ArrayList<>() filled with mapped
                # rows — is not passthrough however it is named, and flagging it would train
                # people to ignore this guard.
                root_id = expr.split(".")[0]
                # The LAST assignment before use is the one that matters — a handler may build a
                # collection, then reassign, and the first match would describe the wrong value.
                assigns = list(re.finditer(
                    r'(?:^|\n)\s*(?:final\s+)?[\w<>,\[\]\s.]*\b'
                    + re.escape(root_id) + r'\s*=\s*([^;]+);',
                    body[:pm.start()]))
                if not assigns:
                    continue
                rhs = assigns[-1].group(1)
                if MAPPER_HINT.search(rhs):
                    continue
                if not CLIENT_CALL.search(rhs):
                    continue
                # A collection seeded from a client call but repopulated through a mapper in the
                # same handler has been reshaped; only an unreshaped payload is the defect.
                if re.search(re.escape(root_id) + r'\.add\(', body) and MAPPER_HINT.search(body):
                    continue
                violations.append((os.path.relpath(full, os.path.dirname(bff_dir)),
                                   path, expr, typed_paths[path]))
                break

# ── 3. self-reach ─────────────────────────────────────────────────────────
if hook_files == 0 or controller_files == 0 or not typed_paths:
    print(f"GUARD FAIL  typed-hook-passthrough: scanned {hook_files} hooks, "
          f"{controller_files} controllers, {len(typed_paths)} typed paths.")
    print("  Matching nothing is a broken guard, not a clean tree.")
    sys.exit(1)

if violations:
    print(f"GUARD FAIL  typed-hook-passthrough: {len(violations)} endpoint(s) pass an upstream "
          f"payload through while their hook declares `attributes`.")
    for path_file, endpoint, expr, hook in violations:
        print(f"  {endpoint}")
        print(f"    handler : {path_file}  ->  Map.of(\"data\", {expr}, ...)")
        print(f"    hook    : {hook} declares attributes on this response")
    print()
    print("  The hook's type is a promise about the payload. Either build the resource")
    print("  (see ClinicalDocumentsController.documentRow or support/JsonApiRows) or correct")
    print("  the hook's type — but do not leave them disagreeing: a declared `attributes`")
    print("  that the payload lacks crashes the consumer on real data and passes on empty.")
    sys.exit(1)

print(f"GUARD PASS  typed-hook-passthrough  ({len(typed_paths)} typed endpoints checked "
      f"across {controller_files} controllers)")
PY
