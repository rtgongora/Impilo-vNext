# Clinical Encounter Capability Inventory

| Capability | Owner service | API surface | UI/BFF surface | Status | Production blocker | Next step |
|---|---|---|---|---|---|---|
| Encounter context + modality + care setting metadata | `pct-service` | `POST /v1/journeys/{id}/encounter/start` | `/internal/v1/encounters` create | implemented | none | continue conformance checks |
| Procedure/OR encounter context tags | `pct-service` | same start endpoint | encounter create + detail surfaces | implemented in this pass | none | extend analytics dashboards |
| Encounter pathway/protocol linkage update | `pct-service` | `PATCH /v1/encounters/{id}/pathway-protocol` | `/internal/v1/encounters/{id}/pathway-protocol`, encounter page indicator/editor | implemented in this pass | none | add guided pick-list backed by knowledge services |
| CDS signals in encounter workspace | guidance/rules/clinical-knowledge + UI client logic | federated routes | encounter `ClinicalAlerts` | partial | no single CDS orchestration API | define CDS aggregation contract |
| Critical event activation + actions | `pct-service` | `/v1/emergency/*` | `/internal/v1/emergency/*` | partial | protocol catalog unification pending | add protocol registry bindings |
| Inpatient admission/transfer/discharge | `inpatient-service` | `/internal/v1/admissions*` | `/internal/v1/admissions*` | implemented (bounded) | ward-round/nursing-plan depth partial | align BFF to canonical inpatient expansions |
| Ward round authoring | mixed (`pct-service` route family) | `/v1/ward-rounds*` | BFF list wired; start/entry endpoints return `501` | partial | inpatient canonical ownership not fully wired | implement inpatient ward-round APIs and BFF routing |
| Procedure workflow phases (pre/intra/post) | federated | mixed | encounter + notes/forms/emergency | partial | no dedicated procedure aggregate | ADR on procedure-service vs coordinator-only model |
| PACS metadata + hierarchy + viewer launch | `pacs-adapter-service` | `/internal/v1/imaging-studies*` | `/internal/v1/imaging/*` | implemented | viewer UX parity still iterative | continue governed viewer rollout |
| DICOMweb passthrough tooling | Orthanc via BFF PACS proxy | `/internal/v1/pacs/dicomweb/*` | imaging operations tools | implemented (bounded) | depends on PACS runtime availability | improve operator telemetry |
# Clinical Encounter Capability Inventory

## Scope

Focused Clinical Encounter Mastery inventory across outpatient, emergency/casualty, inpatient, community, and virtual encounter workflows.

Lovable reference inputs used in this pass:

- `docs/prototype/final/01_site_map.md`
- `docs/prototype/final/02_page_by_page_spec.md`
- `docs/prototype/final/04_api_surface_map.md`
- `docs/prototype/final/06_golden_paths.md` (Path C queue -> encounter -> close)
- `docs/IMPILO_TELEMEDICINE_REFERRAL_WORKFLOWS.md` was requested but not present in this workspace snapshot; telemedicine reference therefore uses current vNext teleconsult workflow docs and implementation.

Status taxonomy:

- `already represented in vNext`
- `represented but incomplete`
- `missing and should be implemented`
- `missing but requires architecture decision`
- `prototype-only / not applicable`
- `blocked by another service`

## Capability Inventory

