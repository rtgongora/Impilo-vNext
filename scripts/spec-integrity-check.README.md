# Spec Integrity Check — README

## Purpose

`spec-integrity-check.sh` verifies that the 8 specification documents under `docs/prototype/final/` are not stubs. It enforces that these files are either:

1. **Detailed spec documents** (>= 200 lines, no placeholder phrases), OR
2. **Index/contract documents** (>= 10 valid relative links to canonical spec sources, no placeholder phrases)

This prevents replication prompts and CI pipelines from silently operating on stub specifications.

## How to Run

```bash
cd /home/user/Impilo-vNext
./scripts/spec-integrity-check.sh
```

## What It Checks

### Prerequisites
- Canonical spec root (`docs/plan/`) exists with `.md` files
- Architecture spec root (`docs/architecture/v1.1/`) exists with `.md` files

### Per-File Checks (8 files)

For each file in `docs/prototype/final/`:

| File | Check |
|------|-------|
| `00_executive_summary.md` | Exists, not a stub |
| `01_site_map.md` | Exists, not a stub |
| `02_page_by_page_spec.md` | Exists, not a stub |
| `03_component_inventory.md` | Exists, not a stub |
| `04_api_surface_map.md` | Exists, not a stub |
| `05_state_and_storage.md` | Exists, not a stub |
| `06_golden_paths.md` | Exists, not a stub |
| `07_opus_execution_contract.md` | Exists, not a stub |

### Stub Detection Heuristics

A file is **not a stub** if it passes ONE of:

- **Detailed doc**: >= 200 lines AND zero matches for placeholder phrases
- **Index doc**: >= 10 valid relative links (to `../../plan/`, `../../architecture/`, `../../../`) AND zero matches for placeholder phrases

Placeholder phrases detected: `stub`, `summary only`, `TODO`, `TBD`, `placeholder`, `PLACEHOLDER`

## Exit Codes

| Code | Meaning |
|------|---------|
| `0` | All checks pass |
| `1` | One or more checks failed |

## Example Output (PASS)

```
=== Spec Integrity Check ===
Canonical spec root: /home/user/Impilo-vNext/docs/plan
Architecture spec root: /home/user/Impilo-vNext/docs/architecture/v1.1
Prototype final dir: /home/user/Impilo-vNext/docs/prototype/final

PASS: Canonical spec root exists with 7 files
PASS: Architecture spec root exists with 7 files
PASS: 00_executive_summary.md — 89 lines, index doc with 18 canonical links
PASS: 01_site_map.md — 78 lines, index doc with 12 canonical links
...

=== RESULTS ===
Passed: 10
Failed: 0

SPEC INTEGRITY CHECK: PASS
```

## Example Output (FAIL)

```
=== RESULTS ===
Passed: 5
Failed: 3

--- Failures ---
  FAIL: 02_page_by_page_spec.md — line_count=5<200 links=0<10
  FAIL: 03_component_inventory.md — line_count=5<200 links=0<10
  FAIL: 06_golden_paths.md — line_count=5<200 placeholders=1

SPEC INTEGRITY CHECK: FAIL
```

## What to Do on Failure

1. **Missing file**: Create the file following the index/contract template pattern from existing files
2. **Low line count + low link count**: The file is a stub — rewrite it as either a detailed spec or an index doc with relative links to canonical sources
3. **Placeholder phrases found**: Remove `TODO`, `TBD`, `stub`, etc. and replace with actual content or links

## CI Integration

Add to your CI pipeline:

```yaml
- name: Spec Integrity Check
  run: ./scripts/spec-integrity-check.sh
```

This gate prevents merging changes that degrade spec quality.
