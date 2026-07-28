#!/usr/bin/env bash
# Fail when this checkout has a node_modules symlink pointing into a DIFFERENT checkout.
#
# WHY THIS EXISTS
# ---------------
# `ui/one-ui-shell` was wiped from the shared checkout three times on 2026-07-27 — 2,885 tracked
# files, gone, twice in one day. The cause is a symlink pointing out of one tree into another:
#
#   worktree/ui/node_modules -> /opt/impilo/repos/Impilo-vNext/ui/node_modules
#
# `next build` emits a standalone output tree that contains a `node_modules/one-ui-shell` workspace
# symlink, and its recursiveDelete cleanup walks THROUGH that link into the real source. Build in
# the worktree, lose the source in the other checkout.
#
# It is silent, which is why it recurred: once the source is gone `npm install` skips the workspace
# (no package.json), `tsc --noEmit` reports SUCCESS because it cannot resolve any import, and a real
# TypeScript error sat on canonical about ten hours unnoticed. One lane spent hours believing it was
# blocked on another lane's defect. Another diagnosed "the worktree has a partial install" when the
# truth was that a previous build had already deleted the source through the link — the symptom
# without the mechanism.
#
# Four other explanations were tested and refuted before the symlinks were found: sparse-checkout,
# skip-worktree/assume-unchanged bits, `npm ci` following the workspace link (reproduced twice, the
# source survived), and destructive npm/deploy scripts.
#
# BOTH LEVELS MATTER, and this is the trap that catches people. Workspace dependencies hoist to the
# `ui/` root, so linking only `ui/one-ui-shell/node_modules` still fails to resolve — and the
# obvious next move is to add `ui/node_modules` too, which is the dangerous one. A guard that
# checked only the inner path would have missed the link that actually caused the damage. This
# checks every node_modules in the tree, at any depth.
#
# THE RULE: never symlink node_modules across trees. Do a real install where one is needed
# (`cd ui && npm ci --ignore-scripts -w one-ui-shell`). It is slower once and cannot reach another
# checkout.
#
# Detection after the fact lives in scripts/guard/watch-shell-source-deletion.sh. This is the
# prevention: it fails before a build can be run.

set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
GUARD_NAME="cross-tree-node-modules"

if [[ ! -d "$REPO_PATH" ]]; then
  echo "GUARD FAIL  $GUARD_NAME: cannot reach $REPO_PATH"
  exit 1
fi

TREE="$(cd "$REPO_PATH" && pwd -P)"

# Every symlink named node_modules anywhere in the tree. -prune keeps us out of the (very large)
# contents of real node_modules directories while still testing the directories themselves.
mapfile -t LINKS < <(
  find "$TREE" -name node_modules \( -type l -print -o -type d -prune \) 2>/dev/null
)

SCANNED="${#LINKS[@]}"
VIOLATIONS=()
for link in "${LINKS[@]}"; do
  [[ -L "$link" ]] || continue
  target="$(readlink -f "$link" 2>/dev/null || true)"
  [[ -z "$target" ]] && continue
  # Inside this tree is fine — that is ordinary npm workspace hoisting.
  case "$target" in
    "$TREE"/*|"$TREE") continue ;;
  esac
  VIOLATIONS+=("$link -> $target")
done

if (( ${#VIOLATIONS[@]} > 0 )); then
  echo "GUARD FAIL  $GUARD_NAME: ${#VIOLATIONS[@]} node_modules symlink(s) point outside this checkout."
  for v in "${VIOLATIONS[@]}"; do
    echo "  $v"
  done
  echo
  echo "  A build here can delete source in the other checkout: next build's standalone-output"
  echo "  cleanup follows these links out of the tree. It has already happened three times, and it"
  echo "  fails silently — the tree looks like a partial install afterwards, not like a deletion."
  echo
  echo "  Fix (removes the LINKS only — never use rm -r on them):"
  for v in "${VIOLATIONS[@]}"; do
    echo "    rm ${v%% ->*}"
  done
  echo "    cd ui && npm ci --ignore-scripts -w one-ui-shell"
  exit 1
fi

# Self-reach, reported only once violations have been ruled out. This check used to sit ABOVE the
# violation report, which made it an early return that could mask a real finding: a tree whose
# node_modules had been removed but which still held a cross-tree link inside build output
# (.next-build/standalone/one-ui-shell/node_modules) reported a clean pass. An early return that
# skips the thing the guard exists to find is the same defect this guard is chasing.
if [[ ! -d "$TREE/node_modules" && ! -d "$TREE/ui/node_modules" && ! -d "$TREE/apps/mobile/node_modules" ]]; then
  echo "GUARD PASS  $GUARD_NAME  (no cross-tree links; note: nothing installed in $TREE)"
  exit 0
fi

echo "GUARD PASS  $GUARD_NAME  (checked $SCANNED node_modules path(s) in $TREE)"
