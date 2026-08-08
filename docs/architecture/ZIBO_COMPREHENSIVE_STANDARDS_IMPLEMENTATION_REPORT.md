# ZIBO — comprehensive standards implementation report

**Required by** Addendum B.44 · **Status vocabulary** fixed by B.43 · **Completion rule** B.47
**Date:** 2026-08-08 · **Measured in:** `impilo-full-preview` at Flyway `v403`

> **This report states what is actually live, not what is intended.** Addendum A.39 forbids marking a
> standard `IMPLEMENTED` because its name appears in documentation, a table exists, an importer
> exists, a ZIP is on disk, five demonstration codes are seeded, or a mapping column is named after
> it. Anything not active carries a **concrete external blocker**, never an engineering excuse
> (B.47). Nothing has been dropped from the list because it was difficult.

## How to read the status column

Only B.43's vocabulary is used. `NOT_STARTED`, `ENGINE_IMPLEMENTING`, `ENGINE_READY`,
`SOURCE_DISCOVERED`, `CREDENTIAL_REQUIRED`, `LICENCE_REQUIRED`, `NORMATIVE_DOCUMENT_REQUIRED`,
`SOURCE_ACQUIRED`, `SOURCE_VERIFIED`, `IMPORTED`, `INDEXED`, `STAGED`, `ACTIVE`,
`ACTIVE_RESTRICTED`, `SUPERSEDED`, `RETIRED`, `FAILED`.

Words B.43 explicitly bans — *aware*, *planned*, *supported conceptually*, *future*, *compatible* —
do not appear as a status anywhere below.

---

## 1. Engine state — the precondition for every content row

| Capability | Status | Evidence |
|---|---|---|
| Concept index (`zibo_concept`) | `ACTIVE` | 302 concepts, 23 CodeSystems, 0 failed |
| `CodeSystem/$lookup` | `ACTIVE` | resolves ICD10ZW `B54`, EDLIZ `PARA-500`, lab-tests-zw `FBC` (with its LOINC property) |
| `CodeSystem/$validate-code` | `ACTIVE` | display mismatch returns an advisory, not an invalidation |
| `ValueSet/$expand` | `ACTIVE` | `clinical-specialty` → 31/31, correctly selects v1.1.0 over v1.0.0 |
| `ValueSet/$validate-code` | `ACTIVE_RESTRICTED` | answers from the expansion, capped at `MAX_COUNT = 1000` — unreliable above that size |
| `ConceptMap/$translate` | `ACTIVE_RESTRICTED` | only 3 genuine clinical translation rows exist to translate with |
| `CodeSystem/$subsumes` | `NOT_STARTED` | requires the A.2 hierarchy engine |
| Hierarchy (`relationship`, `ancestor` closure) | `NOT_STARTED` | **blocks all bulk hierarchical content — B.2** |
| Designations / multilingual search | `NOT_STARTED` | `designations` column exists and is populated; no search over it |
| Fuzzy search (`pg_trgm`) | `NOT_STARTED` | today's `tsvector` uses `'simple'`, so `surg` matches the *code* `SURGERY` by prefix but misses "Surgery" in display text |
| Source-adapter framework (A.34 / B.3) | `NOT_STARTED` | |
| Rights manifest (A.33) | `NOT_STARTED` | **no external content may be redistributed until this exists** |
| Source lock (A.35) | `NOT_STARTED` | |
| Validation telemetry | `ACTIVE` | records `RESOLVED` and `UNKNOWN_CODE`; see §4 |
| Write authorization | `ACTIVE` | 22 endpoints previously had none; PROVIDER/CITIZEN now 403, OPERATOR succeeds |
| Offline signed bundles (Z5) | `NOT_STARTED` | non-conformant with v1.3.11 until built |

**Engine verdict:** flat-vocabulary serving is `ACTIVE`. Hierarchical serving is `NOT_STARTED`, and
Addendum B.2 forbids bulk-loading hierarchical standards into a flat table — so ICD-11, ICF, ICHI,
ICPC-3, SNOMED, LOINC and ORPHAcode are all blocked on the engine before they are blocked on rights.

---

## 2. The register — all 43 sources required by B.44

