# Patient Encounter Structured Forms — Deferred Seams (honest register)

These are **intentionally deferred**, not hidden. Each is a real seam with a safe default now and a clear
future path. No deferral leaves a production path stubbed or mocked.

| Seam | Safe default now | Future path |
|------|------------------|-------------|
| **Full specialty-form breadth** (dental, ophthalmology, ENT, rehab, environmental health, social work, etc.) | Engine proven with ~4 forms wired end-to-end (OPD triage, OPD consultation, adult admission clerking, discharge) + ~6 governed seed definitions. | Author remaining DAK definitions into forms-service as content; no code change needed. |
| **Absorbing bespoke structured entities** (ED triage/trauma, EWS/NEWS2, MAR, fluid balance, procedure checklists) | Left intact and working; generic engine sits alongside. A `via_engine` catalog flag prevents double-capture. | Re-express as DAK definitions and migrate readers when clinically signed off. |
| **Canonical FHIR Questionnaire / QuestionnaireResponse SoR** | We emit a QR-shaped projection to BUTANO alongside discrete Observations/Conditions; no new SoR. | Promote QuestionnaireResponse to a first-class SHR resource if national interoperability requires it. |
| **Per-field CDS decision hooks** (`decisionHooks` in `types.ts` are `local_placeholder`) | Engine carries hooks through; rendering shows local placeholders. | Wire `rules-service` (`/internal/v1/rules/{key}/evaluate`) as the `rules_service` engine. |
| **External-sink atomicity** (BUTANO/OROS) | Eventually-consistent: extraction rows land PENDING/FAILED and are retried by a scheduled poller; submit never rolls back on external failure. | Saga/compensation if stricter guarantees are mandated. |
| **Observation write to BUTANO** | PCT's `ButanoIntegration` has no Observation-write method today, so Observation/Procedure extractions record a provenance row (`route_target=BUTANO`, `status=PENDING`) and emit `pct.form.observation.extracted` for an SHR bridge. Condition→ProblemService, CarePlan→CarePlanService, ServiceRequest→OROS are **fully wired in-process/real**. | Add a `ButanoIntegration.createObservation` → FHIR-gateway write and a consumer that flips PENDING→CONFIRMED. |
| **Zibo terminology validation of coded answers at submit** | Not called from PCT (no `ZiboServiceClient` in PCT); terminology bindings are carried on the definition. | Add a PCT Zibo client and validate coded answers on submit. |
| **Tall per-answer analytics table** | Answers stored as JSONB (matches V020 convention); reporting reads extracted Observations + indicator mappings. | Add `pct_form_answer_index` derived projection if indicator-level analytics need it — as a projection, not SoR. |
| **Real-time collaborative editing / offline conflict-merge** | Single-author draft + autosave; mobile sync uses the existing offline architecture. | CRDT/merge layer if multi-author concurrent editing is required. |
| **Countersign matrix per trainee cadre + sensitive-form list** | Derived from `CadreEngine.escalation.supervisorRequiredFor` + definition `sensitivity`. | Clinical-governance-owned matrix once ratified. |
| **National seed-form clinical sign-off** | Seeds are DAK-aligned **exemplars** (marked as such, per the antenatal precedent), not authoritative national protocols. | Replace with ratified national protocol content via governance workflow. |

## Product-owner decisions parked (safe default implemented, continuing)
- **Countersign/sensitive-form policy matrix** — safe default from CadreEngine escalation + sensitivity
  metadata; refine with clinical governance.
- **Seed clinical content authority** — exemplars only; national ratification is a governance activity.
