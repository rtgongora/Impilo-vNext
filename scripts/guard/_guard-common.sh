#!/usr/bin/env bash
set -euo pipefail
# REPO_PATH defaults to the checkout this script actually lives in, so a guard
# run from a worktree reviews that worktree's HEAD and not the main checkout's.
# An explicit REPO_PATH still wins (CI sets none; it runs inside the checkout).
if [[ -z "${REPO_PATH:-}" ]]; then
  REPO_PATH="$(git -C "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" rev-parse --show-toplevel 2>/dev/null \
    || echo /opt/impilo/repos/Impilo-vNext)"
fi
cd "$REPO_PATH"

guard_pass() { echo "GUARD PASS  $1"; }
guard_fail() { echo "GUARD FAIL  $1"; return 1; }
guard_warn() { echo "GUARD WARN  $1"; }

# Prefer ripgrep when installed; grep -E works on minimal VMs (stdin or files).
guard_filter() {
  local quiet=0
  [[ "${1:-}" == "-q" ]] && { quiet=1; shift; }
  local pat="$1"
  shift
  if command -v rg >/dev/null 2>&1; then
    [[ $quiet -eq 1 ]] && rg -q "$pat" "$@" || rg "$pat" "$@"
  else
    [[ $quiet -eq 1 ]] && grep -qE "$pat" "$@" || grep -E "$pat" "$@"
  fi
}

# ---------------------------------------------------------------------------
# Base-ref resolution — "which changes are under review?"
#
# The base must be the point THIS lane's own work starts from, never simply
# HEAD~1. On a MERGE commit HEAD~1 is the merger's own pre-merge tip, so every
# commit the merge brought in is attributed to whoever ran `git merge`: the
# guards then fail on other lanes' long-shipped work. A fleet told to stop on
# any hit cannot follow that rule against a gate that fires on every merge, and
# learns instead to push past guard output — which costs a real control.
# (Observed 2026-07-27: a lane merging 72 canonical commits was blocked by a
# consolidation and a registry it had never touched.)
#
# Resolution order — the first that applies wins:
#   1. GUARD_BASE_REF        explicit override, the manual escape hatch.
#   2. CI push event         GITHUB_EVENT_BEFORE — what the push adds to that
#                            branch, which on canonical is the right attribution.
#   3. CI pull_request       origin/$GITHUB_BASE_REF.
#   4. unborn / root commit  the empty tree: every file reads as added, so a
#                            parentless HEAD is reviewed in full, never skipped.
#   5. declared target       GUARD_UPSTREAM_REF, else `git config
#                            guard.upstreamRef` — the branch this lane is
#                            measured against.
#   6. fork point            the newest ancestor of HEAD that is already
#                            published on a remote branch other than this
#                            branch's own counterpart. One rule, four shapes:
#                              • merge commit — the merged-in tip is published,
#                                so the merge brings in nothing under review;
#                              • fast-forward — HEAD itself is published, so a
#                                lane that authored nothing is charged nothing;
#                              • linear branch — the fork point, so every commit
#                                this lane authored is under review, not just
#                                the last one;
#                              • first commit on a new branch — the same fork
#                                point, which is HEAD~1.
#                            Own-branch pushes do NOT count as published: the
#                            work is still unreviewed until it reaches canonical.
#   7. HEAD is a merge       the branch the merge brought in (HEAD^2) — the
#                            fallback when no remote refs are available at all.
#   8. plain linear commit   HEAD~1.
#
# Whatever is chosen is then normalised through `git merge-base <ref> HEAD`, so
# the returned value is always an ancestor of HEAD. Guards therefore compare
# `"$BASE" HEAD` (two-dot) — identical to the `...` symmetric form for an
# ancestor, and unlike it, valid when BASE is the empty tree.
#
# Set GUARD_EXPLAIN_BASE=1 to have the chosen rule printed to stderr.
# ---------------------------------------------------------------------------

GUARD_EMPTY_TREE="$(git hash-object -t tree /dev/null 2>/dev/null || echo 4b825dc642cb6eb9a060e54bf8d69288fbee4904)"

guard_is_commit() { git rev-parse --verify --quiet "${1}^{commit}" >/dev/null 2>&1; }

_guard_explain() {
  [[ "${GUARD_EXPLAIN_BASE:-0}" == "1" ]] && echo "base-ref rule: $1" >&2
  return 0
}

# The branch this lane's work is measured against, when one is declared.
_guard_target_ref() {
  local t
  for t in "${GUARD_UPSTREAM_REF:-}" "$(git config --get guard.upstreamRef 2>/dev/null || true)"; do
    if [[ -n "$t" ]] && guard_is_commit "$t"; then
      echo "$t"
      return 0
    fi
  done
  return 1
}

