-- zibo-service V035 — HIV and TB programme value sets.
--
-- The governed terminology the HIV and TB programmes are recorded and reported against: the ART and
-- TB regimens a patient can be put on, the observations the programmes monitor (viral load, CD4, TB
-- bacteriology, WHO clinical stage), and the treatment outcomes national reporting counts. PCT
-- records against these codes (pct_treatment_regimens.regimen_code, pct_observations.code); CKP's
-- DAK rules read them; reporting rolls them up. One governed vocabulary, here, rather than a code
-- list copied into every service.
--
-- ⚠ RATIFICATION REQUIRED. Every code and display below is an ENGINEERING SEED assembled from the
-- WHO consolidated HIV and TB guidelines and the Zimbabwe OSD, but the primary texts are NOT
-- vendored under docs/reference/, so this cannot be mechanically checked against a pinned source and
-- must not drive care until MoHCC ratifies it. This mirrors the RMNP MEC/SPR posture:
-- primaryTextVendored = false blocks ratification. Under-listing a regimen a clinician needs and
-- mis-labelling one both harm, so the set is offered as a reference the UI presents and reporting
-- maps against — not as a closed set the write refuses membership of, until it is complete and
-- ratified.
--
-- CONFIDENTIALITY. The HIV programme observation codes (viral load, CD4, WHO stage) are HIV content
-- by nature. V008 already routes the HIV ICD-10 codes (B20–B24, Z21, R75) to the HIV confidential
-- category; this migration additionally maps the HIV programme observation codes to it, so an
-- isolated viral-load result is classifiable as HIV content even without its problem row in view.
-- The stampable class stays withheld while the map is unratified (ConfidentialCategoryService) — no
-- record carries a protection label that does not protect it.

