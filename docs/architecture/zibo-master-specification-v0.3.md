<!--
SOURCE OF TRUTH. Authored by the Product Owner and delivered to the ZIBO programme
on 2026-08-08. Committed verbatim from the originating session transcript so the text
survives outside a conversation. Do not edit the normative body; record deviations in
the implementation report instead.
-->

ZIBO — execution directive for coding agent
Mission: Turn ZIBO from a seed-artifact registry into the functional national terminology and semantic authority defined in `ZIBO_Master_Spec_v0.3_2026-08-08.md`.
Operating rule
Do not implement from assumptions. First inspect the repository, migrations, live service contracts, tests, configuration, callers and deployment estate. Every material claim below must be reconciled against current code. Where the master spec and the repository conflict, report the conflict and preserve compatibility unless the spec explicitly requires a breaking migration.
Do not substitute mocks, static arrays or UI-only work for terminology capability.
Phase A — evidence and implementation map
Before changing code:

1. Locate ZIBO service, migrations, repositories, controllers/routes, validation engine, pack/assignment model, ConceptMap handling and current seed/import mechanisms.
2. Locate all runtime callers, especially:
   * experience-bff
   * oros-service
   * msika-service
   * clinical-knowledge-platform-service
   * BUTANO terminology validation interceptor
3. Verify:
   * current route mismatch(s);
   * validation enablement/config;
   * validation-log behaviour;
   * artifact version resolution;
   * two-tenant-plane resolution behaviour;
   * exact schema of `zibo_artifacts`, packs, assignments, mappings and logs.
4. Locate the frozen hybrid/federated target architecture and its offline terminology-bundle requirements.
5. Produce a concise implementation map: files, migrations, APIs, compatibility hazards and test strategy.

Then proceed unless there is a hard safety/data-loss blocker.
Phase B — Z1 Repair & Observe
Implement immediately:
Validation telemetry

* Record all terminology validation outcomes, including LENIENT accepted unknowns.
* Record at minimum: tenant/context, resource type/path, system, code, version, result, mode, message/reason, timestamp and correlation/request identity where available.
* Do not log PHI unnecessarily.
* Add queries/metrics for:
   * total codings seen;
   * resolved/valid;
   * unknown system;
   * unknown code;
   * invalid ValueSet membership;
   * unresolved mapping;
   * breakdown by service/resource/path/facility/workspace where policy permits.

Translation compatibility

* Fix the OROS translation mismatch.
* Implement the FHIR-aligned `ConceptMap/$translate`.
* Preserve `/v1/mappings/translate` as a compatibility alias if OROS currently calls it.
* Preserve existing `/v1/map` behaviour unless safely deprecated.

Version resolution

* Remove creation-time-as-version precedence.
* Add `version_scheme` support:
`SEMVER | DATE | INTEGER | PUBLISHER_DEFINED`.
* Preserve publisher version strings exactly.
* Use effective windows for unversioned "current" resolution.
* Exact version lookup must resolve retired historical versions when explicitly requested.

National terminology fallback

* Preserve deliberate tenant-plane separation.
* National-governed terminology must resolve from the national registry plane when absent in the requesting plane.
* Do not duplicate national content across tenant planes as the long-term fix.
* Make fallback observable; do not hide configuration errors.

Safety

* Keep enforcement LENIENT by default until adequate vocabularies exist.
* No clinical workflow should suddenly reject normal real-world data because a 20-concept starter vocabulary is incomplete.

Phase C — Z2 Terminology Engine
Create a derived concept projection. The artifact JSON remains authoritative.
Suggested conceptual schema:

```text
zibo_concept
  concept_id
  tenant_id / authority_scope as appropriate
  artifact_id
  system
  version
  code
  display
  definition
  active
  designations jsonb
  properties jsonb
  search_vector
  unique(system/version/code within the correct authority scope)

```

Rebuild projection atomically on publish/import.
Implement:

* `CodeSystem/$lookup`
* `CodeSystem/$validate-code`
* `ValueSet/$expand`
* `ValueSet/$validate-code`
* `ConceptMap/$translate`

`ValueSet/$expand` must support:

* url
* version where applicable
* free-text filter
* count
* offset
* includeDesignations
* activeOnly

Keep existing bespoke endpoints as compatibility façades over the same engine.
Add proper indexes and tests at ICD-scale cardinality; do not scan `content_json` per keystroke.
Phase D — terminology rights manifest
Add machine-readable rights metadata for external content:

```text
content_owner
source_uri
license_id
license_uri
license_type
redistribution_allowed
offline_bundle_allowed
derivatives_allowed
translation_or_mapping_allowed
commercial_use_allowed
territory_restrictions
attribution_required
license_expiry
source_version
source_release_date
review_status
reviewed_by
reviewed_at

```

The future bundle builder must be able to block content whose rights do not permit redistribution.
Seed rights records separately from terminology content. Do not hard-code legal conclusions deep in clinical services.
Phase D.5 — authoritative dataset acquisition and installation mandate
This is an execution requirement, not a future plan.
Do not stop after building importer code. For every terminology/data source that is legally and technically obtainable, fetch the current authoritative release, preserve the raw source, import it into ZIBO, activate it in the appropriate development/preview environment, and prove it works through the public ZIBO terminology APIs.
Acquisition discipline
Before downloading:

1. Inventory the repository, VM, object stores, existing seed bundles, mounted data directories and deployment configuration for already-downloaded authoritative releases.
2. Prefer an existing verified current copy over downloading a duplicate.
3. Use only the authoritative publisher or MoHCC-controlled source. Do not use GitHub mirrors, scraped terminology websites, unofficial CSVs or third-party repackagings when the official source is available.
4. Where an official source requires an account/login, use credentials already available through the project's normal secret/config mechanism if present. Do not hard-code credentials.
5. If credentials or rights are missing, record the source as `CREDENTIAL_REQUIRED`, `LICENCE_REQUIRED` or `AUTHORITATIVE_SOURCE_REQUIRED`. Do not silently replace it with a web scrape.
6. Verify publisher checksums/signatures when supplied.
7. Preserve every raw downloaded release immutably so the import can be reproduced.
8. Record source URI, release/version, download date, checksum, rights/licence metadata, importer version, concept counts and import status in a machine-readable source manifest/lock file.
9. Re-running acquisition/import MUST be idempotent and MUST NOT create duplicate semantic versions.

Required current source set — acquire/install now where permitted
1. LOINC

* Acquire the current official LOINC release. As of this directive, the current release is LOINC 2.82 (2026-02-24), 109,325 terms.
* Prefer the complete official release bundle, including the LOINC table/core and useful accessory files.
* Preserve the LOINC licence/notice alongside the raw release and expose required attribution in ZIBO licensing metadata.
* Import the official codes unchanged.
* Also ingest/use the official LOINC common UCUM units and relevant answer/panel files where useful to current workflows.
* Do not merely expand the existing 18-code `LabTestsZW` seed.

Proof: report imported active/deprecated/discouraged/trial counts, search examples, `$lookup`, `$expand`, and at least one local-LIMS → LOINC ConceptMap example.
2. UCUM

* Acquire the current official UCUM release artifacts from the authoritative UCUM source.
* At minimum preserve/use the implementer artifact equivalent to `ucum-essence.xml` plus release/version metadata.
* Install unit parsing/validation and wire it to quantitative Observation validation.
* Treat human display and canonical UCUM code separately where required.

