# HPA National Facility Enrichment — Dry-run Reconciliation Report

**Generated:** 2026-07-19 · **Bundle:** `TUSO_HPA_VNEXT_IMPORT_2026_07_19` (source effective 2026-07-17)
**Method:** read-only analysis of the validated candidate feed + read-only name match against the
LIVE `impilo-full-preview` `tuso.facility` estate. No writes. Never computed `1,773 + 6,327`.

## Source validation
- Bundle checksums: **all 28 files OK** (`sha256sum -c CHECKSUMS.sha256`, exit 0).
- Feed records: **6,327** · current institutions CSV: **6,327** · practitioner-in-charge: **6,580** (all exact vs `expected_source_counts.json`).

## Live estate (comparison set)
- **Canonical `tuso.facility` before import: 1,776.**
- `tuso.facility_identifier` on preview: **empty** (0 external identifiers, 0 `HPA_REGISTRATION_NUMBER`)
  → matching against this estate is **name-based** (the D-L5 matcher's trigram/normalised-name path);
  reg-number alias matching will light up only after the first apply stamps `HPA_REGISTRATION_NUMBER`.

## Current HPA feed shape (6,327)
- Every record: `canonical_creation_target = REGULATED_ESTABLISHMENT_OR_FACILITY_SITE`,
  `site_resolution_status = RESOLVE_AGAINST_LIVE_TUSO`,
  `public_visibility = SEARCHABLE_WITH_SOURCE_DATE_AND_COMPLETENESS_DISCLOSURE`.
- **Regulatory councils:** MDPCZ (doctors) **2,591** · PCZ (pharmacy) **1,789** · NCZ (nursing) **1,019** ·
  MLCSCZ **380** · AHPCZ **373** · MRPCZ **169** · Natural Therapists 6. → the feed is dominated by
  **private regulated practices** (doctors + pharmacies), which the public MFL estate does not hold.
- **Top provinces:** Harare 2,863 · Bulawayo 586 · Midlands 541 · Manicaland 504 · Mash West 474 · Mash East 387.

## Current→legacy crosswalk (governs legacy auto-enrich)
| status | count | legacy action |
|---|---|---|
| AUTO_EXACT_REG_CORROBORATED | **2,294** | legacy fields auto-attachable as dated unverified assertions |
| CURRENT_ONLY_NO_SQL_MATCH | **3,442** | no legacy candidate |
| REVIEW_EXACT_REG_IDENTITY_CONFLICT | **324** | preserve as review evidence only |
| REVIEW_EXACT_REG_POSSIBLE_RENAME | **168** | review evidence only |
| REVIEW_EXACT_NAME_PROVINCE_REG_CHANGED | **86** | review evidence only |
| AMBIGUOUS_REGISTRATION_KEY | **13** | review evidence only |

Only the **2,294 AUTO_EXACT_REG_CORROBORATED** attach legacy operational fields automatically; the other
**591 disputed** links stay review-only. The current HPA record remains eligible for live match-or-create
regardless — the dispute only blocks legacy field auto-attachment.

## Estimated match-or-create vs the live 1,776 estate (read-only, name-based)
| bucket | count | importer outcome |
|---|---|---|
| exact normalised-name match | **132** | CONFIRMED_EXISTING_ENRICH (candidate) |
| fuzzy (≥2 shared significant tokens) | **1,643** | POSSIBLE_EXISTING_REVIEW (matcher tightens; not auto-enriched) |
| no name match | **4,552** | NEW_REGULATED_ESTABLISHMENT (create incomplete REGULATOR_LISTED) |

This confirms the doctrine expectation: **most of the 6,327 regulated establishments are NOT in the
1,776 public estate** — they are private doctor/pharmacy practices. The importer creates them as honest
incomplete `REGULATOR_LISTED` records (searchable with source-date + completeness disclosure), with
structured data-gap tasks — it does not fabricate 6,327 new physical sites.

## Varapi — practitioner population opportunity
- **6,580** current practitioner-in-charge rows, **6,457 (98%)** carry a normalised registration number
  → resolvable via `POST /v1/registry/resolve` (`kind=COUNCIL_REG`) into candidate
  `practitioner_in_charge_assignments` (`approval_state='PENDING'`, no authority).
- **5,459** legacy practitioner records for historical relationship + qualification seeding.
- These are a real Varapi **provider-population** seed, not just edges — the registration numbers + councils
  let us stand up candidate provider identities pending Varapi/Tshepo verification.

## Ndila — location + coordinate assertions
- **5,423** legacy facility-location rows (address/locality assertions → `ndila_locations`, UNVERIFIED).
- **5,445** raw geo rows — **registration-keyed, quarantined by default** (only ~1 plausible Zimbabwe
  coordinate); coordinates run through `NdilaCoordinateValidator` and land in
  `ndila_geocode_review_queue` unless plausible AND identity-corroborated. Text search works without coords;
  public map pins show "Map location awaiting confirmation."

## Nhume / facility contacts — privacy split (as doctrine requires)
- **19,643** legacy contact assertions: **FACILITY 14,683** (candidate public endpoints → Tuso
  `facility_contact`, UNVERIFIED, "call before travelling") · **PRACTITIONER 2,996** (restricted personal) ·
  **OWNER 1,964** (restricted unless verified as organisation business info). Dispatch-readiness is NOT
  implied by a contact — it stays derived from Nhume fleet/courier operational state.

## Landed capability (this session)
- **V036** migration (7 tables) — DDL validated against Postgres 16 — committed `2014fc560`.
- **`HpaEnrichmentImportService` + `HpaImportController`** (`/v1/internal/facilities/hpa-import`) — the
  match-or-create engine reusing `FacilityMatchService`, field-level provenance, progressive states,
  completeness dimensions, data-gap tasks, dry-run + idempotency — tuso-service compiles clean — committed `6b08fd11d`.

## Remaining (per approved plan)
Cross-service candidate writes (Varapi PIC/provider population, Ndila locations + geo-queue, Tuso contact
candidates, Tshepo V042 seed); citizen + admin UI; full test suite; full-feed apply on a runtime-proof rig
+ snapshot-and-apply to preview. Live UI-through-gateway proof is deploy-gated (preview `tuso` runs a stale
jar) — staged.
