#!/usr/bin/env bash
# Detect (and attribute) deletion of ui/one-ui-shell source from the SHARED checkout.
#
# WHY THIS EXISTS
# ---------------
# On 2026-07-27 the whole of `ui/one-ui-shell` (2,882 tracked files) vanished from the
# working tree of /opt/impilo/repos/Impilo-vNext — twice, hours apart — while every
# git worktree kept its copy. While it is gone NOBODY can build or type-check the shell:
# `npm install` silently skips the workspace (no package.json), lucide-react and the next
# binary never install, and `tsc --noEmit` reports success because it cannot resolve
# imports. That is how a TypeScript error sat on canonical for ~10h unnoticed.
#
# The cause is NOT yet known. These were tested and REFUTED:
#   - core.sparseCheckout / sparse-checkout file        (unset; no such file)
#   - skip-worktree / assume-unchanged bits             (none set)
#   - `npm ci` following the workspace symlink          (reproduced in a scratch
#     workspace: source survived)
#   - `npm ci --ignore-scripts -w <pkg>`                (same; both workspaces survived)
#   - destructive npm scripts in ui/one-ui-shell        (none)
#   - rm/clean in scripts/deploy, scripts/preview,
#     scripts/full-boot, scripts/dev/build-images.sh    (none)
#
# What IS established: `ui/node_modules/one-ui-shell` is a symlink to `../one-ui-shell`
# (normal npm workspace layout), so anything that recursively deletes node_modules and
# follows that link would wipe the source. And because REPO_PATH defaults to this
# checkout in 22 scripts, a gate run from ANY worktree operates here — so the culprit
# need not be a session working in this tree.
#
# USAGE
#   nohup bash scripts/guard/watch-shell-source-deletion.sh >/dev/null 2>&1 &
#
# On detection it appends a timestamped snapshot (process table, recent npm/git/docker
# commands, git status summary) to the log below, then keeps watching. It NEVER
# modifies the repo — it only observes and records.

set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
WATCH_DIR="$REPO_PATH/ui/one-ui-shell/src"
LOG="${SHELL_DELETION_LOG:-/tmp/impilo-shell-deletion-watch.log}"
INTERVAL="${WATCH_INTERVAL_SECONDS:-5}"
# Below this many files under src/, treat the tree as gutted.
THRESHOLD="${WATCH_MIN_FILES:-200}"

count_files() { find "$WATCH_DIR" -type f 2>/dev/null | wc -l; }

echo "[$(date -Is)] watcher started; dir=$WATCH_DIR interval=${INTERVAL}s threshold=$THRESHOLD" >> "$LOG"

last=$(count_files)
while true; do
  sleep "$INTERVAL"
  now=$(count_files)

  if [[ "$now" -lt "$THRESHOLD" && "$last" -ge "$THRESHOLD" ]]; then
    {
      echo "════════════════════════════════════════════════════════════════"
      echo "[$(date -Is)] SHELL SOURCE DELETED: $last -> $now files under src/"
      echo "--- processes (npm/node/git/docker/bash scripts) ---"
      ps -eo pid,ppid,etimes,user,args --no-headers 2>/dev/null \
        | grep -iE "npm|node |git |docker|check-|run-change-safety|run-local-quality" \
        | grep -v grep | head -40
      echo "--- full process tree (top 30 by start time) ---"
      ps -eo pid,ppid,lstart,args --no-headers --sort=-start_time 2>/dev/null | head -30
      echo "--- git status summary (main checkout) ---"
      git -C "$REPO_PATH" status --porcelain 2>/dev/null \
        | awk '{print substr($0,1,2)}' | sort | uniq -c | head
      echo "--- HEAD ---"
      git -C "$REPO_PATH" rev-parse --short HEAD 2>/dev/null
      echo "--- recent npm logs ---"
      ls -lt "$HOME/.npm/_logs" 2>/dev/null | head -5
      echo "════════════════════════════════════════════════════════════════"
    } >> "$LOG" 2>&1
  fi

  if [[ "$now" -ge "$THRESHOLD" && "$last" -lt "$THRESHOLD" ]]; then
    echo "[$(date -Is)] shell source restored: $last -> $now files" >> "$LOG"
  fi

  last="$now"
done
