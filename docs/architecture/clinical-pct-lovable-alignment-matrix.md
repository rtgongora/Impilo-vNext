# Clinical PCT Lovable Alignment Matrix (vNext-native)

## Scope and guardrails

- Lovable is used as a feature/content reference only.
- vNext shell, route model, BFF/service ownership, trust model, and contracts are preserved.
- No Lovable/Supabase assumptions are imported directly.

Status keys:
- `present and aligned`
- `present but incomplete`
- `missing`
- `not applicable`
- `architecture decision required`

## Encounter workspace coverage (8 menu areas)

| Lovable feature/content | Current vNext equivalent | Status | Recommended vNext-native implementation | Backend/BFF dependency | UI component/route | Blocker |
|---|---|---|---|---|---|---|
| Overview | EHR root + timeline/summary rollup | present and aligned | Keep existing section mapping in canonical encounter nav | `experience-bff` patient chart aggregation | `ui/experience/src/lib/clinical/encounter-workspace-nav.ts`, `/ehr/[patientId]` | none |
| Assessment | Encounter page + vitals/allergies/history/assessments | present and aligned | Keep assessment as current segment cluster; no layout rewrite | `experience-bff` encounter/triage/vitals/notes | `/ehr/[patientId]/encounter/[encounterId]` and related chart routes | none |
| Problems & Diagnoses | Conditions route | present and aligned | Continue routing to conditions with encounter context query | `experience-bff` conditions APIs | `/ehr/[patientId]/conditions` | none |
| Orders & Results | Orders/results/imaging/procedures routes | present and aligned | Keep existing group; maintain OROS/PACS wiring | `experience-bff` -> `oros-service`/`pacs-adapter-service` | `/ehr/[patientId]/orders`, `/results`, `/imaging` | none |
| Care & Management | Medications/care-plan/goals/care-team | present and aligned | Keep grouping, preserve existing care plan ownership boundaries | `experience-bff` -> `pharmacy-service` + care services | `/ehr/[patientId]/medications`, `/care-plans`, `/goals`, `/care-team` | none |
| Consults & Referrals | Consults/referrals/teleconsult tab model | present but incomplete | Keep consults tab architecture; route teleconsult to canonical PCT referral lifecycle + MVUMO consent orchestration | `experience-bff` -> `pct-service` referrals + `mvumo-service` consent | `/ehr/[patientId]/consults`, `/telemedicine/*` | Real-time transport still unavailable by design (no fake AV/chat) |
| Notes & Attachments | Notes/documents routes and encounter note writers | present but incomplete | Keep notes/document split; wire referral attachments to document-service IDs only | `experience-bff` -> `document-service` + notes APIs | `/ehr/[patientId]/notes`, `/ehr/[patientId]/documents` | Attachment upload in teleconsult builder is UI-only pending canonical document linkage |
| Visit Outcome | Discharge/outcome routes + encounter close | present but incomplete | Use canonical encounter close path and resolve encounter->journey linkage before discharge start | `experience-bff` -> `pct-service` encounter + discharge APIs | `/ehr/[patientId]/discharge` and encounter close actions | Discharge completion/clearance orchestration UI still iterative |

## Encounter state model alignment

| Lovable state | Current vNext equivalent | Status | Recommended vNext-native implementation | Blocker |
|---|---|---|---|---|
| No patient/encounter selected | Encounter menu returns null without patient context; mobile encounter has explicit empty state | present but incomplete | Keep route-driven model, add explicit empty context messaging in shared shell where route has no patient | no global shell-wide no-patient banner yet |
| Loading chart | Encounter page loading indicator | present and aligned | keep existing loader pattern | none |
| Unable to load chart | Encounter not found state + teleconsult unavailable state | present and aligned | keep honest unavailable states; avoid local synthetic session creation | none |
| Patient/encounter active | Active encounter badge/status in menu and encounter page | present and aligned | preserve current active-state derivation from encounter status | none |
| Close chart flow | Encounter close confirmation + mutate close endpoint | present and aligned | continue close through canonical PCT completion | none |
| Chart locked/active status | Active/inactive encounter status shown; no lock token workflow | present but incomplete | keep status display; add lock only after backend support exists | lock semantics not exposed by PCT APIs |
| Last saved/dirty state | Local draft state in teleconsult response editor (non-persisted indicator) | present but incomplete | represent local dirty only, no fake persisted timestamp claims | no backend draft autosave contract |

