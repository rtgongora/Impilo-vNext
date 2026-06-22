#!/bin/bash
# beforeShellExecution hook: block preview-mutating deploy commands unless the VM quality
# gates passed at the current HEAD commit.
#
# This closes the raw-command bypass: the deploy scripts already enforce gates, but a direct
# cluster mutation (image update, apply, patch, rollout restart) or chart upgrade would skip
# them. This hook gates those commands on scripts/pipeline/require-fresh-gates.sh.
#
# Detection is command-position aware (implemented in Python): a command only counts as a
# deploy when the cluster/helm CLI is actually *invoked* (first token of a shell segment),
# not when its name merely appears inside a quoted string, commit message, echo, or grep.
#
# Contract: read JSON {command:...} on stdin, print one JSON object with `permission`.
# Always emits valid JSON. failClosed is set in hooks.json so a hook error blocks the
# (narrowly-matched) deploy command rather than letting it through.
set -uo pipefail

REPO="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
input="$(cat 2>/dev/null || true)"

allow() { printf '{"permission":"allow"}\n'; exit 0; }
deny() {
  local msg="$1"
  msg="${msg//\"/\\\"}"
  printf '{"permission":"deny","user_message":"Preview deploy blocked: VM quality gates are not green at HEAD. %s","agent_message":"Blocked by require-gates-before-deploy hook. %s Run: bash scripts/pipeline/run-local-quality-gates.sh and ensure it passes at the current commit, then retry the deploy."}\n' "$msg" "$msg"
  exit 0
}

verdict="$(printf '%s' "$input" | python3 -c '
import json, re, sys

try:
    cmd = json.load(sys.stdin).get("command", "")
except Exception:
    print("ALLOW"); sys.exit(0)

# Split into shell segments on control operators so we can inspect the *invoked* command.
segments = re.split(r"\|\||&&|[;|&\n]", cmd)
deploy = False
for seg in segments:
    s = seg.strip()
    if not s:
        continue
    toks = s.split()
    i = 0
    # Skip leading ENV=VAL assignments (e.g. NS=impilo kubectl ...).
    while i < len(toks) and re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", toks[i]):
        i += 1
    if i >= len(toks):
        continue
    base = toks[i].split("/")[-1]
    if base == "kubectl":
        if re.search(r"\b(set\s+image|apply|patch|scale|replace|create|delete|edit|rollout\s+restart)\b", s) \
           and re.search(r"impilo-(full-)?preview|(-n|--namespace)\s+impilo", s):
            deploy = True
    elif base == "helm":
        rest = " ".join(toks[i + 1:])
        if re.search(r"\b(upgrade|install)\b", rest):
            deploy = True
    elif base in ("bash", "sh", "source", "."):
        if re.search(r"(manual-authorized|full-boot)-preview-deploy\.sh", s):
            deploy = True

print("DEPLOY" if deploy else "ALLOW")
' 2>/dev/null || echo ERR)"

case "$verdict" in
  ALLOW) allow ;;
  DEPLOY) ;;
  *) deny "Hook could not classify the command safely." ;;
esac

if out="$(bash "$REPO/scripts/pipeline/require-fresh-gates.sh" 2>&1)"; then
  allow
else
  deny "$(printf '%s' "$out" | grep -v '^$' | tail -1)"
fi
