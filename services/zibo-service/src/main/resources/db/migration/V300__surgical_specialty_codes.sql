-- zibo-service V300 — surgical specialty codes (Surgery + Procedures Pipeline programme, SB-2).
-- Reserved band per docs/registry/iatg-surgery-procedures-leases.md §3.
--
-- V006 (Wave TM-B2/B3) seeded a 21-concept clinical-specialty CodeSystem with only ONE surgical
-- entry — the generic 'SURGERY' code — because it was scoped to referral/teleconsult routing at
-- the time. The surgical domain pack's own audit (docs/clinical/surgical-domain-pack/audit.md
-- §6) confirms this directly: "Zero of the fifteen. There is no specialty dimension on the
-- episode at all beyond referral.target_specialty" and names 10 missing codes by name:
-- colorectal, upper-GI/HPB, breast and endocrine, vascular, neurosurgery, cardiothoracic,
-- maxillofacial, plastics, paediatric surgery, surgical oncology (the other 5 of the 15 —
-- SURGERY, ORTHOPAEDICS, UROLOGY, ENT, OPHTHALMOLOGY — already exist in V006).
--
-- ADDITIVE, not a mutation of V006. FHIR CodeSystem/ValueSet resources are versioned; this
-- publishes a NEW version (1.1.0) carrying the full concept list V006 already had PLUS the ten
-- additions, rather than rewriting content_json in place — a consumer still pinned to 1.0.0
-- keeps seeing exactly what it saw before. ArtifactRepository.findByTenantIdAndCanonicalUrl
-- (no version) already returns every version under a canonical_url, so this is discoverable
-- without any zibo-service code change — content only, the same "co-edited, additive-only"
-- posture this programme holds in every service it does not own.

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty',
    '1.1.0',
    'impilo-clinical-specialty',
    'Impilo Clinical Specialty Codes',
    'Curated national clinical-specialty code set for referral, teleconsult routing, provider directory coding, and surgical episode specialty (Surgery + Procedures Pipeline programme, surgical domain pack §6 — the fifteen surgical specialties).',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty",
      "version": "1.1.0",
      "id": "impilo-clinical-specialty",
      "name": "impilo-clinical-specialty",
      "title": "Impilo Clinical Specialty Codes",
      "status": "active",
      "experimental": false,
      "caseSensitive": true,
      "content": "complete",
      "publisher": "Ministry of Health and Child Care, Zimbabwe",
      "jurisdiction": [{"coding": [{"system": "urn:iso:std:iso:3166", "code": "ZW"}]}],
      "concept": [
        {"code": "GENERAL_MEDICINE",        "display": "General Medicine"},
        {"code": "INTERNAL_MEDICINE",       "display": "Internal Medicine"},
        {"code": "FAMILY_MEDICINE",         "display": "Family Medicine"},
        {"code": "PAEDIATRICS",             "display": "Paediatrics"},
        {"code": "OBSTETRICS_GYNAECOLOGY",  "display": "Obstetrics & Gynaecology"},
        {"code": "SURGERY",                 "display": "Surgery (General Surgery)"},
        {"code": "ORTHOPAEDICS",            "display": "Orthopaedics"},
        {"code": "UROLOGY",                 "display": "Urology"},
        {"code": "ENT",                     "display": "ENT"},
        {"code": "OPHTHALMOLOGY",           "display": "Ophthalmology"},
        {"code": "DERMATOLOGY",             "display": "Dermatology"},
        {"code": "CARDIOLOGY",              "display": "Cardiology"},
        {"code": "NEUROLOGY",               "display": "Neurology"},
        {"code": "NEPHROLOGY",              "display": "Nephrology"},
        {"code": "ONCOLOGY",                "display": "Oncology"},
        {"code": "PSYCHIATRY",              "display": "Psychiatry"},
        {"code": "RADIOLOGY",               "display": "Radiology"},
        {"code": "PATHOLOGY",               "display": "Pathology"},
        {"code": "ANAESTHESIA",             "display": "Anaesthesia"},
        {"code": "EMERGENCY_MEDICINE",      "display": "Emergency Medicine"},
        {"code": "PUBLIC_HEALTH",           "display": "Public Health"},
        {"code": "COLORECTAL_SURGERY",      "display": "Colorectal Surgery"},
        {"code": "UPPER_GI_HPB_SURGERY",    "display": "Upper GI / Hepatopancreatobiliary Surgery"},
        {"code": "BREAST_ENDOCRINE_SURGERY","display": "Breast and Endocrine Surgery"},
        {"code": "VASCULAR_SURGERY",        "display": "Vascular Surgery"},
        {"code": "NEUROSURGERY",            "display": "Neurosurgery"},
        {"code": "CARDIOTHORACIC_SURGERY",  "display": "Cardiothoracic Surgery"},
        {"code": "MAXILLOFACIAL_SURGERY",   "display": "Maxillofacial Surgery"},
        {"code": "PLASTIC_SURGERY",         "display": "Plastic Surgery"},
        {"code": "PAEDIATRIC_SURGERY",      "display": "Paediatric Surgery"},
        {"code": "SURGICAL_ONCOLOGY",       "display": "Surgical Oncology"}
      ]
    }$cs$,
    md5($cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty",
      "version": "1.1.0",
      "concept": ["GENERAL_MEDICINE","INTERNAL_MEDICINE","FAMILY_MEDICINE","PAEDIATRICS","OBSTETRICS_GYNAECOLOGY","SURGERY","ORTHOPAEDICS","UROLOGY","ENT","OPHTHALMOLOGY","DERMATOLOGY","CARDIOLOGY","NEUROLOGY","NEPHROLOGY","ONCOLOGY","PSYCHIATRY","RADIOLOGY","PATHOLOGY","ANAESTHESIA","EMERGENCY_MEDICINE","PUBLIC_HEALTH","COLORECTAL_SURGERY","UPPER_GI_HPB_SURGERY","BREAST_ENDOCRINE_SURGERY","VASCULAR_SURGERY","NEUROSURGERY","CARDIOTHORACIC_SURGERY","MAXILLOFACIAL_SURGERY","PLASTIC_SURGERY","PAEDIATRIC_SURGERY","SURGICAL_ONCOLOGY"]
    }$cs$),
    'Ministry of Health and Child Care, Zimbabwe',
    'ZW',
    'system-seeder',
    'system-seeder',
    now())
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/clinical-specialty',
    '1.1.0',
    'impilo-clinical-specialty-vs',
    'Impilo Clinical Specialty Value Set',
    'Value set of selectable clinical specialties composing the Impilo clinical-specialty CodeSystem (v1.1.0, +10 surgical specialties).',
    'PUBLISHED',
    $cs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/clinical-specialty",
      "version": "1.1.0",
      "id": "impilo-clinical-specialty-vs",
      "name": "impilo-clinical-specialty-vs",
      "title": "Impilo Clinical Specialty Value Set",
      "status": "active",
      "compose": {
        "include": [
          {
            "system": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty",
            "version": "1.1.0"
          }
        ]
      }
    }$cs$,
    md5($cs$valueset:clinical-specialty:1.1.0$cs$),
    'Ministry of Health and Child Care, Zimbabwe',
    'ZW',
    'system-seeder',
    'system-seeder',
    now())
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
