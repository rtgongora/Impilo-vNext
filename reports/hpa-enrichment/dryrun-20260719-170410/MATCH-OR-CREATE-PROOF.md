# HPA match-or-create — end-to-end rig proof (real data)

Rig: `scripts/runtime-proof/hpa-enrichment-journeys.sh`. Throwaway Postgres 16 loaded with the
**real live preview tuso schema** + the **real ~1,776-facility estate** + **V036**, matched with the
**same pg_trgm GIN engine** the production `FacilityMatchService` uses, over the **real 6,327 feed**.
This mirrors `HpaEnrichmentImportService.reconcile()` (the committed Java is the production capability).

## Results (reproducible)

| stage | result |
|---|---|
| estate before | **1,776** facilities |
| DRY-RUN classify (no writes) | **NEW_REGULATED_ESTABLISHMENT 5,488 · POSSIBLE_EXISTING_REVIEW 687 · CONFIRMED_EXISTING_ENRICH 152** · total **6,327** ✓ (all accounted for) |
| APPLY (create NEW) | 1,776 → **7,264** (+5,488 created); **5,488** `HPA_INSTITUTION_ID` idempotency keys stamped |
| IDEMPOTENCY (2nd apply) | 7,264 → **7,264** — **0 new** (NOT-EXISTS guards + deterministic `HPA-<id>` key) ✓ |
| ROLLBACK by batch | 7,264 → **1,776** — canonical estate intact, **0 canonical rows hard-deleted** ✓ |

## What this proves
- **Match against live Tuso first**, never `1,773 + 6,327`: only **5,488** of 6,327 are genuinely new
  (the feed is dominated by private MDPCZ doctors + PCZ pharmacies absent from the public MFL estate);
  152 credibly match and enrich; 687 are ambiguous/low-confidence and route to review (never
  auto-enriched — registration-number/name alone is not conclusive).
- **Create-if-unmatched works** and is **idempotent**: a re-run creates nothing (deterministic
  `HPA_INSTITUTION_ID = HPA-<id>` key + guarded inserts).
- **Rollback by batch** removes only HPA-created rows; **no canonical facility is ever hard-deleted**.
- New facilities are incomplete `REGULATOR_LISTED` (status INACTIVE, operational status requires
  confirmation) — honest, not fabricated as operational.

## Caveats (honest)
- The rig runs the reconciliation SQL that MIRRORS the committed `HpaEnrichmentImportService`; the
  Java service (compiled, committed `6b08fd11d`/`72b9a21db`) is the production path. Matching is
  identical (same real pg_trgm index + thresholds); orchestration is SQL here vs Java in production.
- Review/enrich thresholds: candidate if name-similarity ≥ 0.55; enrich if ≥ 0.72 single-candidate;
  else review — mirroring the service's `nameCorroborated` cutoff.