## TopBar and patient context alignment

| Lovable content | Current vNext equivalent | Status | Recommended vNext-native implementation | Blocker |
|---|---|---|---|---|
| Patient name | Patient banner + encounter menu header | present and aligned | keep current patient identity rendering with privacy masking | none |
| MRN/Health ID | CPID/Health ID display in patient banner/menu | present and aligned | continue CPID/Health ID mapping as vNext standard | none |
| Ward/bed | Available on admission/inpatient routes, not universal in encounter shell | present but incomplete | show only when supplied by admission context APIs | not all encounter contexts are admitted journeys |
| Allergies | Pulled from allergies API where available | present but incomplete | keep real query-backed rendering; no fixture fallback | depends on backend data completeness |
| Active workspace/facility | Facility shown in headers; workspace context in auth/facility stores | present and aligned | retain current TUSO-driven context model | none |
| User menu | Existing app shell user profile controls | present and aligned | keep shell behavior; no layout rewrite | none |
| Alerts/CDS/critical events | Clinical alerts component in encounter page | present but incomplete | preserve alerts as backend-data-derived only | broader CDS depth still iterative |
| Patient search | Existing search/navigation routes | present and aligned | keep existing search entrypoints | none |
| AI assistant | Existing guided assistant surfaces are bounded | present but incomplete | keep only gated/available assistant functions | bounded service readiness |

## Telemedicine + referral package alignment

| Lovable telemedicine concept | Current vNext equivalent | Status | Recommended vNext-native implementation | Backend/BFF dependency | Blocker |
|---|---|---|---|---|---|
| Referral Package -> Consultation Response | Teleconsult new/session pages and workflow strip | present but incomplete | keep 7-stage builder semantics in vNext routes; route write lifecycle to PCT referral package APIs | `experience-bff` teleconsult routes -> `pct-service /v1/referrals*` | real-time transport remains unavailable |
| Mode: async | represented in builder mode selector | present but incomplete | keep as selectable package preference only | canonical teleconsult API | execution backend missing |
| Mode: chat | represented in builder mode selector + session messaging UI | present but incomplete | keep message UI, fail if backend unavailable, no local echo fallback | `experience-bff` teleconsult messaging | backend missing |
| Mode: audio | represented as selectable mode, execution blocked honestly | present but incomplete | keep blocked execution messaging | canonical media/session service | execution backend missing |
| Mode: video | represented as selectable mode, execution blocked honestly | present but incomplete | keep blocked execution messaging | canonical media/session service | execution backend missing |
| Mode: scheduled | represented in workflow staging semantics | present but incomplete | keep scheduled mode metadata in package | canonical scheduling/teleconsult backend | scheduling linkage pending |
| Mode: board | represented in builder mode selector | present but incomplete | keep as referral option only until board workflow backend exists | canonical teleconsult backend | board workflow backend missing |
| Stage 1 Referral Letter | implemented | present and aligned | keep as required stage | teleconsult API | none |
| Stage 2 Patient Summary | implemented with honest pending backend summary note | present but incomplete | map summary pulls to PCT/BUTANO/OROS/pharmacy/document-service once API available | summary orchestrator in BFF | summary aggregation API pending |
| Stage 3 Visit Summary | implemented with honest pending note | present but incomplete | map to encounter summary endpoint | encounter summary APIs | summary aggregation API pending |
| Stage 4 Attachments | implemented as references | present but incomplete | map file IDs to document-service | `document-service` upload/link APIs | document linkage not wired in teleconsult flow |
| Stage 5 Routing | implemented in builder | present but incomplete | align targets to VARAPI/TUSO workspace/service context | registry lookups + routing APIs | directory/routing integration still partial |
| Stage 6 Consultation Mode | implemented as required stage | present and aligned | keep 6-mode selector and capture preferred/allowed modes | teleconsult API contract | execution backend pending |
| Stage 7 Consent | implemented as required stage | present but incomplete | initiate consent via MVUMO (`/internal/v1/mvumo/consent-requests`) and persist references in PCT referral | MVUMO + TSHEPO decisioning | consent capture/verification completion lifecycle still iterative |

## BFF/backend wiring summary