Proof: validate a representative set of Zimbabwe clinical units and reject malformed/non-UCUM machine codes without preventing a human-readable display.
3. WHO ICD-11

* Install the official WHO ICD-API local deployment in the development/preview estate unless an equivalent approved deployment already exists.
* Use ICD-11 2026-01 MMS English as the current baseline release unless repository constraints require a pinned older release for compatibility.
* Preserve WHO canonical entity URIs and release identifiers.
* Configure local/offline operation and do not require synchronous WHO cloud access on the clinical path.
* Where ZIBO wraps or projects ICD content, keep WHO content unchanged and put national extensions in separate ZIBO artifacts.

Proof: local API health check, version query, search/coding-tool capability, ZIBO lookup/validation against an ICD-11 concept, and disconnected operation test.
4. WHO ATC/DDD

* Target the ATC/DDD Index 2026.
* First check whether the project/MoHCC already has authorised electronic ATC/DDD Excel/XML access or a registered account.
* If authorised electronic files are available, download and import the complete official release.
* If the complete electronic dataset cannot be downloaded because an official registered-user credential/order is required, mark it `CREDENTIAL_REQUIRED` and surface that blocker prominently.
* Do not scrape the public searchable ATC website to manufacture a bulk dataset.
* Public official change/update lists MAY be downloaded and retained for reconciliation, but they are not a substitute for the authorised complete index.
* Keep ATC/DDD as a classification layer; do not use ATC as the canonical prescribed-medicine identity.

Proof: imported release/version and counts if authorised; otherwise exact blocker plus completed importer/tests using legally available fixture data.
5. EDLIZ / Zimbabwe essential medicines content

* Search the repository, VM, MoHCC-controlled data stores and project assets for the most authoritative machine-readable/current EDLIZ source before using any derived copy.
* Acquire/import the complete authoritative national list when available.
* Preserve source version/effective date and MoHCC ownership/provenance.
* Model EDLIZ as formulary/policy relationships over ZNMD medicine concepts.
* Do not treat the current 20-concept seed as a satisfactory EDLIZ implementation.

Proof: source provenance, imported medicine/formulation counts, unmapped items list and sample formulary queries.
6. WHO Essential Medicines and AWaRe reference datasets

* Acquire the current official 2025 WHO Model List of Essential Medicines (24th EML) and 10th EML for Children plus the 2025 WHO AWaRe classification where machine-readable/public official content is available.
* Treat these as reference/enrichment sources, not as the Zimbabwe national formulary.
* Use them to support reconciliation, stewardship and antimicrobial classification alongside EDLIZ.

Proof: source/version captured and reference mappings kept distinct from national policy status.
7. GS1 healthcare standards and local GTIN product data

* Implement the current GS1 healthcare identification rules needed for scanning and parsing; this is not a request to invent a bulk "GS1 medicine terminology".
* Support GS1 DataMatrix and at minimum AIs:
   * `(01)` GTIN
   * `(10)` batch/lot
   * `(17)` expiry
   * `(21)` serial number
* Inspect existing Dura/eLMIS/NatPharm/MSIKA/product-master data for real GTINs and product identifiers and ingest/link those records where authoritative.
* If an authoritative national/regulatory product register with GTINs is available to the project, ingest it through a provenance-aware adapter.
* Do not fabricate GTINs and do not bulk-copy proprietary GS1 registry data without authorised access.

Proof: scan/parser tests using valid GS1 element strings; GTIN → product/package → ZNMD medicine linkage; lot/expiry/serial persistence.
8. National vaccine content

* Inspect CKP, EPI schedule content, Dura/eLMIS product masters and any MoHCC-controlled immunisation datasets already in the estate.
* Materialise a governed national vaccine product CodeSystem, antigen/target CodeSystem/ValueSets and schedule-linked mappings from authoritative Zimbabwe programme content.
* Where product GTINs exist, link them at the trade-item layer.
* Do not infer vaccine product identity merely from an antigen schedule entry.

Proof: current EPI products/antigens represented; combination vaccine test; `Immunization.vaccineCode` picker can return governed codes.
9. Allergy/reaction and clinical-finding interim content

* Search existing CKP/pathway content and current national clinical dictionaries first.
* Create governed Impilo CodeSystems only for gaps that cannot yet be lawfully filled from an international terminology.
* Preserve free-text fallback and explicit `uncoded/unmapped` state.
* Design these concepts to be mappable to SNOMED CT later; do not create SNOMED-like identifiers.

10. Existing local terminology/product masters
Treat the live estate itself as an acquisition source that must be reconciled, not discarded:

* current ZIBO artifacts;
* LIMS/local laboratory dictionaries;
* OROS medicine/procedure/order dictionaries;
* Dura/eLMIS/NatPharm item masters;
* MSIKA catalogues;
* CKP pathways and vaccine schedules;
* legacy ICD-10 mappings;
* programme vocabularies;
* facility/provider specialty lists where ZIBO already governs them.

For each source, classify every row/concept as:
`CANONICAL | NATIONAL_EXTENSION | LOCAL_SOURCE | MAPPED | UNMAPPED | DUPLICATE | RETIRED`.
Explicit licence gates
SNOMED CT

* Prepare the importer, canonical URI handling, terminology model, mappings and tests now.
* Do not download/install production SNOMED CT content unless the environment contains verifiable authorised SNOMED licensing/affiliate credentials or an approved licensed distribution.
* If not authorised, record `LICENCE_REQUIRED` and continue with governed Impilo national concepts.

ISO IDMP

* Do not obtain ISO standards from unofficial/pirated copies.
* Inspect whether the project already has lawful access to the relevant normative standards.
* If not, mark `NORMATIVE_DOCUMENT_REQUIRED`.
* Continue implementing an IDMP-compatible medicines model without claiming formal conformance.

Installation is not complete until it is active
A downloaded ZIP/PDF/XML/CSV is not an implementation.
For each installed terminology/source, demonstrate:

1. raw source retained;
2. licence/rights record created;
3. importer completed;
4. canonical identifiers/version preserved;
5. concept counts reconciled;
6. search index built;
7. CodeSystem/ValueSet/ConceptMap artifacts published;
8. ZIBO APIs return real data;
9. at least one real consumer workflow uses it or an integration test proves the contract;
10. offline-bundle eligibility is explicitly recorded.

At the end of the run, produce an Authoritative Content Acquisition Report with one row per source:

```text
source
authority
requested_version
installed_version
raw_file_or_service_location
checksum
rights_status
download_status
import_status
concept_count
mapping_count
activated
offline_bundle_allowed
blocker
next_action

```

The target state is not "importers exist". The target is the maximum legally usable authoritative content is downloaded, installed, indexed, queryable and actually serving the estate now.
Phase E — content tracks that may proceed now
Build importer/adapters and tests for these as separate plugins/modules so they can be updated independently.
LOINC

* Implement official-release import.
* Preserve LOINC codes/content/version.
* Build Zimbabwe context ValueSets over LOINC; do not fork LOINC by modifying its terms.
* Map existing local `LabTestsZW`/LIMS codes through ConceptMaps with provenance/confidence.
* Search must include appropriate designations/synonyms.

UCUM

* Add UCUM code validation for machine-readable units.
* Integrate with Observation/value validation without turning display strings into arbitrary codes.

ICD-11

* First inspect whether an ICD integration already exists.
* Prefer integration with WHO's official locally deployable ICD API where it fits the architecture.
* Keep WHO canonical identifiers/versioning.
* Zimbabwe additions must live in separate ZIBO artifacts.
* Do not modify WHO content and still label it ICD-11.
* Treat mapping/crosswalk rights separately from plain use of ICD content.

