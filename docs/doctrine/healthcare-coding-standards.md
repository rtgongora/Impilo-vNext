# Healthcare Coding Standards — Interoperability Doctrine

> **Doctrine principle**: All clinical, pharmaceutical, diagnostic, and administrative data
> within the Health Operating System MUST be coded using internationally recognized
> terminology standards. Structured coding ensures semantic interoperability, enables
> cross-border data exchange, supports clinical decision support, and satisfies regulatory
> reporting obligations.

---

## 1. Purpose

The Health Operating System processes clinical observations, diagnoses, procedures,
medications, laboratory results, imaging studies, claims, and public health surveillance
data. Without standardized coding, this data remains siloed, ambiguous, and
non-interoperable.

This doctrine establishes the **mandatory coding systems**, their **scope of application**,
**governance rules**, and **validation requirements** across all Impilo services.

---

## 2. Adopted Coding Systems

| Standard | Full Name | URI | Domain | Version Policy |
|----------|-----------|-----|--------|----------------|
| **ICD-11** | International Classification of Diseases, 11th Revision | `http://id.who.int/icd/release/11` | Diagnoses, mortality, morbidity | WHO annual release |
| **SNOMED CT** | Systematized Nomenclature of Medicine — Clinical Terms | `http://snomed.info/sct` | Clinical findings, procedures, body structures, substances | SNOMED International biannual |
| **LOINC** | Logical Observation Identifiers Names and Codes | `http://loinc.org` | Laboratory tests, clinical observations, vital signs, surveys | Regenstrief biannual |
| **ATC** | Anatomical Therapeutic Chemical Classification | `http://www.whocc.no/atc` | Medication classification | WHO annual |
| **DICOM** | Digital Imaging and Communications in Medicine | `http://dicom.nema.org` | Medical imaging metadata, modality worklists, SOP classes | NEMA continuous |

### 2.1 Supplementary Systems

| Standard | URI | Domain |
|----------|-----|--------|
| **CPT** | `http://www.ama-assn.org/go/cpt` | Procedure coding (claims) |
| **UCUM** | `http://unitsofmeasure.org` | Units of measure for observations |
| **RxNorm** | `http://www.nlm.nih.gov/research/umls/rxnorm` | US drug terminology (cross-mapping) |
| **CVX** | `http://hl7.org/fhir/sid/cvx` | Vaccine codes |
| **ISO 3166** | `urn:iso:std:iso:3166` | Country and subdivision codes |

---

## 3. Scope of Application

### 3.1 Service-to-Standard Mapping

| Service | Primary Standards | Usage |
|---------|-------------------|-------|
| **OROS** (Order & Routing) | LOINC, SNOMED CT | Order item codes, specimen types, body sites |
| **Pharmacy** | ATC, SNOMED CT | Drug codes (formulary), active ingredients, dosage forms |
| **BUTANO** (FHIR SHR) | All | Condition, Observation, MedicationRequest, Procedure resources |
| **Coverage** (Claims) | ICD-11, CPT, ATC | Diagnosis codes on claims, procedure codes, drug codes |
| **Surveillance** | ICD-11, SNOMED CT | Case classification, notifiable disease codes |
| **PACS Adapter** | DICOM | Modality codes, SOP class UIDs, body part examined |
| **Mushex** (Encounter) | ICD-11, SNOMED CT, LOINC | Encounter diagnoses, procedures, observations |
| **Ubomi** (Wellness) | LOINC, SNOMED CT | Vital signs, wellness observations |
| **Product Registry** | ATC, SNOMED CT | Product classification, ingredient coding |
| **Experience BFF** | All (pass-through) | CodeableConcept rendering, terminology search |

### 3.2 Coding Requirements by Data Class

| Data Class | Required System | Field Pattern |
|------------|-----------------|---------------|
| Diagnosis | ICD-11 (primary), SNOMED CT (optional refinement) | `coding_system` + `code` + `display` |
| Lab test / observation type | LOINC | `coding_system` + `code` + `display` |
| Observation value (coded) | SNOMED CT | `coding_system` + `code` + `display` |
| Medication | ATC (classification), SNOMED CT (clinical drug) | `coding_system` + `code` + `display` |
| Procedure | SNOMED CT (primary), CPT (claims) | `coding_system` + `code` + `display` |
| Body site | SNOMED CT | `coding_system` + `code` + `display` |
| Specimen type | SNOMED CT | `coding_system` + `code` + `display` |
| Unit of measure | UCUM | `unit_system` + `unit_code` |
| Imaging modality | DICOM | `coding_system` + `code` + `display` |
| Vaccine | CVX | `coding_system` + `code` + `display` |

---

## 4. Data Model — CodeableConcept