# The branch a merge commit brought in — its non-first parent. For an octopus,
# the non-first parent that already contains the others, else their common
# ancestor (wider, but never attributes one merged branch's work to another).
_guard_merged_in_parent() {
  local -a p
  read -r -a p <<< "$(git rev-list --parents -n1 HEAD)" || true
  if (( ${#p[@]} < 3 )); then
    return 1
  fi
  local -a others=("${p[@]:2}")
  if (( ${#others[@]} == 1 )); then
    echo "${others[0]}"
    return 0
  fi
  local cand o ok
  for cand in "${others[@]}"; do
    ok=1
    for o in "${others[@]}"; do
      if [[ "$cand" == "$o" ]]; then
        continue
      fi
      if ! git merge-base --is-ancestor "$o" "$cand" 2>/dev/null; then
        ok=0
        break
      fi
    done
    if (( ok == 1 )); then
      echo "$cand"
      return 0
    fi
  done
  git merge-base --octopus "${others[@]}" 2>/dev/null || return 1
}

# Remote branches that count as "already published", i.e. somebody else's
# reviewed history. This branch's own counterpart is deliberately excluded —
# pushing your own lane branch does not make your work reviewed.
# Symbolic refs (origin/HEAD) are skipped: they alias a branch, they are not one.
_guard_published_refs() {
  local branch own upstream ref
  branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo HEAD)"
  own="refs/remotes/origin/${branch}"
  upstream="$(git rev-parse --symbolic-full-name '@{upstream}' 2>/dev/null || true)"
  while read -r ref; do
    if [[ -z "$ref" || "$ref" == "$own" || "$ref" == "$upstream" ]]; then
      continue
    fi
    if [[ -n "$(git symbolic-ref -q "$ref" 2>/dev/null || true)" ]]; then
      continue
    fi
    echo "$ref"
  done < <(git for-each-ref --format='%(refname)' refs/remotes/ 2>/dev/null || true)
}

# The newest already-published ancestor of HEAD: everything after it is what
# this lane authored. Several boundaries mean several published branches feed
# HEAD (a lane that merged canonical twice, say) — take the one that contains
# the others, so no published branch's own commits are charged to this lane.
_guard_forked_from() {
  local -a refs bounds
  mapfile -t refs < <(_guard_published_refs)
  if (( ${#refs[@]} == 0 )); then
    return 1
  fi
  if [[ "$(git rev-list --count HEAD --not "${refs[@]}" 2>/dev/null || echo 1)" == "0" ]]; then
    git rev-parse HEAD          # HEAD itself is published: nothing authored here
    return 0
  fi
  mapfile -t bounds < <(git rev-list --boundary HEAD --not "${refs[@]}" 2>/dev/null | sed -n 's/^-//p')
  if (( ${#bounds[@]} == 0 )); then
    return 1
  fi
  local cand o ok
  for cand in "${bounds[@]}"; do
    ok=1
    for o in "${bounds[@]}"; do
      if [[ "$cand" == "$o" ]]; then
        continue
      fi
      if ! git merge-base --is-ancestor "$o" "$cand" 2>/dev/null; then
        ok=0
        break
      fi
    done
    if (( ok == 1 )); then
      echo "$cand"
      return 0
    fi
  done
  git merge-base --octopus "${bounds[@]}" 2>/dev/null || return 1
}

# Reduce any ref to an ancestor of HEAD, so two-dot and three-dot diffs agree.
_guard_normalise_base() {
  local ref="$1" base
  if base="$(git merge-base "$ref" HEAD 2>/dev/null)" && [[ -n "$base" ]]; then
    echo "$base"
  else
    echo "$ref"
  fi
}

resolve_base_ref() {
  local t m f

  if [[ -n "${GUARD_BASE_REF:-}" ]] && guard_is_commit "${GUARD_BASE_REF}"; then
    _guard_explain "GUARD_BASE_REF override (${GUARD_BASE_REF})"
    _guard_normalise_base "$GUARD_BASE_REF"
    return 0
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "push" && -n "${GITHUB_EVENT_BEFORE:-}" ]] \
     && guard_is_commit "${GITHUB_EVENT_BEFORE}"; then
    _guard_explain "CI push event, base = the branch tip before the push"
    _guard_normalise_base "$GITHUB_EVENT_BEFORE"
    return 0
  fi
  if [[ "${GITHUB_EVENT_NAME:-}" == "pull_request" && -n "${GITHUB_BASE_REF:-}" ]] \
     && guard_is_commit "origin/${GITHUB_BASE_REF}"; then
    _guard_explain "CI pull_request, base = merge base with origin/${GITHUB_BASE_REF}"
    _guard_normalise_base "origin/${GITHUB_BASE_REF}"
    return 0
  fi

  # No commit at all, or the repository root commit: nothing to compare against,
  # so compare against the empty tree and review the whole tree.
  if ! guard_is_commit HEAD; then
    _guard_explain "no HEAD commit, base = empty tree (whole tree is new)"
    echo "$GUARD_EMPTY_TREE"
    return 0
  fi
  if ! guard_is_commit 'HEAD^1'; then
    _guard_explain "HEAD has no parent, base = empty tree (whole tree is new)"
    echo "$GUARD_EMPTY_TREE"
    return 0
  fi

  if t="$(_guard_target_ref)"; then
    _guard_explain "declared target ${t}, base = merge base with it"
    _guard_normalise_base "$t"
    return 0
  fi
  if f="$(_guard_forked_from)"; then
    if [[ "$f" == "$(git rev-parse HEAD)" ]]; then
      _guard_explain "HEAD is already published elsewhere (fast-forward); this lane authored nothing, base = HEAD"
    else
      _guard_explain "base = newest published ancestor ${f}; only this lane's own commits are under review"
    fi
    _guard_normalise_base "$f"
    return 0
  fi
  if m="$(_guard_merged_in_parent)"; then
    _guard_explain "no remote refs; HEAD is a merge, base = the branch it brought in (${m})"
    _guard_normalise_base "$m"
    return 0
  fi

  _guard_explain "linear commit, base = HEAD~1"
  _guard_normalise_base 'HEAD~1'
  return 0
}
