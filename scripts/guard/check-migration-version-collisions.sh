#!/usr/bin/env bash
# Migration version-collision guard.
#
# Within one Flyway migration directory, no two files may carry the same version. Flyway keys a
# migration by its version, not its filename, so two files with the same version and DIFFERENT names
# are a collision — and the whole point of this guard is that nothing else catches that collision.
#
# WHY THIS EXISTS. pct V400 was cut twice. Two lease files existed for one programme —
# `iatg-paediatrics-leases.md` and `iatg-paediatric-leases.md`, one letter apart — and each
# independently reserved the V400–V429 band, so two sessions each cut V400 believing the band was
# theirs. Nothing caught it:
#
#   - git merges the two files cleanly. Same version, different filename → no textual conflict, no
#     warning. It is not a merge problem; it is a namespace problem merge tooling cannot see.
#   - Services set `validate-on-migrate: false`, so Flyway does not complain at boot either. On a
#     fresh database it refuses to start ("more than one migration with version 400"); on an estate
#     where one already applied, Flyway marks the OTHER as resolved and its table is never created —
#     a silent, data-shaped failure in somebody else's programme.
#
# It surfaces only on a deployed database, which is the worst place to find it. This is the
# mechanical version, run before the commit that would carry the collision.
#
# THE RULE IT ENFORCES, stated for the lane reading a FAIL: a migration number is checked against
# the MIGRATION DIRECTORY on the merged tree — `ls .../db/migration/` after your merge — never
# against a lease's "Consumed" column. A lease records an agreement; the directory records the fact.
# And one clinical programme owns exactly one lease file: two lease names for one pack is the
# upstream cause of two lanes cutting the same number, which is why this guard also warns on
# near-duplicate lease filenames (see the second half).
#
# TRAPS FOUND BUILDING THIS, each of which produced a CLEAN PASS — the only outcome nobody
# investigates, so each was found by trying to make the guard fail, not by trusting it passed:
#
#   1. Flyway version equality is not string equality. `V58` and `V058` are the SAME version to
#      Flyway (leading zeros are insignificant), and `V1_1` equals `V1.1`. Comparing raw filename
#      prefixes as strings misses exactly the collision that boots and then loses a table. Versions
#      are normalised (each dot/underscore-separated part stripped of leading zeros) before compare.
#   2. A glob that matches nothing passes silently. If the path assumptions here drift and the scan
#      finds zero migration directories, that is a guard checking nothing while reporting safety —
#      worse than no guard. This fails loudly on zero directories, on any directory it cannot read,
#      and on any directory that contains files but parses to zero versioned migrations.
#   3. Non-versioned Flyway files are legitimate (repeatable `R__`, callbacks like `afterMigrate`).
#      They are excluded from the version comparison rather than flagged — but a directory whose
#      ONLY files are non-versioned still trips trap 2, because that is far more likely a parse
#      failure than a real Flyway layout.
# `git ls-files` alone lists only TRACKED files, so a guard using it is blind to the new file it
# exists to catch — the violation arrives untracked, the guard reports clean, and it ships. Found by
# the RMNP lane, whose routing guard PASSED against a deliberately-planted violation.
# --cached --others --exclude-standard = staged + untracked, minus anything gitignored.
set -uo pipefail

REPO_PATH="${REPO_PATH:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$REPO_PATH"

echo "=== Migration version-collision guard ==="

FAIL=0

# ----------------------------------------------------------------------------------------------
# Part 1 — duplicate versions within a migration directory (the blocking check).
# ----------------------------------------------------------------------------------------------

# The migration directories on the MERGED TREE, from git (tracked files only — an untracked,
# not-yet-added migration is not yet part of the namespace git will merge, and the lane cutting it
# is the one running this guard against its own `git add`).
mapfile -t MIG_DIRS < <(git ls-files --cached --others --exclude-standard -- '*/db/migration/*.sql' 2>/dev/null \
  | sed -E 's#(.*/db/migration)/[^/]+$#\1#' | sort -u)

