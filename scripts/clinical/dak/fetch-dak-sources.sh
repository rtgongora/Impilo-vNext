#!/usr/bin/env bash
# Fetch and verify the WHO SMART Guidelines L2 sources listed in
# docs/reference/who-dak/MANIFEST.json.
#
# RUN THIS BY HAND. NEVER IN CI.
#
# The vendored bytes are the citation; the network is not. A build that re-downloaded its clinical
# source material would mean the rules a clinician sees could change because someone else edited a
# file, with no commit, no review and no way to tell afterwards what the system had been saying.
#
# On a changed hash this script does NOT overwrite. It writes <file>.incoming beside the original
# and reports the difference, because a new WHO edition is a clinical review event: somebody has to
# read what changed and decide whether each affected national rule still stands. Accepting the new
# bytes is a separate, deliberate act (--accept), and check-dak-traceability.sh will then name
# exactly which shipped rules cite source text that moved.
#
# Usage:
#   scripts/clinical/dak/fetch-dak-sources.sh            # verify what is committed
#   scripts/clinical/dak/fetch-dak-sources.sh --refresh  # download; write .incoming on a change
#   scripts/clinical/dak/fetch-dak-sources.sh --accept   # replace originals with .incoming files
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
DAK_ROOT="$REPO_ROOT/docs/reference/who-dak"
MANIFEST="$DAK_ROOT/MANIFEST.json"
MODE="verify"
FAIL=0

case "${1:-}" in
  --refresh) MODE="refresh" ;;
  --accept)  MODE="accept" ;;
  "")        MODE="verify" ;;
  *) echo "unknown option: $1"; exit 2 ;;
esac

[[ -f "$MANIFEST" ]] || { echo "FAIL: $MANIFEST missing"; exit 1; }

echo "=== WHO DAK sources ($MODE) ==="

mapfile -t ENTRIES < <(python3 - "$MANIFEST" <<'PY'
import json, sys
for s in json.load(open(sys.argv[1]))["sources"]:
    print("\t".join([s["id"], s["pack"], s["file"], s["url"], s["sha256"], str(s["bytes"]),
                     s.get("ref") or "", s.get("repo") or ""]))
PY
)

for entry in "${ENTRIES[@]}"; do
  IFS=$'\t' read -r id pack file url want_sha want_bytes ref repo <<< "$entry"
  target="$DAK_ROOT/$pack/$file"
  incoming="$target.incoming"

  if [[ "$MODE" == "accept" ]]; then
    if [[ -f "$incoming" ]]; then
      mv "$incoming" "$target"
      echo "ACCEPTED  $id  — remember to re-run extract-dak-l2.py and update MANIFEST.json"
    fi
    continue
  fi

  if [[ "$MODE" == "refresh" ]]; then
    mkdir -p "$(dirname "$target")"
    if ! curl -sSfL --max-time 120 "$url" -o "$incoming"; then
      echo "FAIL      $id  — download failed from $url"
      FAIL=1
      rm -f "$incoming"
      continue
    fi
    got_sha="$(sha256sum "$incoming" | cut -d' ' -f1)"
    if [[ ! -f "$target" ]]; then
      mv "$incoming" "$target"
      echo "NEW       $id  ($got_sha)"
      continue
    fi
    if [[ "$got_sha" == "$want_sha" ]]; then
      rm -f "$incoming"
      echo "UNCHANGED $id"
    else
      echo "CHANGED   $id"
      echo "          manifest : $want_sha"
      echo "          upstream : $got_sha  (kept as $(basename "$incoming"))"
      echo "          WHO has republished this source. Review what changed before accepting:"
      echo "          a new edition can alter a recommendation our rules cite verbatim."
      FAIL=1
    fi
    continue
  fi

  # verify
  if [[ ! -f "$target" ]]; then
    echo "MISSING   $id  — $pack/$file is not committed; run with --refresh"
    FAIL=1
    continue
  fi
  got_sha="$(sha256sum "$target" | cut -d' ' -f1)"
  got_bytes="$(stat -c%s "$target")"
  if [[ "$got_sha" != "$want_sha" ]]; then
    echo "FAIL      $id  — committed bytes do not match the manifest hash"
    echo "          manifest : $want_sha"
    echo "          on disk  : $got_sha"
    FAIL=1
  elif [[ "$got_bytes" != "$want_bytes" ]]; then
    echo "FAIL      $id  — size mismatch ($got_bytes vs $want_bytes)"
    FAIL=1
  else
    echo "OK        $id"
  fi
done

if [[ "$MODE" == "verify" && $FAIL -eq 0 ]]; then
  echo "All vendored WHO sources match their manifest hashes."
fi
exit $FAIL
