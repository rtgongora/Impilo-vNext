#!/usr/bin/env bash
# Governance-pack integrity verifier.
#
# Fails (exit 1) when any invariant below is broken; prints OK lines otherwise.
# Root resolution: explicit $1 (fixtures), else the repository containing this
# script (git rev-parse), else the script's grandparent directory. All checks
# run against absolute paths beneath that root, so the caller's cwd is irrelevant.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -n "${1:-}" ]]; then
  root="$(cd "$1" && pwd)"
elif root="$(git -C "$script_dir" rev-parse --show-toplevel 2>/dev/null)"; then
  :
else
  root="$(cd "$script_dir/../.." && pwd)"
fi

versioned_rel="docs/architecture/hybrid-federated-target-architecture-v1.3.3.md"
pointer_rel="docs/architecture/vnext-hybrid-federation-target-architecture.md"
# The versioned file is the complete document (~4,600 lines). Anything shorter
# than this floor is a pointer or a truncation masquerading as the architecture.
min_versioned_lines=1000
# Any file outside archive/ bearing the architecture H1 and at least this many
# lines counts as a "complete active copy"; there must be exactly one.
copy_floor_lines=200
arch_h1='# Impilo vNext — Hybrid / Federated Target Architecture'

required=(
  "CLAUDE.md"
  "docs/architecture/CLAUDE_GOVERNANCE.md"
  "docs/architecture/ARCHITECTURE_PRECEDENCE.md"
  "$versioned_rel"
  "docs/architecture/product-capability-architecture-v2.0.md"
  "docs/architecture/supersession-notice-v1.0.md"
  "docs/standards/technical-standards-catalogue-v1.0.md"
)

failed=0
fail() { echo "FAIL: $*" >&2; failed=1; }

# 1. Required governance files exist and are non-empty.
for path in "${required[@]}"; do
  if [[ ! -s "$root/$path" ]]; then
    fail "missing or empty: $path"
  else
    echo "OK: $path"
  fi
done

# 2. Root CLAUDE.md imports the governance rules.
if [[ -s "$root/CLAUDE.md" ]] && ! grep -Fq '@docs/architecture/CLAUDE_GOVERNANCE.md' "$root/CLAUDE.md"; then
  fail "root CLAUDE.md does not import the governance rules"
fi

# 3. No controlling/frozen legacy language outside the archive (hard failure).
if legacy_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
    --exclude='supersession-notice-v1.0.md' \
    -E 'Architecture frozen\. Implementation must conform|All business APIs MUST require these headers|X-Tenant-ID: <uuid\|string>.*logical tenant/customer' \
    "$root/docs" 2>/dev/null)"; then
  echo "$legacy_hits" >&2
  fail "legacy controlling language remains outside the archive (matches above)"
fi

# 4. The unversioned pointer names v1.3.3 and links the versioned file.
if [[ -s "$root/$pointer_rel" ]]; then
  if ! grep -Fq 'hybrid-federated-target-architecture-v1.3.3.md' "$root/$pointer_rel" \
     || ! grep -Eq 'v1\.3\.3' "$root/$pointer_rel"; then
    fail "unversioned pointer does not point to v1.3.3: $pointer_rel"
  else
    echo "OK: pointer names v1.3.3"
  fi
else
  fail "missing unversioned pointer: $pointer_rel"
fi

# 5. The versioned v1.3.3 file is the complete document, not a pointer/stub.
if [[ -s "$root/$versioned_rel" ]]; then
  lines="$(wc -l < "$root/$versioned_rel")"
  if (( lines < min_versioned_lines )); then
    fail "versioned architecture is only a pointer or unexpectedly short ($lines lines < $min_versioned_lines): $versioned_rel"
  else
    echo "OK: versioned architecture is complete ($lines lines)"
  fi
fi

# 6. No superseded version is referenced as active outside the archive.
#    Historical references ("supersedes v1.3.1", "corrects v1.3.2", archive paths)
#    are legitimate; active-status phrasing and non-archive paths are not.
#    Add each newly superseded version here when the active version moves on —
#    v1.3.2 was added by v1.3.3, which found it cited as the controlling document
#    in four governance files after it had already been archived.
superseded=(1.3.1 1.3.2)
for v in "${superseded[@]}"; do
  ve="${v//./\\.}"
  if active_hits="$(grep -RIn --exclude-dir=archive --exclude-dir=prompts \
      -E "v${ve}[^.]{0,40}\b(is|remains)\b[^.]{0,40}\b(current|active|controlling|working)\b|hybrid-federated-target-architecture-v${ve}\.md" \
      "$root/docs" 2>/dev/null | grep -v 'archive/')"; then
    echo "$active_hits" >&2
    fail "v$v is referenced as active (or by non-archive path) outside the archive (matches above)"
  else
    echo "OK: v$v only historical outside archive"
  fi
done

# 6b. Every archived architecture draft carries a supersession banner in its first
#     20 lines. Without it the file reads as live architecture to anyone who opens
#     it directly — which is how a corrected schema gets implemented from a draft
#     that was corrected precisely because it was wrong.
arch_archive="$root/docs/architecture/archive"
if [[ -d "$arch_archive" ]]; then
  while IFS= read -r -d '' f; do
    head -3 "$f" | grep -Fq "$arch_h1" || continue
    if ! head -20 "$f" | grep -Fq 'SUPERSEDED'; then
      fail "archived architecture draft carries no supersession banner: ${f#"$root"/}"
    else
      echo "OK: banner present in ${f#"$root"/}"
    fi
  done < <(find "$arch_archive" -name '*.md' -print0 2>/dev/null)
fi

# 7. Exactly one complete active architecture copy exists outside the archive.
copies=()
while IFS= read -r -d '' f; do
  head -3 "$f" | grep -Fq "$arch_h1" || continue
  (( $(wc -l < "$f") >= copy_floor_lines )) || continue
  copies+=("$f")
done < <(find "$root/docs" -name '*.md' -not -path '*/archive/*' -print0 2>/dev/null)
if (( ${#copies[@]} != 1 )); then
  printf '%s\n' "${copies[@]:-<none>}" >&2
  fail "expected exactly 1 complete active architecture copy outside archive, found ${#copies[@]}"
else
  echo "OK: exactly one complete active architecture copy (${copies[0]#"$root"/})"
fi

if (( failed )); then
  echo "GOVERNANCE PACK: FAILED" >&2
else
  echo "GOVERNANCE PACK: OK"
fi
exit "$failed"
