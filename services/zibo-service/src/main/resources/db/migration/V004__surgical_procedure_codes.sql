-- zibo-service V004 — surgical procedure CodeSystem + ValueSet (Wave 3, spec §2).
-- ZIBO is the FHIR terminology engine; it had NO seeded procedure codes, so referral / scheduling /
-- operative-note could not code the intended or performed procedure. This seeds a curated national
-- surgical-procedure CodeSystem (ICD-9-CM-procedure-aligned codes covering the demo operations:
-- hernia repair, caesarean section, appendicectomy, ORIF, laparotomy, cholecystectomy, mastectomy,
-- hysterectomy) plus a ValueSet composing it. Both are PUBLISHED so ValidationEngine.validateCoding
-- (POST /v1/validate/coding) can $validate-code against the system immediately.
--
-- Idempotent: ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING. Seeded for the default tenant.
-- content_hash is md5(content_json) (dedup only). ZIBO owns these codes; other services reference them.

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures',
    '1.0.0',
    'ImpiloSurgicalProcedures',
    'Impilo Surgical Procedure Codes',
    'Curated national surgical-procedure code set (ICD-9-CM-procedure aligned) for perioperative coding.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures",
      "version": "1.0.0",
      "name": "ImpiloSurgicalProcedures",
      "title": "Impilo Surgical Procedure Codes",
      "status": "active",
      "experimental": false,
      "caseSensitive": true,
      "content": "complete",
      "publisher": "Ministry of Health and Child Care, Zimbabwe",
      "jurisdiction": [{"coding": [{"system": "urn:iso:std:iso:3166", "code": "ZW"}]}],
      "concept": [
        {"code": "47.0",  "display": "Appendicectomy"},
        {"code": "47.01", "display": "Laparoscopic appendicectomy"},
        {"code": "74.1",  "display": "Low cervical caesarean section"},
        {"code": "53.00", "display": "Unilateral repair of inguinal hernia"},
        {"code": "53.10", "display": "Bilateral repair of inguinal hernia"},
        {"code": "79.36", "display": "Open reduction of fracture with internal fixation (ORIF)"},
        {"code": "54.11", "display": "Exploratory laparotomy"},
        {"code": "51.23", "display": "Laparoscopic cholecystectomy"},
        {"code": "85.41", "display": "Unilateral simple mastectomy"},
        {"code": "68.49", "display": "Total abdominal hysterectomy"}
      ]
    }$cs$,
    md5($cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures",
      "version": "1.0.0",
      "concept": ["47.0","47.01","74.1","53.00","53.10","79.36","54.11","51.23","85.41","68.49"]
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
    'https://impilo.gov.zw/fhir/ValueSet/surgical-procedures',
    '1.0.0',
    'ImpiloSurgicalProceduresVS',
    'Impilo Surgical Procedures Value Set',
    'Value set of billable/schedulable surgical procedures composing the Impilo surgical-procedure CodeSystem.',
    'PUBLISHED',
    $cs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/surgical-procedures",
      "version": "1.0.0",
      "name": "ImpiloSurgicalProceduresVS",
      "title": "Impilo Surgical Procedures Value Set",
      "status": "active",
      "compose": {
        "include": [
          {
            "system": "https://impilo.gov.zw/fhir/CodeSystem/surgical-procedures",
            "concept": [
              {"code": "47.0",  "display": "Appendicectomy"},
              {"code": "47.01", "display": "Laparoscopic appendicectomy"},
              {"code": "74.1",  "display": "Low cervical caesarean section"},
              {"code": "53.00", "display": "Unilateral repair of inguinal hernia"},
              {"code": "53.10", "display": "Bilateral repair of inguinal hernia"},
              {"code": "79.36", "display": "Open reduction of fracture with internal fixation (ORIF)"},
              {"code": "54.11", "display": "Exploratory laparotomy"},
              {"code": "51.23", "display": "Laparoscopic cholecystectomy"},
              {"code": "85.41", "display": "Unilateral simple mastectomy"},
              {"code": "68.49", "display": "Total abdominal hysterectomy"}
            ]
          }
        ]
      }
    }$cs$,
    md5($cs$valueset:surgical-procedures:1.0.0$cs$),
    'Ministry of Health and Child Care, Zimbabwe',
    'ZW',
    'system-seeder',
    'system-seeder',
    now())
ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