Columns are abbreviated for legibility. Per-source detail (checksum, raw path, importer version,
counts) lands in the source-lock record as each adapter is built (A.35); where a row says
`NOT_STARTED` there is no such record to cite, and inventing one would be the exact false-completion
B.47 names.

### 2.1 WHO Family of International Classifications

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| WHO-FIC (family) | none | `NOT_STARTED` | engine: A.2 hierarchy | build the hierarchy engine |
| ICD-11 Foundation | none | `SOURCE_DISCOVERED` | engine + container not yet deployed | deploy the WHO ICD-API container (**deploy authorised**); CC BY-ND 3.0 IGO |
| ICD-11 MMS | none | `SOURCE_DISCOVERED` | as above | as above |
| ICD-10 (legacy) | **15 concepts**, `ICD10ZW` v10-2025 | `ACTIVE_RESTRICTED` | full WHO ICD-10 is licensed | `PRESERVE_AND_MAP` per A.3.1; the 15 are real and now resolvable (V402) |
| ICF | none in DB · 82 distinct codes in vendored DAKs | `LICENCE_REQUIRED` | DAK packs are `CC BY-NC-SA 3.0 IGO (assumed; not verified)`, `licenceReviewed: false` | **PO/legal decision** (§5.1) |
| ICHI | none in DB · 91 distinct codes in vendored DAKs | `LICENCE_REQUIRED` | as ICF | as ICF |
| ICD-O | none | `NOT_STARTED` | no source acquired | discover the official release |
| WHO Verbal Autopsy | none | `NOT_STARTED` | no VA question set or cause-of-death list exists anywhere in the estate | acquire the WHO 2022 VA instrument |

⚠️ **Correction to the handover brief.** It reports "ICF 1450/892/108" and "ICHI 466+431+126+108".
Those are *cell-presence* counts — rows where the column exists at all, mostly holding the literal
string `"Not classifiable in ICF"`. The real distinct-code totals are **ICF 82** and **ICHI 91**.
The same measurement found the brief *understates* the genuinely valuable content in those packs:
**SNOMED ~1,100** and **LOINC ~413** distinct codes.

### 2.2 Primary care

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| ICPC-3 | none | `NOT_STARTED` | no distribution acquired | acquire under the WONCA licensing mechanism; record the exact CC terms supplied |
| ICPC-2 | none | `NOT_APPLICABLE` | **no legacy EHR and no ICPC footprint exists** — `grep -ri icpc` returns 0 hits estate-wide, including `/opt/impilo/backups` | re-open only if a legacy extract is supplied |

### 2.3 Clinical terminology

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| SNOMED CT | **5 SCTIDs** (blood components, `config/madi/zibo-jurisdiction-pins.yaml`) | `LICENCE_REQUIRED` | **Zimbabwe is not a SNOMED International Member territory** | build RF2 import behind the licence gate; never scrape a browser |
| ICNP / nursing | none | `LICENCE_REQUIRED` | depends on SNOMED affiliate route | author a national nursing CodeSystem meanwhile; keep the ICNP path ready |

### 2.4 Laboratory and diagnostics

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| LOINC | **18 local lab concepts** (`LabTestsZW`, now resolvable), 1 mapping row | `CREDENTIAL_REQUIRED` | official release needs registration/download | register as `rgongora@mohcc.org.zw`; PO handles verification |
| UCUM | `ucum-essence.xml` **on disk** in `org.fhir:ucum:1.0.8`; `UcumUnitConverter` already works in CKP | `SOURCE_ACQUIRED` | not yet a governed ZIBO artifact | wire it as a ZIBO artifact and into Observation validation — **no download needed** |
| WHO eEDL | none | `NOT_STARTED` | no source acquired | acquire; import as `POLICY_LIST`, not as a lab terminology |
| Organism taxonomy | none | `NOT_STARTED` | no source acquired | acquire NCBI Taxonomy as a supporting layer |

