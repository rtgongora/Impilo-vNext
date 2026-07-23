-- zibo-service V007 — WHO ATC medicine CodeSystem (OF-B8 / OF-D — national medicine registry lane).
--
-- ZIBO is the national terminology SoR; until now NO CodeSystem was registered under the WHO ATC
-- canonical system (http://www.whocc.no/atc), so POST /v1/validate/coding returned "not-found" for
-- every ATC-coded medication line (the OF-B3 finding), and the three-layer formulary stance
-- (ZIBO national registry -> coverage cv_formulary -> pharmacy rx_formulary) had no national layer.
--
-- Decision (OF-D): medicines ride the EXISTING CODE_SYSTEM machinery — a medicine registry entry is
-- a coded concept with governed properties (genericName / form / strength / scheduleClass /
-- controlled), exactly the FHIR R4 CodeSystem concept+property model. No new ArtifactType is
-- appended (the ordinal-persisted enum stays untouched) and no parallel lookup machinery is built:
-- validate-coding, artifact resolve and the concept content all reuse what V004/V005/V006 proved.
--
-- Content: an EDLIZ-shaped STARTER set — 20 common medicines with WHO ATC codes, incl. 2 controlled
-- (morphine N02AA01, diazepam N05BA01). The full governed EDLIZ import is a data-governance
-- programme (MCAZ/MoHCC), deliberately NOT this migration.
--
-- Idempotent: ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING. Seeded for both platform
-- tenants (zibo seed precedent tenant + the golden platform tenant) so validate-coding resolves
-- under either trust context.

DO $$
DECLARE
    tenants UUID[] := ARRAY[
        '00000000-0000-0000-0000-000000000001'::uuid,  -- zibo seed precedent (V004-V006)
        '00000000-0000-4000-8000-000000000001'::uuid   -- golden platform tenant
    ];
    t UUID;
    cs_content TEXT := $cs${
      "resourceType": "CodeSystem",
      "url": "http://www.whocc.no/atc",
      "version": "2026.01",
      "id": "who-atc-medicines-zw-starter",
      "name": "who-atc-medicines-zw-starter",
      "title": "WHO ATC — Zimbabwe EDLIZ starter medicine set",
      "status": "active",
      "experimental": false,
      "caseSensitive": true,
      "content": "fragment",
      "publisher": "WHO Collaborating Centre for Drug Statistics Methodology (curated for MoHCC Zimbabwe)",
      "jurisdiction": [{"coding": [{"system": "urn:iso:std:iso:3166", "code": "ZW"}]}],
      "property": [
        {"code": "genericName",   "type": "string",  "description": "INN generic name"},
        {"code": "form",          "type": "string",  "description": "Dose form"},
        {"code": "strength",      "type": "string",  "description": "Strength display"},
        {"code": "scheduleClass", "type": "string",  "description": "ZW schedule class: GENERAL_SALE, PHARMACY_MEDICINE, PRESCRIPTION_ONLY, CONTROLLED_DRUG"},
        {"code": "controlled",    "type": "boolean", "description": "Dangerous Drugs Act controlled substance"}
      ],
      "concept": [
        {"code": "J01CA04", "display": "Amoxicillin 500 mg capsule", "property": [
          {"code": "genericName", "valueString": "Amoxicillin"},
          {"code": "form", "valueString": "capsule"},
          {"code": "strength", "valueString": "500 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "N02BE01", "display": "Paracetamol 500 mg tablet", "property": [
          {"code": "genericName", "valueString": "Paracetamol"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "500 mg"},
          {"code": "scheduleClass", "valueString": "GENERAL_SALE"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "A10BA02", "display": "Metformin 500 mg tablet", "property": [
          {"code": "genericName", "valueString": "Metformin"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "500 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "C08CA01", "display": "Amlodipine 5 mg tablet", "property": [
          {"code": "genericName", "valueString": "Amlodipine"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "5 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "C09AA02", "display": "Enalapril 5 mg tablet", "property": [
          {"code": "genericName", "valueString": "Enalapril"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "5 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "C03AA03", "display": "Hydrochlorothiazide 25 mg tablet", "property": [
          {"code": "genericName", "valueString": "Hydrochlorothiazide"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "25 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "P01BF01", "display": "Artemether + lumefantrine 20 mg/120 mg tablet", "property": [
          {"code": "genericName", "valueString": "Artemether and lumefantrine"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "20 mg/120 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "J01EE01", "display": "Cotrimoxazole 400 mg/80 mg tablet", "property": [
          {"code": "genericName", "valueString": "Sulfamethoxazole and trimethoprim"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "400 mg/80 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "J01AA02", "display": "Doxycycline 100 mg capsule", "property": [
          {"code": "genericName", "valueString": "Doxycycline"},
          {"code": "form", "valueString": "capsule"},
          {"code": "strength", "valueString": "100 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "P01AB01", "display": "Metronidazole 400 mg tablet", "property": [
          {"code": "genericName", "valueString": "Metronidazole"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "400 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "M01AE01", "display": "Ibuprofen 400 mg tablet", "property": [
          {"code": "genericName", "valueString": "Ibuprofen"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "400 mg"},
          {"code": "scheduleClass", "valueString": "PHARMACY_MEDICINE"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "A02BC01", "display": "Omeprazole 20 mg capsule", "property": [
          {"code": "genericName", "valueString": "Omeprazole"},
          {"code": "form", "valueString": "capsule"},
          {"code": "strength", "valueString": "20 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "R03AC02", "display": "Salbutamol 100 microgram/dose inhaler", "property": [
          {"code": "genericName", "valueString": "Salbutamol"},
          {"code": "form", "valueString": "pressurised inhalation"},
          {"code": "strength", "valueString": "100 microgram/dose"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "J01DD04", "display": "Ceftriaxone 1 g powder for injection", "property": [
          {"code": "genericName", "valueString": "Ceftriaxone"},
          {"code": "form", "valueString": "powder for injection"},
          {"code": "strength", "valueString": "1 g"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "J01CE08", "display": "Benzathine benzylpenicillin 2.4 MU powder for injection", "property": [
          {"code": "genericName", "valueString": "Benzathine benzylpenicillin"},
          {"code": "form", "valueString": "powder for injection"},
          {"code": "strength", "valueString": "2.4 MU"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "J05AR27", "display": "Lamivudine + tenofovir disoproxil + dolutegravir 300/300/50 mg tablet", "property": [
          {"code": "genericName", "valueString": "Lamivudine, tenofovir disoproxil and dolutegravir"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "300 mg/300 mg/50 mg"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "A10AB01", "display": "Insulin (human, soluble) 100 IU/mL injection", "property": [
          {"code": "genericName", "valueString": "Insulin (human)"},
          {"code": "form", "valueString": "solution for injection"},
          {"code": "strength", "valueString": "100 IU/mL"},
          {"code": "scheduleClass", "valueString": "PRESCRIPTION_ONLY"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "B03AA07", "display": "Ferrous sulfate 200 mg tablet", "property": [
          {"code": "genericName", "valueString": "Ferrous sulfate"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "200 mg"},
          {"code": "scheduleClass", "valueString": "GENERAL_SALE"},
          {"code": "controlled", "valueBoolean": false}]},
        {"code": "N02AA01", "display": "Morphine 10 mg/mL injection", "property": [
          {"code": "genericName", "valueString": "Morphine"},
          {"code": "form", "valueString": "solution for injection"},
          {"code": "strength", "valueString": "10 mg/mL"},
          {"code": "scheduleClass", "valueString": "CONTROLLED_DRUG"},
          {"code": "controlled", "valueBoolean": true}]},
        {"code": "N05BA01", "display": "Diazepam 5 mg tablet", "property": [
          {"code": "genericName", "valueString": "Diazepam"},
          {"code": "form", "valueString": "tablet"},
          {"code": "strength", "valueString": "5 mg"},
          {"code": "scheduleClass", "valueString": "CONTROLLED_DRUG"},
          {"code": "controlled", "valueBoolean": true}]}
      ]
    }$cs$;
BEGIN
    FOREACH t IN ARRAY tenants LOOP
        INSERT INTO zibo_artifacts
            (tenant_id, fhir_type, canonical_url, version, name, title, description,
             status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
        VALUES (
            t,
            'CODE_SYSTEM',
            'http://www.whocc.no/atc',
            '2026.01',
            'who-atc-medicines-zw-starter',
            'WHO ATC — Zimbabwe EDLIZ starter medicine set',
            'National medicine registry starter: 20 EDLIZ-common medicines coded to WHO ATC with generic name, form/strength and ZW schedule class (2 controlled). Registers the ATC system so validate-coding resolves ATC-coded medication lines. Full EDLIZ import is a governed data programme.',
            'PUBLISHED',
            cs_content::jsonb,
            md5('codesystem:who-atc-medicines-zw-starter:2026.01'),
            'WHO Collaborating Centre for Drug Statistics Methodology (curated for MoHCC Zimbabwe)',
            'ZW',
            'system-seeder',
            'system-seeder',
            now())
        ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
    END LOOP;
END $$;
