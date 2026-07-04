# Telemedicine, Virtual Hospitals, Clinical Groups & Session Modes — Operating Model and Capability Map

**Workstream**: Cursor implementation stream `cursor/e2e-telemedicine-virtual-hospitals`
**Base**: `claude/web-session-anchor-nnnkf6` @ `d44bb6022` (verified against `origin` 2026-07-04)
**Coordination**: Fable seven-pipeline board (`claude/impilo-vnext-coordination-75fzl0` @ `390908fe6`),
P5 telemedicine pipeline — this stream is the **peripheral, non-W0** layer.
**Status**: Wave 0 discovery — repo-grounded, no speculation.

---

## 1. Product doctrine (authoritative for this stream)

- **Telemedicine is a full clinical encounter**, equivalent to a physical encounter, delivered
  virtually or hybrid. Not a video-call feature.
- **Virtual hospitals are service architecture, not duplicate building architecture.** They are
  governed pools of clinical capability organised as virtual service-delivery institutions,
  staffed by real verified people, connected to (but distinct from) physical facilities.
- **Groups are for discovery and routing. Session modes are for clinical governance.**
- Identity classes must not collapse: Physical Facility ID ≠ Virtual Hospital ID ≠ Operating
  Authority ID ≠ Provider Home Affiliation ≠ Provider Virtual Privilege ≠ Encounter Delivery
  Context.

## 2. W0 lease boundary (no-touch, verified from anchor commit file lists)

The last 16 anchor commits (`91f6faeb2..d44bb6022`, W0 session-suite) own:

| Area | Paths (leased — inspected only, never modified by this stream) |
|---|---|
| RTC gateway | `services/rtc-gateway-service/**` |
| Live ingestion | `services/live-service/**` (`RtcSessionEventsConsumer`, media provider) |
| Khuluma RTC consumers | `services/khuluma-service/**` (`RtcCallEventsConsumer`, `RtcGatewayClient`, realtime) |
| Session template doctrine | `libs/session-templates/**`, `contracts/schemas/session-templates/**` |
| BFF session-template registry | `services/experience-bff/**/SessionTemplate*` |
| Web session suite | `ui/one-ui-shell/src/components/session/**`, `src/app/telemedicine/session/**`, `src/app/live/**` (replay), `src/components/live/LiveRoom.tsx`, `src/components/comms/CommsCallModal.tsx`, `src/hooks/useSessionRealtime.ts`, `useKhulumaRealtime.ts`, `useSessionTemplate.ts`, `src/lib/realtime/**`, `src/lib/session/**` |
| Mobile session package | `apps/mobile/packages/mobile-session/**`, consult rooms in citizen/provider apps |
| LiveKit helm/preview | `deploy/helm/impilo-vnext/**` LiveKit values/templates |

This stream **reads** the above (uses `useSessionTemplate`, links to `/telemedicine/session/[id]`)
and writes only in new, non-overlapping paths (see §6).

## 3. Repo-grounded capability map (classification)

Legend: ✅ real+wired · 🟡 partially wired · 🔷 UI missing · 🔶 backend missing ·
⚖️ policy missing · 🔒 W0-owned/no-touch · 🧩 stub/TODO (honest) · ⛔ unsafe to modify in parallel

### 3.1 Telemedicine encounter spine

