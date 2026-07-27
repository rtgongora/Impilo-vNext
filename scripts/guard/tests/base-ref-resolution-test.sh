#!/usr/bin/env bash
#
# The guard's own guard: resolve_base_ref() decides WHICH CHANGES ARE UNDER
# REVIEW, and getting that wrong is not a cosmetic bug. Attributing a merge's
# incoming commits to whoever ran `git merge` makes the gates fire on other
# lanes' shipped work, and a fleet told to stop on any hit then learns to push
# past guard output instead — the guard that cries wolf gets muted.
#
# Each case builds a throwaway repository in a temp dir (no network, no shared
# state, nothing touched outside $TMPDIR) and asserts the resolved base by SHA.
# Both directions matter: a base that is too NEW hides a lane's own violation,
# a base that is too OLD charges it with somebody else's.
#
set -uo pipefail
GUARD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PASS=0
FAIL=0
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Resolve inside $1, with the ambient guard/CI environment stripped so a case
# only ever exercises the rule it names. Extra "VAR=value" args are re-added.
resolve_in() {
  local repo="$1"; shift
  env -u GUARD_BASE_REF -u GUARD_UPSTREAM_REF -u GUARD_EXPLAIN_BASE \
      -u GITHUB_EVENT_NAME -u GITHUB_EVENT_BEFORE -u GITHUB_BASE_REF \
      REPO_PATH="$repo" "$@" \
      bash -c 'source "$0"/_guard-common.sh; resolve_base_ref' "$GUARD_DIR"
}

expect() {
  local name="$1" want="$2" got="$3"
  if [[ "$want" == "$got" ]]; then
    echo "GUARD PASS  base-ref: $name"
    PASS=$((PASS + 1))
  else
    echo "GUARD FAIL  base-ref: $name"
    echo "              expected: $want"
    echo "              actual:   $got"
    FAIL=$((FAIL + 1))
  fi
}

git_q() { git -C "$1" -c user.name=guard -c user.email=guard@test "${@:2}"; }

commit_file() { # repo, path, content, message
  mkdir -p "$(dirname "$1/$2")"
  printf '%s\n' "$3" > "$1/$2"
  git_q "$1" add -- "$2"
  git_q "$1" commit -q -m "$4" -- "$2"
}

new_repo() { # name -> path, with canonical c1..c2 and origin/canonical at c2
  local d="$WORK/$1"
  git init -q -b canonical "$d"
  commit_file "$d" a.txt one "c1"
  commit_file "$d" b.txt two "c2"
  git_q "$d" update-ref refs/remotes/origin/canonical canonical
  echo "$d"
}

EMPTY_TREE="$(git hash-object -t tree /dev/null)"

# --- 1. root commit: nothing to compare against, so review the whole tree ----
R="$WORK/root"; git init -q -b canonical "$R"; commit_file "$R" a.txt one "c1"
expect "root commit reviews the whole tree (empty tree base)" \
  "$EMPTY_TREE" "$(resolve_in "$R")"

# --- 2. linear commit with no remote refs at all: HEAD~1 --------------------
R="$WORK/linear-norem"; git init -q -b canonical "$R"
commit_file "$R" a.txt one "c1"; commit_file "$R" b.txt two "c2"
expect "linear commit, no remotes, falls back to HEAD~1" \
  "$(git -C "$R" rev-parse HEAD~1)" "$(resolve_in "$R")"

# --- 3. lane branch: every commit the lane authored is under review ---------
R="$(new_repo lane)"; C2="$(git -C "$R" rev-parse canonical)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
commit_file "$R" lane2.txt y "lane 2"
expect "lane branch bases on the fork point, not HEAD~1" "$C2" "$(resolve_in "$R")"
# HEAD~1 would review only lane2.txt — a violation two commits back would be
# invisible at exactly the moment the lane runs the gates before pushing.
expect "a violation in the lane's earlier commit is still under review" \
  "lane1.txt" \
  "$(git -C "$R" diff --name-only "$(resolve_in "$R")" HEAD | grep -x 'lane1.txt' || echo MISSING)"