ATC/DDD

* Import/version official classification content under rights metadata.
* ATC is a classification, not the identity of a prescribed medicine.
* Existing ATC-as-medicine assumptions must be identified and migrated carefully.
* Keep compatibility while introducing the proper national medicine identity.

EDLIZ

* Replace starter content with a complete import path from an authoritative MoHCC-controlled source when available.
* Model EDLIZ as formulary/policy metadata over medicine concepts rather than the universal product identity.

Phase F — Zimbabwe National Medicines Dictionary (ZNMD)
Design and implement the canonical medicine identity layer.
Minimum model:

* stable Impilo medicine identifier;
* preferred clinical/generic name;
* ingredient(s)/INN;
* strength and strength basis;
* dose form;
* route(s) where part of identity;
* combination-product composition;
* regulatory status/identifiers where available;
* EDLIZ/formulary relationships;
* programme restrictions/preferred status;
* ATC mappings;
* optional SNOMED mapping slot, disabled until licensed;
* trade-item/package relationships.

Important:

* `MedicationRequest.medicationCodeableConcept` identifies the medicine/clinical drug concept.
* ATC does not become the medication identity.
* GTIN does not become the medication identity.

Create migration/compatibility strategy for current 20 ATC and 20 EDLIZ starter concepts.
Phase G — GS1-aware physical product model
Implement data model and parser support for:

* GTIN;
* GS1 DataMatrix;
* AI (01) GTIN;
* AI (10) batch/lot;
* AI (17) expiry;
* AI (21) serial number.

Rules:

* Read/store existing manufacturer/brand-owner GTINs.
* Never fabricate GTINs.
* A non-GTIN product uses a sovereign National/Impilo Product ID and nullable GTIN.
* A medicine concept may map to many trade items/packages/GTINs.
* A GTIN maps to a physical trade item/package, not to abstract clinical meaning.

Inspect OROS, Dura/eLMIS, MSIKA and dispensing flows and propose the smallest compatible common product-linkage contract.
Do not force a large cross-service rewrite in the first commit. Establish canonical contracts and migrate incrementally.
Phase H — SNOMED-ready but licence-gated
Prepare:

* SNOMED canonical system handling;
* versioned import interface;
* designations;
* relationships/properties sufficient for future navigation;
* reference-set handling;
* ConceptMap support;
* rights gate.

Do not import or redistribute SNOMED CT content unless the repository already contains a demonstrably authorised distribution or the deployment has explicit licence configuration.
Interim national concepts must use real Impilo CodeSystems. Never make SNOMED-looking pseudo-codes.
Phase I — vaccine and allergy semantic content
Create governed national CodeSystems/ValueSets where no deployable authoritative source exists yet.
Separate:

* vaccine product;
* antigen/disease target;
* national immunisation schedule logic.

CKP may own schedule logic. ZIBO owns the terminology identities.
For allergy:

* allergen/substance;
* reaction manifestation;
* appropriate ValueSets by workflow.

Preserve original free text where no code is available and mark it explicitly uncoded/unmapped.
Phase J — offline bundle foundation
Implement the bundle format and signing interface even before every content licence is resolved.
Bundle manifest must include:

* bundle ID;
* pack ID/version;
* issuer;
* sequence;
* creation/effective times;
* artifact hashes;
* ConceptMap hashes;
* required engine/schema version;
* predecessor/supersession;
* signing key ID/signature;
* rights summary;
* revocation/supersession metadata.

Portable artifacts are distributed; node search indexes are rebuilt locally.
Node activation must be atomic and rollback-capable.
Phase K — tests and proof
Add:

* unit tests;
* API conformance tests;
* migration tests;
* property/edge tests for version resolution;
* ValueSet expansion tests;
* historical version tests;
* tenant/national fallback tests;
* rights-gate tests;
* GS1 parser tests;
* disconnected-node tests where infrastructure exists;
* load/performance tests for lookup/search/expand.

Target:

* lookup/validate p95 < 50 ms;
* filtered 20-row expansion p95 < 200 ms at ICD-scale;
* no synchronous National Core requirement on disconnected clinical paths.

Delivery discipline
Make changes in coherent commits/changesets and keep a running implementation ledger:

```text
claim
evidence
change
tests
compatibility impact
remaining risk

```

Do not declare a phase complete based only on code presence. Demonstrate the acceptance criteria.
First delivery expected
The first substantial delivery should include:

1. verified repo implementation map;
2. Z1 fixes;
3. Z2 schema/API foundation;
4. rights-manifest schema;
5. tests proving the above;
6. actual acquisition and installation of every immediately obtainable authoritative dataset in Phase D.5 — not merely an import plan;
7. the Authoritative Content Acquisition Report with versions, checksums, counts, activation status and blockers;
8. real ZIBO API proofs over installed content;
9. explicit list of anything blocked by credentials, external rights or unavailable authoritative national source data.

Start with evidence. Then acquire, install, activate and prove.

