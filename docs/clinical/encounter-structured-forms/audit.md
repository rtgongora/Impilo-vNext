# Patient Encounter Structured Data Entry Forms — Repository Audit

**Date:** 2026-07-01
**Doctrine:** Every patient encounter must use structured data-entry forms selected and rendered by patient,
provider, role/cadre, scope of practice, specialty/discipline, facility context, care setting, visit type,
acuity, and stage of care. Free text may supplement; clinical truth is captured as structured, coded,
reusable data.

This audit was performed **before** any code, per the reuse-first / no-duplicate rule. It establishes what
already exists so the engine **extends** rather than rebuilds.

## Summary — three disconnected partial pieces + full substrate

The estate already contains three partial pieces of a forms engine plus every integration it needs. The work
is connective tissue, not greenfield.

| # | Asset | Location | State | Verdict |
|---|-------|----------|-------|---------|
| 1 | `forms-service` (8240) — generic form-definition registry | `services/forms-service` | `fs_form_schemas` + `fs_form_schema_versions` (immutable JSON snapshots) + outbox + idempotency; controller `/internal/v1/forms`. No clinical metadata, no responses. | **EXTEND** — owns DEFINITIONS |
| 2 | `clinical-forms` DAK model (frontend) | `ui/one-ui-shell/src/lib/clinical-forms/` | Rich WHO-DAK / FHIR-SDC `ClinicalFormDefinition` (`types.ts`) + `DakFormRenderer` + visibility/validate/FHIR-QR mapping helpers + antenatal exemplar. No backend; not server-driven. | **PROMOTE** to canonical wire model |
| 3 | PCT encounters + `CadreEngine` resolver | `services/pct-service` | Mature encounters; pure deterministic cadre resolver (GAP-4 "unify-with-scope-rules pending"); bespoke structured entities (ED triage/trauma, ward round, EWS, MAR, procedure checklist); Butano/Oros/Vito integration; Problem/CarePlan/ClinicalNote services; outbox (V022 top). | **EXTEND** — owns RESPONSES + resolver + extraction |
| 4 | Substrate | across services | Zibo terminology, Butano/FHIR gateway, OROS orders, Dura stock (`inventory-service /v1/dura/pct/*`), Rito, Khuluma, Nompilo/Guidance, Tshepo PolicyEngine + 35 OPA rego policies, Vito/Varapi/Vashandi, event-outbox. | **REUSE** |
| 5 | BFF (8160) | `services/experience-bff` | `EncounterController` (+ `POST /internal/v1/encounters/cadre-decision`); `MobileFormController` (`/internal/v1/mobile/provider/forms/*` — **submissions NOT persisted, client-side UUID = HIGH gap**); `FormsServiceClient`, `PctServiceClient`. Web `useForms.ts` hooks point at **unwired** `/internal/v1/extensions/forms`. | **EXTEND** |

## Key findings

### 1. `forms-service` = definition registry, not encounter-aware
`fs_form_schemas` (form_key, name, current_version, status, tenant/pod) + `fs_form_schema_versions`
(**immutable `schema_json`** per version — this is exactly the "preserve the exact form version" requirement).
No response persistence, no clinical metadata, no encounter binding. Registry SoR for "Forms canonical
records"; forbidden list only identity-source and enterprise-ledgering.

### 2. Frontend already has the WHO-DAK model (the canonical shape)
`ui/one-ui-shell/src/lib/clinical-forms/types.ts` defines `ClinicalFormDefinition` with age/sex/pregnancy/
programme/condition `FieldVisibilityRule`, `terminologyBinding`, FHIR `FhirFieldLink`, `IndicatorLink`,
`DecisionSupportHookRef`, `offlineCapable`, and `audit.sensitivity`. Companions: `DakFormRenderer.tsx`,
`evaluate-visibility.ts`, `validate-form.ts`, `fhir-questionnaire-mapping/to-questionnaire-response.ts`,
`terminology-bindings/`, `indicator-mapping/`, `decision-support-hooks/`, and a **complete antenatal
exemplar** (`clinical-form-definitions/antenatal-contact-1-exemplar.ts`). It has no backend and is not driven
by the server today.

