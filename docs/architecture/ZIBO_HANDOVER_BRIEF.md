# ZIBO — handover brief for its own session

**Date:** 2026-08-08 · **From:** the FHIR/SHR session (branch `phase0/g-fhir-split`)
**Status:** ZIBO is now its own programme. This is the entry point.

> ## ⚠️ Corrections, measured 2026-08-08 after the deploy
>
> Five claims below were wrong or incomplete. Read these first; the body is otherwise sound.
>
> 1. **The governing text is now in the repo.** The Master Specification v0.3, Addendum A and
>    Addendum B are committed at `docs/architecture/zibo-master-specification-v0.3.md`,
>    `…-addendum-a-national-semantic-standards.md`, `…-addendum-b-standards-acquisition.md`. They
>    previously existed only in a session transcript.
> 2. **"Deploy `V400`+ and Z1's criterion is provable" was false.** Deploying created
>    `zibo_concept` and left it at **0 rows**. The projection had one trigger —
>    `ArtifactService.publish` — and every vocabulary in this service was inserted by a migration or
>    a seed script, which never call it. `$expand` on the flagship 31-concept ValueSet returned
>    `"total": 0, "contains": []` with `"success": true`. Fixed by `POST /v1/concepts/rebuild`.
> 3. **Six vocabularies were formally unresolvable**, including ICD10ZW, EDLIZ, LabTestsZW and
>    ProviderSpecialtiesZW. They were seeded `ACTIVE` — a review state — while resolution filters to
>    `PUBLISHED`. Fixed by `V402`.
> 4. **The WHO DAK figures are wrong, and the content is licence-gated.** "ICF 1450 / ICHI 466+431"
>    are *cell-presence* counts; the real distinct-code totals are **ICF 82** and **ICHI 91**. The
>    genuine value there is **SNOMED ~1,100** and **LOINC ~413**, which this brief undersells. All of
>    it is `CC BY-NC-SA 3.0 IGO (assumed; not verified)` with `licenceReviewed: false`, and
>    `LICENCE-NOTICE.md` says an engineer must not flip that flag. Status is **`LICENCE_REQUIRED`**,
>    not "cheapest real win".
> 5. **Four dead terminology routes, not two.** Besides oros and butano: **tuso** `GET`s a
>    `@PostMapping` (405, swallowed into "auto-approved, `zibo_validated=false`") and **varapi**
>    calls `/v1/internal/validate/code`, which ZIBO has never served.
>
> **The alert-contract session id in §6 does not exist.** The live SHR session is
> `local_9ba5279a-6cb5-43bd-bb6b-c8b9721d9b2e`.

## Why this exists

ZIBO started as one item inside the SHR programme — "P3 is blocked because there is no vaccine
vocabulary". Addenda A and B turned it into ~45 international standards, a hierarchy engine, a
source-adapter framework, credentialled acquisition, a rights regime and an offline distribution
layer. That is a multi-week programme with its own licensing and governance decisions. Running it
inside the SHR session starves both.

**Read these three, in order, before touching code:**