# ZIBO — National Terminology & Semantic Authority
## Master specification and implementation baseline v0.3
**Date:** 2026-08-08 · **Status:** IMPLEMENTATION BASELINE v0.3 · **Measured in:** `impilo-full-preview`
**Decision:** ZIBO SHALL be the national semantic authority for Impilo: the service that defines what coded concepts mean, whether they are valid in context, how they map across code systems, and how the same meaning is preserved during disconnected operation.
## Why this exists
P3 of the SHR programme (land AllergyIntolerance, Immunization and CarePlan in BUTANO) was sequenced behind "fix capture first, so allergy and immunisation land coded". That instruction cannot be executed: **there is no vaccine vocabulary in ZIBO, and no allergen vocabulary beyond 20 medicines.** The blocker is not the capture form. It is that the national terminology service holds seed data and lacks the operations a picker needs.
This document measures what ZIBO is today and specifies what it must become. Both halves matter: the gap is only actionable if the starting point is honest.
---
# Part 1 — What ZIBO is today, measured
## 1.1 Content: 44 artifacts, ~254 concepts
| CodeSystem                                                                                  | concepts    | a real one holds | verdict                                    |
| ------------------------------------------------------------------------------------------- | ----------- | ---------------- | ------------------------------------------ |
| `who-atc-medicines-zw-starter`                                                              | **20**      | ATC ≈ 6,500      | seed                                       |
| `EDLIZMedications`                                                                          | **20**      | EDLIZ ≈ 400+     | seed                                       |
| `ICD10ZW`                                                                                   | **15**      | ICD-10 ≈ 14,000  | seed                                       |
| `LabTestsZW`                                                                                | **18**      | LOINC ≈ 100,000  | seed                                       |
| `ProviderSpecialtiesZW`                                                                     | 18          | —                | plausible                                  |
| `impilo-clinical-specialty`                                                                 | 31 (v1.1.0) | —                | plausible                                  |
| `ImpiloSurgicalProcedures`                                                                  | 10          | —                | seed                                       |
| `ICD10ZW`, `ImpiloHivArtRegimen`, `ImpiloTbTreatmentRegimen`, 8 emergency/programme systems | 4–14 each   | —                | closed operational vocabularies, plausible |
The 20 ATC concepts are Amoxicillin, Paracetamol, Metformin, Amlodipine, Enalapril… The artifact is honestly named `-starter`. Nothing is mislabelled; it is simply not yet national terminology.
**Absent entirely:** vaccines, allergens (food / environmental / substance), reaction manifestations, and any general clinical-finding vocabulary beyond 15 ICD-10 codes.
## 1.2 ValueSets: 13 of 15 are empty shells
Only three ValueSets enumerate concepts — `EncounterTypesZW` (7), `ImpiloSurgicalProceduresVS` (10), `WorkspaceTypesZW` (13). The other twelve, including `ImpiloHivArtRegimenVS`, `ImpiloTbTreatmentRegimenVS` and every emergency ValueSet, contain **zero**. They reference their CodeSystem through `compose.include` without enumerating — valid FHIR, and unusable here, because **ZIBO has no `$expand`**. A consumer resolving one of those canonical URLs receives a ValueSet with nothing in it and cannot tell that from an empty vocabulary.
## 1.3 API surface
| Route                                                           | Purpose                                      |
| --------------------------------------------------------------- | -------------------------------------------- |
| `POST /v1/validate/coding` · `/resource` · `/job`               | validate a coding or a resource              |
| `GET /v1/artifacts/resolve`                                     | resolve a canonical URL to its artifact JSON |
| `GET /v1/artifacts/observation-definitions`                     | ObservationDefinition lookup (CKP)           |
| `POST /v1/map` · `GET /v1/map/sources` · `POST /v1/map/rebuild` | ConceptMap translation                       |
| `GET /v1/medicines/{atcCode}`                                   | ATC medicine lookup                          |
| `/v1/packs`, `/v1/assignments`                                  | pack publishing and scope assignment         |
| `POST /v1/import/fhir-bundle` · `/csv`, `GET /v1/export/...`    | content import/export                        |
| `POST /internal/v1/confidentiality/classify`                    | sensitivity classification                   |
**Not present:** `$expand`, `$lookup`, `$subsumes`, `$validate-code` in its FHIR-standard shape, and any free-text concept search. There is no concept table — content lives as whole FHIR resources in `zibo_artifacts.content_json`.
## 1.4 Governance model — the good part
`zibo_packs` → `zibo_pack_artifacts` → `zibo_assignments` already models the right idea: content is versioned into packs, packs are assigned to a scope (`TENANT` / `FACILITY` / `WORKSPACE`), and each assignment carries a `policy_mode` (`STRICT` / `LENIENT`). `ValidationEngine` resolves the effective mode by precedence: *explicit override > workspace > facility > tenant > global default*.
This is a genuinely good design and the spec below keeps it. Current state: **2 packs, 2 assignments, both `TENANT`-scoped and both `LENIENT`.**
## 1.5 What is inert or broken
| Finding                                                          | Evidence                                                                                                                                                                                                       |
| ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **No validation has ever run**                                   | `zibo_validation_logs` = **0**, `zibo_validation_jobs` = **0**                                                                                                                                                 |
| **BUTANO's terminology validation is off**                       | `butano.terminology.validation-enabled` defaults `false`, `ZIBO_VALIDATION_ENABLED` unset in the estate, mode `LENIENT`                                                                                        |
| **oros calls a route that does not exist**                       | `ZiboIntegration:133` posts `/v1/mappings/translate`; ZIBO serves `/v1/map`. Terminology translation for lab orders has never worked. Same class as madi's `/internal/v1/fhir/Observation`.                    |
| **The mapping index is nearly all sensitivity, not translation** | 75 rows: 64 are ICD-10 → confidentiality category, 4 programme → confidentiality, 4 programme → LOINC. Genuine clinical translations: **3 rows, one each** (icd10-zw→ICD-10, EDLIZ→SNOMED, lab-tests-zw→LOINC) |
| **Version resolution is by creation time, not semver**           | `ArtifactRepository.findAllVersions` orders `createdAt DESC`. Publishing a 1.0.1 patch after 1.1.0 silently starts serving the older content.                                                                  |
| **Terminology is seeded unevenly across tenant planes**          | 37 artifacts in the REGISTRY tenant, 7 in CARE. A resolve under the wrong plane returns nothing, and `useClinicalSpecialties` falls back silently and permanently — a picker that looks governed and never is. |
**Not a defect, corrected here:** `(tenant_id, canonical_url, version)` *is* uniquely indexed. The apparent ATC duplicate is the same artifact seeded into both tenant planes with identical content hashes; the clinical-specialty pair is legitimate 1.0.0 → 1.1.0 versioning.
---
# Part 2 — The demand side, measured
## 2.1 Coded elements the record already expects
BUTANO's `TerminologyValidationInterceptor` already extracts and would validate:
`AllergyIntolerance.code` · `Condition.code` · `DiagnosticReport.code` · `Immunization.vaccineCode` · `MedicationRequest.medicationCodeableConcept` · `Observation.code` · `Observation.valueCodeableConcept` · `Procedure.code` · `ServiceRequest.code`
Nine coded elements. **Four have no adequate vocabulary and one has none at all.**
## 2.2 Who calls ZIBO
20 services reference it; four call it at runtime:
| Service                               | Operations used                                                                   |
| ------------------------------------- | --------------------------------------------------------------------------------- |
| `experience-bff`                      | artifacts resolve/versions, assignments, map, packs, validate coding/resource/job |
| `oros-service`                        | `validate/coding`, `mappings/translate` *(404 — see 1.5)*                         |
| `msika-service`                       | mappings, packs, validate                                                         |
| `clinical-knowledge-platform-service` | `artifacts/observation-definitions`                                               |
The database carries 34 `*coding_system` / `*code_system` columns across clinical migrations — the estate is *modelled* for coded data throughout and is filling almost none of it.
## 2.3 The disconnected requirement
`hybrid-federated-target-architecture-v1.3.11.md` makes terminology a **national registry ZIBO owns as source authority**, and requires nodes to run on **signed bundles**: the Bundle Publisher "signs policy/standing/consent/revocation/**terminology**", Facility Edge and Hospital Node profiles list "policy + consent + **terminology bundles**", pharmacy a "product/terminology bundle".
**ZIBO today has no bundle signing, no distribution, and no node-side cache.** Routine local clinical care must not synchronously depend on the National Core (mandatory rule 7), so an online-only terminology service is non-conformant with the frozen architecture regardless of its content.
---
# Part 3 — The gap
## 3.1 Content
| Needed for                                               | Have              | Need                                   |
| -------------------------------------------------------- | ----------------- | -------------------------------------- |
| `Immunization.vaccineCode`                               | **nothing**       | national vaccine CodeSystem + ValueSet |
| `AllergyIntolerance.code` — drug                         | 20 ATC            | full ATC / EDLIZ                       |
| `AllergyIntolerance.code` — food, environment, substance | **nothing**       | allergen ValueSets                     |
| `AllergyIntolerance.reaction.manifestation`              | **nothing**       | manifestation ValueSet                 |
| `Condition.code`                                         | 15 ICD-10         | full ICD-10 (or ICD-11)                |
| `MedicationRequest`                                      | 20 ATC / 20 EDLIZ | full ATC + EDLIZ                       |
| `Observation.code`, `DiagnosticReport.code`              | 18 lab tests      | LOINC subset                           |
| `Procedure.code`                                         | 10 surgical       | national procedure set                 |
⚠️ **External terminologies have different rights and redistribution conditions; they MUST NOT be treated as one licensing class.**
The terminology engine and loaders are engineering work and SHALL proceed now. Each external content source SHALL pass a rights review before national redistribution or inclusion in signed offline bundles. This does **not** necessarily imply a licence fee or procurement.
Immediate position:
* **LOINC**: usable now under its published no-fee licence, subject to its licence conditions and attribution.
* **UCUM**: usable now under its no-charge, royalty-free licence, subject to its no-derivatives/content-integrity conditions.
* **ICD-11**: usable now under WHO's CC BY-ND 3.0 IGO terms; WHO also provides locally deployable ICD API software.
* **FHIR R4 terminology operations**: implement now; the FHIR specification is published under CC0.
* **ATC/DDD**: use now as the medicine classification / drug-utilisation layer, preserving official versions and completing rights review for redistribution.
* **GS1**: implement parsing and use of existing GTIN/DataMatrix identifiers now. Creation of GS1 identifiers requires appropriately licensed GS1 identifiers/prefixes.
* **SNOMED CT**: architecture-ready now, but content deployment in Zimbabwe SHALL remain licence-gated until the applicable SNOMED licence, membership route or exemption is in place.
* **ISO IDMP**: architect toward the model now; formal conformance requires access to the relevant normative ISO standards.
## 3.2 Capability
1. **`$expand`** — without it 13 of 15 ValueSets are unusable and every consumer must target a CodeSystem directly, defeating the ValueSet layer entirely.
2. **Concept search / `$lookup`** — the blocker the UI already names in `app/registry/terminology/[id]/page.tsx`. No picker over 6,500 ATC codes is possible without it.
3. **`$validate-code`** in FHIR shape — `validate/coding` is close but bespoke.
4. **`$translate`** — the ConceptMap layer exists; the route oros calls does not.
5. **Signed offline bundles** — required by v1.3.11 for every node profile.
## 3.3 Correctness
6. **Semver version resolution** — replace `createdAt DESC`.
7. **Tenant seeding policy** — governed terminology must resolve identically in both planes, or the resolve must be plane-agnostic for national registries.
8. **A concept index** — `content_json` scanning cannot serve search or expansion at ICD-10 scale.
## 3.4 Standards and terminology implementation matrix
The build SHALL distinguish **engine capability**, **content availability**, and **rights to redistribute content**. A licence-gated terminology MUST NOT block implementation of the engine or of unrelated open/available standards.
| Standard / terminology                 | Role in Impilo                                                     | Engineering          | Content deployment                     | Interim / implementation rule                                                                  |
| -------------------------------------- | ------------------------------------------------------------------ | -------------------- | -------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **HL7 FHIR R4 terminology operations** | Interoperable terminology API                                      | **NOW**              | **NOW**                                | Native ZIBO API surface                                                                        |
| **LOINC**                              | Laboratory and clinical observations                               | **NOW**              | **NOW**                                | Load source terminology; publish Zimbabwe context ValueSets rather than inventing replacements |
| **UCUM**                               | Machine-readable units of measure                                  | **NOW**              | **NOW**                                | Validate coded quantities and observation units                                                |
| **ICD-11**                             | Disease classification / reporting and selected clinical coding    | **NOW**              | **NOW, rights-aware**                  | Preserve WHO content unchanged; national additions live in separate ZIBO artifacts             |
| **ATC/DDD**                            | Medicine classification and drug-utilisation analytics             | **NOW**              | **NOW, rights-aware**                  | Map national medicinal products to ATC; ATC SHALL NOT be the medicine identity                 |
| **EDLIZ**                              | Zimbabwe formulary / essential-medicines policy                    | **NOW**              | **NOW, MoHCC-governed**                | Formulary/status layer over national medicine concepts                                         |
| **GS1 GTIN / DataMatrix**              | Physical trade-item/package identity and traceability              | **NOW**              | **NOW** for existing identifiers       | Read/store GTIN, lot, expiry, serial; never fabricate GTINs                                    |
| **SNOMED CT**                          | Rich clinical terminology / findings / disorders / procedures etc. | **NOW architecture** | **LICENCE-GATED**                      | Use governed Impilo national codes until licensed; map later without rewriting history         |
| **ISO IDMP family**                    | Regulated medicinal-product information model                      | **NOW architecture** | **NORMATIVE-STANDARD ACCESS REQUIRED** | Keep national medicines model IDMP-compatible; do not claim conformance prematurely            |
| **Legacy ICD-10**                      | Historical reporting / legacy integration                          | **NOW preservation** | **RIGHTS-REVIEW**                      | Preserve and map legacy data while moving strategic classification work toward ICD-11          |
### 3.4.1 Authoritative source acquisition policy
ZIBO SHALL not stop at importer capability. For every terminology or reference dataset that is **legally and technically obtainable**, the programme SHALL acquire the current authoritative release, preserve the untouched source, record provenance and rights, import it, activate it, and prove it through the ZIBO terminology API.
A downloaded archive or implemented loader does **not** constitute completion.
For every source, ZIBO SHALL maintain a machine-readable acquisition/source manifest containing at minimum:
```text
source_name
publisher_authority
source_uri
requested_version
installed_version
release_date
downloaded_at
raw_source_location
checksum
rights_status
rights_manifest_id
importer_version
import_status
concept_count
mapping_count
activation_status
offline_bundle_allowed
blocker
```
Acquisition rules:
1. Prefer an existing verified current authoritative copy already present in the estate over downloading a duplicate.
2. Use the authoritative publisher or MoHCC-controlled source; do not substitute unofficial mirrors or scraped bulk datasets.
3. Preserve raw source releases immutably so every import can be reproduced.
4. Use publisher checksums/signatures where supplied.
5. Imports SHALL be idempotent.
6. Credential-gated content SHALL be marked `CREDENTIAL_REQUIRED`.
7. Licence-gated content SHALL be marked `LICENCE_REQUIRED`.
8. Missing authoritative national source data SHALL be marked `AUTHORITATIVE_SOURCE_REQUIRED`.
9. A gated source SHALL not block unrelated terminology engineering or open/available content.
10. The maximum legally usable authoritative content SHALL be installed now rather than deferred behind future "content loading" work.
### 3.4.2 Required authoritative source baseline
The implementation baseline SHALL actively target the following source set:
| Source                         | Baseline action                                                                                                                                                |
| ------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LOINC**                      | Acquire and load the current official release; replace the 18-code seed with an actual searchable terminology service and Zimbabwe context ValueSets           |
| **UCUM**                       | Acquire the current official implementer artifacts and use them for machine-readable unit validation                                                           |
| **WHO ICD-11**                 | Deploy the official local ICD API or equivalent rights-compliant local representation; use the current MMS English release approved by the implementation team |
| **WHO ATC/DDD**                | Acquire the current authorised electronic release where project credentials permit; never scrape the public search interface into an unofficial bulk dataset   |
| **EDLIZ**                      | Acquire the complete authoritative MoHCC-controlled content and model it as national formulary/policy over medicine concepts                                   |
| **WHO EML / EMLc**             | Load current official reference lists as international reference/enrichment content, not Zimbabwe policy                                                       |
| **WHO AWaRe**                  | Load current official antimicrobial classification/reference content for stewardship and mapping                                                               |
| **GS1 healthcare identifiers** | Implement GTIN/DataMatrix parsing and ingest real GTIN/product identifiers from authoritative local product masters                                            |
| **Zimbabwe vaccine content**   | Reconcile EPI/CKP/Dura/eLMIS authoritative sources into governed vaccine-product, antigen and schedule-linked terminology                                      |
| **Local estate dictionaries**  | Reconcile LIMS, OROS, Dura/eLMIS/NatPharm, MSIKA, CKP, legacy ICD and programme vocabularies into governed/mapped terminology assets                           |
| **SNOMED CT**                  | Prepare the engine/import path now but keep actual content behind a verifiable licence gate                                                                    |
| **ISO IDMP**                   | Keep the national medicines model structurally compatible; do not claim formal conformance without lawful access to the normative standards                    |
For current external releases, the implementation team SHALL re-check publisher version and rights status at acquisition time rather than hard-coding a version forever into the architecture.
### 3.4.3 Installation definition of done
A terminology/reference source is not considered installed until all applicable steps below are complete:
1. authoritative raw source retained;
2. source version and checksum recorded;
3. rights manifest created;
4. importer completed;
5. canonical identifiers and publisher version preserved;
6. concept/mapping counts reconciled;
7. search/index projection built;
8. CodeSystem/ValueSet/ConceptMap artifacts published as applicable;
9. real ZIBO API queries return source content;
10. at least one consumer integration or integration test proves use;
11. offline-bundle eligibility explicitly recorded;
12. acquisition/import status visible operationally.
The system SHALL be able to report, per source, whether it is:
`DISCOVERED | ACQUIRED | IMPORTED | INDEXED | ACTIVE | BLOCKED_CREDENTIAL | BLOCKED_LICENCE | BLOCKED_SOURCE`.
### 3.5 Semantic layers — do not collapse them
ZIBO SHALL distinguish:
1. **Clinical concept** — what the clinician means.
2. **Classification** — how that concept is grouped for reporting/statistics (e.g. ICD, ATC).
3. **Formulary / policy** — whether and how Zimbabwe permits or recommends use (e.g. EDLIZ).
4. **Regulated medicinal product** — the clinically meaningful medicinal product definition.
5. **Trade item / package** — the physical commercial item (e.g. GS1 GTIN).
6. **Physical instance** — trade item plus batch/lot, expiry, serial where present.
A code from one layer SHALL NOT masquerade as a code from another.
---
# Part 4 — Specification
## 4.1 Conformance target
ZIBO SHALL implement the FHIR R4 terminology operations below. Full HL7 terminology-service conformance is **not** the target; this subset is what the estate's nine coded elements need.
| Operation                   | Route                                     | Must support                                                                                                                 |
| --------------------------- | ----------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `CodeSystem/$lookup`        | `GET /v1/CodeSystem/$lookup?system&code`  | display, designations, properties, `version`                                                                                 |
| `CodeSystem/$validate-code` | `GET\|POST /v1/CodeSystem/$validate-code` | `system`, `code`, `display`, returns `result`, `message`                                                                     |
| `ValueSet/$expand`          | `GET\|POST /v1/ValueSet/$expand?url`      | `filter` (free text), `count`, `offset`, `includeDesignations`, `activeOnly`                                                 |
| `ValueSet/$validate-code`   | `GET\|POST /v1/ValueSet/$validate-code`   | membership check against the expansion                                                                                       |
| `ConceptMap/$translate`     | `GET\|POST /v1/ConceptMap/$translate`     | `source`, `code`, `target`; **and keep `/v1/mappings/translate` as an alias** so oros starts working without a client change |
Existing bespoke routes (`/v1/validate/coding`, `/v1/artifacts/resolve`) SHALL remain, delegating to the same engine, so the four live callers are not broken.
## 4.2 Content model — a concept index
`content_json` remains the artifact of record. A derived, indexed projection SHALL be added:
```text
zibo_concept (
  concept_id, tenant_id, artifact_id, system, version, code,
  display, definition, active, designations jsonb, properties jsonb,
  search_vector tsvector,
  UNIQUE (tenant_id, system, version, code)
)
```
Rebuilt on artifact publish. `search_vector` is a Postgres full-text index over display + designations — sufficient for a type-ahead picker at ICD-10 scale, and no new infrastructure.
**The projection never becomes the origin.** The artifact JSON stays authoritative; the index is derivable and rebuildable (mandatory rule 9).
## 4.3 Versioning and resolution
Creation time SHALL never determine semantic version precedence.
Every artifact SHALL carry:
* `version` — the upstream or Impilo version string exactly as published;
* `version_scheme` — `SEMVER | DATE | INTEGER | PUBLISHER_DEFINED`;
* `release_date`;
* `effective_start` / `effective_end`;
* `version_sort_key` — a derived sortable value appropriate to the declared scheme.
Rules:
* Impilo-authored artifacts SHOULD use semantic versioning.
* External terminologies SHALL preserve the publisher's version exactly rather than coercing it into SemVer.
* An unversioned resolve SHALL return the highest eligible `PUBLISHED` version whose effective window contains "now".
* A pinned `version` SHALL be honoured exactly, including retired versions, so a historical record can be interpreted against the vocabulary in force when it was written.
* Historical clinical records SHALL retain the coding system URI and version used at capture time.
## 4.4 Tenancy
National governed terminology is **owned by the National Core** (v1.3.11 §source authority). Resolution SHALL therefore fall back from the requesting tenant to the national registry plane rather than returning empty. The current split (37 artifacts in one plane, 7 in the other) is a seeding accident producing silent fallbacks; see `two-tenant-planes-are-deliberate` — the planes are deliberate, this asymmetry is not.
## 4.5 Offline distribution — the v1.3.11 requirement
* A **terminology bundle** is a signed, versioned, self-contained export of a pack: artifacts, concept index, and ConceptMaps.
* The Bundle Publisher SHALL sign it; nodes SHALL verify the signature before activation.
* A node SHALL serve `$lookup`, `$expand`, `$validate-code` and `$translate` **entirely from its local bundle**, with no synchronous National Core call on the clinical path.
* Bundle staleness SHALL be a distinct, surfaced state (`STALE`), never silently equivalent to current — mandatory rule 19.
## 4.6 Validation policy
The existing pack/assignment/policy-mode model is retained unchanged. Additionally:
* `STRICT` SHALL be reachable per scope without a global flip, so a facility can be held to coded data while the rest of the estate is `LENIENT`.
* An unknown code under `LENIENT` SHALL be recorded in `zibo_validation_logs` — today the table has 0 rows and the estate therefore has no measurement of how uncoded it actually is. **This is the cheapest high-value change in this document**: turn on logging before turning on enforcement, and the coding gap becomes a number instead of an argument.
* Enforcement SHALL NOT be enabled for any element until its vocabulary is loaded. A `STRICT` flip over a 20-concept ATC would refuse almost every real prescription.
## 4.7 Non-functional
|                                | Target                                                                                                 |
| ------------------------------ | ------------------------------------------------------------------------------------------------------ |
| `$lookup` / `$validate-code`   | p95 < 50 ms (indexed point lookup)                                                                     |
| `$expand` with filter, 20 rows | p95 < 200 ms at ICD-10/ICD-11 scale                                                                    |
| Bundle size, national pack     | budgeted and measured; edge nodes are disk-constrained                                                 |
| Availability                   | clinical path never blocks on National Core — local bundle first, LENIENT degrade where policy permits |
| Auditability                   | every terminology release, mapping and validation decision has provenance                              |
| Rebuildability                 | all search/index projections are derivable from signed terminology artifacts                           |
## 4.8 Zimbabwe National Medicines Dictionary
ZIBO SHALL introduce a governed **Zimbabwe National Medicines Dictionary (ZNMD)** rather than treating ATC as the identity of a prescribed medicine.
A national medicinal-product concept SHALL be capable of representing:
* stable Impilo medicine identifier;
* preferred generic/clinical display;
* active ingredient(s) / INN;
* strength and strength basis;
* dose form;
* route(s), where part of the product definition;
* combination-product structure;
* EDLIZ/formulary status and level-of-care rules;
* programme restrictions or preferred-use flags;
* regulatory identifiers and status where available;
* ATC mapping(s);
* optional SNOMED CT mapping(s) once licensed;
* links to one or more commercial/package records.
**Rule:** `MedicationRequest.medicationCodeableConcept` SHALL identify the medicine/clinical drug concept, not a GTIN and not merely an ATC classification code.
ATC remains a classification mapping for utilisation, analytics and interoperability.
## 4.9 GS1 product and traceability layer
GS1 SHALL be implemented as the identity/traceability layer for physical products and logistics.
At minimum the platform SHALL understand:
* **GTIN** — trade item/package identity;
* **GS1 DataMatrix** healthcare parsing;
* Application Identifier **(01)** GTIN;
* **(10)** batch/lot;
* **(17)** expiration date;
* **(21)** serial number where present.
The model SHALL allow a single national medicinal-product concept to link to multiple GTINs representing different manufacturers, brands or package configurations.
Rules:
* Existing manufacturer/brand-owner GTINs MAY be captured and used immediately.
* Impilo SHALL NOT fabricate GS1 GTINs.
* A product without a GTIN SHALL use a sovereign Impilo/National Product ID with `gtin = null`.
* Creation of GS1 identification keys by MoHCC or another Zimbabwean authority SHALL use legitimately licensed GS1 identifiers/prefixes.
* GTIN SHALL identify a trade item, not clinical meaning.
* Product recalls SHALL be able to target GTIN + lot/batch and, where relevant, serial.
* OROS, Dura/eLMIS, MSIKA and dispensing workflows SHALL use the common product linkage rather than maintaining incompatible product identities.
## 4.10 Terminology rights manifest
Every external CodeSystem/terminology release SHALL have a machine-readable rights record.
Minimum fields:
```text
content_owner
source_uri
license_id
license_uri
license_type
redistribution_allowed
offline_bundle_allowed
derivatives_allowed
translation_or_mapping_allowed
commercial_use_allowed
territory_restrictions
attribution_required
license_expiry
source_version
source_release_date
review_status
reviewed_by
reviewed_at
```
The bundle publisher SHALL enforce these rights:
* content prohibited from offline redistribution SHALL not enter an offline terminology bundle;
* attribution requirements SHALL travel with the bundle;
* expired or unreviewed rights SHALL surface as a release blocker, not a silent warning.
## 4.11 Clinical terminology vs classifications
ZIBO SHALL NOT use ICD or ATC as substitutes for a richer clinical terminology where the clinical concept requires more detail.
Pattern:
`clinical concept` → `ConceptMap` → `classification`
Examples:
* Impilo clinical diagnosis/finding → ICD-11 reporting code;
* ZNMD medicine → ATC code;
* local laboratory order/result concept → LOINC where a valid mapping exists.
Until SNOMED CT licensing is in place, national clinical concepts SHALL use stable Impilo CodeSystems with:
* stable code;
* preferred display;
* synonyms/designations;
* definition;
* status;
* provenance;
* effective dates;
* replacement/deprecation links;
* mapping provenance.
**Never fabricate an international code.**
When a later mapping becomes available, the original clinical coding SHALL remain on the historical record. Mappings are interpretive metadata, not retroactive rewriting of what was recorded.
## 4.12 Vaccine and allergy semantic model
The vaccine model SHALL distinguish:
* vaccine product / administered vaccine;
* antigen(s) or disease targets;
* national immunisation schedule / eligibility logic.
CKP MAY own schedule logic; ZIBO owns the terminology needed to identify products, antigens and coded observations.
Allergy SHALL distinguish:
* allergen/substance;
* allergen category where useful;
* reaction manifestation;
* severity/criticality fields that are already part of the FHIR clinical resource model.
Where an internationally governed concept is not available for immediate lawful deployment, ZIBO SHALL use a governed Impilo CodeSystem rather than freehand pseudo-codes.
## 4.13 Multilingual terminology
ZIBO SHALL treat `designation` as operational data, not decoration.
Search and display SHALL support:
* preferred English term;
* Shona designation(s) where clinically/governmentally approved;
* Ndebele designation(s) where clinically/governmentally approved;
* common clinical synonyms/abbreviations;
* language and use metadata for every designation.
The national preferred code remains language-independent.
## 4.14 Terminology governance
ZIBO SHALL implement governance for national and extended content:
* named content steward / owning authority;
* draft → review → approved → published → retired lifecycle;
* clinical review and sign-off;
* provenance and source evidence;
* proposed concept/mapping workflow;
* deprecation and replacement;
* emergency correction workflow;
* release notes and effective dates;
* terminology quality dashboards;
* mapping confidence / equivalence;
* audit trail for every material content change.
National extensions SHALL be visibly separate from externally governed terminologies.
---
# Part 5 — Phased build
Each phase is independently shippable. Engineering and content/legal work SHALL run in parallel.
### Z1 — Repair & Observe *(do first; no content procurement dependency)*
Implement:
* log every validation outcome, including LENIENT unknowns;
* fix the OROS translation route mismatch and preserve a compatibility alias;
* fix version resolution;
* implement national-plane fallback;
* turn on terminology telemetry in a safe LENIENT mode;
* add health/diagnostic metrics for resolution, validation and mapping.
**Acceptance:**
* `zibo_validation_logs` is non-zero under real clinical activity;
* a query answers the percentage of clinical codings that resolve against governed terminology;
* OROS translation no longer 404s;
* a national artifact resolves consistently across tenant planes;
* no production clinical write is rejected merely because a vocabulary is incomplete.
### Z2 — Terminology Engine
Implement:
* `zibo_concept` projection;
* rebuild-on-publish;
* text search;
* `CodeSystem/$lookup`;
* `CodeSystem/$validate-code`;
* `ValueSet/$expand`;
* `ValueSet/$validate-code`;
* `ConceptMap/$translate`;
* pagination/filtering/designations;
* `version_scheme` and correct resolution semantics;
* terminology rights manifest schema;
* common engine behind legacy bespoke APIs and FHIR operations.
**Acceptance:**
* type-ahead search over available vocabularies is < 200 ms at target scale;
* all compose-based ValueSets expand correctly;
* legacy callers still work;
* rights metadata is queryable for every external terminology;
* tests cover exact-version historical lookup.
### Z3 — Authoritative Content Acquisition & National Content
Proceed without waiting for SNOMED. Z3 is complete only when legally obtainable authoritative content is **acquired, imported, indexed, activated and proven**, not when loaders merely exist:
1. **LOINC**
   * load an appropriate official release;
   * build Zimbabwe laboratory/diagnostic ValueSets;
   * map existing LabTestsZW/LIMS concepts;
   * preserve local source codes where mapping confidence is incomplete.
