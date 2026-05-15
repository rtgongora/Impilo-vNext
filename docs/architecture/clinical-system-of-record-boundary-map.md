# Clinical System-of-Record Boundary Map

## Canonical SoR Ownership

| Domain | System of Record | Notes |
|---|---|---|
| Longitudinal shared health record | `butano-service` | Canonical SHR owner for clinical longitudinal data |
| FHIR representation/access | `butano-fhir` + `fhir-gateway-service` | FHIR projection/access boundary over SHR |
| Pharmacy prescription workflow | `pharmacy-service` | Canonical owner for prescription workflow state in this implementation scope |
| Order/result orchestration | `oros-service` | Orchestration owner; not SHR duplication owner |
| Inpatient admission/bed workflow | `inpatient-service` | Workflow owner, not SHR canonical owner |
| Clinical forms/documents | `forms-service`, `document-service` | Document/form workflow stores; final longitudinal references should be reflected via SHR boundaries |

## Explicit Boundary Rules

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