| Capability | Class | Evidence |
|---|---|---|
| Teleconsult request lifecycle (DRAFT→SUBMITTED→ACCEPTED→…→COMPLETED) | ✅ | `pct-service` `TelemedicineOrchestrationService`; BFF `TeleconsultController` (`/internal/v1/teleconsult/**`, 21 endpoints) |
| Consent gate (MVUMO → media token blocked until granted) | ✅ | `TeleconsultController.consentBlocksMedia`; MVUMO `remote-sessions` API |
| Media token / session provisioning | ✅🔒 | rtc-gateway `/internal/v1/rtc/**`; template-driven fail-closed grants (W0) |
| Billing on completion (COSTA draft→approve→finalize) | ✅ | `TeleconsultController.triggerTeleconsultBilling`; PCT `TELECONSULT_COMPLETED` value trigger |
| Routing: PRACTITIONER/PROVIDER, TEAM/SPECIALTY_POOL, WORKSPACE, FACILITY_SERVICE | ✅ | BFF validation vs Varapi/Tuso (`TeleconsultController.java:855-935`) |
| Routing: ON_CALL / POOL / NATIONAL_POOL / UNIT | 🔶⚖️ | Honest 501 `ROUTING_TYPE_UNAVAILABLE`; PCT V020 has `routing_kind/routing_pool_id` columns + `POST /referrals/{id}/route` **not** proxied by BFF; no pool/on-call directory backend |
| Waiting room admission gate | 🟡 | Event-only (`waiting_room.entered` → `started`); no backend gate — flagged to W0 in board §7 liaison |
| Documentation into encounter | 🟡 | Thin FHIR `DiagnosticReport` writeback; no full encounter-note surface from teleconsult |
| Orders/prescribing from teleconsult | 🔶 | No orders path from teleconsult (P3 owns prescribe hook; R1 owns OROS contract) |
| Telehealth session records | ✅ | PCT `pct_telehealth_sessions`, `/v1/telehealth/**` |
| SLA/ops telemetry | ✅ | BFF `/internal/v1/teleconsult/ops/{sla,rtc-health,specialty-workbench}`; analytics `/internal/v1/telemedicine/sla` |
| Recording→learning artefact | 🧩🔒 | live-service `onRecordingAvailable` explicit W1 TODO |

### 3.2 Session modes / governance

| Capability | Class | Evidence |
|---|---|---|
| Session template doctrine-as-data | ✅🔒 | 5 modes: `TELEMEDICINE`, `MEETING`, `LIVE_EVENT`, `LEARNING_LIVE`, `LEARNING_RECORDING` (`libs/session-templates`) |
| BFF template registry endpoint | ✅🔒 | `GET /internal/v1/session-templates[/{mode}]` |
| Clinical session-mode taxonomy (10 modes: encounter/hybrid/advice/MDT/case-presentation/case-notes/audit/teaching/emergency/diagnostics) | 🔷⚖️ | No UI mapping and no dedicated templates for MDT, audit/mortality review, emergency advisory, diagnostics review — governance semantics undefined |
| Identity visibility / privacy badges | 🔷⚖️ | No privacy-level display model anywhere in UI |
| Case presentation (structured brief; pseudonymised/anonymised) | 🔶 | Absent (only a catalog label in `ClinicalReferences.tsx`) |
| Case notes access grants / minimum-necessary | ⚖️ | Tshepo/OPA policy exists for records generally; no session-scoped case-notes access model |
| Audit/mortality review sessions | 🔶 | Death pathway exists (PCT `DeathWorkflow`, `/work/clinical/death-cases`) but **no review-session capability**; Rito owns mortality review per guidance V010 comment |

### 3.3 Virtual hospitals

| Capability | Class | Evidence |
|---|---|---|
| Doctrine: `VIRTUAL_HOSPITAL` tier, `VIRTUAL_ONLY` deployment, `VIRTUAL_CARE_NETWORK` archetype | ✅ (contract only) | `contracts/facility-operating-model.ts`, `docs/architecture/facility-operating-model.md` |
| Varapi `VIRTUAL_PROVIDER_FOR` affiliation; practice context `VIRTUAL`; UI `VIRTUAL_POOL` work scope | ✅ (enums only) | `varapi AffiliationType`, `useWorkContext.ts` |
| Virtual hospital entity/registry/backing service | 🔶 | No `virtualHospital` code anywhere; Tuso `facility_type` is free string, no virtual seeds |
| Provider pools / rosters for virtual duty | 🔶 | Vashandi rosters are facility-scoped; Khuluma on-call is presence-only; no pool directory |
| Virtual wards / remote monitoring panels | 🔶 | No matches for virtual ward / monitoring plan |
| Diaspora/cross-border provider model | 🔶⚖️ | Zero matches for `diaspora`; jurisdiction/privilege gating absent |
| Provinces reference data | 🟡 | Tuso ZW geo has districts/wards via bulk import API (`/v1/internal/geo/zw/**`); provinces are codes on facilities, not a seeded table |

