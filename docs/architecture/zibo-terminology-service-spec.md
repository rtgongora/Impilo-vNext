# ZIBO — specification for a functional national terminology service

**Date:** 2026-08-08 · **Status:** DRAFT for review · **Measured in:** `impilo-full-preview`

## Why this exists

P3 of the SHR programme (land AllergyIntolerance, Immunization and CarePlan in BUTANO) was
sequenced behind "fix capture first, so allergy and immunisation land coded". That instruction
cannot be executed: **there is no vaccine vocabulary in ZIBO, and no allergen vocabulary beyond 20
medicines.** The blocker is not the capture form. It is that the national terminology service holds
seed data and lacks the operations a picker needs.

This document measures what ZIBO is today and specifies what it must become. Both halves matter:
the gap is only actionable if the starting point is honest.

---

# Part 1 — What ZIBO is today, measured

## 1.1 Content: 44 artifacts, ~254 concepts

| CodeSystem | concepts | a real one holds | verdict |
|---|---|---|---|
| `who-atc-medicines-zw-starter` | **20** | ATC ≈ 6,500 | seed |
| `EDLIZMedications` | **20** | EDLIZ ≈ 400+ | seed |
| `ICD10ZW` | **15** | ICD-10 ≈ 14,000 | seed |
| `LabTestsZW` | **18** | LOINC ≈ 100,000 | seed |
| `ProviderSpecialtiesZW` | 18 | — | plausible |
| `impilo-clinical-specialty` | 31 (v1.1.0) | — | plausible |
| `ImpiloSurgicalProcedures` | 10 | — | seed |
| `ICD10ZW`, `ImpiloHivArtRegimen`, `ImpiloTbTreatmentRegimen`, 8 emergency/programme systems | 4–14 each | — | closed operational vocabularies, plausible |

The 20 ATC concepts are Amoxicillin, Paracetamol, Metformin, Amlodipine, Enalapril… The artifact is
honestly named `-starter`. Nothing is mislabelled; it is simply not yet national terminology.

**Absent entirely:** vaccines, allergens (food / environmental / substance), reaction
manifestations, and any general clinical-finding vocabulary beyond 15 ICD-10 codes.

## 1.2 ValueSets: 13 of 15 are empty shells

Only three ValueSets enumerate concepts — `EncounterTypesZW` (7), `ImpiloSurgicalProceduresVS` (10),
`WorkspaceTypesZW` (13). The other twelve, including `ImpiloHivArtRegimenVS`,
`ImpiloTbTreatmentRegimenVS` and every emergency ValueSet, contain **zero**. They reference their
CodeSystem through `compose.include` without enumerating — valid FHIR, and unusable here, because
**ZIBO has no `$expand`**. A consumer resolving one of those canonical URLs receives a ValueSet with
nothing in it and cannot tell that from an empty vocabulary.

## 1.3 API surface

| Route | Purpose |
|---|---|
| `POST /v1/validate/coding` · `/resource` · `/job` | validate a coding or a resource |
| `GET /v1/artifacts/resolve` | resolve a canonical URL to its artifact JSON |
| `GET /v1/artifacts/observation-definitions` | ObservationDefinition lookup (CKP) |
| `POST /v1/map` · `GET /v1/map/sources` · `POST /v1/map/rebuild` | ConceptMap translation |
| `GET /v1/medicines/{atcCode}` | ATC medicine lookup |
| `/v1/packs`, `/v1/assignments` | pack publishing and scope assignment |
| `POST /v1/import/fhir-bundle` · `/csv`, `GET /v1/export/...` | content import/export |
| `POST /internal/v1/confidentiality/classify` | sensitivity classification |

**Not present:** `$expand`, `$lookup`, `$subsumes`, `$validate-code` in its FHIR-standard shape, and
any free-text concept search. There is no concept table — content lives as whole FHIR resources in
`zibo_artifacts.content_json`.

## 1.4 Governance model — the good part

`zibo_packs` → `zibo_pack_artifacts` → `zibo_assignments` already models the right idea: content is
versioned into packs, packs are assigned to a scope (`TENANT` / `FACILITY` / `WORKSPACE`), and each
assignment carries a `policy_mode` (`STRICT` / `LENIENT`). `ValidationEngine` resolves the effective
mode by precedence: *explicit override > workspace > facility > tenant > global default*.

