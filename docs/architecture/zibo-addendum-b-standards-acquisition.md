<!--
SOURCE OF TRUTH. Authored by the Product Owner and delivered to the ZIBO programme
on 2026-08-08. Committed verbatim from the originating session transcript so the text
survives outside a conversation. Do not edit the normative body; record deviations in
the implementation report instead.
-->

# ADDENDUM B — COMPREHENSIVE STANDARDS ACQUISITION AND IMPLEMENTATION DIRECTIVE
Applies to: ZIBO Execution Directive v0.3
Status: NORMATIVE EXECUTION ADDENDUM
Date: 2026-08-08
B.0 Mission
Implement the full semantic scope defined by ZIBO Master Specification v0.3 + Addendum A.
Do not merely make ZIBO “aware” of the standards.
Do not return a report saying that the standards would be useful.
Do not create a standards roadmap and stop.
Do not seed representative examples and claim support.
Implement them.
For every source listed below:

* inspect the actual estate first;
* determine whether content is already present;
* discover the current official publisher release;
* determine rights/access;
* acquire legally available source content;
* preserve it;
* import it;
* index it;
* activate it;
* expose it through ZIBO;
* map current local content;
* test it;
* prove real queries;
* report exact blockers for anything that cannot legally be activated.

Where content is licence-gated, complete the engine/import/activation pathway so that adding an authorised package requires configuration/content activation rather than another engineering project.
B.1 First inspect the estate
Before implementation, inventory all existing semantic assets across the repository, databases, VM and deployment environment.
Search for:

```text
ICD
ICD10
ICD11
WHO-FIC
ICF
ICHI
ICPC
SNOMED
LOINC
UCUM
ATC
DDD
EDLIZ
INN
EML
AWaRe
MedDRA
WHODrug
IDMP
EDQM
GS1
GTIN
GPC
UDI
GMDN
EMDN
MeDevIS
DICOM
RadLex
Playbook
ISBT
ICNP
ORPHA
HPO
vaccine
allergen
organism
procedure
diagnosis
symptom
complaint
reason for encounter
terminology
coding
CodeSystem
ValueSet
ConceptMap

```

Inspect:

* ZIBO;
* BUTANO;
* OROS;
* Dura/eLMIS;
* NatPharm integrations;
* LIMS;
* MSIKA;
* Madi;
* CKP;
* UBOMI;
* PCT;
* Rito;
* Daidzai;
* Sorojena;
* experience-bff;
* mobile apps;
* facility/hospital node bundles;
* existing import directories;
* mounted data directories;
* secrets/configuration;
* object stores;
* historical EHR migration data.

Produce an evidence-based inventory.
Then implement.
B.2 Upgrade the ZIBO engine before bulk content
The new standards require hierarchical semantics.
Implement migrations and repository support for:

```text
zibo_concept
zibo_concept_relationship
zibo_concept_ancestor
zibo_designation
zibo_source_release
zibo_rights_manifest
zibo_source_lock
zibo_mapping_provenance

```

Exact naming may follow repository conventions.
Requirements:

* artifact JSON remains authoritative where appropriate;
* projections remain rebuildable;
* relationship closure is derived;
* indexes are tenant/authority aware;
* national fallback remains correct;
* external canonical URIs remain unchanged.

Implement:

```text
CodeSystem/$lookup
CodeSystem/$validate-code
CodeSystem/$subsumes
ValueSet/$expand
ValueSet/$validate-code
ConceptMap/$translate

```

Add:

* parent/child API;
* ancestor/descendant API;
* fuzzy terminology search;
* ValueSet-scoped search;
* language/designation search;
* system/version filtering;
* inactive concept behaviour;
* replaced-by behaviour.

Do not bulk-load hierarchical standards into a flat table without their relationships.
B.3 Implement source-adapter architecture
Create a reusable source-provider interface.
It must support:

```text
discover release
download/acquire
authenticate where configured
checksum/signature verification
rights verification
unpack
parse
validate
stage
diff
import
build hierarchy
build search index
build mappings
test
publish
rollback

```

Each source adapter SHALL generate an acquisition report.
A standard-specific adapter may reuse common CSV/XML/JSON/FHIR/RF2/OWL loaders.
B.4 WHO-FIC implementation
Implement WHO-FIC as a coherent family.
WHO ICD local infrastructure
Inspect whether the official WHO ICD local API is already deployed.
If absent and legally obtainable:

* acquire the current official WHO local ICD API distribution;
* deploy it in the preview/development estate;
* persist required data;
* configure local/offline operation;
* health-check it;
* record exact version/release.

Implement ZIBO adapters over both:

* ICD-11 Foundation;
* ICD-11 MMS.

Do not expose only MMS if Foundation semantics are accessible.
Preserve:

* Foundation URIs;
* linearization codes;
* titles;
* synonyms/designations where provided;
* hierarchy;
* exclusions/inclusions as available;
* extension codes;
* post-coordination metadata where exposed;
* version/release.

Proof:

```text
search Foundation
lookup Foundation URI
lookup MMS code
walk hierarchy
validate code
subsumes
map Foundation ↔ MMS where official relationship exists
offline WHO API test

```

B.5 ICF implementation
Acquire the current authoritative ICF source through WHO-supported mechanisms.
Implement:

* complete allowed content;
* hierarchy;
* codes;
* terms;
* environmental factors;
* qualifiers/reference semantics;
* current version;
* historical/update handling.

Implement ICF search and expansion.
Create starter Impilo ValueSets for:

* mobility;
* self-care;
* communication;
* cognition;
* activities of daily living;
* participation;
* environmental factors;
* rehabilitation assessment.

Do not manually invent replacement functioning codes where ICF provides them.
Proof:

* lookup;
* search;
* hierarchy;
* subsumption;
* ValueSet expansion;
* FHIR integration test for structured functioning assessment.

B.6 ICHI implementation
Acquire the current authoritative ICHI release.
Implement:

* complete permitted classification;
* Target;
* Action;
* Means;
* extension codes;
* hierarchy;
* combination semantics supported by the source;
* current version.

Map existing:

* `ImpiloSurgicalProcedures`;
* OROS procedures;
* CKP procedures/interventions;
* theatre procedure dictionaries;
* rehabilitation interventions;
* nursing interventions where suitable;
* public-health interventions where suitable.

Do not delete local codes if no exact ICHI mapping exists.
Instead:

```text
local concept
→ ConceptMap
→ ICHI

```

Proof:

* procedure typeahead;
* `$lookup`;
* `$validate-code`;
* `$subsumes`;
* surgery mapping;
* diagnostic-intervention mapping;
* rehabilitation mapping.

B.7 ICPC-3 implementation
Acquire the current official ICPC-3 distribution under the current WONCA licensing mechanism.
Record the exact Creative Commons/licence terms actually supplied with the distribution.
Import full available content including:

* codes;
* preferred terms;
* inclusion terms;
* synonyms;
* chapters;
* components;
* relationships;
* metadata.

Configure ICPC-3 as the primary ZIBO terminology for:

* Reason for Encounter;
* presenting complaint;
* primary-care symptom;
* primary-care problem;
* primary-care process.

Expose real searchable ValueSets to the UI.
Do not reduce ICPC-3 to a static list in frontend code.
Proof:

* search “headache”;
* search common Zimbabwe primary-care presentations;
* capture multiple presenting complaints;
* preserve free narrative;
* map to corresponding WHO/ICD concepts only where a valid mapping exists.

B.8 ICPC-2 legacy migration
Locate:

* exact legacy EHR ICPC-2 table;
* version/edition;
* local additions;
* translations/synonyms;
* usage counts in historical data.

Create a frozen historical ZIBO representation.
Classify each legacy concept:

```text
OFFICIAL_ICPC2
LOCAL_EXTENSION
UNKNOWN_PROVENANCE

```

Acquire an authoritative ICPC-2 → ICPC-3 mapping where available and rights permit.
Where no official mapping exists:

* create a mapping-review queue;
* do not auto-map by text similarity and call it authoritative.

Preserve original ICPC-2 coding in historical records.
Proof:

* old record remains interpretable;
* equivalent ICPC-3 suggestion available where mapped;
* no historical rewrite.

B.9 SNOMED implementation
Implement SNOMED technical capability fully now.
Support RF2:

```text
Concept
Description
TextDefinition where applicable
Relationship
Concrete values where applicable
OWL expression refset
Language refsets
Association refsets
Simple/complex refsets required by implementation
Module dependencies

```

Implement:

* hierarchy;
* subsumption;
* historical associations;
* national extension architecture;
* edition dependency;
* versioned import;
* ECL-compatible evaluation or a well-defined subset sufficient for ValueSet generation.

Search existing secrets/files for authorised SNOMED credentials/distributions.
If authorised:

* import;
* activate;
* prove.

If not:

```text
status = LICENCE_REQUIRED

```

Do not scrape SNOMED browsers.
Do not use unofficial distributions.
The rest of ZIBO continues.
B.10 Nursing / ICNP
Inspect whether ICNP/SNOMED nursing content is already licensed or present.
Implement the ICNP importer/refset handling.
If an authorised ICNP distribution or SNOMED affiliate route exists:

* load current ICNP nursing reference content;
* preserve version;
* expose nursing ValueSets.

If absent:

* record licence blocker;
* implement national nursing CodeSystem;
* map to ICHI and ICF where valid;
* keep the ICNP activation path ready.

Proof:

* nursing diagnosis/problem capture;
* nursing intervention capture;
* functioning link where appropriate;
* no forced physician-diagnosis terminology.

B.11 LOINC implementation
Acquire the current official LOINC release using configured authorised access.
Preserve the entire authorised release bundle.
Import all useful core content rather than a tiny national subset.
At minimum support:

* LOINC main table;
* status;
* component;
* property;
* time;
* system;
* scale;
* method;
* class;
* panels;
* answer lists where relevant;
* consumer/display names where available;
* accessory files useful to mappings.

Reconcile:

```text
LabTestsZW
LIMS dictionaries
OROS test orderables
CKP observation definitions

```

Create Zimbabwe ValueSets rather than modifying official LOINC.
Proof:

* real concept counts;
* search;
* panels;
* lookup;
* ValueSet expansion;
* local → LOINC mapping report.

B.12 UCUM implementation
Acquire the official UCUM implementer artifacts.
Implement a real UCUM parser/validator or approved library integration.
Do not treat UCUM as merely a list of common strings.
Validate units used by:

* laboratory results;
* vital signs;
* anthropometry;
* medicine strengths;
* infusion rates;
* renal measures;
* clinical calculations.

Build automated tests for common Zimbabwe clinical units.
B.13 WHO eEDL implementation
Acquire the current official WHO electronic Essential Diagnostics List.
Import its semantic content as a `POLICY_LIST` / `REFERENCE_DATASET`.
Map diagnostic items to:

* LOINC;
* ICHI where intervention classification is relevant;
* device/IVD product categories where relevant.

Do not claim WHO eEDL entries are LOINC concepts.
Create a separate Zimbabwe Essential Diagnostics policy layer capable of adopting or varying from WHO recommendations.
B.14 WHO INN implementation
Acquire current authoritative WHO INN content.
Prioritise:

* Recommended INNs;
* historical Recommended INNs;
* current status.

Treat Proposed INNs separately.
Build/update:

```text
INN substance
→ ZNMD ingredient
→ pharmaceutical/medicinal products

```

