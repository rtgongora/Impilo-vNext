# Patient Encounter Structured Forms Engine — Design of Record

Companion to [`audit.md`](audit.md). This is the authoritative design for the engine. Ownership, contracts,
and the GAP-4 resolution below are binding; deviations must update this file.

## 1. Ownership (no new service, reuse-first)

```
DEFINITIONS  (SoR = forms-service)             RESPONSES + RESOLVE + EXTRACT  (SoR = pct-service)
  fs_form_schemas  (EXTENDED clinical meta)      pct_form_response        (version-locked answers JSONB)
  fs_form_schema_versions (immutable DAK JSON)   pct_form_extracted_resource (provenance → clinical res.)
  GET /internal/v1/forms?careSetting&…(catalog)  pct_form_signature / pct_form_amendment (sign/countersign/amend)
                                                 pct_form_resolver_decisions (audit, mirrors V015)
                                                 FormScopeEngine (pure) + FormResolverService (I/O)
                                                 FormResponseService (lifecycle) + FormExtractionService
             experience-bff proxies both; one-ui-shell + provider-app render the resolved forms.
```

**Boundary contract:** forms-service **must-not-own-form-responses / clinical-encounters**; PCT
**must-not-own-form-definitions** (only version-locks against the immutable `form_schema_version_id` token).
Encoded in `docs/registry/services-registry.yaml`.

## 2. Canonical form-definition model

The wire contract is the existing frontend `ClinicalFormDefinition`
(`ui/one-ui-shell/src/lib/clinical-forms/types.ts`) — WHO-DAK / FHIR-SDC aligned. forms-service persists it:
the full definition JSON lives immutably in `fs_form_schema_versions.schema_json`; queryable applicability +
governance columns are lifted onto `fs_form_schemas` (V002) so the resolver can filter server-side:

`form_type, care_setting[], care_stage[], specialty[], encounter_contexts[], required_workflow,
obligation_default (MANDATORY|RECOMMENDED|OPTIONAL), permitted_cadres[], min/max_age_months,
sex_applicability, require_pregnant, programme_applicability[], facility_levels[], terminology_bindings,
resource_mappings, indicators, requires_countersign, offline_capable, sensitivity, governance(author,
reviewer, approver, effective_date, review_date, source_guideline, change_reason)`.

Governance lifecycle: `DRAFT → REVIEW → APPROVED → ACTIVE → RETIRED` (reuses forms-service publish/retire).

## 3. Resolver — FormScopeEngine (pure) + FormResolverService (GAP-4)

**FormScopeEngine** (`pct/core/forms/FormScopeEngine.java`) mirrors `CadreEngine`: static, deterministic,
no I/O, never mutates CadreEngine. It **composes** a resolved `CadreDecision`:

`FormResolutionRequest{ cadreDecision, careSetting, careStage, specialty, encounterContext, acuity,
patientFacts, providerScope, catalog[] } → FormResolution{ mandatory[], recommended[], optional[],
prohibited[+reason], countersignRequired[], auditRef }`.

**GAP-4 unification rule** (form scope = cadre workflows ∩ form catalog):
1. **Patient applicability** — catalog DAK metadata (age band / sex / pregnant / programmes) using the exact
   `FieldVisibilityRule` semantics from `types.ts`. Non-applicable → dropped entirely.
2. **Setting/stage applicability** — care_setting / care_stage / specialty / encounter_context match.
3. **Cadre-workflow gate** — each catalog entry's `required_workflow` vs `cadreDecision.permittedWorkflows()`:
   - permitted & not escalated → completable (bucket by `obligation_default`).
   - permitted & in `escalation.supervisorRequiredFor` → **COUNTERSIGN_REQUIRED**.
   - not permitted → **PROHIBITED** (emitted with reason; doctrine "no fake completions" — cockpit greys it).
   - emergency widening (Law 1) flows through from CadreDecision automatically.