2. **UCUM**
   * load/validate UCUM;
   * make machine-readable units part of Observation and quantitative-data validation.
3. **ICD-11**
   * integrate the WHO ICD API/local deployment or a rights-compliant ZIBO projection;
   * preserve WHO content and version identifiers;
   * separate Zimbabwe extensions and mappings from WHO content.
4. **ATC/DDD**
   * load/version official classification content under approved rights;
   * use only as classification/utilisation metadata;
   * map national medicine concepts/packages to ATC.
5. **EDLIZ**
   * load complete MoHCC-governed content;
   * separate formulary/policy semantics from medicine identity.
6. **WHO EML / EMLc and AWaRe**
   * acquire current official reference content;
   * keep it distinct from EDLIZ national policy;
   * use it for reconciliation, stewardship and international-reference mappings.
7. **GS1 awareness**
   * implement GTIN/DataMatrix parsing;
   * persist GTIN, lot, expiry and serial;
   * ingest real identifiers from authoritative product masters where available;
   * link physical trade items to ZNMD concepts.
8. **Local estate reconciliation**
   * inventory and reconcile LIMS, OROS, Dura/eLMIS/NatPharm, MSIKA, CKP, programme and legacy terminology sources;
   * classify each source concept as canonical, national extension, mapped, unmapped, duplicate or retired.
