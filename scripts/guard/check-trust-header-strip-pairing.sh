#!/usr/bin/env bash
# Trust-header strip/regenerate pairing gate (Checkpoint 4).
#
# Stripping a client-supplied trust header and regenerating it from validated evidence are ONE
# control, not two. Break the pair in either direction and you get a defect that looks like
# hardening:
#
#   strip without regenerate  -> the header is DELETED. The upstream loses context it needs, and
#                               the outage looks like an unrelated bug.
#   regenerate without strip  -> when the PDP is off nothing overwrites the client's value, so
#                               the anti-impersonation control ends at the gate.
#
# This gate reads the rendered Envoy config and asserts every identity header stripped on the
# catch-all route is one the PDP is allowed to re-add.
#
# It was written because the first attempt at Checkpoint 4 nearly stripped x-facility-id,
# x-work-context-token and x-purpose-of-use — all of which the browser shell genuinely supplies
# and the PDP does not regenerate.
set -euo pipefail
source "$(dirname "$0")/_guard-common.sh"
cd "$REPO_PATH"

CHART="deploy/helm/impilo-vnext"
FAIL=0

render() {
  helm template impilo-full-preview "$CHART" -n impilo-full-preview \
    -f "$CHART/values-full-preview.yaml" \
    -f "$CHART/values-full-preview-runtime.generated.yaml" \
    -f "$CHART/values-full-preview-bff-env.generated.yaml" \
    --set envoy.extAuthz.enabled="$1" 2>/dev/null
}

# Takes (mode, rendered-file). The manifest is passed as a FILE, not on stdin: `python3 -`
# already uses stdin for the program text, so a piped manifest is consumed by the heredoc and
# the parser reads an empty document. The anti-vacuous check caught exactly that.
check_pairing() {
  python3 - "$1" "$2" <<'PY'
import sys, yaml

ext_authz = sys.argv[1] == "true"
raw = open(sys.argv[2]).read()
cm = None
for doc in yaml.safe_load_all(raw):
    if doc and doc.get("kind") == "ConfigMap" and doc["metadata"]["name"] == "envoy-config":
        cm = yaml.safe_load(doc["data"]["envoy.yaml"])
if cm is None:
    sys.exit("no envoy-config ConfigMap rendered — gate would prove nothing")

routes = cm["static_resources"]["listeners"][0]["filter_chains"][0]["filters"][0] \
    ["typed_config"]["route_config"]["virtual_hosts"][0]["routes"]
catch_all = [r for r in routes if r["match"].get("prefix") == "/"]
if not catch_all:
    sys.exit("no catch-all route found — gate would prove nothing")
stripped = set(catch_all[0].get("request_headers_to_remove", []))

filters = cm["static_resources"]["listeners"][0]["filter_chains"][0]["filters"][0] \
    ["typed_config"]["http_filters"]
ext = [f for f in filters if f["name"] == "envoy.filters.http.ext_authz"]

if ext_authz and not ext:
    sys.exit("extAuthz=true rendered no ext_authz filter")
if not ext_authz and ext:
    sys.exit("extAuthz=false rendered an ext_authz filter")

regenerated = set()
if ext:
    for p in ext[0]["typed_config"]["authorization_response"]["allowed_upstream_headers"]["patterns"]:
        regenerated.add(p["exact"])

# Identity headers: meaningless to the upstream unless something authoritative supplies them.
IDENTITY = {"x-actor-id", "x-actor-type", "x-tenant-id", "x-subject-id",
            "x-provider-id", "x-assurance-level"}

orphans = sorted((stripped & IDENTITY) - regenerated)
if orphans:
    sys.exit("stripped with no regenerator (the header would be DELETED): " + ", ".join(orphans))

if not ext_authz and (stripped & IDENTITY):
    sys.exit("identity headers stripped while the PDP is disabled — nothing can re-add them: "
             + ", ".join(sorted(stripped & IDENTITY)))

if ext_authz:
    missing = sorted(IDENTITY - stripped)
    if missing:
        sys.exit("PDP regenerates these but they are not stripped, so a client value survives "
                 "when the PDP omits one: " + ", ".join(missing))

print(f"    extAuthz={sys.argv[1]}: {len(stripped)} stripped, {len(regenerated)} regenerated, pairing intact")
PY
}

TMP=$(mktemp); trap 'rm -f "$TMP"' EXIT
for mode in false true; do
  render "$mode" > "$TMP"
  if check_pairing "$mode" "$TMP"; then
    guard_pass "strip/regenerate pairing holds with extAuthz=$mode"
  else
    guard_fail "strip/regenerate pairing broken with extAuthz=$mode" || true
    FAIL=1
  fi
done

exit "$FAIL"
