-- zibo-service V005 — clinical-specialty CodeSystem + ValueSet (TM-B2/B3).
-- ZIBO is the FHIR terminology engine; it had NO seeded clinical-specialty codes, so referral /
-- teleconsult routing / practitioner directory could not code the requested specialty against a
-- registered CodeSystem. The telemedicine teleconsult-create form (ui/one-ui-shell telemedicine/new)
-- currently hardcodes a free-text <select> specialty list; this seeds a curated national
-- clinical-specialty CodeSystem covering every one of those options (plus common broad specialties)
-- so the picker can bind to a governed value set and POST /v1/validate/coding can $validate-code
-- against the system immediately.
--
-- Idempotent: ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING. Seeded for the default tenant.
-- content_hash is md5(content_json) (dedup only). ZIBO owns these codes; other services reference them.

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty',
    '1.0.0',
    'impilo-clinical-specialty',
    'Impilo Clinical Specialty Codes',
    'Curated national clinical-specialty code set for referral, teleconsult routing, and provider directory coding.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty",
      "version": "1.0.0",
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
        {"code": "SURGERY",                 "display": "Surgery"},
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
        {"code": "PUBLIC_HEALTH",           "display": "Public Health"}
      ]
    }$cs$,
    md5($cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty",
      "version": "1.0.0",
      "concept": ["GENERAL_MEDICINE","INTERNAL_MEDICINE","FAMILY_MEDICINE","PAEDIATRICS","OBSTETRICS_GYNAECOLOGY","SURGERY","ORTHOPAEDICS","UROLOGY","ENT","OPHTHALMOLOGY","DERMATOLOGY","CARDIOLOGY","NEUROLOGY","NEPHROLOGY","ONCOLOGY","PSYCHIATRY","RADIOLOGY","PATHOLOGY","ANAESTHESIA","EMERGENCY_MEDICINE","PUBLIC_HEALTH"]
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
    '1.0.0',
    'impilo-clinical-specialty-vs',
    'Impilo Clinical Specialty Value Set',
    'Value set of selectable clinical specialties composing the Impilo clinical-specialty CodeSystem.',
    'PUBLISHED',
    $cs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/clinical-specialty",
      "version": "1.0.0",
      "id": "impilo-clinical-specialty-vs",
      "name": "impilo-clinical-specialty-vs",
      "title": "Impilo Clinical Specialty Value Set",
      "status": "active",
      "compose": {
        "include": [
          {
            "system": "https://impilo.gov.zw/fhir/CodeSystem/clinical-specialty"
          }
        ]
      }
    }$cs$,
    md5($cs$valueset:clinical-specialty:1.0.0$cs$),
    'Ministry of Health and Child Care, Zimbabwe',
    'ZW',
    'system-seeder',
    'system-seeder',
    now())
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