| Feature area | UI component/route | BFF route | Backend service/API | Trust/Registry deps | Persistence/audit deps | Status |
|---|---|---|---|---|---|---|
| Encounter list/get/create/close | encounter pages and hooks | `/internal/v1/encounters*` | `pct-service` `/v1/patient/{cpid}/timeline`, `/v1/journeys/{id}/encounter/start`, `/v1/encounters/{id}/complete` | TSHEPO + facility context | PCT persistence/audit | wired and real |
| Encounter discharge from encounter route | discharge actions | `/internal/v1/encounters/{id}/discharge` | PCT discharge by journey (encounter->journey resolved in BFF) | TSHEPO + journey context | PCT discharge audit | wired and real |
| Mobile encounter list/create/close | provider mobile encounter surfaces | `/internal/v1/mobile/provider/encounters*` | PCT encounter APIs | TSHEPO | PCT + COSTA bill draft side effect | wired and real (fail-close) |
| Mobile triage write/read | provider mobile triage | `/internal/v1/mobile/provider/triage*` | PCT triage/journey APIs | TSHEPO + journey context | PCT triage persistence | partial (encounter_id interpreted as journey id) |
| Referrals | consult/referral UI + mobile referral routes | `/internal/v1/referrals*`, `/internal/v1/mobile/provider/referrals*` | PCT referrals API assumptions | TSHEPO + provider/facility context | PCT referral persistence/audit | backend present but contract evidence incomplete in pct.openapi |
| Provider telemedicine sessions | mobile/provider telemedicine | `/internal/v1/mobile/provider/telemedicine/*` | PCT telehealth APIs | TSHEPO + provider identity | PCT telehealth persistence/audit | wired and real |
| Citizen telehealth sessions | citizen mobile telehealth | `/internal/v1/mobile/citizen/telehealth/*` | PCT telehealth APIs | TSHEPO + patient identity | PCT telehealth persistence/audit | wired and real |
| Teleconsult builder/session | web telemedicine new/session routes | `/internal/v1/teleconsult/*` | PCT referral lifecycle + MVUMO consent initiation | TSHEPO + registry routing + consent | PCT referral + MVUMO consent persistence/audit | partial (real-time message/media transport intentionally unavailable) |

## Home-level Telemedicine Hub alignment

| Lovable hub view | Current vNext equivalent | Status | Recommended vNext-native implementation | Backend/BFF dependency | Blocker |
|---|---|---|---|---|---|
| Incoming Consults | `/telemedicine` incoming counters + queue links | present but incomplete | maintain facility lens and derive from PCT telehealth/referral status | `experience-bff` mobile telemedicine + referrals incoming | advanced queue bucketing still iterative |
| Outgoing Referrals | `/telemedicine` outgoing counters | present and aligned | keep provider/facility scoped session list and referral links | `pct-service /v1/telehealth`, `/v1/referrals` | none |
| Pending Consent | referral stage 7 state in teleconsult builder/session | present but incomplete | map consent status from PCT referral fields (`consent_status`) | `pct-service /v1/referrals/{id}` + MVUMO | completion/waiver policy automation still iterative |
| Scheduled Consults | telehealth `SCHEDULED` sessions | present and aligned | keep schedule lists from canonical telehealth session state | `pct-service /v1/telehealth` | none |
| Board Reviews | mode `board` represented as virtual mode metadata | present but incomplete | keep as workflow mode metadata until dedicated board runtime exists | `pct-service` virtual mode metadata | no board-specific execution backend |
| Active Sessions | telehealth `IN_PROGRESS` sessions | present and aligned | keep join/rejoin as state transitions only | `pct-service /v1/telehealth/{id}/join` | no fake media signaling |
| Completed / Closed | telehealth completed + referral completed | present and aligned | keep completed state from canonical PCT transitions | `pct-service` telehealth/referral completion endpoints | none |

## Focused Closure Delta (This Pass)

| Area | Status Before | Status After |
|---|---|---|
| Stage 4 Attachments verification | present but incomplete | strict BFF+PCT validation added using document-service metadata lookup and UUID-only references |
| Stage 5 Routing validation depth | present but incomplete | practitioner/workspace/facility-service validation wired via VARAPI/TUSO; unsupported on-call/team/pool made explicit `501` blockers |
| Real-time transport boundary | explicit unavailable | unchanged: still explicit unavailable with no synthetic chat/audio/video success |

