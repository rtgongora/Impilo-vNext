<!--
SOURCE OF TRUTH. Authored by the Product Owner and delivered to the ZIBO programme
on 2026-08-08. Committed verbatim from the originating session transcript so the text
survives outside a conversation. Do not edit the normative body; record deviations in
the implementation report instead.
-->

# ADDENDUM A — COMPREHENSIVE NATIONAL SEMANTIC STANDARDS IMPLEMENTATION
**Applies to:** ZIBO Master Specification v0.3
**Status:** NORMATIVE IMPLEMENTATION ADDENDUM
**Target baseline after incorporation:** ZIBO Master Specification v0.4
**Date:** 2026-08-08
## A.0 Decision
ZIBO SHALL implement the full standards and semantic-content architecture defined in this Addendum.
The language **“aware of”, “compatible with”, “consider”, “future support”, “possible integration” or equivalent SHALL NOT constitute implementation**.
For every standard or authoritative semantic source listed here, ZIBO SHALL do one of the following:
1. **IMPLEMENT_AND_ACTIVATE** — implement the engine capability, acquire the authoritative content, import it, index it, expose it through ZIBO, govern it and distribute it where rights allow;
2. **IMPLEMENT_WITH_RIGHTS_GATE** — completely implement the data model, importer, terminology operations, mappings, validation, governance and activation path, but prevent use of copyrighted content until the required licence or authorised distribution is present;
3. **IMPLEMENT_WITH_NORMATIVE_GATE** — implement the architecture and data model but withhold claims of formal standards conformance until the relevant normative documents have been lawfully acquired and verified;
4. **PRESERVE_AND_MAP** — retain an existing/legacy standard exactly for historical interpretation while mapping new capture to the chosen successor.
A rights or credential barrier SHALL block only the protected content. It SHALL NOT block ZIBO engineering.
ZIBO SHALL therefore become more than a terminology repository.
It SHALL be the national authority for:
* terminologies;
* classifications;
* nomenclatures;
* identifier schemes;
* ValueSets;
* ConceptMaps;
* semantic reference datasets;
* controlled data dictionaries;
* national extensions;
* product vocabularies;
* internationally governed semantic content;
* semantic provenance;
* terminology rights;
* release/version governance;
* offline semantic distribution.
---
# A.1 Expanded ZIBO artifact model
The existing FHIR artifact model SHALL remain supported but SHALL be expanded so that ZIBO can govern semantic assets which are not naturally represented as a conventional CodeSystem alone.
Every governed semantic asset SHALL have an `artifact_kind`.
Minimum artifact kinds:
```text
CODE_SYSTEM
VALUE_SET
CONCEPT_MAP
NAMING_SYSTEM
IDENTIFIER_SCHEME
CLASSIFICATION
ONTOLOGY
REFERENCE_SET
REFERENCE_DATASET
DATA_DICTIONARY
POLICY_LIST
FORMULARY
PRODUCT_NOMENCLATURE
DEVICE_NOMENCLATURE
PROCEDURE_CLASSIFICATION
CLINICAL_KNOWLEDGE_DICTIONARY
SEMANTIC_BUNDLE
```
FHIR-native resources SHALL be used wherever they correctly represent the asset.
Non-FHIR reference assets SHALL still receive:
```text
canonical_uri
source_authority
source_version
release_date
effective_period
status
rights_manifest
checksum
provenance
language
governance_owner
supersedes
superseded_by
```
The original authoritative asset SHALL remain preserved.
ZIBO SHALL NOT mutate an external authoritative terminology merely to make it fit the Impilo data model.
Zimbabwe adaptations SHALL exist as:
* ValueSets;
* ConceptMaps;
* supplements;
* national extensions;
* designations;
* policy layers;
* reference sets;
rather than disguised modifications of international content.
---
# A.2 Hierarchical terminology engine
The flat `zibo_concept` projection specified in v0.3 is necessary but no longer sufficient.
ZIBO SHALL implement first-class hierarchical semantics.
Add derived relationship structures conceptually equivalent to:
```text
zibo_concept_relationship (
    relationship_id,
    authority_scope,
    system,
    version,
    source_code,
    relationship_type,
    target_code,
    relationship_group,
    active,
    properties
)
zibo_concept_ancestor (
    authority_scope,
    system,
    version,
    ancestor_code,
    descendant_code,
    depth
)
```
The closure/ancestor projection SHALL be rebuildable from authoritative source content.
The engine SHALL support:
* parent lookup;
* child lookup;
* ancestor lookup;
* descendant lookup;
* hierarchy depth;
* concept subsumption;
* reference-set membership;
* property filtering;
* hierarchical ValueSet expansion;
* inactive/replaced concept traversal.
FHIR terminology operations SHALL therefore include:
* `CodeSystem/$lookup`
* `CodeSystem/$validate-code`
* **`CodeSystem/$subsumes`**
* `ValueSet/$expand`
* `ValueSet/$validate-code`
* `ConceptMap/$translate`
ZIBO SHOULD additionally support terminology closure operations where they materially improve hierarchical terminology use.
Search SHALL support:
* exact code;
* preferred term;
* synonym;
* designation;
* abbreviation;
* normalized spelling;
* language;
* prefix;
* token;
* fuzzy matching;
* hierarchy-scoped search;
* ValueSet-scoped search.
PostgreSQL full-text indexing MAY be supplemented with `pg_trgm` or equivalent indexed fuzzy search.
No terminology picker SHALL scan whole JSON artifacts synchronously.
---
# A.3 Comprehensive implementation register
The following register is normative.
## A.3.1 WHO Family of International Classifications
| Standard                               | National role                                                                                                                     | ZIBO requirement                                              |
| -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **WHO-FIC shared semantic foundation** | Common semantic framework joining WHO classifications and terminology                                                             | **IMPLEMENT_AND_ACTIVATE**                                    |
| **ICD-11 Foundation**                  | Rich disease, disorder, sign, symptom, injury, cause and extension semantic concepts                                              | **IMPLEMENT_AND_ACTIVATE**                                    |
| **ICD-11 MMS**                         | Morbidity/mortality statistical classification                                                                                    | **IMPLEMENT_AND_ACTIVATE**                                    |
| **ICF**                                | Functioning, disability, activity, participation and environmental factors                                                        | **IMPLEMENT_AND_ACTIVATE**                                    |
| **ICHI**                               | Health interventions across diagnostic, medical, surgical, nursing, rehabilitation, primary care, mental health and public health | **IMPLEMENT_AND_ACTIVATE**                                    |
| **ICD-10**                             | Legacy morbidity/reporting compatibility                                                                                          | **PRESERVE_AND_MAP**                                          |
| **ICD-O**                              | Cancer topography, morphology, behaviour and grade                                                                                | **IMPLEMENT_AND_ACTIVATE**                                    |
| **WHO Verbal Autopsy standard**        | Structured community mortality ascertainment and cause-of-death workflows                                                         | **IMPLEMENT_AND_ACTIVATE semantic components/reference data** |
ZIBO SHALL integrate WHO-FIC as a family, not as unrelated independent files.
A WHO Foundation entity URI SHALL remain distinct from a statistical linearization code.
The platform SHALL be able to retain both where applicable.
Example:
```text
clinical_semantic_identity = WHO Foundation URI
statistical_classification = ICD-11 MMS code
```
ZIBO SHALL preserve extension-code semantics and post-coordination structures where supported by the official WHO model.
---
# A.4 Presenting complaints, symptoms and primary-care semantics
## A.4.1 ICPC-3
ZIBO SHALL implement **ICPC-3** as the primary international classification for:
* Reason for Encounter;
* presenting complaint;
* symptom;
* patient-stated reason for seeking care;
* primary-care problem;
* primary-care diagnosis;
* primary-care process of care;
* episode-oriented primary-care classification.
ICPC-3 SHALL become a first-class ZIBO CodeSystem/classification.
It SHALL be searchable by:
* code;
* preferred term;
* synonyms;
* inclusion terms;
* presentation type;
* chapter;
* component.
The UX SHALL support coded multi-selection with preservation of narrative patient language.
A patient's presenting complaint SHALL NOT be overwritten by a later diagnosis.
Example:
```text
PRESENTATION
  headache
  vomiting
ASSESSMENT
  malaria
```
Both remain clinically valid information.
## A.4.2 ICPC-2 preservation
The existing legacy EHR ICPC-2 implementation SHALL be preserved as historical terminology.
ZIBO SHALL:
* inventory the exact ICPC-2 edition/version used by the legacy EHR;
* import or preserve the exact existing codes and displays;
* retain historical records unchanged;
* build an ICPC-2 → ICPC-3 ConceptMap;
* preserve mapping provenance and equivalence;
* map onward to ICD-11 only where legitimate.
No historical ICPC-2 record SHALL be rewritten to pretend that ICPC-3 was recorded originally.
## A.4.3 Episode semantics
ZIBO SHALL support semantic linkage across:
```text
Reason for Encounter
        ↓
Presenting Complaint / Symptom
        ↓
Assessment / Problem
        ↓
Intervention
        ↓
Outcome
        ↓
Episode of Care
```
BUTANO and the experience layer SHALL be capable of preserving these distinctions longitudinally.
---
# A.5 Clinical findings and diagnoses
ZIBO SHALL operate a layered clinical terminology model.
Priority order:
1. internationally governed clinical concept where legally deployable;
2. WHO-FIC concept where semantically appropriate;
3. ICPC-3 in primary-care contexts;
4. governed Impilo national concept;
5. free-text assertion explicitly marked uncoded.
**ICD statistical classifications SHALL NOT automatically become the clinical concept simply because they are available.**
Where appropriate, one clinical assertion MAY carry multiple codings:
```text
CodeableConcept:
    Impilo national concept
    ICPC-3
    WHO Foundation
    ICD-11 MMS
    SNOMED CT   // when licensed and available
```
Each coding SHALL retain:
* code system;
* version;
* code;
* display;
* provenance;
* mapping source where derived;
* whether directly selected or mapped.
Mapped codes SHALL NOT be represented as if they were clinician-selected.
---
# A.6 SNOMED CT
SNOMED CT SHALL be implemented as a first-class ZIBO terminology target.
ZIBO SHALL implement now:
* RF2 import;
* concepts;
* descriptions;
* language reference sets;
* relationships;
* OWL axioms where required;
* historical associations;
* reference sets;
* module/dependency handling;
* edition/version handling;
* compositional expression storage where required;
* hierarchy traversal;
* subsumption;
* ECL-capable subset selection where feasible;
* SNOMED ConceptMap ingestion;
* national extension capability;
* SNOMED rights gating.
Actual SNOMED CT content SHALL activate only when an authorised licence/distribution is present.
Until then, clinical workflows SHALL remain functional using WHO-FIC, ICPC-3, other available standards and governed Impilo concepts.
The absence of SNOMED licensing SHALL NOT result in:
* fabricated SNOMED identifiers;
* fake SNOMED subsets;
* blocked clinical capture;
* indefinite terminology-engine postponement.
---
# A.7 Functioning, disability and rehabilitation — ICF
ICF SHALL be a first-class ZIBO classification.
It SHALL support:
* body functions;
* body structures;
* activities;
* participation;
* environmental factors;
* qualifiers;
* functioning profiles;
* rehabilitation assessment;
* disability assessment;
* longitudinal functional status.
ICF SHALL be available to:
* rehabilitation;
* physiotherapy;
* occupational therapy;
* speech therapy;
* disability services;
* chronic disease care;
* neurology;
* paediatrics;
* geriatrics;
* mental health;
* community care;
* social-care interfaces.
ICF-CY SHALL NOT be treated as a separate new terminology where its content has been merged back into ICF.
---
# A.8 Procedures and health interventions — ICHI
ICHI SHALL become ZIBO's primary international health-intervention classification.
ZIBO SHALL implement:
* ICHI stem codes;
* Target;
* Action;
* Means;
* extension codes;
* intervention grouping;
* packages/combinations where supported;
* hierarchy;
* search;
* validation;
* mapping from existing Impilo procedure vocabularies.
ICHI SHALL cover, where supported:
* medical interventions;
* surgical interventions;
* diagnostic interventions;
* nursing interventions;
* rehabilitation;
* functioning support;
* mental health;
* allied health;
* primary care;
* public health;
* traditional medicine.
The existing `ImpiloSurgicalProcedures` seed SHALL become a mapped national extension/reference set rather than the national procedure universe.
---
# A.9 Nursing terminology
ZIBO SHALL implement a nursing terminology layer.
The preferred international target SHALL include **ICNP / ICNP SNOMED CT Nursing Practice Refset** where appropriately licensed.
ZIBO SHALL implement:
* ICNP import capability;
* nursing diagnoses/problems;
* nursing interventions;
* nursing outcomes/concepts where present;
* mapping to ICHI;
* mapping to ICF;
* mapping to SNOMED when licensed;
* national nursing extensions.
Where rights prevent immediate content activation, ZIBO SHALL provide governed Impilo nursing concepts while the licence gate is resolved.
The nursing domain SHALL NOT be forced to express every nursing concept as a physician diagnosis.
---
# A.10 Laboratory and diagnostic semantics
## A.10.1 LOINC
LOINC SHALL be fully implemented.
ZIBO SHALL:
* import complete authorised official release content;
* retain statuses;
* retain components/properties;
* support panels;
* support answer lists;
* support document/clinical classes where relevant;
* build Zimbabwe context ValueSets;
* map existing LIMS/local codes;
* support longitudinal version updates.
`LabTestsZW` SHALL become a national/local compatibility layer, not the primary laboratory vocabulary.
## A.10.2 UCUM
UCUM SHALL be implemented as the machine-readable units standard.
ZIBO SHALL validate UCUM codes used in clinical data.
Display strings MAY differ from canonical machine codes but SHALL not replace them.
## A.10.3 WHO electronic Essential Diagnostics List
WHO eEDL SHALL be implemented as an international diagnostics-policy reference.
ZIBO SHALL model:
```text
diagnostic clinical identity      → LOINC or appropriate terminology
WHO essential status             → WHO eEDL
Zimbabwe national policy         → Zimbabwe Essential Diagnostics List
physical IVD product             → device/product terminology + GTIN/UDI
```
WHO eEDL SHALL NOT be mistaken for a laboratory observation terminology.
## A.10.4 Microbiology and organism identity
ZIBO SHALL implement a microbiology organism-identity layer.
Where a deployable clinical terminology already provides an authorised organism code, that code SHALL be preferred.
ZIBO SHALL additionally support a biological taxonomy source such as NCBI Taxonomy for:
* organism identity;
* microbial species;
* genomic/molecular linkage;
* laboratory reconciliation.
It SHALL be treated as a supporting biological taxonomy, not falsely declared to be the sole clinical nomenclature authority.
National microbiology subsets SHALL be governed in ZIBO.
---
# A.11 Medicines semantic stack
ZIBO SHALL implement the medicines stack as distinct semantic layers.
```text
WHO INN
    ↓
substance identity
Zimbabwe National Medicines Dictionary
    ↓
clinical medicinal / pharmaceutical product
ATC/DDD
    ↓
classification and utilisation
EDLIZ
    ↓
Zimbabwe formulary/policy
WHO EML / EMLc / AWaRe
    ↓
international reference/policy
Regulatory/product identity
    ↓
GS1 GTIN / trade item
    ↓
lot + expiry + serial
```
These layers SHALL NOT be collapsed.
---
# A.12 WHO International Nonproprietary Names
WHO INN SHALL be implemented as the authoritative generic active-substance naming layer.
ZIBO SHALL:
* ingest Recommended INN content from authoritative WHO sources;
* preserve current/historical releases;
* support INN synonyms/designations where available;
* preserve substance identity separately from finished medicinal products;
* link ZNMD ingredients to INN;
* preserve INN stems/reference material where useful.
Proposed INNs SHALL NOT automatically enter production as established active-substance names.
If proposed INNs are imported for regulatory awareness, they SHALL have a distinct status from Recommended INNs.
---
# A.13 Zimbabwe National Medicines Dictionary
ZNMD remains mandatory and SHALL be expanded to support:
* active substances;
* ingredient roles;
* strength numerator/denominator;
* concentration;
* dose form;
* route;
* unit of presentation;
* container;
* package;
* combination products;
* clinical drug;
* pharmaceutical product;
* regulated medicinal product;
* brand/trade product;
* manufacturer/MAH;
* regulatory status;
* formulary status;
* EDLIZ;
* programme status;
* ATC;
* WHO EML;
* AWaRe;
* GTIN;
* pack size;
* product lifecycle;
* replacement/discontinuation.
No national medicine concept SHALL use ATC or GTIN as its primary clinical identity.
---
# A.14 ISO IDMP
ZIBO SHALL implement the ISO Identification of Medicinal Products architecture.
The implementation programme SHALL explicitly cover the IDMP family, including where applicable:
* ISO 11615 — medicinal product information;
* ISO 11616 — pharmaceutical product identification;
* ISO 11238 — substances;
* ISO 11239 — dose forms, units of presentation, routes and packaging-related controlled vocabularies;
* ISO 11240 — units of measurement;
* applicable implementation technical specifications;
* current ISO IDMP ontology work;
* PhPID concepts;
* medicinal product identifiers;
* pharmaceutical product identifiers;
* substance identifiers;
* package/product relationships.
Where normative ISO documents have not yet been lawfully acquired, ZIBO SHALL still implement the structural model from lawful sources and existing programme knowledge but SHALL mark formal IDMP conformance:
`NORMATIVE_DOCUMENT_REQUIRED`
until verification is complete.
Formal conformance SHALL NOT be falsely claimed.
---
# A.15 EDQM Standard Terms
ZIBO SHALL implement EDQM Standard Terms where rights/access allow.
They SHALL provide controlled terminology for:
* pharmaceutical dose forms;
* routes/methods of administration;
* units of presentation;
* containers;
* closures;
* administration devices;
* combination terms;
* patient-friendly terms where applicable.
ZNMD SHALL map national medicine attributes to these terms rather than inventing incompatible equivalents where an appropriate international term exists.
---
# A.16 ATC/DDD
ATC/DDD SHALL be fully implemented as a classification layer.
It SHALL support:
* full hierarchy;
* current official version;
* previous versions;
* mapping from ZNMD;
* drug-utilisation analytics;
* DDD metadata where permitted;
* historical version interpretation.
ATC SHALL NOT identify the clinical medicinal product.
---
# A.17 Essential medicines and antimicrobial stewardship
ZIBO SHALL implement:
* EDLIZ;
* WHO electronic/model EML;
* EMLc;
* WHO AWaRe.
Each SHALL remain semantically distinct.
Example:
```text
medicine = ZNMD concept
edliz_status = ESSENTIAL
who_eml_status = LISTED
aware_group = ACCESS
atc = J01...
```
International reference status SHALL never silently become Zimbabwe national policy.
---
# A.18 Pharmacovigilance — MedDRA and WHODrug
## A.18.1 MedDRA
ZIBO SHALL implement MedDRA capability for:
* adverse events;
* adverse drug reactions;
* medical history;
* product-quality issues;
* medication errors;
* investigations;
* indications;
* safety reporting;
* Standardised MedDRA Queries where licensed.
Implementation SHALL include:
* hierarchical levels;
* current and historical releases;
* multilingual distributions where licensed;
* term status;
* mapping;
* hierarchy traversal;
* version migration;
* rights controls.
Production content SHALL activate under an appropriate organisational subscription/licence.
Until then, ZIBO SHALL retain available WHO-FIC/Impilo adverse-event capture and map to MedDRA after activation where clinically valid.
## A.18.2 WHODrug Global
ZIBO SHALL implement WHODrug Global integration capability for pharmacovigilance drug identification.
It SHALL include:
* authorised release ingestion;
* medicinal-product lookup;
* drug coding;
* ATC relationships;
* release/version management;
* mapping to ZNMD;
* mapping to national regulatory products.
Where the national pharmacovigilance programme/regulator has an authorised subscription, ZIBO SHALL use it under the applicable licence.
WHODrug SHALL complement, not replace, ZNMD.
---
# A.19 GS1 — complete healthcare implementation
ZIBO SHALL implement GS1 healthcare semantics beyond merely storing GTIN.
## Identification
Support as appropriate:
* GTIN;
* GLN;
* SSCC;
* GMN;
* relevant GS1 asset/logistics identifiers.
## Data carriers
Support:
* GS1 DataMatrix;
* GS1 Application Identifiers;
* human-readable interpretation.
Minimum healthcare AIs:
```text
(01) GTIN
(10) batch/lot
(11) manufacturing date where present
(17) expiry
(21) serial
```
## Product classification
ZIBO SHALL implement **GS1 Global Product Classification (GPC)** for trade-item classification where applicable.
## Supply-chain relationship
GTIN SHALL identify the trade item.
GLN MAY identify relevant supply-chain parties/locations.
SSCC MAY identify logistics units.
Clinical concepts SHALL remain separate.
---
# A.20 Medical devices
ZIBO SHALL implement a national device semantic stack.
## International layers
Implement:
* WHO MeDevIS;
* EMDN;
* GMDN;
* GS1 UDI;
* GS1 GTIN;
* GS1 GPC;
* applicable IMDRF UDI semantics;
* national regulatory identifiers.
Model:
```text
clinical/device type
      ↓
EMDN / GMDN
WHO reference
      ↓
MeDevIS
national regulated device
      ↓
National Device ID
trade item / model
      ↓
UDI-DI / GTIN / GMN
physical instance
      ↓
UDI-PI
lot
serial
expiry
manufacturing date
```
Device terminology SHALL be available to:
* procurement;
* inventory;
* theatre;
* implants;
* imaging;
* laboratory equipment;
* assistive technology;
* consumables;
* adverse-event reporting;
* maintenance;
* recalls.
GMDN content SHALL activate only under authorised terms.
EMDN and MeDevIS SHALL be implemented independently where lawfully available.
---
# A.21 Medical products of human origin — ISBT 128
ZIBO SHALL implement **ISBT 128**.
This is mandatory for the semantic and identification architecture supporting Madi and any national services involving:
* blood;
* blood components;
* cellular therapy;
* tissues;
* human milk;
* organs;
* other medical products of human origin where the standard applies.
Implementation SHALL include:
* ISBT 128 Standard Terminology;
* Product Description Codes;
* Donation Identification Number handling;
* ABO/Rh and relevant reference tables;
* product attributes;
* facility identifiers;
* barcode/data structures;
* traceability;
* relevant FHIR semantic representations.
ZIBO SHALL parse and validate ISBT 128 identifiers.
Facilities SHALL use legitimately allocated identifiers where registration is required.
Impilo SHALL NOT fabricate globally scoped ISBT identifiers.
---
# A.22 Imaging terminology
ZIBO SHALL implement an imaging semantic stack.
## DICOM controlled terminology
Implement:
* DICOM coding schemes used by the estate;
* Context Groups;
* Context IDs;
* Structured Report controlled terminology relevant to supported workflows;
* imaging anatomy/procedure semantic mappings.
## RadLex
Implement:
* RadLex terminology import;
* hierarchy;
* search;
* imaging concepts;
* mappings;
* version management;
* rights conditions.
## LOINC/RSNA Radiology Playbook
Implement the LOINC/RSNA Radiology Playbook for imaging orderables/procedure naming where applicable.
Imaging semantics SHALL connect:
```text
clinical request
   ↓
LOINC/RSNA or governed imaging orderable
procedure/intervention
   ↓
ICHI
radiology terminology
   ↓
RadLex / DICOM
DICOM study/series
   ↓
actual imaging instance
```
ZIBO SHALL therefore serve imaging semantic content to OROS, imaging order entry, PACS/RIS integration and diagnostic reporting.
---
# A.23 Rare disease and phenotyping
## ORPHAcode
ZIBO SHALL implement Orphanet/ORPHAcode nomenclature.
It SHALL support:
* rare-disease identity;
* hierarchy/classification;
* synonyms;
* current/inactive entities;
* mappings to ICD-10;
* mappings to ICD-11;
* other mappings supplied by the authoritative source;
* versioned annual updates.
ORPHAcode SHALL complement WHO-FIC rather than overwrite it.
## Human Phenotype Ontology
ZIBO SHALL implement HPO for:
* detailed phenotypic abnormalities;
* rare disease workup;
* genetics;
* dysmorphology;
* genomic medicine;
* computable phenotyping.
HPO SHALL be versioned and attribution requirements enforced.
Where relevant:
```text
disease → ORPHAcode / ICD-11
phenotype → HPO
clinical finding → WHO-FIC / national / SNOMED when available
```
---
# A.24 Clinical knowledge and WHO SMART Guidelines
ZIBO SHALL implement the semantic components of WHO SMART Guidelines and Digital Adaptation Kits.
ZIBO SHALL acquire and version relevant DAK assets including:
* core data dictionaries;
* terminology bindings;
* coded data elements;
* ValueSets;
* indicator definitions;
* semantic mappings;
* workflow identifiers;
* canonical data-element identifiers.
ZIBO SHALL NOT become the clinical rules engine.
Division of responsibility:
```text
ZIBO
    semantic identities
    terminology bindings
    ValueSets
    data dictionaries
    mappings
    versions
CKP
    executable clinical recommendations
    pathway logic
    scheduling
    decision support
Sorojena
    indicator computation
    analytics definitions
Experience layer
    forms
    workflows
    user interaction
```
Relevant WHO SMART/DAK content SHALL be incorporated as it becomes available across areas such as:
* antenatal care;
* family planning;
* postnatal care;
* immunisation;
* HIV;
* TB;
* infectious-disease surveillance;
* maternal/newborn care;
* self-monitoring/remote care;
* future WHO SMART domains relevant to Zimbabwe.
---
# A.25 WHO growth and anthropometric reference standards
ZIBO SHALL implement WHO clinical reference datasets for growth.
At minimum:
* WHO Child Growth Standards 0–5 years;
* WHO Growth Reference 5–19 years;
* supported anthropometric indicators;
* z-score reference data;
* growth-velocity reference data where officially provided.
Separation:
```text
measurement identity → LOINC
unit                 → UCUM
reference dataset    → WHO Growth Standards
interpretation       → CKP/reference calculation service
```
ZIBO SHALL version and distribute the authoritative reference data and identify which reference/version was used for an interpretation.
A calculated z-score SHALL be reproducible historically.
---
# A.26 WHO UHC Compendium
ZIBO SHALL implement the semantic health-action content of the WHO UHC Compendium as a reference classification/catalogue.
It SHALL be usable to map:
```text
WHO health action
       ↓
ICHI intervention
       ↓
Zimbabwe national service
       ↓
facility capability
       ↓
benefit/coverage rule
       ↓
costing/billing
```
The WHO UHC Compendium SHALL remain an international reference layer and SHALL NOT automatically define Zimbabwe's national benefit package.
---
# A.27 Mortality and CRVS semantics
ZIBO SHALL provide mortality semantics to UBOMI/CRVS including:
* ICD mortality classifications;
* ICD-11 mortality semantics;
* historical ICD-10 mortality compatibility;
* WHO Verbal Autopsy semantic/reference assets;
* cause-of-death lists;
* mappings required by national mortality systems;
* maternal/perinatal cause-of-death semantic support.
The VA questionnaire/workflow itself may execute outside ZIBO, but its semantic assets and versions SHALL be registered and governed by ZIBO.
---
# A.28 Cancer semantics
ZIBO SHALL implement:
* ICD-11 neoplasm semantics;
* ICD-O topography;
* ICD-O morphology;
* behaviour;
* grade;
* mappings between cancer terminologies where authoritative.
The architecture SHALL support later implementation of additional oncology standards such as tumour staging systems without redesigning ZIBO.
Specialty terminology SHALL be implemented through modular source adapters.
---
# A.29 Cross-cutting reference code systems
ZIBO SHALL also govern commonly required national reference CodeSystems where appropriate.
These SHALL include authoritative implementations/mappings for:
* FHIR core CodeSystems and required ValueSets;
* ISO country codes where required;
* language identifiers / BCP 47-compatible language tags;
* currencies where required;
* administrative sex/gender code systems required by interoperability specifications;
* relationship/kinship code systems where nationally governed;
* provider specialties;
* facility types;
* service types;
* encounter types;
* programme classifications;
* confidentiality/sensitivity codes.
ZIBO SHALL distinguish international reference systems from national administrative vocabularies.
---
# A.30 Semantic bindings to major FHIR clinical elements
The following minimum bindings SHALL exist.
| Clinical element                  | Primary semantic sources                                                     |
| --------------------------------- | ---------------------------------------------------------------------------- |
| Reason for Encounter              | ICPC-3                                                                       |
| Presenting complaint              | ICPC-3 + WHO-FIC/clinical concepts                                           |
| Symptom/sign                      | ICPC-3, WHO-FIC Foundation, national clinical concepts, SNOMED when licensed |
| Condition/problem                 | WHO-FIC / ICPC-3 / national clinical concepts / SNOMED when licensed         |
| Statistical diagnosis             | ICD-11 MMS                                                                   |
| Function/disability               | ICF                                                                          |
| Intervention/procedure            | ICHI + domain terminology                                                    |
| Imaging order                     | LOINC/RSNA Playbook + ICHI                                                   |
| Imaging terminology               | RadLex + DICOM                                                               |
| Laboratory observation            | LOINC                                                                        |
| Unit                              | UCUM                                                                         |
| Organism                          | authorised clinical terminology + supporting biological taxonomy             |
| Medication substance              | WHO INN                                                                      |
| Clinical medicine                 | ZNMD                                                                         |
| Medicine classification           | ATC                                                                          |
| Zimbabwe formulary                | EDLIZ                                                                        |
| International medicines reference | WHO EML/EMLc/AWaRe                                                           |
| Medicine trade item               | GS1 GTIN                                                                     |
| Vaccine                           | governed vaccine/ZNMD concept + product linkage                              |
| Allergy substance                 | governed allergen terminology / SNOMED when licensed                         |
| Reaction/adverse event            | clinical terminology + MedDRA where licensed                                 |
| Device type                       | EMDN/GMDN                                                                    |
| Device reference                  | MeDevIS                                                                      |
| Device trade identity             | UDI/GTIN                                                                     |
| Blood/MPHO product                | ISBT 128                                                                     |
| Rare disease                      | ORPHAcode + ICD-11                                                           |
| Phenotype                         | HPO                                                                          |
| Oncology morphology/site          | ICD-O                                                                        |
| Nursing concepts                  | ICNP + ICHI/ICF mappings                                                     |
| Mortality                         | ICD + WHO VA semantics                                                       |
Bindings SHALL generally be expressed through ValueSets and profiles rather than hard-coded application arrays.
---
# A.31 Multiple coding rule
FHIR `CodeableConcept` is capable of carrying multiple codings.
Impilo SHALL use that capability deliberately.
Example:
```text
Patient selected / clinician selected:
    ICPC-3 presenting complaint
Mapped:
    WHO Foundation concept
    ICD-11 MMS category
Later enrichment:
    SNOMED CT concept
```
Each coding SHALL indicate whether it was:
```text
DIRECT
MAPPED_EXACT
MAPPED_BROADER
MAPPED_NARROWER
MAPPED_RELATED
INFERRED
LEGACY
```
A mapped coding SHALL never be indistinguishable from a directly recorded coding.
---
# A.32 Mapping model
`ConceptMap` SHALL be upgraded from a small convenience feature into national semantic infrastructure.
Every mapping SHALL carry, where applicable:
```text
source_system
source_version
source_code
target_system
target_version
target_code
equivalence
mapping_method
confidence
source_authority
reviewed_by
reviewed_at
effective_period
mapping_version
comment
```
Mappings SHALL be versioned.
A change to a mapping SHALL not rewrite historical clinical data.
ZIBO SHALL support mapping chains, but applications SHOULD prefer authoritative direct mappings where available rather than deriving a chain silently.
---
# A.33 Rights-aware distribution
Every semantic source SHALL have explicit rights metadata.
Rights SHALL control:
* central import;
* internal use;
* display;
* search;
* mapping;
* export;
* redistribution;
* offline packaging;
* API exposure;
* third-party access;
* national extension;
* derivative content.
The Bundle Publisher SHALL evaluate rights **per terminology release**, not only per bundle.
A bundle MAY therefore contain:
```text
LOINC          included
ICD-11         included
ICF            included
ICHI           included
national codes included
SNOMED         excluded: licence not valid for target node
MedDRA         excluded or encrypted/licence-scoped
```
Clinical nodes SHALL still function when a licensed terminology is excluded, using the available national/international alternatives defined by policy.
---
# A.34 Release acquisition and update service
ZIBO SHALL implement a standard source-adapter interface.
Conceptually:
```text
TerminologySourceAdapter {
    discoverCurrentRelease()
    compareInstalledRelease()
    acquire()
    verifyChecksum()
    verifyRights()
    parse()
    validateSource()
    stage()
    buildIndex()
    runRegressionTests()
    publish()
    buildMappings()
    generateReport()
}
```
Each implemented external standard SHALL have its own adapter or appropriate reusable provider adapter.
Updates SHALL NOT auto-promote to production merely because a newer source exists.
Flow:
```text
DISCOVER
→ ACQUIRE
→ VERIFY
→ IMPORT
→ STAGE
→ TEST
→ CLINICAL/SEMANTIC REVIEW where required
→ APPROVE
→ PUBLISH
→ DISTRIBUTE
```
---
# A.35 Source lock and reproducibility
Every installed standard SHALL be represented in a national semantic source lock.
Example:
```yaml
source: LOINC
authority: Regenstrief Institute
version: <publisher version>
release_date: <date>
checksum: <sha256>
raw_source: <immutable path>
importer_version: <git sha/version>
rights_manifest: <id>
artifact_count: <n>
concept_count: <n>
status: ACTIVE
```
Equivalent records SHALL exist for all sources.
Any clinical interpretation SHALL be reproducible against the terminology/reference version in use at the time.
---
# A.36 Sovereign national extensions
Where international standards do not cover a Zimbabwe requirement, ZIBO SHALL create a national concept.
National concept URIs SHALL be unmistakably national.
Example family:
```text
https://terminology.impilo.gov.zw/CodeSystem/clinical
https://terminology.impilo.gov.zw/CodeSystem/medicine
https://terminology.impilo.gov.zw/CodeSystem/vaccine-product
https://terminology.impilo.gov.zw/CodeSystem/allergen
https://terminology.impilo.gov.zw/CodeSystem/procedure
https://terminology.impilo.gov.zw/CodeSystem/nursing
https://terminology.impilo.gov.zw/CodeSystem/device
```
National codes SHALL be designed for permanence.
They SHALL NOT encode transient implementation details such as facility IDs in the semantic identifier.
---
# A.37 No-code-fabrication rule
Under no circumstances SHALL Impilo:
* generate fake SNOMED codes;
* generate fake ICD codes;
* generate fake ATC codes;
* generate fake LOINC codes;
* fabricate GTINs;
* fabricate ISBT identifiers;
* claim ISO conformance without verification;
* transform national concepts into international-looking identifiers.
Missing external content results in:
```text
governed Impilo code
```
or:
```text
uncoded/unmapped text
```
not counterfeit semantics.
---
# A.38 National semantic governance board
ZIBO SHALL support governance across at least:
* clinical terminology;
* primary care;
* medicines/pharmacy;
* diagnostics/laboratory;
* nursing;
* rehabilitation;
* imaging;
* oncology;
* blood/transfusion;
* devices;
* public health;
* CRVS;
* data standards;
* health informatics;
* regulatory representation.
Each governed national vocabulary SHALL have:
* accountable owner;
* steward;
* review workflow;
* issue process;
* release process;
* retirement/replacement mechanism.
---
# A.39 ZIBO implementation definition
A standard SHALL NOT be marked `IMPLEMENTED` because:
* its name appears in documentation;
* a database table exists;
* an importer exists;
* a ZIP exists on disk;
* five demonstration codes are seeded;
* a UI says the standard is available;
* there is a mapping column named after it.
`IMPLEMENTED` requires all applicable items:
1. authoritative release identified;
2. rights determined;
3. raw source preserved;
4. importer implemented;
5. full permitted content loaded;
6. version preserved;
7. canonical identifiers preserved;
8. hierarchy loaded where present;
9. designations loaded where present;
10. search works;
11. lookup works;
12. validation works;
13. expansion works where applicable;
14. subsumption works where applicable;
15. mappings work where applicable;
16. real consumer contract proven;
17. telemetry exists;
18. governance owner exists;
19. offline rights known;
20. release state is visible.
For gated content, `IMPLEMENTED_RIGHTS_GATED` requires all engineering items above that can legally be completed without distributing the protected content.
---
# A.40 Revised strategic interpretation of ZIBO
ZIBO is hereby defined as:
> **Zimbabwe's national semantic authority for health — providing computable meaning, classifications, terminology, nomenclature, mappings, identifier semantics and reference data across clinical care, public health, medicines, diagnostics, devices, health interventions and disconnected operation.**
It SHALL answer:
1. **What does this concept mean?**
2. **What terminology/classification does it belong to?**
3. **Is it valid in this context and version?**
4. **What are its parents, children and related concepts?**
5. **How does it map to another standard?**
6. **What national policy or formulary applies to it?**
7. **Which physical product or identifier represents it?**
8. **Can this node interpret it while offline?**
9. **Which authoritative release produced this meaning?**
10. **Are we legally entitled to distribute it here?**
That is the target state.
This Addendum is normative and supersedes any narrower interpretation of ZIBO in Master Specification v0.3.