### 3.4 Groups & routing discovery

| Capability | Class | Evidence |
|---|---|---|
| Provider directory | ✅ | Varapi search → BFF `/internal/v1/registry/providers` + `/internal/v1/teleconsult/routing/providers?q=`; UI `/registry/providers` |
| Facility directory | ✅ | Tuso search → BFF `/internal/v1/facilities`, `/internal/v1/teleconsult/routing/facilities?q=`; UI `/registry/facilities`, `/discover/facilities` |
| Workspace directory | ✅ | Tuso workspaces → BFF `/internal/v1/teleconsult/routing/workspaces?facility_id=` (numeric); UI `/workspace` picker |
| Governed clinical groups | 🔶⚖️ | Absent. Social groups (`community-service` `social_*`) are wellness-domain — no consent/clinical-audit model, unsuitable without new governance |
| Request threads bound to referrals | 🟡 | Khuluma conversations support `links` (`REFERRAL`/`ENCOUNTER`/`CASE` object types) — real seam, no telemedicine UI |
| Thread→session conversion | 🟡🔒 | Khuluma meetings/calls exist; W0 owns call surface; no group-thread→LiveKit affordance |
| Pinning (people/groups/facilities/workspaces) | 🔷 | Shell app pinning only (`useShellStore.pinApp`); no clinical routing pinning |

### 3.5 Adjacent integrations (consumed, not modified)

| System | State | Notes |
|---|---|---|
| Khuluma | ✅ conversations/escalations/on-call presence; SLA policies (`V003__escalation_sla`) | Escalations are conversation-scoped, not queue/session-scoped |
| Nompilo | ✅ `POST /internal/v1/nompilo/context` route-bound guidance; `NompiloContextualGuidance` component | Guidance items seeded in guidance-service Flyway |
| Notification templates | ✅ appointments (`V009/V010`); session keys only in session-template doctrine (`rtc.telemedicine.*`) | No queue/session notification templates seeded |
| COSTA/MusheX/coverage | ✅ teleconsult completion billing; patient/facility category context | Double-bill hazard R3 untouched |
| PACS/imaging | ✅ order routing IMAGING→PACS; viewers | No teleconsult-imaging review session mode |
| Fundo | ✅ native LMS; learning-live/recording templates | Recording→artefact path is W1 TODO (W0) |

## 4. UI mechanics that shape this stream (verified)

- Pages under `ui/one-ui-shell/src/app/**` route via the filesystem; `routes.ts` /
  `app-registry.ts` are **not required** and are on the no-touch list. Route parity
  (`npm run test:routes`) verifies registered→filesystem only and **ignores** unregistered
  pages (precedent: `/groups`, `/groups/[id]`).
- Unregistered pages bypass registry guards — new pages therefore consume real
  role/facility context from stores/hooks directly and expose read/deep-link surfaces only.
- `npm run test:no-stubs` blocks `JSON.stringify` UI dumps, empty `onClick`, stub markers
  on changed pages. All new surfaces use real hooks or honest empty/deferred states.

## 5. What this stream builds (safe waves)