if [[ ${#MIG_DIRS[@]} -eq 0 ]]; then
  echo "FAIL: no migration directories found under any */db/migration/."
  echo "      This guard scanned nothing. Either the repository layout changed and the glob above"
  echo "      needs updating, or this is a partial checkout. A guard that scans nothing must fail."
  echo "Migration version-collision guard: FAILED"
  exit 1
fi

# A directory that exists in git but is unreadable on the working tree cannot be scanned — fail
# rather than skip it.
for d in "${MIG_DIRS[@]}"; do
  if [[ ! -d "$d" || ! -r "$d" || ! -x "$d" ]]; then
    echo "FAIL: migration directory not readable on the working tree: $d"
    FAIL=1
  fi
done

TOTAL_FILES=0
TOTAL_VERSIONED=0

for d in "${MIG_DIRS[@]}"; do
  [[ -d "$d" && -r "$d" && -x "$d" ]] || continue

  # Per-directory: normalise each versioned file to its Flyway version and detect any version
  # claimed by more than one file. awk carries the whole directory's map so it can name BOTH
  # colliding files, not just the second.
  dir_out=$(git ls-files --cached --others --exclude-standard -- "$d/*.sql" 2>/dev/null | awk -F/ '
    {
      base = $NF
      files_seen++
      # Versioned migration: V<version>__<description>.sql, version up to the FIRST double underscore.
      if (match(base, /^V[0-9][0-9_.]*__/)) {
        raw = substr(base, 2, RLENGTH - 3)            # between leading V and the __ separator
        # Normalise: strip leading zeros from each dot/underscore-separated part.
        n = split(raw, parts, /[._]/)
        norm = ""
        for (i = 1; i <= n; i++) {
          p = parts[i]
          sub(/^0+/, "", p)
          if (p == "") p = "0"
          norm = (i == 1) ? p : norm "." p
        }
        versioned++
        if (norm in firstfile) {
          print "DUP\t" norm "\t" firstfile[norm] "\t" base
        } else {
          firstfile[norm] = base
        }
      }
    }
    END {
      print "STATS\t" files_seen "\t" versioned
    }
  ')

  files_seen=$(awk -F'\t' '$1=="STATS"{print $2}' <<< "$dir_out")
  versioned=$(awk -F'\t' '$1=="STATS"{print $3}' <<< "$dir_out")
  TOTAL_FILES=$(( TOTAL_FILES + ${files_seen:-0} ))
  TOTAL_VERSIONED=$(( TOTAL_VERSIONED + ${versioned:-0} ))

  # Trap 2/3: a directory with files but zero parseable versions is a parse failure, not a valid
  # repeatable-only layout — no service in this estate has one, so treat it as broken.
  if [[ "${versioned:-0}" -eq 0 ]]; then
    echo "FAIL: $d contains ${files_seen:-0} file(s) but zero parseable V<n>__ migrations."
    echo "      Either the filenames are malformed or this guard's parser needs to learn a new"
    echo "      Flyway layout. It must not silently pass a directory it could not read."
    FAIL=1
    continue
  fi

  while IFS=$'\t' read -r tag norm file1 file2; do
    [[ "$tag" == "DUP" ]] || continue
    svc=$(sed -E 's#services/([^/]+)/.*#\1#' <<< "$d")
    echo "FAIL: duplicate migration version V$norm in $svc"
    echo "        $d/$file1"
    echo "        $d/$file2"
    echo "      Flyway keys on version, not filename. On a fresh DB it refuses to boot; on an estate"
    echo "      where one already applied, the other is marked resolved and its table is never made."
    echo "      Renumber against \`ls $d\` on the merged tree, not against a lease's Consumed column."
    FAIL=1
  done <<< "$dir_out"
done

# ----------------------------------------------------------------------------------------------
# Part 2 — near-duplicate lease filenames (warn: the upstream cause, not itself a build break).
#
# One clinical programme owns one lease file. Two lease names a single edit apart
# (paediatric/paediatrics) let two lanes each believe a band is theirs and cut the same number —
# the exact origin of the pct V400 collision above. This is a WARN, not a FAIL: an out-of-tree
# rename is the fix, and blocking every commit on a docs-registry heuristic is disproportionate.
# It reports; the lanes reconcile.
# ----------------------------------------------------------------------------------------------

mapfile -t LEASE_FILES < <(git ls-files --cached --others --exclude-standard -- 'docs/registry/*-leases.md' 2>/dev/null | sort)

if [[ ${#LEASE_FILES[@]} -gt 1 ]]; then
  # Edit distance ≤ 1 between two lease basenames (insertion, deletion, or substitution of a single
  # character) — catches the plural-s and the one-letter-typo cases without hand-listing them.
  lease_warn=$(printf '%s\n' "${LEASE_FILES[@]}" | awk -F/ '
    function lev(a, b,   la, lb, i, j, cost, d, prev, cur) {
      la = length(a); lb = length(b)
      if (la == 0) return lb
      if (lb == 0) return la
      for (j = 0; j <= lb; j++) prev[j] = j
      for (i = 1; i <= la; i++) {
        cur[0] = i
        for (j = 1; j <= lb; j++) {
          cost = (substr(a, i, 1) == substr(b, j, 1)) ? 0 : 1
          d = prev[j] + 1
          if (cur[j-1] + 1 < d) d = cur[j-1] + 1
          if (prev[j-1] + cost < d) d = prev[j-1] + cost
          cur[j] = d
        }
        for (j = 0; j <= lb; j++) prev[j] = cur[j]
      }
      return prev[lb]
    }
    { names[NR] = $NF }
    END {
      for (i = 1; i <= NR; i++)
        for (j = i + 1; j <= NR; j++)
          if (lev(names[i], names[j]) <= 1)
            print "WARN: near-duplicate lease filenames (" lev(names[i], names[j]) " char apart): " \
                  names[i] " and " names[j]
    }
  ')
  if [[ -n "$lease_warn" ]]; then
    echo "$lease_warn"
    echo "      One programme owns one lease file. Two names a single edit apart let two lanes"
    echo "      reserve the same band independently — the upstream cause of a version collision."
    echo "      Consolidate to one filename per pack."
  fi
fi

# ----------------------------------------------------------------------------------------------

if [[ $FAIL -eq 0 ]]; then
  echo "  ${#MIG_DIRS[@]} migration directories, $TOTAL_VERSIONED versioned migrations, no version collisions"
  echo "Migration version-collision guard: OK"
else
  echo "Migration version-collision guard: FAILED"
fi
exit $FAIL