**FormResolverService** (`pct/core/forms/FormResolverService.java`) mirrors `CadreEngineService`: resolve
CadreDecision → `VitoIntegration` patient facts (de-PII'd) → `FormsCatalogIntegration` catalog slice
(degrade-gracefully) → pure engine → stamp auditRef, persist `pct_form_resolver_decisions`, outbox
`FORM_RESOLUTION_RESOLVED`. Fallback: if forms-service V002 lags, read applicability from the immutable
`schema_json`.

## 4. Response lifecycle

```
DRAFT ─edit→ IN_PROGRESS ─submit(validate+AUTHOR sign)→ SUBMITTED ─amend(reason)→ AMENDED
   └─void(reason)→ VOIDED                              (corrected response) → old = SUPERSEDED
```
- **Version-lock at DRAFT:** persist `form_key/form_schema_id/form_version/form_schema_version_id`; immutable
  for the response's life — validates & extracts against the locked snapshot even if a new version publishes.
- **submit:** validate answers vs locked schema, AUTHOR signature row, then extract (or defer if countersign).
- **countersign gate:** COUNTERSIGN_REQUIRED forms defer extraction until a satisfying-cadre countersignature.
- **amend:** append-only `pct_form_amendment` (prior+new answers+reason); re-extract only changed link_ids.
- **void:** soft, reason required; already-extracted resources emit downstream `entered-in-error` (no delete).

## 5. Extraction → reusable clinical resources

Driven by the locked definition's `resource_mappings` + per-field `terminologyBinding`/`fhir`:

| Answer semantics | Resource | Sink (existing) | route_target | commit |
|---|---|---|---|---|
| vitals / coded observations | Observation | `ButanoIntegration` + FHIR gateway | BUTANO | async (outbox/retry) |
| diagnosis / problem fields | Condition/Problem | `ProblemService.add(Map)` | PCT_PROBLEM | in-tx |
| care-plan / goal fields | CarePlan+Goal | `CarePlanService.create(Map)` | PCT_CARE_PLAN | in-tx |
| order fields (lab/imaging/referral) | ServiceRequest | `OrosIntegration.submitOrder` | OROS | async |

External sinks are eventually-consistent (PENDING/FAILED + scheduled retry mirroring `OutboxPublisher`);
they **never roll back submit**. Idempotency on `(response_id, source_link_ids)`. Provenance: every
`pct_form_extracted_resource` row ties resource → response → `form_schema_version_id` → source link_ids.
QuestionnaireResponse projection posted to BUTANO alongside `butano_encounter_ref` (backend
`QuestionnaireResponseMapper` ported from `to-questionnaire-response.ts`, guarded by a golden fixture).

## 6. Policy

`infra/opa/impilo/pct_forms.rego` (resource `pct-form`; actions view/start/edit/submit/amend/countersign/
void/admin; cadre + licence + facility-scope + purpose-of-use; trainee→countersign; suspended/expired→block;
break-glass audited; sensitive-form restriction) + `pct_forms_test.rego`, registered in
`policy_decision.rego`. Java `PolicyEngine` `policy_rules` seed is authoritative; OPA runs shadow. Writes
enforced at Envoy ext_authz (seed `clinical-form-response-*` rule, mirroring V018 `clinical-cadre-decision-*`).

## 7. Contracts

**PCT (internal):** `POST /v1/forms/resolve`; `POST /v1/forms/responses` (DRAFT),
`GET /v1/forms/responses/{id}`, `PATCH /v1/forms/responses/{id}/answers`,
`POST /v1/forms/responses/{id}/{submit|countersign|amend|void}`, `GET /v1/encounters/{id}/form-responses`.
**forms-service:** existing `/internal/v1/forms` + `GET /internal/v1/forms?careSetting&careStage&specialty&context` (catalog).
**BFF:** `GET /internal/v1/encounters/{id}/forms/resolve|available`,
`POST …/forms/{formId}/responses/draft`, `PUT /internal/v1/form-responses/{id}`,
`POST /internal/v1/form-responses/{id}/{submit|countersign|amend|void}`,
`GET /internal/v1/encounters/{id}/form-responses`,
`GET /internal/v1/patients/{id}/structured-clinical-timeline`; wire `/internal/v1/extensions/forms*` →
forms-service; `MobileFormController` persists via PCT.

## 8. Alignment checklist (CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST)

1. Core transaction: **Clinical encounter / documentation**. 2. Lifecycle: capture → validate → extract →
close. 3. Actor: provider (role/cadre). 4. Person journey: assessment/consultation/admission/discharge.
5. Provider journey: document care. 7. SoR plane: clinical. 8. Data owner: forms-service (defs) + PCT
(responses). 9. State: creates form_response (DRAFT→…→SUBMITTED/AMENDED/VOIDED). 10. Events:
`FORM_RESOLUTION_RESOLVED`, `pct.form.response.submitted`, `pct.form.extracted`. 11. Permission: OPA
`pct-form` + PolicyEngine; consent via existing care-relationship guard. 12. Audit: `pct_form_resolver_
decisions` + signatures + amendments + outbox. 13. UI: encounter page forms panel + renderer (web + mobile).
14. BFF: EncounterFormsController + extensions/forms. 15. Tests: pure engine, lifecycle, extraction, policy,
BFF, web, mobile. 16. Analytics: indicator mappings + extracted Observations. 17. Offline: `offline_capable`
+ mobile draft/sync. 18. Failure paths: degrade-gracefully sinks, version-lock, prohibited-block. 20. No
duplicate truth: composes CadreEngine, extends fs_form_schemas, promotes DAK model. 23. Nompilo: post-submit
guidance/handoff. 24. Accessibility: renderer a11y. 26. Rito feedback hook on adverse-event answers.