# --- 4. first commit on a new branch: the fork point, which is HEAD~1 -------
R="$(new_repo first)"; C2="$(git -C "$R" rev-parse canonical)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
expect "first commit on a new branch bases on the fork point" "$C2" "$(resolve_in "$R")"

# --- 5. MERGE: canonical's incoming commits are NOT charged to this lane ----
R="$(new_repo merge)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
git_q "$R" checkout -q canonical
commit_file "$R" c3.txt z "c3 — another lane's work"
git_q "$R" update-ref refs/remotes/origin/canonical canonical
C3="$(git -C "$R" rev-parse canonical)"
git_q "$R" checkout -q lane
git_q "$R" merge -q --no-edit -m "merge canonical into lane" canonical
expect "merge commit bases on the merged-in tip, not HEAD~1" "$C3" "$(resolve_in "$R")"
# and the defect this replaced, stated as an assertion rather than a memory:
expect "merge commit does NOT base on HEAD~1" \
  "different-from-HEAD~1" \
  "$([[ "$(resolve_in "$R")" == "$(git -C "$R" rev-parse HEAD~1)" ]] && echo "same-as-HEAD~1" || echo "different-from-HEAD~1")"

# --- 6. merge, but the lane's own violation is still under review -----------
# The lane's file must appear in the reviewed diff; the other lane's must not.
REVIEWED="$(git -C "$R" diff --name-only "$(resolve_in "$R")" HEAD)"
expect "merge: the lane's own file is under review" \
  "lane1.txt" "$(echo "$REVIEWED" | grep -x 'lane1.txt' || echo MISSING)"
expect "merge: another lane's file is not under review" \
  "" "$(echo "$REVIEWED" | grep -x 'c3.txt' || true)"

# --- 7. fast-forward: a lane that authored nothing is charged nothing -------
R="$(new_repo ff)"
git_q "$R" checkout -q -b lane
commit_file "$R" c3.txt z "c3 on canonical"   # commit lands, then canonical catches up
git_q "$R" branch -q -f canonical lane
git_q "$R" update-ref refs/remotes/origin/canonical canonical
expect "fast-forwarded branch has nothing under review" \
  "$(git -C "$R" rev-parse HEAD)" "$(resolve_in "$R")"

# --- 8. pushing your OWN branch does not make the work reviewed -------------
R="$(new_repo own)"; C2="$(git -C "$R" rev-parse canonical)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
commit_file "$R" lane2.txt y "lane 2"
git_q "$R" update-ref refs/remotes/origin/lane lane   # as if pushed
expect "own pushed branch still leaves the lane's work under review" \
  "$C2" "$(resolve_in "$R")"

# --- 9. the manual escape hatches still work -------------------------------
R="$(new_repo override)"; C1="$(git -C "$R" rev-parse canonical~1)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
commit_file "$R" lane2.txt y "lane 2"
expect "GUARD_BASE_REF override wins" "$C1" "$(resolve_in "$R" GUARD_BASE_REF="$C1")"
expect "GUARD_UPSTREAM_REF names the branch to measure against" \
  "$(git -C "$R" rev-parse canonical)" \
  "$(resolve_in "$R" GUARD_UPSTREAM_REF=refs/remotes/origin/canonical)"

# --- 10. CI events ---------------------------------------------------------
R="$(new_repo ci)"; C1="$(git -C "$R" rev-parse canonical~1)"
git_q "$R" checkout -q -b lane
commit_file "$R" lane1.txt x "lane 1"
commit_file "$R" lane2.txt y "lane 2"
expect "CI push event uses the tip before the push" "$C1" \
  "$(resolve_in "$R" GITHUB_EVENT_NAME=push GITHUB_EVENT_BEFORE="$C1")"
expect "CI pull_request event uses the base branch" \
  "$(git -C "$R" rev-parse canonical)" \
  "$(resolve_in "$R" GITHUB_EVENT_NAME=pull_request GITHUB_BASE_REF=canonical)"

echo ""
if (( FAIL > 0 )); then
  echo "GUARD FAIL  base-ref resolution: $FAIL failed, $PASS passed"
  exit 1
fi
echo "GUARD PASS  base-ref resolution: $PASS/$PASS cases"
exit 0