All coded data MUST use the `CodeableConcept` pattern, aligned with HL7 FHIR R4:

```
CodeableConcept {
  coding: [
    {
      system: string    // Terminology system URI (e.g. "http://loinc.org")
      version: string?  // System version (e.g. "2.77")
      code: string      // Code value (e.g. "85354-9")
      display: string   // Human-readable display name
      userSelected: boolean?  // Was this code chosen by the user?
    }
  ]
  text: string?         // Free-text fallback / clinician narrative
}
```

### 4.1 Multi-Coding Rule

A single concept MAY carry multiple codings from different systems. For example, a
diagnosis may carry both an ICD-11 code (for reporting) and a SNOMED CT code (for
clinical detail). The **primary** coding is determined by the data class rules in §3.2.

### 4.2 Database Representation

Coded fields in relational tables use the triple-column pattern:

```sql
coding_system   VARCHAR(255) NOT NULL,  -- System URI
code            VARCHAR(100) NOT NULL,  -- Code value
display         VARCHAR(500),           -- Display name
coding_version  VARCHAR(50)             -- System version (optional)
```

For entities requiring multiple codings, use a JSONB `codings` column containing an
array of `{system, code, display, version}` objects.

---

## 5. Validation Rules

### 5.1 Structural Validation (All Services)

1. `coding_system` MUST be a recognized URI from the approved list (§2)
2. `code` MUST be non-empty and match the format expected by the coding system
3. `display` SHOULD be provided; if absent, services SHOULD resolve it from the
   terminology server

### 5.2 Semantic Validation (Clinical Services)

1. BUTANO (HAPI FHIR) SHALL validate codes against loaded terminology value sets
2. OROS SHALL validate order item codes against the facility catalog, which itself
   references LOINC/SNOMED CT
3. Pharmacy SHALL validate drug codes against the formulary, which maps to ATC

### 5.3 Cross-System Consistency

When a coded value crosses service boundaries via Kafka events:
1. The event envelope MUST include the full `CodeableConcept` (system + code + display)
2. Consumers MUST NOT strip or transform coding information
3. If a consumer requires a different coding system, it MUST perform a mapping and
   retain the original coding alongside the mapped code

---

## 6. Terminology Service Architecture

```
┌─────────────────────────────────────────────────┐
│              HAPI FHIR (BUTANO)                 │
│  ┌───────────────────────────────────────────┐  │
│  │  Terminology Server ($validate-code,      │  │
│  │  $lookup, $translate, $expand)            │  │
│  │                                           │  │
│  │  Loaded Code Systems:                     │  │
│  │  • ICD-11 (CodeSystem + ValueSets)        │  │
│  │  • SNOMED CT (snapshot)                   │  │
│  │  • LOINC (CodeSystem + panels)            │  │
│  │  • ATC (CodeSystem)                       │  │
│  │  • CVX (CodeSystem)                       │  │
│  │  • UCUM (CodeSystem)                      │  │
│  └───────────────────────────────────────────┘  │
└────────────────────┬────────────────────────────┘
                     │ FHIR API
    ┌────────────────┼────────────────┐
    │                │                │
┌───┴───┐      ┌────┴────┐     ┌─────┴─────┐
│ OROS  │      │Pharmacy │     │ Coverage  │
│       │      │         │     │           │
│ LOINC │      │  ATC    │     │  ICD-11   │
│SNOMED │      │ SNOMED  │     │   CPT     │
└───────┘      └─────────┘     └───────────┘
```

---

## 7. Governance

1. **Addition of new coding systems** requires doctrine review and approval
2. **Version upgrades** follow the source organization's release cycle; the platform
   SHALL support running two versions concurrently during transition periods
3. **Local extensions** (e.g., Zimbabwe-specific drug codes) MUST use a designated
   namespace (`http://impilo.gov.zw/coding/`) and MUST map to the nearest international
   code where one exists
4. **Deprecation of codes** within a system MUST be handled gracefully — deprecated codes
   remain readable but are flagged and excluded from new data entry

---

## 8. Implementation Artifacts

| Artifact | Location | Purpose |
|----------|----------|---------|
| `CodingSystem` enum | `libs/shared-kernel-java/.../terminology/` | Canonical system URIs |
| `CodeableConcept` model | `libs/shared-kernel-java/.../terminology/` | Shared data model |
| `CodingValidator` | `libs/shared-kernel-java/.../terminology/` | Structural validation |
| TypeScript types | `contracts/coding-standards.ts` | UI/BFF type contracts |
| FHIR terminology config | `services/butano-fhir/` | Terminology server setup |
| Reference migrations | Per-service Flyway scripts | `coding_system` columns |