-- ─────────────────────────────────────────────────────────────────────
-- 1. CodeSystem + ValueSet — HIV ART regimens
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/hiv-art-regimen',
    '1.0.0',
    'ImpiloHivArtRegimen',
    'Impilo HIV ART Regimens',
    'Antiretroviral therapy regimens by treatment line. Engineering seed from the WHO consolidated '
        || 'HIV guidelines and the Zimbabwe OSD; pending MoHCC ratification.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/hiv-art-regimen",
      "version": "1.0.0",
      "name": "ImpiloHivArtRegimen",
      "title": "Impilo HIV ART Regimens",
      "status": "active",
      "content": "complete",
      "property": [{"code": "line", "type": "string", "description": "Treatment line the regimen belongs to"}],
      "concept": [
        {"code": "TLD",        "display": "TDF + 3TC + DTG (tenofovir/lamivudine/dolutegravir)", "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "TAFED",      "display": "TAF + FTC + DTG",                                       "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "TLE",        "display": "TDF + 3TC + EFV (efavirenz)",                           "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "ABC_3TC_DTG","display": "ABC + 3TC + DTG",                                       "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "AZT_3TC_DTG","display": "AZT + 3TC + DTG",                                       "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "AZT_3TC_EFV","display": "AZT + 3TC + EFV",                                       "property": [{"code": "line", "valueString": "FIRST_LINE"}]},
        {"code": "AZT_3TC_LPVr","display": "AZT + 3TC + LPV/r (lopinavir/ritonavir)",              "property": [{"code": "line", "valueString": "SECOND_LINE"}]},
        {"code": "TDF_3TC_LPVr","display": "TDF + 3TC + LPV/r",                                    "property": [{"code": "line", "valueString": "SECOND_LINE"}]},
        {"code": "AZT_3TC_ATVr","display": "AZT + 3TC + ATV/r (atazanavir/ritonavir)",             "property": [{"code": "line", "valueString": "SECOND_LINE"}]},
        {"code": "TDF_3TC_DTG_2L","display": "TDF + 3TC + DTG (second-line switch)",               "property": [{"code": "line", "valueString": "SECOND_LINE"}]},
        {"code": "DRVr_DTG_NRTI","display": "DRV/r (darunavir/ritonavir) + DTG + optimised NRTI",  "property": [{"code": "line", "valueString": "THIRD_LINE"}]},
        {"code": "ABC_3TC_LPVr","display": "ABC + 3TC + LPV/r (paediatric)",                       "property": [{"code": "line", "valueString": "FIRST_LINE"}]}
      ]
    }$cs$,
    md5('hiv-art-regimen-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/hiv-art-regimen',
    '1.0.0',
    'ImpiloHivArtRegimenVS',
    'Impilo HIV ART Regimens (all)',
    'All ART regimens. Binding target for pct_treatment_regimens.regimen_code on HIV_CARE enrolments.',
    'PUBLISHED',
    $vs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/hiv-art-regimen",
      "version": "1.0.0",
      "name": "ImpiloHivArtRegimenVS",
      "status": "active",
      "compose": {"include": [{"system": "https://impilo.gov.zw/fhir/CodeSystem/hiv-art-regimen"}]}
    }$vs$,
    md5('hiv-art-regimen-vs-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 2. CodeSystem + ValueSet — TB treatment regimens
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/tb-treatment-regimen',
    '1.0.0',
    'ImpiloTbTreatmentRegimen',
    'Impilo TB Treatment Regimens',
    'TB treatment regimens (drug-susceptible, drug-resistant and preventive). Standard code letters: '
        || 'H isoniazid, R rifampicin, Z pyrazinamide, E ethambutol, P rifapentine, M moxifloxacin, '
        || 'Bdq bedaquiline, Pa pretomanid, Lzd linezolid. Engineering seed pending MoHCC ratification.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/tb-treatment-regimen",
      "version": "1.0.0",
      "name": "ImpiloTbTreatmentRegimen",
      "title": "Impilo TB Treatment Regimens",
      "status": "active",
      "content": "complete",
      "property": [{"code": "class", "type": "string", "description": "DS_TB, DR_TB or TPT"}],
      "concept": [
        {"code": "2HRZE_4HR",   "display": "2HRZE / 4HR — drug-susceptible TB, new and relapse",   "property": [{"code": "class", "valueString": "DS_TB"}]},
        {"code": "2HRZE_4HRE",  "display": "2HRZE / 4HRE — retreatment (legacy)",                   "property": [{"code": "class", "valueString": "DS_TB"}]},
        {"code": "2HRZ_4HR",    "display": "2HRZ / 4HR — paediatric, low-risk of ethambutol",       "property": [{"code": "class", "valueString": "DS_TB"}]},
        {"code": "2HPMZ_2HPM",  "display": "2HPMZ / 2HPM — 4-month rifapentine/moxifloxacin regimen","property": [{"code": "class", "valueString": "DS_TB"}]},
        {"code": "BPaLM",       "display": "BPaLM — bedaquiline, pretomanid, linezolid, moxifloxacin (6 months)", "property": [{"code": "class", "valueString": "DR_TB"}]},
        {"code": "BPaL",        "display": "BPaL — bedaquiline, pretomanid, linezolid",              "property": [{"code": "class", "valueString": "DR_TB"}]},
        {"code": "MDR_LONGER",  "display": "Longer MDR-TB regimen (bedaquiline-containing)",         "property": [{"code": "class", "valueString": "DR_TB"}]},
        {"code": "TPT_3HP",     "display": "3HP — isoniazid + rifapentine weekly for 12 weeks",      "property": [{"code": "class", "valueString": "TPT"}]},
        {"code": "TPT_6H",      "display": "6H — isoniazid daily for 6 months",                       "property": [{"code": "class", "valueString": "TPT"}]},
        {"code": "TPT_1HP",     "display": "1HP — isoniazid + rifapentine daily for 1 month",        "property": [{"code": "class", "valueString": "TPT"}]}
      ]
    }$cs$,
    md5('tb-treatment-regimen-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/tb-treatment-regimen',
    '1.0.0',
    'ImpiloTbTreatmentRegimenVS',
    'Impilo TB Treatment Regimens (all)',
    'All TB regimens. Binding target for pct_treatment_regimens.regimen_code on TB_TREATMENT enrolments.',
    'PUBLISHED',
    $vs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/tb-treatment-regimen",
      "version": "1.0.0",
      "name": "ImpiloTbTreatmentRegimenVS",
      "status": "active",
      "compose": {"include": [{"system": "https://impilo.gov.zw/fhir/CodeSystem/tb-treatment-regimen"}]}
    }$vs$,
    md5('tb-treatment-regimen-vs-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 3. CodeSystem + ValueSet — HIV/TB programme observations
--    The codes pct_observations.code carries for programme monitoring. Mapped to LOINC below where
--    a confident equivalent exists; the rest are impilo-native and say so rather than assert a LOINC
--    that might be wrong (a mis-asserted LOINC is a data-quality lie that surfaces only downstream).
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation',
    '1.0.0',
    'ImpiloHivTbProgrammeObservation',
    'Impilo HIV/TB Programme Observations',
    'Observations the HIV and TB programmes monitor. Engineering seed pending MoHCC ratification.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation",
      "version": "1.0.0",
      "name": "ImpiloHivTbProgrammeObservation",
      "title": "Impilo HIV/TB Programme Observations",
      "status": "active",
      "content": "complete",
      "property": [{"code": "programme", "type": "string"}, {"code": "unit", "type": "string"}],
      "concept": [
        {"code": "HIV_VIRAL_LOAD",   "display": "HIV plasma viral load", "property": [{"code": "programme", "valueString": "HIV_CARE"}, {"code": "unit", "valueString": "copies/mL"}]},
        {"code": "CD4_ABSOLUTE",     "display": "CD4 absolute count",    "property": [{"code": "programme", "valueString": "HIV_CARE"}, {"code": "unit", "valueString": "cells/uL"}]},
        {"code": "CD4_PERCENT",      "display": "CD4 percentage",        "property": [{"code": "programme", "valueString": "HIV_CARE"}, {"code": "unit", "valueString": "%"}]},
        {"code": "WHO_CLINICAL_STAGE","display": "WHO HIV clinical stage (1-4)", "property": [{"code": "programme", "valueString": "HIV_CARE"}]},
        {"code": "TB_SMEAR",         "display": "TB sputum smear microscopy (AFB)", "property": [{"code": "programme", "valueString": "TB_TREATMENT"}]},
        {"code": "TB_XPERT_MTB",     "display": "Xpert MTB/RIF — M. tuberculosis detection", "property": [{"code": "programme", "valueString": "TB_TREATMENT"}]},
        {"code": "TB_XPERT_RIF",     "display": "Xpert MTB/RIF — rifampicin resistance", "property": [{"code": "programme", "valueString": "TB_TREATMENT"}]},
        {"code": "TB_CULTURE",       "display": "TB culture", "property": [{"code": "programme", "valueString": "TB_TREATMENT"}]},
        {"code": "TB_LF_LAM",        "display": "Urine lipoarabinomannan (LF-LAM)", "property": [{"code": "programme", "valueString": "TB_TREATMENT"}]}
      ]
    }$cs$,
    md5('hiv-tb-programme-observation-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/hiv-tb-programme-observation',
    '1.0.0',
    'ImpiloHivTbProgrammeObservationVS',
    'Impilo HIV/TB Programme Observations (all)',
    'All programme observation codes. Binding target for pct_observations.code on programme monitoring.',
    'PUBLISHED',
    $vs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/hiv-tb-programme-observation",
      "version": "1.0.0",
      "name": "ImpiloHivTbProgrammeObservationVS",
      "status": "active",
      "compose": {"include": [{"system": "https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation"}]}
    }$vs$,
    md5('hiv-tb-programme-observation-vs-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 4. CodeSystem + ValueSet — programme treatment outcomes (WHO cohort outcomes)
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/programme-treatment-outcome',
    '1.0.0',
    'ImpiloProgrammeTreatmentOutcome',
    'Impilo Programme Treatment Outcomes',
    'WHO cohort treatment outcomes for HIV and TB programmes. Aligns with the pct_programme_enrolments '
        || 'exit_reason vocabulary. Engineering seed pending MoHCC ratification.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/programme-treatment-outcome",
      "version": "1.0.0",
      "name": "ImpiloProgrammeTreatmentOutcome",
      "title": "Impilo Programme Treatment Outcomes",
      "status": "active",
      "content": "complete",
      "concept": [
        {"code": "CURED",              "display": "Cured (TB, bacteriologically confirmed)"},
        {"code": "TREATMENT_COMPLETED","display": "Treatment completed"},
        {"code": "TREATMENT_FAILED",   "display": "Treatment failed"},
        {"code": "DIED",               "display": "Died"},
        {"code": "LOST_TO_FOLLOW_UP",  "display": "Lost to follow-up"},
        {"code": "TRANSFERRED_OUT",    "display": "Transferred out (outcome not yet known here)"},
        {"code": "STOPPED_BY_PATIENT", "display": "Stopped treatment against advice"},
        {"code": "NOT_EVALUATED",      "display": "Not evaluated (no outcome assigned)"}
      ]
    }$cs$,
    md5('programme-treatment-outcome-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/programme-treatment-outcome',
    '1.0.0',
    'ImpiloProgrammeTreatmentOutcomeVS',
    'Impilo Programme Treatment Outcomes (all)',
    'All programme treatment outcomes. Reporting target for HIV and TB cohort outcomes.',
    'PUBLISHED',
    $vs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/programme-treatment-outcome",
      "version": "1.0.0",
      "name": "ImpiloProgrammeTreatmentOutcomeVS",
      "status": "active",
      "compose": {"include": [{"system": "https://impilo.gov.zw/fhir/CodeSystem/programme-treatment-outcome"}]}
    }$vs$,
    md5('programme-treatment-outcome-vs-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 5. ConceptMap — programme observation -> LOINC, flattened for lookup.
--    Only the codes with a confident LOINC equivalent are mapped; equivalence 'equivalent'.
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CONCEPT_MAP',
    'https://impilo.gov.zw/fhir/ConceptMap/programme-observation-to-loinc',
    '1.0.0',
    'ImpiloProgrammeObservationToLoinc',
    'Impilo Programme Observation to LOINC',
    'Maps HIV/TB programme observation codes to LOINC for FHIR export. Only confident equivalents '
        || 'are mapped; unmapped codes are impilo-native pending a curated LOINC binding.',
    'PUBLISHED',
    $cm${
      "resourceType": "ConceptMap",
      "url": "https://impilo.gov.zw/fhir/ConceptMap/programme-observation-to-loinc",
      "version": "1.0.0",
      "name": "ImpiloProgrammeObservationToLoinc",
      "status": "active",
      "sourceUri": "https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation",
      "targetUri": "http://loinc.org",
      "ratificationStatus": "ENGINEERING_SEED"
    }$cm$,
    md5('programme-observation-to-loinc-1.0.0'),
    'MEDICINE-W3', 'ZW', 'medicine-hiv-tb-seed', 'medicine-hiv-tb-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

INSERT INTO zibo_mappings_index
    (tenant_id, source_system, source_code, source_display,
     target_system, target_code, target_display, equivalence, conceptmap_ref, confidence)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation',
    m.source_code, m.source_display,
    'http://loinc.org', m.target_code, m.target_display, 'equivalent',
    (SELECT artifact_id FROM zibo_artifacts
      WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
        AND canonical_url = 'https://impilo.gov.zw/fhir/ConceptMap/programme-observation-to-loinc'
        AND version = '1.0.0'),
    1.00
FROM (VALUES
    ('HIV_VIRAL_LOAD', 'HIV plasma viral load', '20447-9', 'HIV 1 RNA [#/volume] (viral load) in Plasma'),
    ('CD4_ABSOLUTE',   'CD4 absolute count',    '24467-3', 'CD3+CD4+ (T4 helper) cells [#/volume] in Blood'),
    ('CD4_PERCENT',    'CD4 percentage',        '8123-2',  'CD3+CD4+ cells/100 cells in Blood'),
    ('TB_SMEAR',       'TB sputum smear microscopy (AFB)', '11475-1', 'Microscopic observation [Identifier] in Sputum by Acid fast stain')
) AS m(source_code, source_display, target_code, target_display)
WHERE NOT EXISTS (
    SELECT 1 FROM zibo_mappings_index x
     WHERE x.tenant_id = '00000000-0000-0000-0000-000000000001'
       AND x.source_system = 'https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation'
       AND x.source_code = m.source_code
       AND x.target_system = 'http://loinc.org'
);

-- ─────────────────────────────────────────────────────────────────────
-- 6. Extend the confidential map (V008) — HIV programme observation codes are HIV content.
--    So an isolated viral-load/CD4/WHO-stage result classifies as HIV even without its problem row.
--    Uses the same ConceptMap-to-confidential-category machinery; the stampable class stays withheld
--    while unratified (CATEGORY_MAP_RATIFIED = false), so nothing is falsely labelled.
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_mappings_index
    (tenant_id, source_system, source_code, source_display,
     target_system, target_code, target_display, equivalence, conceptmap_ref, confidence)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation',
    m.source_code, m.source_display,
    'https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category',
    'HIV', 'HIV status, care and treatment', 'wider',
    (SELECT artifact_id FROM zibo_artifacts
      WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
        AND canonical_url = 'https://impilo.gov.zw/fhir/ConceptMap/clinical-code-to-confidential-category'
        AND version = '1.0.0'),
    1.00
FROM (VALUES
    ('HIV_VIRAL_LOAD',    'HIV plasma viral load'),
    ('CD4_ABSOLUTE',      'CD4 absolute count'),
    ('CD4_PERCENT',       'CD4 percentage'),
    ('WHO_CLINICAL_STAGE','WHO HIV clinical stage')
) AS m(source_code, source_display)
WHERE NOT EXISTS (
    SELECT 1 FROM zibo_mappings_index x
     WHERE x.tenant_id = '00000000-0000-0000-0000-000000000001'
       AND x.source_system = 'https://impilo.gov.zw/fhir/CodeSystem/hiv-tb-programme-observation'
       AND x.source_code = m.source_code
       AND x.target_system = 'https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category'
);