This is a genuinely good design and the spec below keeps it. Current state: **2 packs, 2 assignments,
both `TENANT`-scoped and both `LENIENT`.**

## 1.5 What is inert or broken

| Finding | Evidence |
|---|---|
| **No validation has ever run** | `zibo_validation_logs` = **0**, `zibo_validation_jobs` = **0** |
| **BUTANO's terminology validation is off** | `butano.terminology.validation-enabled` defaults `false`, `ZIBO_VALIDATION_ENABLED` unset in the estate, mode `LENIENT` |
| **oros calls a route that does not exist** | `ZiboIntegration:133` posts `/v1/mappings/translate`; ZIBO serves `/v1/map`. Terminology translation for lab orders has never worked. Same class as madi's `/internal/v1/fhir/Observation`. |
| **The mapping index is nearly all sensitivity, not translation** | 75 rows: 64 are ICD-10 → confidentiality category, 4 programme → confidentiality, 4 programme → LOINC. Genuine clinical translations: **3 rows, one each** (icd10-zw→ICD-10, EDLIZ→SNOMED, lab-tests-zw→LOINC) |
| **Version resolution is by creation time, not semver** | `ArtifactRepository.findAllVersions` orders `createdAt DESC`. Publishing a 1.0.1 patch after 1.1.0 silently starts serving the older content. |
| **Terminology is seeded unevenly across tenant planes** | 37 artifacts in the REGISTRY tenant, 7 in CARE. A resolve under the wrong plane returns nothing, and `useClinicalSpecialties` falls back silently and permanently — a picker that looks governed and never is. |

**Not a defect, corrected here:** `(tenant_id, canonical_url, version)` *is* uniquely indexed. The
apparent ATC duplicate is the same artifact seeded into both tenant planes with identical content
hashes; the clinical-specialty pair is legitimate 1.0.0 → 1.1.0 versioning.

---

# Part 2 — The demand side, measured

## 2.1 Coded elements the record already expects

BUTANO's `TerminologyValidationInterceptor` already extracts and would validate:

`AllergyIntolerance.code` · `Condition.code` · `DiagnosticReport.code` ·
`Immunization.vaccineCode` · `MedicationRequest.medicationCodeableConcept` · `Observation.code` ·
`Observation.valueCodeableConcept` · `Procedure.code` · `ServiceRequest.code`

Nine coded elements. **Four have no adequate vocabulary and one has none at all.**

## 2.2 Who calls ZIBO

20 services reference it; four call it at runtime:

| Service | Operations used |
|---|---|
| `experience-bff` | artifacts resolve/versions, assignments, map, packs, validate coding/resource/job |
| `oros-service` | `validate/coding`, `mappings/translate` *(404 — see 1.5)* |
| `msika-service` | mappings, packs, validate |
| `clinical-knowledge-platform-service` | `artifacts/observation-definitions` |

The database carries 34 `*coding_system` / `*code_system` columns across clinical migrations — the
estate is *modelled* for coded data throughout and is filling almost none of it.

## 2.3 The disconnected requirement

