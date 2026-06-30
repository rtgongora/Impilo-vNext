# WS#7 — Daidzai Emergency & Disaster Suite — Discovery & Ownership

> Canonical name **Daidzai** (the older spec draft says "Daidzo" — do not use). Daidzai is the
> emergency / disaster / public-health response **command** brain. It orchestrates the existing
> systems-of-record (Nhume=dispatch, Ndila=maps, PCT=clinical encounter, Tuso/Indawo=facility,
> Vito=identity, Khuluma=comms, Rito=after-action, etc.) — it does **not** duplicate any of them.

Discovery date: 2026-06-30. Branch: `feature/daidzai-emergency-suite` (from `origin/claude/crazy-merkle-3ad1a1` tip `2932eff`).

## Net-new confirmation

`grep -ril -E "emergency_request|emergency_incident|\bSOS\b|incident_command|disaster_incident" services/*/src`
returns only wellness "emergency contacts" surfaces (simba/wellness/experience-bff) and a card-print
`emergency-capsule.json` template — **no emergency/SOS/incident-command/dispatch-orchestration owner exists**.
`ls services/` shows no `daidzai-service`. Therefore Daidzai is **net-new** with no existing owner to extend.

Port: **8392** chosen — verified free in `docs/runbooks/port-allocation.md` (8390 Khuluma, 8391 Rito are the
highest documented) and via `grep port: services/*/src/main/resources/application.yml` (8390/8391/8399 used;
8392–8398 free).

## Ownership table (capability | existing owner found | extend or build | notes)

| Capability | Existing owner found (cited) | Extend / Build / Hook | Notes |
|---|---|---|---|
| Emergency request / SOS incident creation | NONE (grep above) | **BUILD (daidzai)** | `emergency_request` + `emergency_incident` are daidzai-owned truth |
| Triage severity classification | booking-service has `TriageStatus` enum (booking domain only) | **BUILD (daidzai)** rules-based | distinct from booking triage; emergency triage category |
| Maps / routing / nearest-service | `ndila-service` (8155) | **HOOK** | Daidzai stores geo + calls Ndila; never a map engine |
| Dispatch / fleet / delivery execution | `nhume-service` (8210) | **HOOK** | Daidzai creates dispatch *need* + tracks mission status; Nhume executes |
| Emergency clinical encounter / triage / handoff record | `pct-service` (8088) | **HOOK** | Prehospital record references PCT encounter; Butano owns record truth |
| FHIR record truth | `butano-service` (8090) | reuse via PCT | no clinical truth duplicated in daidzai |
| Facility / site readiness | `tuso-service` (8084), `indawo-service` (8150) | **HOOK** | facility-readiness query for resource matching |
| Identity incl. temporary/unknown/anonymous subject | `vito-service` (8082) | **HOOK** | daidzai stores subject ref + identity-mode; Vito owns identity |
| Responder scope / credential | `varapi-service` (8083) | hook (depth-deferred) | responder role gating at policy layer for MVP |
| Responder roster | `vashandi-workforce-service` (8167) | hook (deferred) | mission assignment depth deferred |
| Break-glass / access / consent | `tshepo-*` + OPA | **policy (BUILD rego + V028 seeds)** | emergency-access, citizen-SOS-allowed, sensitive-type masking |
| Comms (SOS ack, alerts) | `khuluma-service` (8390) | **HOOK** | request-only; Khuluma delivers |
| Citizen guidance (what-to-do) | `guidance-service` (8260) + NompiloContextualGuidance | **HOOK + V009 seeds** | domain='daidzai' seeds |
| Emergency stock / kits / PPE (Dura) | `inventory-service` | hook (deferred) | resource_request references; execution deferred |
| Ambulance / equipment readiness | `asset-registry-service` (8310) | hook (deferred) | readiness query deferred |
| Blood (Madi) | `madi-service` (8300) | hook (deferred) | resource_request type BLOOD; execution deferred |
| Emergency orders | `oros-service` (8089) | hook (deferred) | order issuance deferred to Oros |
| After-action / quality review | `rito-quality-safety-service` (8391) | **HOOK** | disaster closure routes to Rito case |
| Death / CRVS | `ubomi-service` (8087) | hook (deferred) | fatality reporting deferred |
| Terminology | `zibo-service` (8085) | hook (deferred) | category code systems deferred |
| External providers | `msika-service` (8086) | hook (deferred) | private ambulance integration deferred |
| Protocols / training (outbreak templates) | `fundo` / clinical-knowledge-platform (`V005__ed_emergency_pathways.sql`) | hook (deferred) | outbreak protocol templates depth-deferred |

## New-service justification

No existing service owns emergency-response *command/orchestration*. The closest neighbours each own one
slice (Nhume=dispatch, Ndila=maps, PCT=care, Rito=after-action) and the doctrine forbids duplicating a
system-of-record. Daidzai is the missing orchestration brain that holds the `emergency_incident` aggregate
and coordinates those owners. It is therefore a legitimate net-new service, plane = **experience** (command/
orchestration over sovereign owners), schema `daidzai`, tables prefixed `dai_*`.