| Wave | Content | Risk |
|---|---|---|
| 0 | This capability map | docs-only |
| 1–2 | Config-as-data substrate under `ui/one-ui-shell/src/lib/telemedicine/`: virtual-hospital operating model (12 strategic institutions + provincial network), session-mode governance matrix (10 clinical modes mapped onto the 5 W0 templates with honest `templated`/`planned` states), clinical-group taxonomy + governance doctrine, privacy display levels, client-side pinning (discovery-only) | new files only |
| 1–2 | New routes `/work/telemedicine`, `/work/telemedicine/virtual-hospitals[/​[id]]` | new files only |
| 3 | `/work/telemedicine/routing` (real provider/facility/workspace search + virtual-hospital targets + pinning), `/work/telemedicine/groups` (governed taxonomy, creation honestly blocked) | new files only |
| 4–5 | `/work/telemedicine/session-modes` (real template registry + governance matrix + privacy badges + Khuluma notification key surfacing), `/work/telemedicine/operations` (real RTC health/ops SLA/analytics/specialty workbench + honest helpdesk gaps) | new files only |
| 6 | Page/lib tests, gates, this doc's journey register (§8), handoff notes (§7) | tests/docs |

Explicitly **not** done here: backend entities/migrations for virtual hospitals, provider
pools, clinical groups, case presentations, audit sessions (see handoffs §7); anything in §2.

## 6. Files owned by this stream

- `docs/architecture/telemedicine-virtual-hospitals-operating-model.md` (this file)
- `ui/one-ui-shell/src/lib/telemedicine/**` (new)
- `ui/one-ui-shell/src/hooks/queries/useSessionModes.ts`, `useTeleconsultRouting.ts` (new)
- `ui/one-ui-shell/src/components/telemedicine/PrivacyBadge.tsx` (new)
- `ui/one-ui-shell/src/app/work/telemedicine/**` (new)

## 7. Handoff notes (for Fable / W0 / backend workers)

1. **HO-1 → W0**: ON_CALL/POOL/NATIONAL_POOL/UNIT routing 501s are the seam where virtual
   hospitals attach. PCT already has `routing_kind`/`routing_pool_id` (V020) and
   `POST /v1/referrals/{id}/route` — but the BFF never proxies it. When W0 confirms the lease
   boundary, a peripheral worker can add the BFF proxy + a pool directory; UI target picker
   (Wave 3) already emits the target taxonomy.
2. **HO-2 → backend owner (new work, coordinator-gated)**: Virtual hospital substrate needs a
   sovereign home. Recommendation: Tuso-adjacent **virtual service-delivery entity** registry
   (NOT rows in `tuso.facility` masquerading as physical facilities), keyed by Virtual
   Hospital ID with operating-authority, regulatory-status, linked-facility role mappings
   (host/referral/staff-contributing/access-point), provider privileges, jurisdiction policy.
   The UI config substrate (`virtual-hospitals.ts`) is the seed spec for that model.
3. **HO-3 → session-template owner (W0)**: Clinical governance needs templates beyond the 5
   current modes: MDT/case-review, audit/mortality-review (no-name/no-blame flags), emergency
   advisory (rapid-join + strict audit), diagnostics review (imaging context), and
   provider-to-provider advice. `session-modes.ts` defines the required fields; adding JSON
   templates is a `libs/session-templates` change → W0 lease.
4. **HO-4 → policy owner (Tshepo/OPA — RED)**: identity-visibility levels (full /
   care-team-limited / pseudonymised / anonymised / emergency / restricted), case-notes access
   grants for MDT/teaching/audit participants, diaspora/cross-border privilege gating.
   The privacy display model in `privacy.ts` is presentational only until policy backs it.
5. **HO-5 → community/khuluma owner**: governed clinical groups should NOT reuse wellness
   social groups. Khuluma conversations + `links` (REFERRAL/CASE) are the nearest governed
   seam for request threads; a `ClinicalGroup` model (owner, eligibility, allowed request
   types, allowed session modes, identity levels, audit) is net-new backend.
