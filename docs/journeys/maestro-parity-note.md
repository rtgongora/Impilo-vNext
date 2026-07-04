# Maestro ↔ Scenario Seed Parity Note

Status: doc-level mapping (journey-closure Wave 6, 2026-07-04). No flow edits —
the existing flows are UI-surface smokes, not data-coupled journeys, so they run
against any estate; the seeds below make their surfaces non-empty and therefore
meaningfully assertable.

## Context

The journey-closure session proved Scenarios A–D end-to-end on the live preview
estate via API steel threads (`scripts/e2e/run-all-scenarios.sh`). The mobile
Maestro flows (`apps/mobile/maestro/flows/`) exercise the same journey surfaces
on the provider/citizen apps. This note maps the journey seeds to the flows that
benefit from them.

## Mapping

| Maestro flow | Journey surface | Seed / proof that populates it |
|---|---|---|
| `provider-tier2-queue-triage.yaml` | Queue management + triage | `scripts/operator/seed-scenario-a-estate.sh` (facility, personas, assignments); a Scenario A run leaves queue entries WAITING→IN_CONSULTATION at Harare Central (`f1000000-…-0001`) |
| `provider-tier2-lab-onepath.yaml` | Results view (PENDING/RESULTED filters) | Scenario A phases 5–6 create an OROS lab order through RESULT_AVAILABLE for the golden patient |
| `provider-tier2-pharmacy-onepath.yaml` | Pharmacy dispense surface | Scenario B leaves priced bills; pharmacy dispense emits `pharmacy.stock.movement.requested` → Dura ledger (backlog ③) |
| `provider-tier3-wave4-ops-reports.yaml` | Ops/reports | Billing + coverage rows from Scenario B (`costa_bill_headers`, `cv_claims`) |
| `citizen-tier1-smoke.yaml` / `citizen-tier3-facility.yaml` | Citizen shell + facility | Same TUSO facility seed as Scenario A |

## Login parity

Maestro flows authenticate with the same Keycloak realm the journeys use.
Personas: `dr.mapfumo` / `nurse.chienda`, password `ImpiloTest123!` (reconciled
by `scripts/operator/reconcile-keycloak-realm-users.sh`). Health-ID anchors
`c0000000-…-0001/-0007` map to `PROV-ZW-00001/-00007`.

## Known limits

- Flows assert stable testIDs/surface presence, not row-level data — they pass
  on an empty estate but only *mean* something after the seeds above have run.
- No Maestro flow covers teleconsult video (LiveKit) — browser media join is a
  Playwright concern (pending firewall ports; see Scenario A runbook).
- Extending flows to assert seeded row content (e.g. the golden patient's name
  in Results) is a candidate follow-up, not committed scope.