Search current medicine masters for ingredient names not mapped to an INN.
Produce reconciliation output.
Do not treat trade names as INNs.
B.15 ATC/DDD implementation
Acquire the current authorised WHO ATC/DDD electronic release if credentials permit.
Do not scrape the public index to synthesize a bulk terminology.
Implement hierarchy and versioning.
Map:

```text
ZNMD medicine → ATC
existing ATC seed → official ATC
Dura/eLMIS items → ZNMD → ATC

```

Preserve DDD metadata where licensed/permitted.
Report exact credential blocker if full electronic distribution cannot be obtained.
B.16 EDLIZ implementation
Locate the most authoritative current EDLIZ source controlled by MoHCC.
Search:

* repository;
* VM;
* programme data;
* pharmacy directorate assets;
* eLMIS imports;
* documents already converted to machine-readable structures.

Create a reproducible machine-readable national formulary release.
Do not infer the complete national list from the existing 20 concepts.
Map every EDLIZ item to ZNMD.
Produce:

* mapped;
* partially mapped;
* ambiguous;
* unmapped;
* duplicate;
* retired;

reports.
B.17 WHO EML, EMLc and AWaRe
Acquire current official electronic/machine-readable WHO content.
Import:

* WHO EML;
* EMLc;
* AWaRe.

Keep source identities distinct.
Map to:

* WHO INN;
* ZNMD;
* ATC;
* EDLIZ.

Do not let WHO EML status overwrite EDLIZ national policy.
Expose stewardship queries such as:

```text
EDLIZ medicines not in WHO EML
WHO EML medicines not in EDLIZ
antibiotics by AWaRe category
facility antibiotic utilisation by AWaRe

```

B.18 ZNMD implementation
Implement the expanded Zimbabwe National Medicines Dictionary schema.
Model at least:

```text
Substance
Ingredient
Strength
Dose form
Route
Unit of presentation
Pharmaceutical product
Clinical medicinal product
Regulated medicinal product
Trade product
Package
Manufacturer / MAH
Regulatory status
Formulary relationships
Classification relationships
GTIN relationships

```

Use stable Impilo IDs.
Migrate existing seed content.
Do not throw away provenance.
B.19 ISO IDMP implementation
Inspect repository/docs for legitimately acquired ISO IDMP material.
Implement the data model to cover:

```text
ISO 11238
ISO 11239
ISO 11240
ISO 11615
ISO 11616
relevant ISO implementation specifications
PhPID
medicinal product identification
substance identification
pharmaceutical product identity

```

Also account for current IDMP ontology work.
If normative documents are unavailable:

```text
formal_conformance = NORMATIVE_DOCUMENT_REQUIRED

```

Do not pirate them.
Implementation continues.
Generate an IDMP gap/conformance matrix showing exactly which ZNMD structures correspond to each IDMP concept and which need correction after normative review.
B.20 EDQM Standard Terms
Check authorised access.
Acquire and import current Standard Terms where permitted.
Implement:

* dose forms;
* routes;
* methods of administration;
* units of presentation;
* containers;
* closures;
* administration devices;
* combinations;
* permitted designations.

Map existing medicine master free-text attributes.
Generate unmapped/ambiguous report.
B.21 MedDRA implementation
Search for an existing organisational MedDRA subscription.
If available:

* acquire current release;
* import all authorised hierarchy levels;
* load translations permitted by licence;
* implement SMQs where authorised;
* activate pharmacovigilance search/validation.

If no authorised production subscription exists:

* build and test importer using only permissible development fixtures/schema;
* create `LICENCE_REQUIRED`;
* prepare subscription activation configuration;
* continue adverse-event capture with lawful alternatives.

Do not redistribute MedDRA outside licence terms.
Integrate with:

* Rito;
* medication adverse events;
* vaccine safety;
* device adverse events;
* pharmacovigilance workflows.

B.22 WHODrug Global implementation
Search for existing UMC/WHODrug access through:

* national pharmacovigilance programme;
* regulator;
* existing project secrets;
* authorised programme files.

If an authorised subscription exists:

* acquire current release;
* load;
* map to ZNMD;
* map to ATC;
* version.

If not:

* implement production-ready import and mapping capability;
* create `LICENCE_REQUIRED` or `CREDENTIAL_REQUIRED` as appropriate.

Do not replace ZNMD with WHODrug.
B.23 GS1 healthcare implementation
Implement a production-grade GS1 parser.
Support at minimum:

```text
GTIN
GLN
SSCC
GMN where relevant
GS1 DataMatrix
GS1 Application Identifiers

```

Required AIs:

```text
01 GTIN
10 batch/lot
11 manufacture date
17 expiry
21 serial number

```

Implement check-digit validation.
Implement variable-length AI parsing correctly.
Do not use simplistic string slicing.
Map actual GTINs from:

* Dura/eLMIS;
* NatPharm;
* MSIKA;
* regulatory product masters;
* hospital item masters.

Implement GPC ingestion/classification where official content permits.
Proof:

* valid scan;
* malformed scan rejection;
* check-digit rejection;
* batch;
* expiry;
* serial;
* GTIN → trade item → ZNMD medicine.

B.24 Device nomenclature implementation
Acquire/import authorised current:

* WHO MeDevIS;
* EMDN;
* GMDN where rights allow.

Implement national device model.
Map:

```text
national device
→ EMDN
→ GMDN where authorised
→ MeDevIS reference
→ GPC where applicable
→ UDI-DI / GTIN

```

Implement GS1 UDI structures.
Support UDI-DI / UDI-PI distinction.
Inspect product/device inventory in:

* Dura;
* laboratories;
* theatre;
* imaging;
* biomedical engineering;
* facilities;
* MSIKA.

Generate device reconciliation report.
B.25 ISBT 128 implementation
Implement ISBT 128 for Madi and MPHO workflows.
Acquire current official technical terminology/reference assets permitted for use.
Implement:

* Donation Identification Number parsing/validation;
* Product Description Codes;
* product terminology;
* blood group reference tables;
* facility identifier semantics;
* required barcode/data structures;
* versioned reference tables.

Do not invent ICCBBA facility identifiers.
Inspect whether Zimbabwe blood services/facilities already have ISBT registrations/codes.
Link:

```text
ISBT product identity
→ Madi blood product
→ actual donation/unit

```

Build FHIR integration tests consistent with ICCBBA/FHIR guidance where applicable.
B.26 Imaging semantic implementation
DICOM
Load/implement relevant DICOM controlled terminology and Context Groups used in supported workflows.
Identify currently used DICOM coding schemes from PACS/RIS integrations.
RadLex
Acquire authorised RadLex release.
Import:

* concepts;
* synonyms;
* hierarchy;
* imaging anatomy and findings used by current workflows.

LOINC/RSNA Radiology Playbook
Acquire official permitted release.
Implement imaging orderables.
Map existing imaging orders and procedure names.
Produce:

```text
local imaging order
→ Playbook/LOINC
→ ICHI
→ DICOM/RadLex semantics

```

Do not make imaging procedure identity depend on free-text labels.
B.27 ORPHAcode implementation
Acquire the current official Orphanet nomenclature pack.
Preserve licence/attribution.
Import:

* ORPHAcodes;
* preferred names;
* synonyms;
* hierarchy/classifications;
* active/inactive status;
* mappings supplied by authoritative source;
* differential release data.

Integrate into rare-disease workflows.
Do not replace ICD-11 coding; support both.
B.28 HPO implementation
Acquire the current HPO release.
Preserve:

* version;
* terms;
* synonyms;
* hierarchy;
* relationships;
* required attribution.

Implement phenotype search.
Integrate with:

* rare disease;
* paediatric dysmorphology;
* genetics/genomics;
* undiagnosed disease workflows.