**Acceptance:**
* an Authoritative Content Acquisition Report exists for every required source;
* every immediately obtainable source is acquired, rights-recorded, imported, indexed and active;
* real pickers/search exist for labs, units, classifications and medicines;
* no new clinical workflow needs to invent a local code merely because ZIBO cannot search;
* product scanning can resolve a GTIN to a product record when that mapping exists;
* any content not installed has an explicit blocker (`CREDENTIAL_REQUIRED`, `LICENCE_REQUIRED` or `AUTHORITATIVE_SOURCE_REQUIRED`) rather than an unqualified TODO.
### Z4 — National Clinical Semantic Content
Implement:
* Zimbabwe National Medicines Dictionary;
* vaccine product and antigen terminology;
* allergen/substance concepts;
* reaction manifestations;
* national clinical finding/procedure concepts needed by active care pathways;
* multilingual designations;
* governance workflow.
SNOMED CT SHALL remain a pluggable mapping/terminology target. If licensing becomes available, import it under its own canonical system/version and progressively map national concepts.
**Acceptance:**
* every coded element used by P3/P4 has a named, loaded and governed vocabulary;
* immunisation and allergy capture can be coded without fabricated identifiers;
* every national concept has provenance and an owner;
* mappings state equivalence/confidence.
### Z5 — Sovereign Distribution
Implement:
* signed terminology bundle manifest;
* portable bundle content;
* node-side index construction;
* signature verification;
* atomic activation/rollback;
* staleness states;
* revocation/supersession;
* local serving of lookup/expand/validate/translate;
* per-terminology rights enforcement during bundle build.
**Acceptance:**
* a Facility Edge/Hospital Node with National Core unreachable continues to search, expand, validate and translate;
* node reports bundle version and `STALE` state correctly;
* disallowed content cannot accidentally be packaged into an offline bundle.
### Z6 — Governance & Evolution
Implement:
* proposal/review/approval UI;
* terminology steward roles;
* release workflow;
* deprecation/replacement;
* mapping review queues;
* multilingual term review;
* usage analytics and unknown-code dashboards;
* quality metrics;
* formal SNOMED onboarding path if/when licensed;
* IDMP conformance assessment after normative standards are acquired.
**Acceptance:**
* terminology change is a governed operational process rather than a database-seeding exercise;
* every production vocabulary has a steward, rights status and release history;
* unknown-code telemetry feeds directly into the content backlog.
---
# What this means for the SHR programme
**P3 is not blocked on completion of the entire ZIBO programme.**
**Programme decision:** land P3 with governed coding wherever a vocabulary exists. Where none exists, permit `CodeableConcept.text` with an explicit `uncoded/unmapped` state and preserve the clinician's original text permanently. Such entries SHALL enter a terminology-reconciliation queue.
Rules:
* never fabricate a code;
* never silently treat free text as governed coding;
* later mappings SHALL not overwrite the original clinical assertion;
* Z4 completes the coded-capture path for vaccines, allergens and reaction manifestations.
**P4 is affected more than it looked.** "OROS is the coded SoR" rests on `oros_prescription_items.coding_system` defaulting to `http://www.whocc.no/atc` — but the governed content behind that URL is 20 starter concepts. Prescribing is coded against a list that omits almost every real medicine, so the coded/free-text distinction drawn between OROS and pharmacy's frozen `rx_prescriptions` is much thinner than the P4 plan assumed. **Z3 is a precondition for P4 delivering what it claims.**
**Z1 is worth doing regardless and immediately.** Until validation logging is on, every statement about how coded this estate is — including the ones in this document — rests on counting artifacts rather than counting what clinicians actually record.
---
# Part 6 — Non-negotiable implementation principles
1. **No fake codes.** A missing vocabulary is represented as missing/uncoded, never by inventing an international-looking identifier.
2. **No synchronous National Core dependency on the clinical path.**
3. **Clinical meaning, classification, formulary and physical product identity are different layers.**
4. **GTIN is a trade-item identifier, not a medicine concept.**
5. **ATC is a classification, not the prescription identity.**
6. **External content retains its canonical system and publisher version.**
7. **National extensions remain visibly national extensions.**
8. **Rights metadata is executable policy.**
9. **Original clinical coding is immutable history; later mappings enrich interpretation rather than rewrite history.**
10. **The artifact of record remains portable; indexes are derived and rebuildable.**
11. **Validation is measured before enforcement is tightened.**
12. **Every production vocabulary has a steward, provenance and lifecycle.**
13. **Importers are not delivery.** Content work is complete only when the authoritative source is acquired, activated and queryable, or explicitly blocked by rights/credentials/source availability.
# Part 7 — Immediate work authorised by this baseline
The following work does **not** wait for SNOMED membership/licensing or ISO procurement:
* Z1 in full;
* Z2 in full;
* LOINC loader and Zimbabwe ValueSet scaffolding;
* UCUM support;
* ICD-11 integration spike using the official locally deployable API;
* ATC loader/classification mapping path, subject to rights metadata;
* complete EDLIZ import path;
* ZNMD schema and seed migration from existing medicine content;
* GS1 GTIN/DataMatrix parser and product linkage model;
* terminology rights manifest;
* authoritative-source acquisition/source-lock manifest;
* actual acquisition and installation of every immediately obtainable authoritative dataset in the required source baseline;
* Authoritative Content Acquisition Report;
* multilingual designation/search support;
* offline bundle manifest/schema and signing interface (content inclusion remains rights-aware);
* test fixtures, metrics and dashboards needed to prove the service works.
SNOMED CT content import is **prepared but disabled behind a licensing gate**.
---
# Reference position used for implementation decisions
Current official-source checks made for this baseline:
* HL7 FHIR licence: FHIR specification published under CC0.
* LOINC licence: no licence fees/royalties for permitted commercial or non-commercial use, with conditions.
* UCUM licence: worldwide no-charge/royalty-free use under stated conditions.
* WHO ICD-11: CC BY-ND 3.0 IGO; WHO provides locally deployable ICD API software.
* WHO ATC/DDD: classification and drug-utilisation methodology; national linking of medicinal products/packages to ATC is an intended use.
* GS1 Healthcare: GTIN + DataMatrix support product identification and traceability; licensed GS1 identifiers/prefixes are required to issue GS1 keys.
* SNOMED International: Zimbabwe is not listed among current Member territories; non-Member deployment requires the applicable licensing process unless exempted.
* ISO IDMP: normative ISO standards define regulated medicinal-product identification/data structures; access to the standards is handled separately from ZIBO engine construction.
This section is operational guidance, not legal advice. Rights status SHALL be re-checked when content is actually imported or redistributed.
