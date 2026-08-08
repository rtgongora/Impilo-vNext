#!/usr/bin/env bash
# Governed FHIR writes must land in the SHR, not in the stock HAPI server beside it -- and the
# configuration that says so must reach the pod that reads it.
#
# The estate runs two R4 servers on port 8090. `butano-service` is the SHR: HAPI FHIR JPA with
# header validation, tenant enforcement, PII prevention, provenance stamping and terminology
# validation on the write path. `hapi-fhir` is stock `hapiproject/hapi` with none of that and no
# authentication at all. Measured 2026-08-08 from inside the namespace:
#
#   GET http://hapi-fhir:8090/fhir/Patient          no credential, no headers -> 200 + Bundle
#   GET http://butano-service:8090/fhir/Patient     no credential, no headers -> 403
#     "Missing mandatory trust headers: X-Tenant-Id, X-Correlation-Id, X-Actor-Id,
#      X-Actor-Type, X-Purpose-Of-Use"
#
# fhir-gateway-service holds the consent PEP, and `fhir_route` is empty in every environment
# measured -- so FHIR_GATEWAY_DEFAULT_TARGET_BASE is not a fallback, it is THE delivery target for
# every forward the gateway serves. Pointing it at the stock server meant the PEP evaluated
# consent and then handed the resource to a store any pod could read back without one.
#
# ── WHY THIS GUARD ASSERTS PLACEMENT, NOT JUST VALUE ────────────────────────────────────────
#
# Its first version grepped values-full-preview.yaml for the right VALUE and passed. The line it
# matched was under `experienceBff.env`, and templates/microservice.yaml renders only
# `fullBootServices.<id>.env` -- so the gateway pod never received it, and the BFF (which reads
# neither variable) received it instead. The guard reported a fix that was not deployed anywhere.
#
# A correct value in a block the target service never reads is worse than an absent one, because
# it reads as done. So the assertions below are STRUCTURAL: parse the YAML, and require the key
# at the path the chart actually renders for that service.
set -uo pipefail
cd "$(dirname "$0")/../.."

FAIL=0
note() { printf '  %s\n' "$1"; }
fail() { printf 'FAIL: %s\n' "$1" >&2; FAIL=1; }

echo "=== SHR write-target guard ==="

RUNTIME=deploy/helm/impilo-vnext/values-full-preview-runtime.generated.yaml
PREVIEW=deploy/helm/impilo-vnext/values-full-preview.yaml
SHR_URL="http://butano-service:8090/fhir"

for f in "$RUNTIME" "$PREVIEW"; do
  [ -f "$f" ] || { fail "$f is missing — this guard cannot check a file that is not there"; }
done
[ "$FAIL" -eq 0 ] || exit 1

# ── 1. Structural: the gateway's own env block carries what the gateway's code reads ─────────
python3 - "$RUNTIME" "$PREVIEW" "$SHR_URL" <<'PY'
import sys, yaml

runtime_path, preview_path, shr_url = sys.argv[1], sys.argv[2], sys.argv[3]
runtime = yaml.safe_load(open(runtime_path)) or {}
preview = yaml.safe_load(open(preview_path)) or {}
failures, notes = [], []

# The env keys fhir-gateway-service's source actually reads, and the value each must carry.
# FHIR_GATEWAY_DEFAULT_TARGET_BASE -> GatewayForwardService (the delivery target)
# CONSENT_SERVICE_BASE_URL         -> ConsentEnforcementService (unreachable == fail-closed DENY)
REQUIRED = {
    "FHIR_GATEWAY_DEFAULT_TARGET_BASE": shr_url,
    "CONSENT_SERVICE_BASE_URL": "http://tshepo-consent-service:8182",
}

svc = (runtime.get("fullBootServices") or {}).get("fhir-gateway-service")
if not isinstance(svc, dict):
    failures.append(f"{runtime_path}: no fullBootServices['fhir-gateway-service'] entry — "
                    "templates/microservice.yaml renders env from there and nowhere else")
    svc = {}
env = svc.get("env") or {}

for key, expected in REQUIRED.items():
    actual = env.get(key)
    if actual is None:
        failures.append(
            f"fullBootServices['fhir-gateway-service'].env is missing {key}. The gateway pod will "
            f"not receive it. Set it in specialEnv('fhir-gateway-service') in "
            f"scripts/full-boot/generate-full-preview-runtime-values.mjs and regenerate — NOT in "
            f"experienceBff.env, which only templates/experience-bff.yaml consumes.")
    elif actual != expected:
        failures.append(f"fullBootServices['fhir-gateway-service'].env.{key} = {actual!r}, "
                        f"expected {expected!r}")
    else:
        notes.append(f"gateway env {key} = {actual}")

# ── 2. Nothing anywhere may route a FHIR write at the ungoverned stock server ────────────────
TARGET_KEYS = set(REQUIRED) | {"FHIR_BASE_URL", "HAPI_FHIR_URL"}
checked = 0

def walk(node, path, source):
    global checked
    if isinstance(node, dict):
        for k, v in node.items():
            if k in TARGET_KEYS and isinstance(v, str):
                checked += 1
                if "hapi-fhir" in v:
                    failures.append(f"{source}: {path}.{k} = {v} — that is the ungoverned stock "
                                    "server, which answers any pod with no credential")
            walk(v, f"{path}.{k}", source)
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, f"{path}[{i}]", source)

walk(runtime, "", runtime_path)
walk(preview, "", preview_path)

# A guard that finds nothing to check reports success. Anchor on the count actually expected:
# the gateway's two, its FHIR_BASE_URL, and butano-service's HAPI_FHIR_URL.
if checked < 4:
    failures.append(f"only {checked} FHIR target assignments found across both values files — "
                    "expected at least 4. The keys or the file layout moved and this guard is now "
                    "checking almost nothing.")
else:
    notes.append(f"{checked} FHIR target assignments checked across both values files")

for n in notes:
    print(f"  {n}")
for f in failures:
    print(f"FAIL: {f}", file=sys.stderr)
sys.exit(1 if failures else 0)
PY
[ "$?" -eq 0 ] || FAIL=1

# ── 3. The generator and the generated file must agree ──────────────────────────────────────
# The generated file is the artefact, but it is regenerated routinely; if the generator does not
# also carry the keys, the next regeneration silently drops them (the failure mode that produced
# this whole finding, and the one that drops MENTAL_HEALTH_SERVICE_BASE_URL from the BFF file).
GEN=scripts/full-boot/generate-full-preview-runtime-values.mjs
for key in FHIR_GATEWAY_DEFAULT_TARGET_BASE CONSENT_SERVICE_BASE_URL; do
  if grep -q "$key" "$GEN"; then
    note "generator carries $key"
  else
    fail "$GEN does not set $key — the next regeneration drops it from the gateway's env"
  fi
done

if [ "$FAIL" -eq 0 ]; then
  echo "PASS: the SHR is the governed write target, and the config reaches the pod that reads it"
fi
exit "$FAIL"
