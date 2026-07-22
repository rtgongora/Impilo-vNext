-- zibo-service V005 — Regulatory jurisdiction CodeSystem + ValueSet (ROM-W3/R3).
-- The jurisdiction vocabulary for regulatory appointments (org_registry_regulatory_appointment
-- .jurisdiction_code) and the tshepo-authz jurisdiction dimension. Councils default NATIONAL; the
-- ten provinces exist for the HPA inspectorate and future regional offices — modelled now,
-- defaulted, never speculatively subdivided (districts deferred until a regulator needs them).
-- Idempotent: ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING. Default tenant.

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/regulatory-jurisdiction',
    '1.0.0',
    'ImpiloRegulatoryJurisdiction',
    'Impilo Regulatory Jurisdiction Codes',
    'Jurisdiction scope for regulatory appointments and policy (NATIONAL + the ten provinces).',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/regulatory-jurisdiction",
      "version": "1.0.0",
      "name": "ImpiloRegulatoryJurisdiction",
      "title": "Impilo Regulatory Jurisdiction Codes",
      "status": "active",
      "content": "complete",
      "concept": [
        {"code": "NATIONAL", "display": "National"},
        {"code": "BULAWAYO", "display": "Bulawayo Metropolitan Province"},
        {"code": "HARARE", "display": "Harare Metropolitan Province"},
        {"code": "MANICALAND", "display": "Manicaland Province"},
        {"code": "MASHONALAND_CENTRAL", "display": "Mashonaland Central Province"},
        {"code": "MASHONALAND_EAST", "display": "Mashonaland East Province"},
        {"code": "MASHONALAND_WEST", "display": "Mashonaland West Province"},
        {"code": "MASVINGO", "display": "Masvingo Province"},
        {"code": "MATABELELAND_NORTH", "display": "Matabeleland North Province"},
        {"code": "MATABELELAND_SOUTH", "display": "Matabeleland South Province"},
        {"code": "MIDLANDS", "display": "Midlands Province"}
      ]
    }$cs$,
    md5('regulatory-jurisdiction-1.0.0'),
    'ROM-W2', 'ZW', 'rom-seed', 'rom-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
