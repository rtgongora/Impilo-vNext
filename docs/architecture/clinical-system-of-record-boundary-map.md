# Clinical System-of-Record Boundary Map

## Canonical SoR Ownership

| Domain | System of Record | Notes |
|---|---|---|
| Care Continuum (person's cradle-to-grave clinical journey) | `pct-service` | Continuum owner: visit journeys, encounters, problems, care plans, allergies, growth, immunisation doses, birth/death pathways, referrals, community care context. Every other care-path service is a subordinate component ([care-continuum-doctrine](../doctrine/care-continuum-doctrine.md) CC-1/CC-2) |
| Longitudinal shared health record | `butano-service` | Canonical SHR owner for clinical longitudinal data |
| FHIR representation/access | `butano-fhir` + `fhir-gateway-service` | FHIR projection/access boundary over SHR |
| Pharmacy prescription workflow | `pharmacy-service` | Canonical owner for prescription workflow state in this implementation scope |
| Order/result orchestration | `oros-service` | Orchestration owner; not SHR duplication owner |
| Inpatient admission/bed workflow | `inpatient-service` | Workflow owner, not SHR canonical owner |
| Clinical forms/documents | `forms-service`, `document-service` | Document/form workflow stores; final longitudinal references should be reflected via SHR boundaries |

## Explicit Boundary Rules

- `pct-service` owns the Care Continuum; components (inpatient, oros, booking, telemonitoring, forms, madi, daidzai-as-delegated-correlator) own operational/phase state and must carry a resolvable PCT anchor on every clinical record (CC-5). The PCT↔BUTANO relationship is contribution/projection, not subordination (CC-3).
- `butano-service` remains the canonical SHR owner; workflow services must not independently become longitudinal SHR authorities.
- `butano-fhir`/`fhir-gateway-service` are the canonical FHIR boundary; non-FHIR workflow services should not duplicate full FHIR responsibility.
- `pharmacy-service` owns prescription lifecycle workflow state for operational prescribing/dispensing; longitudinal reconciliation into SHR remains a follow-up dependency.
- `oros-service` remains orchestration-centric and should reference, not duplicate, SHR ownership.

## Runtime Boundary Proof

- Source-level ownership assertions are enforced by `ClinicalPlaneEvidenceGuardTest`:
  - `butano-service` controllers remain non-FHIR ownership surfaces.
  - `butano-fhir` exposes dedicated FHIR controller ownership.
  - `fhir-gateway-service` exposes dedicated forwarding gateway ownership.
- Runtime validation harness: `test/integration/clinical-shr-fhir-runtime.(sh|ps1)`.

## Follow-On Enhancements (Non-Blocking)

- Expand workflow-to-SHR reconciliation telemetry for deeper operational observability.