### 2.5 Medicines

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| ATC/DDD | **20 starter concepts**, honestly named `who-atc-medicines-zw-starter` | `CREDENTIAL_REQUIRED` | official electronic release needs credentials; **B.15 forbids scraping the public index** | acquire under approved rights |
| EDLIZ | **20 starter concepts**; 2025 PDF hash-pinned at `docs/reference/edliz-2025/`, 19 sections extracted | `SOURCE_VERIFIED` | **none — this is MoHCC's own content** | complete the PDFBox extraction into a machine-readable national formulary release. **The cheapest real content win available** |
| WHO INN | none | `NOT_STARTED` | no source acquired | acquire Recommended INNs; keep Proposed separate |
| ZNMD | none | `NOT_STARTED` | none — national, ours to author | **M2.** Build the A.13 schema and seed from existing medicine content |
| WHO EML / EMLc / AWaRe | none | `NOT_STARTED` | no source acquired | acquire; keep distinct from EDLIZ national policy |
| ISO IDMP | none | `NORMATIVE_DOCUMENT_REQUIRED` | normative ISO standards not lawfully acquired | model structurally; **do not claim conformance** (A.14) |
| EDQM Standard Terms | none | `LICENCE_REQUIRED` | access not established | check authorised access |
| MedDRA | none | `LICENCE_REQUIRED` | no organisational subscription found | build importer against permissible fixtures only |
| WHODrug Global | none | `CREDENTIAL_REQUIRED` | no UMC access found | check national PV programme / regulator |

⚠️ **Measured caveat for P4.** `oros_prescription_items` holds **26 rows carrying one code**,
`C09AA05`, and 24 of them are literally `"Proof item C09AA05"`. `coding_system` is also inconsistent
— 24 rows say `ATC`, 2 say the canonical URI `http://www.whocc.no/atc`. "OROS is the ATC-coded
source of truth" is thinner than the SHR plan assumed.

### 2.6 Products, devices, blood, imaging

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| GS1 identifiers (GTIN/GLN/SSCC) | **zero real data** — `inv_items` 1 row / 0 GTIN, `msika_product_details` 0 rows; two duplicate barcode parsers, no check digits, no AI parsing | `NOT_STARTED` | none — engineering | build a real AI parser with check digits |
| GS1 GPC | none | `NOT_STARTED` | no source acquired | |
| MeDevIS / EMDN / GMDN / UDI | none | `NOT_STARTED` | GMDN licensed; EMDN and MeDevIS lawfully available | acquire EMDN and MeDevIS first |
| ISBT 128 | none | `NOT_STARTED` | ICCBBA registration needed for facility identifiers | **never fabricate ISBT identifiers** (A.21) |
| DICOM / RadLex / LOINC-RSNA Playbook | none | `NOT_STARTED` | no source acquired | |

### 2.7 Rare disease, oncology, knowledge, reference

| Standard | Installed | Status | Exact blocker | Next executable action |
|---|---|---|---|---|
| ORPHAcode | none | `NOT_STARTED` | no source acquired | acquire the Orphanet pack; preserve attribution |
| HPO | none | `NOT_STARTED` | no source acquired | |
| WHO SMART Guidelines / DAKs | 4 packs vendored, hash-pinned in `MANIFEST.json` | `LICENCE_REQUIRED` | `licenceReviewed: false`; `LICENCE-NOTICE.md` forbids an engineer flipping it | **PO/legal decision** (§5.1) |
| WHO Growth Standards | none in ZIBO | `NOT_STARTED` | | ZIBO must govern the reference dataset even if CKP calculates |
| WHO UHC Compendium | none | `NOT_STARTED` | no source acquired | |
| FHIR shared terminology | scattered hard-coded lists across services and UI | `NOT_STARTED` | none — inventory work | run the B.35 inventory of hard-coded code lists |
| National semantic extensions | **7 artifacts, 28 concepts** — vaccine antigen (8), reaction manifestation (16), allergen category (4), + 4 ValueSets | `ACTIVE` | none | see §3 |

### 2.8 Not applicable, with evidence

| Item | Why | What would make it applicable |
|---|---|---|
| B.8 ICPC-2 legacy migration | No legacy EHR exists. `grep -ri icpc` → 0 hits across the repo, all migration directories and `/opt/impilo/backups` | a legacy EHR extract being supplied |
| B.30 Sorojena wiring | The service does not exist in any repository | the service being created |

---

## 3. What went live in this wave

