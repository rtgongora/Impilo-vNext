#!/usr/bin/env bash
# worktree-review.sh — structured review / merge / retire inventory for git worktrees.
#
# READ-ONLY. Produces a classification table so retirement decisions are made on
# facts, not guesses. Retiring a worktree removes only its WORKING DIRECTORY —
# the branch ref and all its commits stay in the repository, so nothing is lost
# provided the branch has no uncommitted or unpushed work. This script's whole
# job is to prove which of those two conditions hold.
#
# Classification:
#   MERGED-CLEAN   branch fully contained in the canonical branch, no local
#                  changes  → safe to retire (work already in canonical)
#   PUSHED-CLEAN   not merged, but tip == its remote tracking ref, clean tree
#                  → safe to retire (work preserved on the remote)
#   UNMERGED-WORK  has commits not in canonical AND not pushed → REVIEW/MERGE
#   DIRTY          uncommitted changes present → DO NOT RETIRE, review by hand
#   DETACHED/OTHER → review by hand
#
# usage: worktree-review.sh [canonical-branch]   (default: current branch)
set -uo pipefail

REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "not a git repo" >&2; exit 1; }
CANON="${1:-$(git -C "$REPO" branch --show-current)}"

printf '%-58s %-46s %-14s %7s %7s %6s %s\n' \
  WORKTREE BRANCH CLASS AHEAD BEHIND DIRTY SIZE

git -C "$REPO" worktree list --porcelain 2>/dev/null | awk '
  /^worktree /{wt=substr($0,10)}
  /^branch /{br=substr($0,8); print wt"\t"br}
  /^detached/{print wt"\tDETACHED"}
' | while IFS=$'\t' read -r wt br; do
  [ -d "$wt" ] || continue
  [ "$wt" = "$REPO" ] && continue                     # never classify the main checkout
  short="${br#refs/heads/}"

  if [ "$br" = "DETACHED" ]; then
    class="DETACHED"; ahead="-"; behind="-"
  else
    # ahead/behind vs canonical
    read -r ahead behind < <(git -C "$REPO" rev-list --left-right --count "$short...$CANON" 2>/dev/null || echo "? ?")
    if git -C "$REPO" merge-base --is-ancestor "$short" "$CANON" 2>/dev/null; then
      class="MERGED-CLEAN"
    else
      up="$(git -C "$REPO" rev-parse --verify --quiet "$short@{upstream}" 2>/dev/null || true)"
      tip="$(git -C "$REPO" rev-parse --verify --quiet "$short" 2>/dev/null || true)"
      if [ -n "$up" ] && [ "$up" = "$tip" ]; then class="PUSHED-CLEAN"; else class="UNMERGED-WORK"; fi
    fi
  fi

  dirty="$(git -C "$wt" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  [ "${dirty:-0}" -gt 0 ] && class="DIRTY"

  size="$(du -sh -x "$wt" 2>/dev/null | cut -f1)"
  printf '%-58s %-46s %-14s %7s %7s %6s %s\n' \
    "${wt#$REPO/}" "$short" "$class" "$ahead" "$behind" "$dirty" "$size"
done