Do not alter HPO source content.
Use national supplements/mappings for Zimbabwe-specific designations.
B.29 Organism/taxonomy implementation
Implement an organism identity layer.
Acquire an appropriate current supporting biological taxonomy source such as NCBI Taxonomy.
Create mappings from local laboratory organism names.
Do not misrepresent NCBI taxonomy as the final clinical authority.
Where SNOMED/WHO coded organism concepts are available, map them appropriately.
Required output:

```text
local organism string
normalized organism identity
clinical coding where available
taxonomy identifier
mapping confidence

```

B.30 WHO SMART Guidelines / DAKs
Inventory all published WHO SMART Guidelines relevant to Impilo.
Acquire machine-readable assets, not merely PDFs.
Import into ZIBO:

* data-element dictionaries;
* terminology bindings;
* ValueSets;
* coded choices;
* canonical identifiers;
* indicator semantic definitions;
* version metadata.

Wire:

* CKP to decision logic;
* ZIBO to terminology;
* Sorojena to indicators;
* experience layer to forms/workflows.

Do not duplicate decision logic inside ZIBO.
Build a WHO SMART content catalogue in ZIBO showing:

```text
domain
WHO publication/version
data dictionary installed
terminology bindings installed
CKP logic installed
FHIR IG installed
national adaptation
status

```

B.31 WHO growth standards
Acquire official computable reference data for:

* 0–5 years;
* 5–19 years;
* supported anthropometric indicators;
* velocity datasets where applicable.

Preserve raw official source.
Implement versioned reference dataset ingestion.
Wire:

```text
LOINC measurement
+ UCUM unit
+ sex/age/context
+ WHO reference version
→ z-score / percentile interpretation

```

Calculation code may live in CKP/reference-calculation service but ZIBO SHALL govern/distribute the authoritative reference dataset/version.
Prove with known WHO examples.
B.32 WHO Verbal Autopsy
Acquire the official WHO 2022 VA semantic/reference assets and subsequent official supporting updates where applicable.
Register:

* questionnaire version;
* data dictionary;
* coded answers;
* cause-of-death list;
* mappings;
* analytical/output terminology.

Wire UBOMI/CRVS to use the governed version.
Do not modify WHO content and still call it the original WHO VA instrument.
National adaptations SHALL be separately versioned.
B.33 WHO UHC Compendium
Acquire current machine-readable/downloadable health-action content.
Import as a reference catalogue.
Map where possible:

```text
WHO health action
→ ICHI
→ Impilo service
→ facility capability

```

Do not automatically make WHO content national benefit policy.
Expose it as a reference layer for planning and coverage design.
B.34 ICD-O
Acquire current official ICD-O content under applicable rights.
Implement:

* topography;
* morphology;
* behaviour;
* grade where represented;
* hierarchy/lookup;
* mappings.

Wire to:

* oncology;
* pathology;
* cancer registry;
* BUTANO cancer records.

Do not approximate tumour morphology with general ICD diagnosis codes when ICD-O is required.
B.35 FHIR core terminology
Inventory all FHIR code systems and required ValueSets currently hard-coded across services/frontends.
Move governed/shared ones into ZIBO where appropriate.
Do not unnecessarily copy immutable FHIR specification constants if a client library already supplies them reliably, but ZIBO SHALL be able to validate the code systems used in persisted/exchanged Impilo records.
Generate a report of:

```text
FHIR codes hard-coded in UI
FHIR codes hard-coded in services
codes persisted in DB
canonical ZIBO binding

```

Remove dangerous duplicated code lists.
B.36 Cross-cutting international/national reference systems
Implement authoritative reference systems required by the estate, including as applicable:

* country;
* language;
* currency;
* national administrative geographies;
* provider specialty;
* facility type;
* service type;
* programme;
* encounter type;
* relationship;
* confidentiality/sensitivity.

Do not invent duplicate CodeSystems when an existing national/international authoritative one already serves the purpose.
B.37 Map the entire current estate
Run an estate-wide semantic inventory.
For every column/field matching:

```text
code
coding
code_system
coding_system
terminology
classification
type
category
status
reason
diagnosis
procedure
medicine
test
unit
device
product
symptom
complaint

```

determine:

```text
current source
intended standard
actual standard
whether validated
whether mapped
whether free text
whether silently defaulted

```

Produce a Semantic Debt Register.
Priority remediation:

1. fake-coded data;
2. defaults claiming an international standard where code is actually local/free text;
3. unresolved mapping endpoints;
4. duplicate incompatible local vocabularies;
5. unversioned coding;
6. codes without system;
7. systems without canonical URI.

B.38 Capture migration
Implement UX changes only after the terminology API works.
Pickers SHALL use ZIBO APIs.
Required examples:
Presenting complaint
ICPC-3 multi-select + narrative.
Diagnosis
contextual clinical concept search + classification mappings.
Procedure
ICHI-backed search.
Lab
LOINC.
Unit
UCUM.
Medicine
ZNMD.
Device
national device + EMDN/GMDN + UDI/GTIN linkage.
Blood
ISBT 128.
Functioning
ICF.
Do not ship thousands of concepts to the browser as static JSON.
B.39 Offline semantic bundle expansion
Update terminology bundles to include all rights-permitted semantic assets required by the target node.
A Hospital Node bundle may contain:

```text
ICPC-3
WHO-FIC / ICD-11
ICF
ICHI
LOINC
UCUM
ZNMD
EDLIZ
ATC
WHO reference lists
national ValueSets
national ConceptMaps
device nomenclature
imaging terminology
ISBT 128 content
reference datasets

```

only where rights permit.
Implement:

* source-specific rights evaluation;
* node-specific entitlement;
* content hashes;
* version lock;
* local hierarchy/index rebuild;
* activation validation.

Licensed content SHALL not leak to an unauthorised node.
B.40 Version update tests
For every terminology adapter create tests for:

```text
new release discovered
same release re-imported
new concepts
retired concepts
renamed concept
changed parent
mapping change
licence change
rollback
historical lookup
offline bundle still pinned to old release

```

Do not assume terminology releases are append-only.
B.41 Mapping review workbench
Implement an operational mapping queue.
States:

```text
UNMAPPED
AUTO_SUGGESTED
REVIEW_REQUIRED
APPROVED
REJECTED
SUPERSEDED

```

Machine/text similarity MAY suggest a map.
It SHALL NOT automatically mark it authoritative.
High-risk mappings require clinical review.
Expose:

* source concept;
* candidate target;
* equivalence;
* confidence;
* reviewer;
* rationale.

B.42 Rights and credential dashboard
Implement an operational view listing every external source:

```text
source
current release
installed release
rights
credential availability
central use allowed
offline redistribution allowed
activation state
next release check
blocker

```

This SHALL prevent “SNOMED missing” or “ATC incomplete” from being rediscovered months later.
B.43 Standards implementation status vocabulary
Use only:

```text
NOT_STARTED
ENGINE_IMPLEMENTING
ENGINE_READY
SOURCE_DISCOVERED
CREDENTIAL_REQUIRED
LICENCE_REQUIRED
NORMATIVE_DOCUMENT_REQUIRED
SOURCE_ACQUIRED
SOURCE_VERIFIED
IMPORTED
INDEXED
STAGED
ACTIVE
ACTIVE_RESTRICTED
SUPERSEDED
RETIRED
FAILED

```

Do not use vague statuses such as:

```text
aware
planned
supported conceptually
future
compatible

```

B.44 Required implementation report
Produce:
`ZIBO_COMPREHENSIVE_STANDARDS_IMPLEMENTATION_REPORT.md`
It SHALL contain one row per standard/source.
Columns:

```text
standard
authority
purpose
current official release discovered
installed release
rights status
credential status
raw source path
checksum
adapter
concept count
relationship count
designation count
ValueSet count
mapping count
API proof
consumer proof
offline eligible
status
exact blocker
next executable action

```

The report SHALL include all of:

```text
WHO-FIC
ICD-11 Foundation
ICD-11 MMS
ICF
ICHI
ICD-10 legacy
ICD-O
WHO Verbal Autopsy
ICPC-3
ICPC-2
SNOMED CT
ICNP
LOINC
UCUM
WHO eEDL
WHO INN
ATC/DDD
EDLIZ
WHO EML
EMLc
AWaRe
ZNMD
ISO IDMP
EDQM Standard Terms
MedDRA
WHODrug
GS1 identifiers
GS1 GPC
MeDevIS
EMDN
GMDN
UDI
DICOM terminology
RadLex
LOINC/RSNA Playbook
ISBT 128
ORPHAcode
HPO
organism taxonomy
WHO SMART/DAKs
WHO Growth Standards
WHO UHC Compendium
FHIR shared terminology
national semantic extensions

```

Nothing silently disappears from the report because it was difficult.
B.45 Acceptance tests by clinical story
Do not test standards only through synthetic API calls.
Demonstrate these stories.
Primary care
Patient says:

```text
headache
vomiting

```

Record ICPC-3 presenting complaints.
Later clinician diagnoses malaria.
All presentation data remains.
Laboratory
Order/result uses LOINC.
Result unit validates using UCUM.
Rehabilitation
Stroke patient has diagnosis plus ICF functional assessment.
Procedure
Surgical procedure resolves to ICHI.
Medicine
Prescription records ZNMD medicine.
It maps to ATC and EDLIZ.
Dispensing scan resolves a GTIN and batch/expiry.
Pharmacovigilance
Adverse event can be represented and routed toward MedDRA/WHODrug when authorised.
Device
Implant records national device identity + EMDN/GMDN + UDI/GTIN + serial where present.
Blood
Blood unit resolves through ISBT 128 identity and product code.
Imaging
Imaging order uses governed orderable terminology and maps through Playbook/ICHI/DICOM/RadLex.
Rare disease
Disease has ORPHAcode / ICD relationship and phenotypic observations use HPO.
Child growth
Weight/height coded with LOINC + UCUM and interpreted against the correct versioned WHO reference.
Offline hospital
National Core disconnected.
The hospital node still searches and validates its permitted semantic bundle.
B.46 Performance acceptance
At national-scale content volumes:

```text
exact lookup              p95 < 50 ms
code validation           p95 < 50 ms
filtered search 20 rows   p95 < 200 ms
ValueSet expansion page   p95 < 200 ms where feasible
hierarchy parent/children p95 < 100 ms
subsumption               p95 < 100 ms

```

Measure using realistic large terminologies.
Do not benchmark only the 31-concept specialty list.
B.47 Non-negotiable completion rule
The work is not complete because:

```text
SNOMED adapter written
LOINC CSV downloaded
ICD Docker container started
ICPC table created
GS1 parser has one test
ICF mentioned in docs

```

Completion means the maximum legally deployable semantic estate is actually live.
At the end of implementation, ZIBO SHALL be able to demonstrate:

```text
search
lookup
validate
expand
subsumes
translate
version
history
rights
offline distribution
consumer use

```

across the standards for which those operations apply.
Anything not active SHALL have a concrete external blocker, not an engineering excuse.
B.48 Final instruction to coding agent
Treat the Master Specification and both implementation addenda as governing requirements.
Do not respond with another broad plan.
Inspect first.
Then change code.
Acquire content.
Run migrations.
Deploy required supporting services.
Import authoritative releases.
Build indexes.
Wire consumers.
Run tests.
Measure.
Produce the implementation report.
Where licensing blocks a content source, finish everything up to the legal boundary and move immediately to the next source.
Implement the estate, not the PowerPoint version of the estate.
Proceed.