## Skeleton source

Copied EXACTLY from `services/rito-quality-safety-service`: pom.xml (parent, shared-kernel/shared-core/
tech-companion deps), application.yml, V001 Flyway baseline, package `zw.gov.mohcc.impilo.daidzai`,
`dai_outbox` + CompanionOutboxPublisher pattern, TrustContextFilter SecurityConfig, SecurityBaselineConfig,
RestControllerAdvice, MockMvc/H2 `@SpringBootTest` test setup, BFF RestTemplate client + ServiceEndpoints.

## Deferred owner-routed seams (WS#7 authoritative register — none faked)

Built REAL: emergency spine (SOS intake → triage → incident → dispatch-need + mission
timeline → PCT handoff link → resource-request → outbox) + disaster spine (declare DISASTER/MCI
→ affected sites → resource-request → command dashboard → close→Rito after-action) + OPA policy +
tshepo V028 + guidance V009 + web (9 routes) + mobile slices. Everything below is classified, not faked.

| Area | Classification | Seam/owner | Built surface | Deferred depth | No-fake guarantee |
|---|---|---|---|---|---|
| Maps / routing / nearest-service | owner-routed hook | Ndila (8155) | geo stored on request/incident; `ndila_route_ref` field; /emergency/services explains the seam | actual routing/tiles call | no fake map widget; page states Ndila owns routing |
| Dispatch execution | owner-routed hook | Nhume (8210) | dispatch *need* created + mission timeline tracked; `nhume_mission_ref`; OwnerRoutedGateway logs intent | live HTTP dispatch call + ref return | no fake ambulance tracker; mission events are real records, status only |
| Emergency clinical encounter / record | owner-routed hook | PCT (8088) / Butano (8090) | handoff endpoint stores `pct_encounter_ref`; status→HANDOVER | live PCT encounter create | clinical record never duplicated in daidzai |
| Facility readiness matching | substrate exists + wiring-deferred | Tuso (8084) / Indawo (8150) | `facility_id` + affected-site Indawo refs | live readiness query | no fabricated availability numbers |
| Comms delivery (SOS ack / alerts) | owner-routed hook | Khuluma (8390) | requestNotification intent (logged) on SOS + disaster activation | live Khuluma send | request-only; no fake "delivered" status |
| After-action review | owner-routed hook | Rito (8391) | disaster close stores `rito_case_ref` | live Rito case create | review record owned by Rito, link only |
| Emergency stock / kits / PPE | owner-routed hook | inventory-service (Dura) | resource_request type KIT_PPE, owner=DURA | live stock reservation | no fake stock levels |
| Blood | owner-routed hook | Madi (8300) | resource_request type BLOOD, owner=MADI | live Madi crossmatch/issue | no fake blood availability |
| Ambulance / equipment readiness | owner-routed hook | asset-registry (8310) | resource_request type EQUIPMENT/AMBULANCE | live readiness | no fake fleet status |
| Emergency orders | owner-routed hook | OROS (8089) | (resource_request can model the need) | live order issuance | orders never issued from daidzai |
| Death / CRVS | not present + explicitly deferred | Ubomi (8087) | triage BLACK category recognised | fatality reporting workflow | no fake death record |
| Responder roster / scope | substrate exists + wiring-deferred | Vashandi (8167) / Varapi (8083) | actor captured on mission events; role gating at PDP | live roster assignment | no fake responder assignment |
| Outbreak protocol templates (cholera/measles/AFP/VHF/…) | partially built + depth-deferred | Fundo / clinical-knowledge-platform | incident_type=OUTBREAK supported on the aggregate | the ~10 disease protocol templates | no fake protocol content |
| MCI triage-tag depth | partially built + depth-deferred | daidzai | rules-based RED/ORANGE/YELLOW/GREEN/BLACK triage | full START/SALT MCI tag algorithm | classifier is real + tested, just not full MCI depth |
| Full incident-command role matrix | policy-config deferred | daidzai PDP | command/dispatcher/responder roles in rego + V028 | full ICS role hierarchy | roles enforced, hierarchy depth deferred |
| Protocol-versioning engine | not present + explicitly deferred | Forms/Rules | — | versioned protocol engine | none built, no fake |
| Offline-first depth | substrate exists + wiring-deferred | tshepo-offline / mobile | mobile slices written | offline queue + sync for SOS | not claimed; mobile not test-run here |
| External adapters (DHIS2/IDSR/LIMS/SMS/USSD/IVR) | not present + explicitly deferred | integration-hub | channel field (USSD/IVR) on request | adapter implementations | none built, no fake |
| Analytics dashboards depth | not present + explicitly deferred | reporting (8176) | outbox events emitted for downstream | analytics surfaces | no fake charts |
| @PreAuthorize method-security | policy-config deferred | tshepo PDP + Envoy ext_authz | authz enforced at PDP (rego + V028) consistent with rito/madi | per-method annotations | enforcement is real at the gateway/PDP layer |