| Capability | Lovable reference | vNext owner service | Current API | Current UI/BFF surface | Implementation status | Production blocker | Recommended next step |
|---|---|---|---|---|---|---|---|
| Appointment booking and scheduling | prototype scheduling routes and API map | `tuso-service` + `experience-bff` | `GET/POST /internal/v1/appointments*` -> TUSO booking APIs | `ui/experience` scheduling surfaces | already represented in vNext | none for core CRUD | add explicit encounter entry-point tagging from appointment to journey/encounter |
| Walk-in arrival and queue registration | golden path queue flow | `pct-service` + `experience-bff` queue controller | `POST /v1/journeys/start`, queue endpoints | `ui/experience/src/app/queue/*` | represented but incomplete | BFF queue has fallback/synthetic lanes in some paths | continue fail-close parity sweep on queue fallback paths |
| Triage assessment and acuity | triage panel patterns | `pct-service` | `POST /v1/journeys/{id}/triage` | `/queue/triage`, encounter triage sections | already represented in vNext | none for canonical triage write path | map triage category to encounter metadata consistently |
| Queue routing and service-point transitions | queue lane transitions | `pct-service` | queue item status + transfer APIs | `/queue`, `/queue/triage`, BFF queue routes | represented but incomplete | BFF still supports local fallback queue state in outage scenarios | move remaining fallback to explicit unavailable state |
| Encounter start metadata (context/entry/modality/care setting/priority) | encounter creation workflow | `pct-service` | `POST /v1/journeys/{id}/encounter/start` | BFF encounter create + `/ehr/[patientId]/encounters` form | already represented in vNext (this pass) | none | expand analytics and reporting over new metadata fields |
| Duplicate active encounter protection per journey | encounter lifecycle rigor | `pct-service` | encounter start service guard | encounter start via BFF/mobile | already represented in vNext (this pass) | none | add explicit typed domain exception code in future refinement |
| Encounter closure and discharge handoff | golden path close + discharge workflows | `pct-service` + `experience-bff` | complete/discharge APIs | encounter detail + discharge page | represented but incomplete | discharge pathway completeness depends on downstream service readiness | add explicit follow-up linkage payload in close/discharge contract |
| Emergency/casualty activation and resuscitation flow | emergency panel/workflow patterns | `pct-service` via BFF | emergency activation/phase/CPR APIs | clinical emergency pages through BFF | represented but incomplete | some BFF emergency routes include non-blocking fallback behavior | remove residual non-blocking synthetic response paths |
| Inpatient admission, transfer, discharge | inpatient and bed-management concepts | `inpatient-service` + `pct-service` + BFF | inpatient admissions/transfers/discharge APIs | care/emergency/inpatient BFF surfaces + bed pages | represented but incomplete | ward round and transfer-accept BFF routes explicitly 501 | implement canonical ward-round/transfer-accept backend wiring |
| Orders/results lifecycle | orders/results workflow | `oros-service` | `/v1/orders*`, `/v1/orders/{id}/results*` | orders/results pages and hooks | already represented in vNext | none | attach stronger encounter outcome linkage in UI copy and metrics |
| Pharmacy prescribing/dispensing | prescribing and medication management | `pharmacy-service` | `/v1/prescriptions*` | medications, pharmacy pages, mobile provider | already represented in vNext | none | add inpatient medication-administration ownership contract map |
| Clinical notes and document attachments | chart notes and attachment lifecycle | `document-service` + `pct-service` + BFF | document object APIs + PCT note refs | notes/documents pages and teleconsult attachment paths | represented but incomplete | note/attachment UX consistency across all encounter contexts | standardize note template usage by encounter context |
| Referral/consultation lifecycle | consult/referral workflow | `pct-service` + BFF + MVUMO | referral stage/submit/respond/complete APIs | consults, telemedicine pages | already represented in vNext | on-call/team/pool routing backend missing | keep explicit 501 and define future directory service ADR |
| Virtual encounter modality and session state | telemedicine workflow | `pct-service` + BFF | telehealth APIs + referral package APIs | telemedicine pages, mobile provider telemedicine | represented but incomplete | realtime chat/audio/video transport intentionally unavailable | keep explicit unavailable states until comms signaling service exists |
| Community outreach encounter context | community encounter patterns | `pct-service` + `experience-bff` | supported via encounter context + journey referral source | partial via queue/intake and encounter forms | missing and should be implemented | no dedicated community workflow board route | add bounded community encounter board + filters in experience UI |
| Care pathway and protocol linkage | pathway/protocol guidance | `pct-service` + `clinical-knowledge-platform-service` + `forms-service` + `rules-service` | CKP pathways/rules + forms APIs + PCT refs | clinical tools, guidance, encounter start form | represented but incomplete | no end-to-end PCT->CKP invocation orchestration contract | add BFF orchestration endpoint for pathway start from encounter |
| Follow-up and review appointment linkage | follow-up stage | `tuso-service` + `pct-service` + BFF | appointments APIs + discharge/encounter APIs | discharge and scheduling routes | represented but incomplete | explicit encounter follow-up linkage field absent | add follow-up action payload on encounter close/discharge |
| Clinical knowledge and decision support | knowledge search and rules | `guidance-service` + `rules-service` + `clinical-knowledge-platform-service` | guidance/rules/clinical APIs | guidance + clinical tools pages | already represented in vNext | none for baseline access | add encounter-context aware recommendations in BFF aggregation |
| SHR/FHIR longitudinal boundary | shared health record | `butano-service`, `butano-fhir`, `fhir-gateway-service` | FHIR and SHR APIs | interoperability/search/FHIR surfaces | already represented in vNext | none for controlled baseline | deepen writeback reconciliation coverage |
| Identity/provider/facility/terminology dependency | registry and trust references | `vito`, `varapi`, `tuso`, `zibo`, `tshepo` | internal registry/trust APIs | BFF orchestration and UI registry contexts | represented but incomplete | first green `registry-fullstack-runtime` and trust runtime gates pending | keep blockers explicit in readiness registers |
