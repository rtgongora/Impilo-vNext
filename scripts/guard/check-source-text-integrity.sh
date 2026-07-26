#!/usr/bin/env bash
# Source-text integrity guard.
#
# A tracked source file must be text: no NUL bytes, and not classified binary by git.
#
# WHY THIS EXISTS. MecMatrix.java — the medical eligibility matrix — joined two codes into an index
# key with a literal NUL byte. It compiled, the whole service suite passed, and every other guard
# passed. What it also did was make the file binary as far as git is concerned, so the commit
# recorded "Bin 0 -> 5475 bytes" and the diff was unreadable. A clinical rule nobody can review in a
# pull request is a clinical rule nobody reviews.
#
# It was caught by a human reading the output of a command they had just run. That is not a control
# — it worked once, by luck, and will not work reliably for anyone. This is the mechanical version.
#
# The check is deliberately narrow. It does not police encoding, line endings or unicode: those are
# style. It catches the one thing that silently removes a file from human review.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

echo "=== Source text integrity guard ==="

# Extensions that must always be readable as text. Binary assets (images, PDFs, spreadsheets, jars,
# fonts) are legitimately binary and are not listed.
# One grep over the whole list rather than one per file. The per-file loop was correct and took
# over two minutes across ~17,000 tracked files, which is too slow for a gate people run before
# every commit — and a gate that is slow enough to skip is a gate that gets skipped.
#
# Two bugs were found in this eight-line check by trying to make it fail, and both produced a
# CLEAN PASS rather than an error, which is the only outcome nobody investigates:
#
#   1. -a was missing. Without it grep detects the file as binary and DECLINES TO SEARCH IT,
#      reporting no match — a guard against binary source files, defeated by binary detection.
#   2. `xargs -0 LC_ALL=C grep` makes xargs try to execute LC_ALL=C as a command. It does not
#      exist, xargs fails, and `2>/dev/null || true` turned that into silence. Hence `env`, and
#      hence no blanket stderr suppression.
#
# Both passed a clean repository and passed a file containing a NUL. A gate that cannot fail is
# worse than no gate: it reports safety it never checked.
OFFENDERS=$(git ls-files -z -- \
  '*.java' '*.ts' '*.tsx' '*.js' '*.jsx' '*.py' '*.sh' '*.sql' '*.yml' '*.yaml' \
  '*.json' '*.md' '*.xml' '*.properties' '*.rego' '*.css' '*.html' 2>/dev/null \
  | xargs -0 -r env LC_ALL=C grep -laP '\x00' || true)

CHECKED=$(git ls-files -- \
  '*.java' '*.ts' '*.tsx' '*.js' '*.jsx' '*.py' '*.sh' '*.sql' '*.yml' '*.yaml' \
  '*.json' '*.md' '*.xml' '*.properties' '*.rego' '*.css' '*.html' 2>/dev/null | wc -l)

FAIL=0
if [[ -n "$OFFENDERS" ]]; then
  FAIL=1
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    echo "FAIL: $file contains a NUL byte."
  done <<< "$OFFENDERS"
  echo "      git classifies these as binary and will not produce a readable diff for them."
  echo "      If a NUL is being used as a separator or sentinel, use a character that cannot occur"
  echo "      in the data instead — and check what removing it leaves behind, because deleting a"
  echo "      separator silently turns a+b into ab."
fi

# Deliberately only one check, on the WORKING TREE.
#
# An earlier version also asked git whether the committed blob was binary. That lagged: it reported
# a file as binary after the working tree had been fixed but before the fix was committed, which is
# exactly when a developer is running the guard. It is also redundant — git decides a file is binary
# by looking for a NUL in the first 8000 bytes, so scanning for the NUL is the same test done
# earlier, against what you are about to commit rather than what you already did.

if [[ $FAIL -eq 0 ]]; then
  echo "  $CHECKED tracked source files, all readable as text"
  echo "Source text integrity guard: OK"
else
  echo "Source text integrity guard: FAILED"
fi
exit $FAIL