| Change | Proof |
|---|---|
| Flyway `300 → 403` in preview | pod log: "Successfully applied 3 migrations … now at version v402", then v403 |
| `V402` published 6 stranded vocabularies | 37 PUBLISHED, **0 ACTIVE** remaining |
| Concept index populated | `POST /v1/concepts/rebuild` → 23/23 CodeSystems, 302 concepts, 0 failed |
| `$expand` answers | `clinical-specialty` 31/31; emergency/HIV/TB ValueSets 4, 14, 12, 10 |
| Write authorization | PROVIDER and CITIZEN → **403** with an audit line; OPERATOR → success |
| **M1** — vaccine, allergen, manifestation | `$expand` as a PROVIDER: 8, 16, 4, 20. `$validate-code` accepts `BCG`, refuses `COVID19` |

**M1 content is servable but not clinically ratified.** All seven resources carry `"status":
"draft"` and `"experimental": true` in their own bodies. The *artifact* status is `PUBLISHED` only
because resolution filters to `PUBLISHED` and anything else is unresolvable — which is precisely
what `V402` had to repair. A.38 requires a named steward before ratification can be claimed.

---

## 4. The number nobody could state

`zibo_validation_logs` held **0 rows** for the life of the service, so no statement about how coded
this estate is — including the ones in the specification — rested on anything but counting artifacts.

The instrument now exists and the denominator is real:

```sql
SELECT round(100.0 * count(*) FILTER (WHERE result = 'RESOLVED') / nullif(count(*), 0), 1)
FROM zibo_validation_logs;
```

**It currently answers on n = 2, both self-generated.** That is an instrument, not a finding, and it
must not be quoted as a coverage figure. A real number needs clinical traffic, and
`butano.terminology.validation-enabled` still defaults to `false` with `ZIBO_VALIDATION_ENABLED`
unset in the estate. Turning that on — in `LENIENT`, where an unknown code is recorded and nothing
is refused — is the next cheap high-value change.

---

## 5. Blockers requiring a decision outside engineering

### 5.1 WHO DAK licence — blocks ICF, ICHI, and ~2,500 vendored codes

Every file in `docs/reference/who-dak/` is pinned in `MANIFEST.json` as
`"licence": "CC BY-NC-SA 3.0 IGO (assumed; not verified)"`, `"licenceReviewed": false`.
`LICENCE-NOTICE.md` states the ShareAlike question is unresolved and that these flags **must not be
flipped by an engineer**.

At stake: ~1,100 SNOMED-GPS, ~413 LOINC, ~581 ICD-11, ~459 ICD-10, 91 ICHI and 82 ICF distinct
codes already on disk. Note the 2025 packs use SNOMED **GPS** (Global Patient Set), which is a
different licence footing again from SNOMED CT.

**Decision needed:** whether this content may be loaded into ZIBO, and whether it may be
redistributed in an offline node bundle. Those are two separate permissions under A.33.

### 5.2 LOINC registration

Authorised in principle; blocked on account creation and email verification by the PO.

### 5.3 A named clinical steward for the M1 content

The vaccine, allergen-category and reaction-manifestation vocabularies are authored and live but
carry no accountable owner. A.38 requires one per governed national vocabulary before ratification.

### 5.4 Unowned ZIBO credentials

`butano`, `tuso` and `varapi` do not authenticate to ZIBO and will keep failing closed (401 / 405 /
404 respectively). `oros` is owned by the SHR session under its P5. **This must not be resolved by
weakening ZIBO's security.**

---

## 6. Honest completion statement

Per B.47, this work is **not** complete. What is true:

- The engine serves flat vocabularies and refuses what it cannot honestly serve.
- Every write to national terminology is now authorized and audited.
- One milestone (M1) is delivered, deployed and proven against the real clinical call path.
- Every remaining standard has a status from B.43's vocabulary and a concrete external blocker.

What is not true, and must not be claimed: hierarchy, subsumption, fuzzy and multilingual search,
the source-adapter framework, the rights manifest, source locks, and signed offline distribution are
all `NOT_STARTED`. Until the rights manifest exists, no externally licensed content may be
redistributed to a node at all — so Z5 is blocked on A.33 before it is blocked on engineering.