6. **HO-6 → Fable register**: helm `webhookUrl` port 8196 vs rtc-gateway 8195 already flagged
   on the board (§7 liaison, risk #9); not touched here.

## 8. Acceptance journey register (Wave 6 keeps this honest)

Statuses: `implemented` · `partially implemented` · `existing-surfaced` · `blocked-W0-lease` ·
`blocked-missing-backend` · `blocked-policy-or-migration` · `deferred`

| # | Journey | Status | Grounding |
|---|---|---|---|
| 1 | Provincial VH entry → triage → teleconsult → billing → closeout | partially implemented | Teleconsult+billing spine ✅; provincial VH queue = config-only (HO-2); entry via routing page → `/telemedicine/new` |
| 2 | Facility escalation → National Telemedicine Hospital | partially implemented | Escalate = teleconsult request with TEAM/SPECIALTY_POOL routing (real); national pool routing 501 (HO-1) |
| 3 | District → Virtual Specialist Hospital provider-to-provider | partially implemented | Referral + respond-structured real; advice session mode not templated (HO-3) |
| 4 | ANC → Virtual Maternal/Newborn follow-up + reminder | blocked-missing-backend | No follow-up plan/monitoring backend; appointment reminders exist (notification V009/V010) |
| 5 | Sensitive mental-health follow-up, restricted access | blocked-policy-or-migration | HO-4 |
| 6 | Chronic disease observation → abnormal triggers queue | blocked-missing-backend | No remote-monitoring observation intake |
| 7 | Imaging → Virtual Radiology Review Centre → write-back | partially implemented | Imaging lifecycle + viewers ✅ (P6); review-centre session mode absent (HO-3) |
| 8 | Ward/ICU deterioration → Virtual ICU Support Desk | blocked-missing-backend | No pool routing (HO-1) + no VH substrate (HO-2) |
| 9 | Emergency request → Virtual Emergency Advisory Unit priority routing | blocked-missing-backend | Same seams; emergency template absent (HO-3) |
| 10 | Rehab virtual follow-up → care plan adjusted | blocked-missing-backend | No tele-follow-up plan model |
| 11 | CHW home observation → Community Follow-up Unit review | blocked-missing-backend | Vashandi CHW context exists; no monitoring intake |
| 12 | Learning/supervision participation w/ consent + Fundo artefact | blocked-W0-lease | Recording→artefact is W0 W1 TODO; LEARNING_LIVE template exists |
| 13 | Failed call → helpdesk diagnostics → retry/fallback | partially implemented | RTC health real; per-session failure diagnostics not exposed (W0 webhook events exist; no query API) |
| 14 | Pin specialist group, consult from active encounter | partially implemented | Pinning (Wave 3, client-side) + real teleconsult create; groups config-only |
| 15 | Facility → workspace → group queue selection | partially implemented | Facility/workspace real; group queue backend absent |
| 16 | Case presentation to group w/o full identity | blocked-missing-backend | Case presentation model absent (HO-5, HO-4) |
| 17 | Thread → LiveKit provider-to-provider session | blocked-W0-lease | Khuluma calls owned by W0 surface |
| 18 | Encounter Menu documents like physical encounter | partially implemented | Links to real EHR surfaces; orders/prescribe from teleconsult missing (P3/R1) |
| 19 | MDT in Case Presentation Mode, pseudonymised | blocked-missing-backend | HO-3 + HO-4 + case model |
| 20 | Care-team member opens Case Notes Review Mode | blocked-policy-or-migration | HO-4 |
| 21 | Learner sees only permitted de-identified data | blocked-policy-or-migration | HO-4 |
| 22 | Maternal death audit, no-name/no-blame + action tracking | blocked-missing-backend | Audit session model absent; death pathway exists separately |
| 23 | Radiology review with de-identification checks | blocked-missing-backend | DICOM de-id checks absent |
| 24 | Emergency Advisory Mode rapid access + strict audit | blocked-missing-backend | HO-3 |
| 25 | Governance blocks unauthorised clinical group creation | implemented (by honest absence) | Groups page states creation is governance-gated and disabled until backend exists — fail-closed truthful UX |
| 26 | Diaspora member gated by privilege/jurisdiction | blocked-policy-or-migration | HO-4; capability model documented in substrate |
| 27 | Audit log records identity exposure/case-notes access/join | partially implemented | Session joins audited via rtc events (W0); identity-exposure audit absent (HO-4) |