### 3. PCT `CadreEngine` is the resolver foundation (GAP-4)
`services/pct-service/.../core/cadre/CadreEngine.java` is a pure, deterministic resolver: cadre-family +
context + acuity + access-state → permitted workflows + cockpit tabs/actions (enabled + requiresStepUp) +
escalation (supervisorRequiredFor, break-glass). `CadreEngineService` wraps it (stamps auditRef, persists
`pct_cadre_decisions` V015, emits outbox). The registry flags **GAP-4 "unify-with-scope-rules pending"** — the
form resolver discharges this by **composing** CadreEngine (form scope = permittedWorkflows ∩ catalog
requiredWorkflow; escalation → countersign), never mutating its purity.

### 4. `EncounterEntity` carries every resolver dimension
`pct_encounters`: encounterType, encounterContext, modality, virtualMode, careSetting, priority,
triageCategory, pathwayRef, protocolRef, subjectCpid, facilityId, assignedProviderId, journeyId,
butanoEncounterRef. Note PK is `Long id`; there is also `encounterRef UUID`. `pct_problems`/`pct_care_plans`
(V017) store `encounter_id VARCHAR(64)` — the convention new form tables follow.

### 5. Structured forms already exist as bespoke entities (the anti-pattern to resolve)
ED triage (`EdTriageAssessmentEntity`, JSONB vitals/discriminators/danger_signs), trauma survey (JSONB
checklist), ward round, EWS/NEWS2, MAR, fluid balance, procedure checklists — all real and persisted, but
**hard-coded per domain**. There is **no generic engine**. Decision: leave bespoke entities intact (they
work); the generic engine sits alongside and may absorb them later (deferred).

### 6. No general encounter form-RESPONSE engine
V020 added a narrow `structured_response JSONB` on `pct_referrals` for telemedicine only. There is no
server-persisted, version-locked, encounter-bound response store, no extraction to clinical resources. **This
is the gap the engine fills.**

### 7. Substrate is mature and reusable
- **Zibo** terminology: `ZiboServiceClient` (validate/coding, map, value sets).
- **Butano/FHIR**: `FhirGatewayServiceClient.createResource`, `ButanoIntegration`; FHIR gateway allowlists
  `QuestionnaireResponse` but no canonical Questionnaire SoR yet.
- **OROS** orders: `POST /v1/fhir/ServiceRequest`; PCT `OrosIntegration.submitOrder(journeyId, payload)`.
- **Dura** stock: `inventory-service` `/v1/dura/pct/{availability,reserve,consume}`.
- **Rito**: `RitoServiceClient` safety-incident/quality-signal.
- **Khuluma**: `POST /internal/v1/khuluma/delivery/dispatch`.
- **Nompilo**: `GuidanceServiceClient.ask` + `nompilo/handoffs`.
- **Tshepo**: Java `PolicyEngine` (authoritative) + OPA shadow (`OpaDecisionClient`), 35 rego policies in
  `infra/opa/impilo/` each with a `_test.rego`.
- **Vito/Varapi/Vashandi** context clients; **event-outbox** pattern per service (`OutboxPublisher`).

## Route / migration conventions to follow
- PCT public routes `/v1/...`; internal `/internal/v1/...`. Reads use `TrustContextHolder.require()` +
  `ClinicalVisibilityGuard`; `ApiResponse.ok(..., correlationId)` envelope.
- PCT migrations: unqualified `pct_*` (V015/V017), app-assigned UUID PKs, `TIMESTAMPTZ DEFAULT now()`, JSONB.
  Highest PCT = V022; highest inpatient = V018; forms-service = V001.
- BFF: `@RequestMapping("/internal/v1/{domain}")`, `data`+`meta` envelope, `CompanionHeaders`,
  `Idempotency-Key` on mutations; `{Service}ServiceClient` RestTemplate wrappers.
- Web: Next.js app-router `src/app/.../page.tsx`; TanStack hooks `use{Resource}`; `api-client.ts` injects
  Health-OS v1.2 trust headers.

## Ownership decision (validated against registry guardrails)
- **forms-service = SoR for form DEFINITIONS + versions + governance** (extend `fs_form_schemas`; keep DAK
  JSON in the immutable version snapshot). It lacks the clinical trust/visibility/care-relationship guard
  stack by design → it must not hold responses.
- **pct-service = SoR for encounter-bound form RESPONSES + resolver + extraction** (encounter aggregate,
  problems, care plans, cadre decisions already live here; responses are encounter clinical truth).
- **No new service**; **all-in-PCT** rejected (duplicates forms-service versioning/governance);
  **new-service** rejected (splits the encounter aggregate, duplicates the trust stack).

See `design.md` for the full architecture and `deferred-seams.md` for honest deferrals.
