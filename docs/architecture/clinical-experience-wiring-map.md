# Clinical Experience Wiring Map

## Encounter Workspace Wiring (Deep Pass)

| UX capability | UI route | BFF route | Backend owner | Status |
|---|---|---|---|---|
| Encounter context + lifecycle actions | `/ehr/[patientId]/encounter/[encounterId]` | `/internal/v1/encounters/*` | `pct-service` | wired |
| Pathway/protocol status + update | same | `PATCH /internal/v1/encounters/{id}/pathway-protocol` | `pct-service` | wired in this pass |
| CDS/alerts panel (federated) | same (`ClinicalAlerts`) | allergies/conditions/vitals/pharmacy routes | multiple clinical owners | partial |
| Inpatient admission workflows | inpatient + encounter outcome surfaces | `/internal/v1/admissions*` | `inpatient-service` | wired (bounded) |
| Procedure/OR context visibility | encounter screens | encounter context metadata route family | `pct-service` coordinator + specialist services | partial |
| PACS study metadata + viewer launch | imaging routes/components | `/internal/v1/imaging/*` | `pacs-adapter-service` via BFF | wired |
| DICOMweb operations tooling | imaging operations | `/internal/v1/pacs/*` | BFF PACS proxy + Orthanc | wired (bounded) |

## Honest Unavailable States

- BFF returns fail-close typed upstream errors for unavailable encounter/imaging upstream dependencies.
- Ward round authoring endpoints in current BFF remain explicit `501` where not yet wired.
# Clinical Experience Wiring Map

## Clinical UI -> BFF -> Backend Wiring

| UI Surface | BFF Route | Clinical Backend API | Status |
|---|---|---|---|
| `ui/experience` medications page (prescribe) | `POST /internal/v1/pharmacy/prescriptions` | `POST /v1/prescriptions` (`pharmacy-service`) | wired |
| `ui/experience` medications page (cancel) | `POST /internal/v1/pharmacy/prescriptions/{id}/cancel` | `POST /v1/prescriptions/{id}/cancel` | wired |
| `ui/experience` medications page (dispense) | `POST /internal/v1/pharmacy/dispense` | `POST /v1/prescriptions/{id}/dispense` | wired |
| Mobile provider prescribe | `POST /internal/v1/mobile/provider/prescriptions` | `POST /v1/prescriptions` | wired |
| Mobile provider cancel | `POST /internal/v1/mobile/provider/prescriptions/{id}/cancel` | `POST /v1/prescriptions/{id}/cancel` | wired |
| Mobile citizen list/detail/refill | `/internal/v1/mobile/citizen/prescriptions*` | `/v1/prescriptions/patient/{cpid}`, `/v1/prescriptions/{id}`, `/v1/prescriptions/{id}/refill` | wired |
| Mobile provider telemedicine | `/internal/v1/mobile/provider/telemedicine/sessions*` | `pct-service /v1/patient/{cpid}/telehealth`, `/v1/telehealth`, `/v1/telehealth/*` | wired (patient + facility list, validated/fail-close) |
| Imaging/PACS workflows | `/internal/v1/imaging/*`, `/internal/v1/pacs/*` | `pacs-adapter-service /internal/v1/imaging-studies*` + Orthanc/DICOMweb proxy | wired |
| Encounter workspace (web) | `/internal/v1/encounters*` | `pct-service` encounter/timeline APIs | wired (canonical/fail-close) |
| Mobile encounter workflow | `/internal/v1/mobile/provider/encounters*` | `pct-service` encounter APIs | wired (canonical/fail-close) |
| Mobile triage workflow | `/internal/v1/mobile/provider/triage*` | `pct-service` journey/triage APIs | partial (journey-vs-encounter id semantics) |
| Teleconsult builder/session (web) | `/internal/v1/teleconsult/*` | `pct-service /v1/referrals*` + `mvumo-service /internal/v1/mvumo/consent-requests` | partial (canonical write lifecycle wired; live chat/media transport intentionally unavailable) |
| Teleconsult attachment verification (web) | `PUT/POST /internal/v1/teleconsult/sessions/{id}/referral|submit` | `document-service /v1/internal/objects/{id}` (lookup only) + PCT referral APIs | wired (strict ID verification + typed fail-close errors) |
| Teleconsult routing lookup (web) | `/internal/v1/teleconsult/routing/providers|facilities|workspaces` | `varapi-service /v1/internal/providers/search` + `tuso-service /v1/internal/facilities/search` + `/v1/internal/facilities/{id}/workspaces` | wired (directory lookup only, no synthetic data) |
| Encounter mastery metadata capture | `/internal/v1/encounters` (create) | `pct-service /v1/journeys/{id}/encounter/start` | wired (context, entry point, modality, care setting, priority, pathway/protocol refs) |

## Clinical Wiring Evidence Added

- Removed explicit `501` blockers in BFF prescription create/cancel paths after backend implementation.
- Added/updated controller tests in `experience-bff` validating real write-path wiring behavior.
- Added telemedicine controller tests validating required payload fields, bad-request behavior, and fail-close upstream handling.
- Added backend service tests in `pharmacy-service` validating state transitions.
- Added cross-service clinical evidence guardrail test validating authz/audit and boundary ownership markers.
- Added PACS to the cross-service clinical evidence guard so auth/audit posture regressions fail test gates.
- Removed encounter local fallback behavior in Experience-BFF and wired close action to canonical PCT completion.
- Converted teleconsult in-memory production-path behavior to explicit backend blocker responses to avoid fake clinical success.

## Wiring Gap Status

- Remaining blockers for this scope:
  - teleconsult live transport (chat/audio/video signaling) backend not yet in canonical stack;
- on-call/team/pool routing requires a future canonical directory/on-call capability (explicitly blocked, no fake success).
