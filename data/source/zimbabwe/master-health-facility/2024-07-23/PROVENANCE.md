# Zimbabwe Master Health Facility — TUSO Absorption Package

**Source label:** `MASTER_HEALTH_FACILITY_2024_07_23`
**Source file date:** 2024-07-23
**Placed:** 2026-07-01 (facility-lifecycle absorption wave)

National master facility dataset absorbed into TUSO as the facility foundation. **Not a demo seed.**
TUSO is the source of truth for facility master identity; imported facilities enter as
`IMPORTED_PENDING_CONFIGURATION` — they are **not** fully configured or operational merely by being
in this list.

## Files

| File | Rows | Role |
|---|---|---|
| `clean_tuso_facility_import.csv` | 1773 | **The only** dataset eligible for automatic staged import. Already canonicalised (`*_raw` + `*_canonical` columns, completeness flags, `facility_setup_state`, `source_label`). |
| `clean_rows_with_acceptable_missing_fields.csv` | 194 | Importable rows with acceptable missing fields (lat/long/type/ownership/status) — must remain visible as missing. |
| `duplicate_facility_code_review.csv` | 81 | Review only — **not** auto-imported; needs human resolution. |
| `duplicate_facility_name_review.csv` | 47 | Review only — **not** auto-imported; needs human resolution. |
| `excluded_missing_facility_code.csv` | 124 | Excluded — no facility code; never imported. |
| `excluded_missing_required_or_duplicates.csv` | 250 | Full excluded/review set (missing-code + duplicate-code + duplicate-name). |
| `zim_master_health_facilities_cleaned_for_tuso_2024_07_23.xlsx` | — | Cleaned workbook (human reference). |
| `README_FOR_OPUS.md`, `opus_facility_absorption_instruction.txt` | — | Package instructions. |

## Counts (verified 2026-07-01 against the package — match the spec exactly)

- Original source rows: **2,023** (= 1,773 clean + 250 excluded/review)
- Clean import-ready rows: **1,773**
- Excluded/review rows: **250**
- Missing facility-code rows excluded: **124**
- Duplicate facility-code rows (review): **81**
- Duplicate facility-name rows (review): **47**
- Clean rows with acceptable missing fields: **194**

No discrepancy — counts were verified before any import wiring.

## Product-truth rules (binding)

- Missing facility code ⇒ excluded (never imported; no fake codes generated).
- Duplicate facility code ⇒ excluded/review (no auto-merge, no "pick first").
- Duplicate facility name ⇒ excluded/review (import only on explicit human "genuinely distinct" decision).
- Missing lat/long, type, ownership, status ⇒ **acceptable** for import, but preserved as raw
  missing + a structured completeness flag, shown on the frontend and in the setup checklist. **No
  fake defaults** (no `ACTIVE`/`GOVERNMENT`/`CLINIC`/`0,0`/Harare coordinates for blanks).
- Existing verified TUSO facility data must not be overwritten by blank CSV values.
- Imported facilities are **not** marked fully configured/activated/operational.
