# HPA → Varapi practitioner population — rig proof (real data)

Throwaway Postgres loaded with the **real live-preview varapi schema** + **V027**, over the **real
6,580 practitioner-in-charge rows** from the HPA feed. Mirrors `HpaPractitionerImportService`
(the committed Java is the production path): resolve each practitioner to an existing varapi provider
by council registration number, persist a CANDIDATE relationship that grants NO authority.

The preview varapi provider registry is **empty (0 providers)** — so the HPA import IS the population:
all 6,580 become candidate onboarding records. To exercise the resolve path, 3 providers were seeded
with 3 real practitioner registration numbers from the feed.

## Results (reproducible)
| metric | value |
|---|---|
| PIC candidates populated | **6,580** |
| RESOLVED to an existing provider (by reg#) | **4** (from 3 seeded reg numbers — one practitioner is PIC of 2 facilities) |
| UNRESOLVED (new onboarding candidates) | **6,576** |
| rows granting any authority | **0** (100% `authority_grant=NONE`, `approval_state=PENDING`) |
| idempotency (2nd run) | **0 new** (6,580 stable; unique `bundle_id + source_record_key`) |

## What this proves
- The 6,580 practitioners (6,457 with a council registration number) are captured as candidate
  relationships linked to their facility (`hpa_institution_id` → tuso `HPA_INSTITUTION_ID=HPA-<id>`).
- Resolution by registration number works (seeded providers resolve; one to multiple facilities).
- The import grants **no authority** — every row is `PENDING` / `NONE`; materialisation into the
  authoritative `practitioner_in_charge_assignments` needs a resolved provider + tuso facility + human
  approval, never this import alone.
- Idempotent re-run creates nothing.