1. `docs/architecture/zibo-terminology-service-spec.md` — the measured baseline (committed `753be910c`)
2. ZIBO Master Specification v0.3 + Addendum A + Addendum B (the PO's directives)
3. This brief — what is already done, what was measured, and what is decided

---

## 1. What has already landed

All on `phase0/g-fhir-split`, all pushed. **⚠️ None of it is deployed.** Live ZIBO is at Flyway
`V300`; `V400` and `V401` are committed and unapplied, so `zibo_concept` does not exist in the
cluster.

| Commit | What |
|---|---|
| `1a5bf0199` | `V400` — validation telemetry columns (the log had no `code` column and recorded only failures, so it could never produce a coverage percentage) + `version_scheme`/`version_sort_key` |
| `0d4025a83` | `ArtifactResolutionService` — version-ordered resolution, effective window honoured, national-plane fallback. `VersionOrdering`. Telemetry on every outcome including `RESOLVED` |
| `9766e56c3` | The two dead routes aliased (`/v1/mappings/translate` for oros, `/api/v1/terminology/validate` for butano) + scheduler trust context |
| `c47d757f7` | `V401` `zibo_concept` index, `ConceptProjectionService`, five FHIR operations with honest refusals |

**Migration lease: `V400`–`V449`**, claimed in `docs/registry/zibo-terminology-leases.md`. `V009`
and `V301` both sit inside surgery's committed block — do not take them.

### Two things in there that matter for everything after

- **The projection is never the origin.** `content_json` remains the artifact of record;
  `zibo_concept` is derived and rebuildable. Keep it that way when the big loaders land.
- **`$expand` refuses what it cannot honestly expand.** A `filter`, `exclude` or nested ValueSet
  reference returns **501, not a truncated 200**. A partial expansion that looks complete would
  silently omit codes from a clinician's picker. LOINC and ICD-11 ValueSets *will* use filters —
  that refusal is where you find out, and it is deliberate.

---

## 2. The measured estate (Addendum B.1) — do not re-derive

| Standard | State |
|---|---|
| **ICD-10** | The only classification with real data, and it is 15 concepts + **64 prefix→sensitivity rows** (a confidentiality classifier, not a diagnosis vocabulary) + 3 genuine translations |
| **ICD-11** | Constants, a UI picker and a BFF route — but `search.ss_search_index` is **0 rows** and nothing indexes ICD11. **No ICD-11 code has ever been stored.** |
| **ICPC-2 / ICPC-3** | `grep -ri icpc` → **0 hits estate-wide.** No legacy EHR, no migration directory, no third-party dump in `/opt/impilo/backups` |
| **ICF** | **1450 / 892 / 108 codes already vendored** inside `docs/reference/who-dak/*/extracted/data-dictionary.json`. Zero in code or DB. No rehab/functional-assessment capture exists |
| **ICHI** | **466 + 431 + 126 + 108 already vendored** in the same DAK files. In-estate it is one costing tariff label with 0 items |
| **Procedures (actual)** | ICD-9-CM with **10 codes**. `procedures.procedure_definition` has 66 rows, `icd9cm_procedure_code` on 10, `snomed_ct_code` deliberately NULL on all 66 — read the rationale at the head of its `V003` |
| **SNOMED CT** | 5 real SCTIDs, all blood components in `config/madi/zibo-jurisdiction-pins.yaml`. No RF2 loader, no refsets, no licence config |
| **UCUM** | **Already on disk** — `org.fhir:ucum:1.0.8` contains `ucum-essence.xml`, and CKP's `UcumUnitConverter` already works. Wire it; do not download it |
| **EDLIZ** | 2025 PDF vendored and hash-pinned at `docs/reference/edliz-2025/`, with a working `EdlizPdfIngestionService`. Only 19 sections extracted so far |
| **GTIN** | **Zero real data.** `inv_items` 1 row / 0 gtin; `msika_product_details` 0 rows. Two duplicate barcode parsers, no check digits, no GS1 AI parsing anywhere |
| **Nothing at all** | ICNP/nursing · ICD-O · ORPHAcode · HPO · organism taxonomy · ISBT 128 · MedDRA · WHODrug · EDQM · EMDN/GMDN/MeDevIS · RadLex |
| **WHO VA** | The frame exists (`pct_verbal_autopsy`, `ubomi.death_notification`) with 0 rows. **No VA question set and no cause-of-death list exist in any form** |

### The single biggest reuse opportunity

**HAPI's terminology tables are deployed and empty.** `trm_concept`, `trm_concept_pc_link`,
`trm_valueset_concept` and friends exist in both the `butano` and `hapi` databases, **all at 0
rows**, and HAPI's `TermLoaderSvc` natively parses **SNOMED RF2, LOINC and ICD-10** — the three
hardest importers.

**Decision taken: reuse HAPI's parsers *inside ZIBO*.** Pull the loader/parsing classes in as a
library and write their output into `zibo_concept`. ZIBO stays the terminology authority and the
schema stays ours. **Spike this first** — confirm the parsers are usable without the full JPA stack.
If they are not, fall back to native adapters and say so; do not let a failed spike become a silent
rewrite.

> **Spike result (2026-08-08): PASSES.** `hapi-fhir-jpaserver-base:7.4.0` is already in `~/.m2`
> (4.17 MB), holding `TermLoaderSvcImpl`, 31 LOINC handlers, 3 SNOMED RF2 handlers, `Icd10Loader`,
> `Icd10CmLoader` and all five `TermVersionAdapterSvc*`. `TermLoaderSvcImpl` takes **two
> interfaces** and exposes a public static `withoutProxyCheck(...)` factory that exists precisely to
> skip the Spring-proxy assertion; across 46 KB of bytecode it calls only **four methods** on them,
> and only one matters — `storeNewCodeSystemVersion`. The ICD loaders need no service at all. **No
> JPA stack, no EntityManager, no ApplicationContext.**
>
> The cost is footprint: the pom has 75 dependency blocks, dragging in Jena, JScience, GraphQL-Java,
> Elasticsearch and Thymeleaf. So add it **with aggressive exclusions** and implement a ZIBO-side
> `ITermCodeSystemStorageSvc` writing into `zibo_concept` + `zibo_concept_relationship`, rather than
> adopting the artifact wholesale. Nothing needs downloading — the full closure is already resolved.

**Rejected:** making BUTANO's HAPI the terminology server. It would split terminology across two
services and make BUTANO the semantic authority — recreating exactly the FHIR-split problem the SHR
session just spent this whole branch closing.

---

## 3. Decisions already taken

| | |
|---|---|
| **Loaders** | Reuse HAPI's parsers inside ZIBO (spike first) |
| **Sequencing** | Engine first → free content → credentialled → licence-gated engines only |
| **No subject** | Record `NOT_APPLICABLE` with evidence rather than building for absent data |
| **Credentials** | Register as `rgongora@mohcc.org.zw`; PO handles email verification. Passwords into `Secret/impilo-app-secrets` via `scripts/secrets/bootstrap-secrets.sh`. **Never scrape** a public index to synthesise a bulk dataset |
| **ICD-11 deploy** | **Authorised** — deploy the WHO ICD-API container into `impilo-full-preview`. Purely additive; nothing scaled down |

### `NOT_APPLICABLE`, with evidence

- **B.8 ICPC-2 legacy migration** — there is no legacy EHR and no ICPC footprint anywhere. Trigger
  that would make it applicable: a legacy EHR extract being supplied.
- **B.30 Sorojena wiring** — the service does not exist in any repo. Trigger: the service being created.

Record both in the register. Nothing disappears because it was difficult.

---

## 4. Suggested first tranche

1. **Spike HAPI's parsers** — decides the whole adapter strategy, so do it before writing adapters.
2. **Z3 hierarchy engine** — `zibo_concept_relationship`, `zibo_concept_ancestor` (derived closure),
   `zibo_designation`, `zibo_source_release`/`_lock`, `zibo_rights_manifest`,
   `zibo_mapping_provenance`; `artifact_kind` per A.1; `$subsumes` and ancestor/descendant APIs;
   `pg_trgm` alongside the existing tsvector. **A hierarchy must not be loaded into a flat table.**
3. **Deploy `V400`+ to preview** and confirm `$expand` answers — this is also what finally makes Z1's
   acceptance criterion provable.
4. **Tier 1 content**, cheapest real wins first: the WHO DAK bindings already on disk (ICF, ICHI,
   LOINC, SNOMED, ICD), UCUM wiring, then the ICD-API container.

---

## 5. ⚠️ Two open security items — land before real content

1. **No ZIBO write endpoint has an authorisation check.** No `@PreAuthorize`/`@Secured` anywhere in
   `api/controller/`; `POST /v1/import/fhir-bundle`, `/v1/artifacts/{id}/publish` and
   `/v1/map/rebuild` are open to any authenticated caller. Loading national terminology makes this
   materially worse. **This must land before Tier 1 content.**
2. **oros and butano do not authenticate to ZIBO.** oros builds trust headers with no bearer token;
   butano uses a bare `new RestTemplate()`. Both now receive **401 rather than 404** — a different
   failure, not a fixed one. Do **not** "fix" this by relaxing ZIBO's security. The SHR session is
   implementing oros's credentials as part of P5; butano's is unowned.

---

## 6. The alert contract — what this session needs back

The SHR session has **parked two work items on ZIBO**. Please message session
`local_ac299811-ef5a-4b33-b8b9-bc591a46cb86` (titled around the FHIR split / SHR programme) when
either milestone lands. Each directly unblocks parked clinical work.

| Milestone | What must be true | Unblocks |
|---|---|---|
| **M1** | A governed **vaccine** CodeSystem + ValueSet, and **allergen** + **reaction-manifestation** ValueSets, published and answering `ValueSet/$expand` in preview | **P3** — allergy, immunisation and care-plan capture, plus their BUTANO Kafka listeners |
| **M2** | Either **real ATC loaded**, or **ZNMD published** as the medicine identity layer with ATC mappings | **P4** — medications with OROS as the source of truth |

**Prerequisite for both:** ZIBO `V400`+ applied in preview with `$expand` answering, so a picker has
something real to call.

Context for M1: the EPI schedule at
`services/clinical-knowledge-platform-service/src/main/resources/clinical/zw-epi-schedule.json` has
**8 antigens and 18 doses and no codes at all** — and the file itself says codes are local "because
zibo-service does not yet hold a vaccine terminology. When it does, these codes become the local
mapping." That is the natural source for the vaccine CodeSystem.

Context for M2: `oros_prescription_items.coding_system` defaults to `http://www.whocc.no/atc`, but
the governed content behind that URL is **20 starter concepts**. So "OROS is the ATC-coded SoR" is
much thinner than the SHR plan assumed.

---

## 7. Standing rules that apply to this work

- **No fabricated codes, ever.** A missing vocabulary is `uncoded`/`unmapped`, never an
  international-looking identifier.
- **BUTANO data must never be deleted.**
- **Nothing in `impilo-full-preview` scaled down, deleted or restarted.** Additive only. The helm
  HOLD stands; deploys need authorisation.
- **Every guard red-proved** by breaking what it protects and confirming it fails.
- **Measure before claiming.** The whole reason this programme exists is that
  `zibo_validation_logs` held zero rows, so nobody could state how coded the estate was — including
  the documents asserting it.
