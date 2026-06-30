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