`hybrid-federated-target-architecture-v1.3.11.md` makes terminology a **national registry ZIBO owns
as source authority**, and requires nodes to run on **signed bundles**: the Bundle Publisher "signs
policy/standing/consent/revocation/**terminology**", Facility Edge and Hospital Node profiles list
"policy + consent + **terminology bundles**", pharmacy a "product/terminology bundle".

**ZIBO today has no bundle signing, no distribution, and no node-side cache.** Routine local
clinical care must not synchronously depend on the National Core (mandatory rule 7), so an online-
only terminology service is non-conformant with the frozen architecture regardless of its content.

---

# Part 3 — The gap

## 3.1 Content

| Needed for | Have | Need |
|---|---|---|
| `Immunization.vaccineCode` | **nothing** | national vaccine CodeSystem + ValueSet |
| `AllergyIntolerance.code` — drug | 20 ATC | full ATC / EDLIZ |
| `AllergyIntolerance.code` — food, environment, substance | **nothing** | allergen ValueSets |
| `AllergyIntolerance.reaction.manifestation` | **nothing** | manifestation ValueSet |
| `Condition.code` | 15 ICD-10 | full ICD-10 (or ICD-11) |
| `MedicationRequest` | 20 ATC / 20 EDLIZ | full ATC + EDLIZ |
| `Observation.code`, `DiagnosticReport.code` | 18 lab tests | LOINC subset |
| `Procedure.code` | 10 surgical | national procedure set |

⚠️ **ATC, ICD-10 and LOINC are licensed.** Loading them is a procurement and licensing act before it
is an engineering one. That is why this is specified rather than done.

## 3.2 Capability

1. **`$expand`** — without it 13 of 15 ValueSets are unusable and every consumer must target a
   CodeSystem directly, defeating the ValueSet layer entirely.
2. **Concept search / `$lookup`** — the blocker the UI already names in
   `app/registry/terminology/[id]/page.tsx`. No picker over 6,500 ATC codes is possible without it.
3. **`$validate-code`** in FHIR shape — `validate/coding` is close but bespoke.
4. **`$translate`** — the ConceptMap layer exists; the route oros calls does not.
5. **Signed offline bundles** — required by v1.3.11 for every node profile.

## 3.3 Correctness

6. **Semver version resolution** — replace `createdAt DESC`.
7. **Tenant seeding policy** — governed terminology must resolve identically in both planes, or the
   resolve must be plane-agnostic for national registries.
8. **A concept index** — `content_json` scanning cannot serve search or expansion at ICD-10 scale.

---

# Part 4 — Specification

## 4.1 Conformance target

ZIBO SHALL implement the FHIR R4 terminology operations below. Full HL7 terminology-service
conformance is **not** the target; this subset is what the estate's nine coded elements need.

| Operation | Route | Must support |
|---|---|---|
| `CodeSystem/$lookup` | `GET /v1/CodeSystem/$lookup?system&code` | display, designations, properties, `version` |
| `CodeSystem/$validate-code` | `GET\|POST /v1/CodeSystem/$validate-code` | `system`, `code`, `display`, returns `result`, `message` |
| `ValueSet/$expand` | `GET\|POST /v1/ValueSet/$expand?url` | `filter` (free text), `count`, `offset`, `includeDesignations`, `activeOnly` |
| `ValueSet/$validate-code` | `GET\|POST /v1/ValueSet/$validate-code` | membership check against the expansion |
| `ConceptMap/$translate` | `GET\|POST /v1/ConceptMap/$translate` | `source`, `code`, `target`; **and keep `/v1/mappings/translate` as an alias** so oros starts working without a client change |

Existing bespoke routes (`/v1/validate/coding`, `/v1/artifacts/resolve`) SHALL remain, delegating to
the same engine, so the four live callers are not broken.

## 4.2 Content model — a concept index

`content_json` remains the artifact of record. A derived, indexed projection SHALL be added:

```
zibo_concept (
  concept_id, tenant_id, artifact_id, system, version, code,
  display, definition, active, designations jsonb, properties jsonb,
  search_vector tsvector,
  UNIQUE (tenant_id, system, version, code)
)
```

Rebuilt on artifact publish. `search_vector` is a Postgres full-text index over display +
designations — sufficient for a type-ahead picker at ICD-10 scale, and no new infrastructure.

**The projection never becomes the origin.** The artifact JSON stays authoritative; the index is
derivable and rebuildable (mandatory rule 9).

## 4.3 Versioning and resolution

- Version resolution SHALL be **semver-ordered**, not creation-ordered.
- An unversioned resolve SHALL return the highest `PUBLISHED` version whose effective window
  contains "now" — `zibo_artifacts` already has `effective_start` / `effective_end` (V003).
- A pinned `version` SHALL be honoured exactly, including retired versions, so a historical record
  can be interpreted against the vocabulary in force when it was written.

## 4.4 Tenancy

National governed terminology is **owned by the National Core** (v1.3.11 §source authority). Resolution
SHALL therefore fall back from the requesting tenant to the national registry plane rather than
returning empty. The current split (37 artifacts in one plane, 7 in the other) is a seeding accident
producing silent fallbacks; see [[two-tenant-planes-are-deliberate]] — the planes are deliberate,
this asymmetry is not.

## 4.5 Offline distribution — the v1.3.11 requirement

- A **terminology bundle** is a signed, versioned, self-contained export of a pack: artifacts,
  concept index, and ConceptMaps.
- The Bundle Publisher SHALL sign it; nodes SHALL verify the signature before activation.
- A node SHALL serve `$lookup`, `$expand`, `$validate-code` and `$translate` **entirely from its
  local bundle**, with no synchronous National Core call on the clinical path.
- Bundle staleness SHALL be a distinct, surfaced state (`STALE`), never silently equivalent to
  current — mandatory rule 19.

## 4.6 Validation policy

The existing pack/assignment/policy-mode model is retained unchanged. Additionally:

- `STRICT` SHALL be reachable per scope without a global flip, so a facility can be held to coded
  data while the rest of the estate is `LENIENT`.
- An unknown code under `LENIENT` SHALL be recorded in `zibo_validation_logs` — today the table has
  0 rows and the estate therefore has no measurement of how uncoded it actually is. **This is the
  cheapest high-value change in this document**: turn on logging before turning on enforcement, and
  the coding gap becomes a number instead of an argument.
- Enforcement SHALL NOT be enabled for any element until its vocabulary is loaded. A `STRICT` flip
  over a 20-concept ATC would refuse almost every real prescription.

## 4.7 Non-functional

| | Target |
|---|---|
| `$lookup` / `$validate-code` | p95 < 50 ms (indexed point lookup) |
| `$expand` with filter, 20 rows | p95 < 200 ms at ICD-10 scale |
| Bundle size, national pack | budgeted and measured; edge nodes are disk-constrained |
| Availability | clinical path never blocks on ZIBO — LENIENT degrade or local bundle |

---

# Part 5 — Phased build

Each phase is independently shippable and independently useful.

### Z1 — Make the gap measurable *(smallest, do first)*
Log every validation outcome including LENIENT passes and unknown codes. Fix
`/v1/mappings/translate`. Fix semver resolution. Fix tenant fallback.
**Acceptance:** `zibo_validation_logs` is non-zero and a query answers "what percentage of clinical
codings in this estate resolve against governed terminology" — a number nobody can state today.

### Z2 — Concept index and the operations that need it
`zibo_concept` projection, rebuild-on-publish, `$lookup`, `$validate-code`, `$expand` with filter.
**Acceptance:** a type-ahead picker over `impilo-clinical-specialty` (31 concepts) returns in
< 200 ms, and every one of the 13 empty ValueSets expands to its CodeSystem's concepts.

### Z3 — Content loading *(licensing-gated, not engineering-gated)*
Full ATC, EDLIZ, ICD-10 (or ICD-11), LOINC subset. Requires licence review and clinical sign-off.
**Acceptance:** each of the nine coded elements in §2.1 has a named, loaded, versioned vocabulary.

### Z4 — The vocabularies with no source
Vaccine CodeSystem (derivable from the EPI schedule's antigen series in
`clinical-knowledge-platform-service`), allergen ValueSets, reaction manifestations. These must be
**authored**, so they need clinical ownership, not just import.
**Acceptance:** immunisation and allergy capture can be coded — the P3 precondition.

### Z5 — Signed offline bundles
Bundle build, signing, node verification, local serving, staleness surfacing.
**Acceptance:** a Facility Edge node with the National Core unreachable still validates and expands,
and reports its bundle as `STALE` rather than current.

---

# What this means for the SHR programme

**P3 is not blocked on all of this.** It is blocked on Z4 for coded capture only. The decision the
programme needs is whether to:

- **(a)** land P3 with `CodeableConcept.text` where no vocabulary exists — never a fabricated code —
  and let Z3/Z4 backfill coding later; or
- **(b)** hold allergy and immunisation out of the SHR until Z4 completes.

**P4 is affected more than it looked.** "OROS is the coded SoR" rests on
`oros_prescription_items.coding_system` defaulting to `http://www.whocc.no/atc` — but the governed
content behind that URL is 20 starter concepts. Prescribing is coded against a list that omits
almost every real medicine, so the coded/free-text distinction drawn between OROS and pharmacy's
frozen `rx_prescriptions` is much thinner than the P4 plan assumed. **Z3 is a precondition for P4
delivering what it claims.**

**Z1 is worth doing regardless and immediately.** Until validation logging is on, every statement
about how coded this estate is — including the ones in this document — rests on counting artifacts
rather than counting what clinicians actually record.
