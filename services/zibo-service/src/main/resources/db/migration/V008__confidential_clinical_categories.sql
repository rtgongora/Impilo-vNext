-- zibo-service V008 — Confidential clinical categories: the governed answer to
-- "which clinical content is confidential by nature?"
--
-- WHY THIS LIVES HERE. The trust plane enforces WHO may read specially-protected content
-- (tshepo-authz Step 4.7 / the VisibilityProfile.confidentialCategories obligation) and each clinical
-- system of record stamps WHICH of its records carry that class. Left to itself, every service
-- would answer "is this confidential?" with its own hardcoded list of codes, scattering a
-- confidentiality decision across the estate where nobody can review it and no two services agree.
-- Zibo is the terminology system of record, so the list is a governed, versioned, effective-dated
-- artifact here — reviewable, and changeable without a code deploy.
--
-- THREE ARTIFACTS:
--   1. CodeSystem  confidential-clinical-category      — the category vocabulary
--   2. ValueSet    confidential-clinical-category      — bindable set of all categories
--   3. ConceptMap  clinical-code-to-confidential-category — clinical code -> category
-- plus flattened rows in zibo_mappings_index for lookup without parsing the ConceptMap.
--
-- ⚠ RATIFICATION REQUIRED. The category list and every code mapping below are an ENGINEERING
-- SEED, structured and traceable but NOT national protocol. MoHCC and a clinical governance body
-- must ratify them before they drive disclosure decisions in production. Under-classifying leaks a
-- confidence; over-classifying hides records from the clinicians treating the person. Both harm.
--
-- ⚠ SOURCE CODES ARE PREFIXES. ICD-10 confidentiality follows blocks, not individual codes, so
-- source_code holds a prefix ("B20" covers B20.0…B20.9) and equivalence is 'wider' to say so.
-- Consumers must match by longest prefix, so a narrower mapping beats a broader one — that is how
-- F10–F19 lands on SUBSTANCE_USE rather than being swallowed by the F00–F99 mental-health block.
-- Idempotent: ON CONFLICT DO NOTHING on the artifact key; mapping rows guarded by NOT EXISTS.

-- ─────────────────────────────────────────────────────────────────────
-- 1. CodeSystem — the category vocabulary
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CODE_SYSTEM',
    'https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category',
    '1.0.0',
    'ImpiloConfidentialClinicalCategory',
    'Impilo Confidential Clinical Categories',
    'Categories of clinical content that are confidential by nature and therefore classified '
        || 'SPECIALLY_PROTECTED. Engineering seed pending MoHCC ratification.',
    'PUBLISHED',
    $cs${
      "resourceType": "CodeSystem",
      "url": "https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category",
      "version": "1.0.0",
      "name": "ImpiloConfidentialClinicalCategory",
      "title": "Impilo Confidential Clinical Categories",
      "status": "active",
      "content": "complete",
      "concept": [
        {
          "code": "SEXUAL_REPRODUCTIVE_HEALTH",
          "display": "Sexual and reproductive health",
          "definition": "Contraception, termination of pregnancy, sexually transmitted infections, sexual health counselling."
        },
        {
          "code": "HIV",
          "display": "HIV status, care and treatment",
          "definition": "HIV testing, diagnosis, status, antiretroviral treatment and related care."
        },
        {
          "code": "MENTAL_HEALTH",
          "display": "Mental health",
          "definition": "Mental, behavioural and neurodevelopmental disorders, psychiatric care, self-harm and suicidality."
        },
        {
          "code": "SAFEGUARDING",
          "display": "Safeguarding and child protection",
          "definition": "Suspected or confirmed abuse, neglect or maltreatment, and the protection response to it."
        },
        {
          "code": "SUBSTANCE_USE",
          "display": "Substance use",
          "definition": "Alcohol and other substance use disorders and their treatment."
        },
        {
          "code": "GENDER_BASED_VIOLENCE",
          "display": "Gender-based and intimate partner violence",
          "definition": "Sexual assault, intimate partner violence, and the clinical response to it."
        }
      ]
    }$cs$,
    md5('confidential-clinical-category-1.0.0'),
    'PAEDS-W5', 'ZW', 'confidentiality-seed', 'confidentiality-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 2. ValueSet — bindable set of all categories
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'VALUE_SET',
    'https://impilo.gov.zw/fhir/ValueSet/confidential-clinical-category',
    '1.0.0',
    'ImpiloConfidentialClinicalCategoryVS',
    'Impilo Confidential Clinical Categories (all)',
    'All confidential clinical categories. Binding target for record-level confidentiality '
        || 'classification in clinical systems of record.',
    'PUBLISHED',
    $vs${
      "resourceType": "ValueSet",
      "url": "https://impilo.gov.zw/fhir/ValueSet/confidential-clinical-category",
      "version": "1.0.0",
      "name": "ImpiloConfidentialClinicalCategoryVS",
      "status": "active",
      "compose": {
        "include": [
          {"system": "https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category"}
        ]
      }
    }$vs$,
    md5('confidential-clinical-category-vs-1.0.0'),
    'PAEDS-W5', 'ZW', 'confidentiality-seed', 'confidentiality-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 3. ConceptMap — clinical code (prefix) -> confidential category
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_artifacts
    (tenant_id, fhir_type, canonical_url, version, name, title, description,
     status, content_json, content_hash, publisher, jurisdiction, created_by, published_by, published_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'CONCEPT_MAP',
    'https://impilo.gov.zw/fhir/ConceptMap/clinical-code-to-confidential-category',
    '1.0.0',
    'ImpiloClinicalCodeToConfidentialCategory',
    'Impilo Clinical Code to Confidential Category',
    'Maps ICD-10 code prefixes to confidential clinical categories. Source codes are PREFIXES; '
        || 'consumers must match by longest prefix so a narrower mapping wins. Engineering seed '
        || 'pending MoHCC ratification.',
    'PUBLISHED',
    $cm${
      "resourceType": "ConceptMap",
      "url": "https://impilo.gov.zw/fhir/ConceptMap/clinical-code-to-confidential-category",
      "version": "1.0.0",
      "name": "ImpiloClinicalCodeToConfidentialCategory",
      "status": "active",
      "sourceUri": "http://hl7.org/fhir/sid/icd-10",
      "targetUri": "https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category",
      "prefixMatch": true,
      "ratificationStatus": "ENGINEERING_SEED"
    }$cm$,
    md5('clinical-code-to-confidential-category-1.0.0'),
    'PAEDS-W5', 'ZW', 'confidentiality-seed', 'confidentiality-seed', NOW()
) ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────
-- 4. Flattened lookup rows
--
-- Ordering note: F10–F19 (substance use) are listed as their own narrower prefixes so that
-- longest-prefix matching routes them to SUBSTANCE_USE and not to the broader F mental-health
-- block. Both are protected either way; the distinction matters for reporting and for any future
-- per-category policy.
-- ─────────────────────────────────────────────────────────────────────
INSERT INTO zibo_mappings_index
    (tenant_id, source_system, source_code, source_display,
     target_system, target_code, target_display, equivalence, conceptmap_ref, confidence)
SELECT
    '00000000-0000-0000-0000-000000000001',
    'http://hl7.org/fhir/sid/icd-10',
    m.source_code,
    m.source_display,
    'https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category',
    m.target_code,
    m.target_display,
    'wider',
    (SELECT artifact_id FROM zibo_artifacts
      WHERE tenant_id = '00000000-0000-0000-0000-000000000001'
        AND canonical_url = 'https://impilo.gov.zw/fhir/ConceptMap/clinical-code-to-confidential-category'
        AND version = '1.0.0'),
    1.00
FROM (VALUES
    -- HIV
    ('B20', 'HIV disease resulting in infectious and parasitic diseases', 'HIV', 'HIV status, care and treatment'),
    ('B21', 'HIV disease resulting in malignant neoplasms',               'HIV', 'HIV status, care and treatment'),
    ('B22', 'HIV disease resulting in other specified diseases',          'HIV', 'HIV status, care and treatment'),
    ('B23', 'HIV disease resulting in other conditions',                  'HIV', 'HIV status, care and treatment'),
    ('B24', 'Unspecified human immunodeficiency virus disease',           'HIV', 'HIV status, care and treatment'),
    ('Z21', 'Asymptomatic HIV infection status',                          'HIV', 'HIV status, care and treatment'),
    ('R75', 'Laboratory evidence of HIV',                                 'HIV', 'HIV status, care and treatment'),

    -- Sexual and reproductive health
    ('A50', 'Congenital syphilis',            'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A51', 'Early syphilis',                 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A52', 'Late syphilis',                  'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A53', 'Other and unspecified syphilis', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A54', 'Gonococcal infection',           'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A55', 'Chlamydial lymphogranuloma',     'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A56', 'Other sexually transmitted chlamydial diseases', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A57', 'Chancroid',                      'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A58', 'Granuloma inguinale',            'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A59', 'Trichomoniasis',                 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A60', 'Anogenital herpesviral infection', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A63', 'Other predominantly sexually transmitted diseases', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('A64', 'Unspecified sexually transmitted disease', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('O04', 'Medical abortion',               'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('Z30', 'Encounter for contraceptive management', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('Z31', 'Encounter for procreative management',   'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('Z32', 'Encounter for pregnancy test and childbirth/childcare instruction', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),
    ('Z70', 'Counselling related to sexual attitude, behaviour and orientation', 'SEXUAL_REPRODUCTIVE_HEALTH', 'Sexual and reproductive health'),

    -- Substance use (narrower than the F block on purpose — see ordering note)
    ('F10', 'Alcohol-related disorders',      'SUBSTANCE_USE', 'Substance use'),
    ('F11', 'Opioid-related disorders',       'SUBSTANCE_USE', 'Substance use'),
    ('F12', 'Cannabis-related disorders',     'SUBSTANCE_USE', 'Substance use'),
    ('F13', 'Sedative/hypnotic-related disorders', 'SUBSTANCE_USE', 'Substance use'),
    ('F14', 'Cocaine-related disorders',      'SUBSTANCE_USE', 'Substance use'),
    ('F15', 'Other stimulant-related disorders', 'SUBSTANCE_USE', 'Substance use'),
    ('F16', 'Hallucinogen-related disorders', 'SUBSTANCE_USE', 'Substance use'),
    ('F17', 'Nicotine dependence',            'SUBSTANCE_USE', 'Substance use'),
    ('F18', 'Inhalant-related disorders',     'SUBSTANCE_USE', 'Substance use'),
    ('F19', 'Other psychoactive substance-related disorders', 'SUBSTANCE_USE', 'Substance use'),
    ('Z71.4', 'Alcohol abuse counselling and surveillance', 'SUBSTANCE_USE', 'Substance use'),
    ('Z71.5', 'Drug abuse counselling and surveillance',    'SUBSTANCE_USE', 'Substance use'),

    -- Mental health (the rest of the F block, plus self-harm and its sequelae)
    ('F0', 'Organic, including symptomatic, mental disorders', 'MENTAL_HEALTH', 'Mental health'),
    ('F2', 'Schizophrenia, schizotypal and delusional disorders', 'MENTAL_HEALTH', 'Mental health'),
    ('F3', 'Mood (affective) disorders',      'MENTAL_HEALTH', 'Mental health'),
    ('F4', 'Neurotic, stress-related and somatoform disorders', 'MENTAL_HEALTH', 'Mental health'),
    ('F5', 'Behavioural syndromes associated with physiological disturbances', 'MENTAL_HEALTH', 'Mental health'),
    ('F6', 'Disorders of adult personality and behaviour', 'MENTAL_HEALTH', 'Mental health'),
    ('F7', 'Intellectual disability',         'MENTAL_HEALTH', 'Mental health'),
    ('F8', 'Disorders of psychological development', 'MENTAL_HEALTH', 'Mental health'),
    ('F9', 'Behavioural and emotional disorders with onset in childhood and adolescence', 'MENTAL_HEALTH', 'Mental health'),
    ('X6', 'Intentional self-harm by poisoning', 'MENTAL_HEALTH', 'Mental health'),
    ('X7', 'Intentional self-harm',           'MENTAL_HEALTH', 'Mental health'),
    ('X8', 'Intentional self-harm',           'MENTAL_HEALTH', 'Mental health'),
    ('R45.8', 'Other symptoms and signs involving emotional state', 'MENTAL_HEALTH', 'Mental health'),
    ('Z91.5', 'Personal history of self-harm', 'MENTAL_HEALTH', 'Mental health'),

    -- Safeguarding and child protection
    ('T74', 'Maltreatment syndromes',         'SAFEGUARDING', 'Safeguarding and child protection'),
    ('T76', 'Suspected maltreatment syndromes', 'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Y06', 'Neglect and abandonment',        'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Y07', 'Other maltreatment syndromes',   'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Z04.4', 'Examination and observation following alleged rape or seduction', 'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Z04.5', 'Examination and observation following other inflicted injury',    'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Z61', 'Problems related to negative life events in childhood', 'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Z62', 'Problems related to upbringing', 'SAFEGUARDING', 'Safeguarding and child protection'),
    ('Z63', 'Other problems related to primary support group', 'SAFEGUARDING', 'Safeguarding and child protection'),

    -- Gender-based and intimate partner violence
    ('T74.1', 'Physical abuse',               'GENDER_BASED_VIOLENCE', 'Gender-based and intimate partner violence'),
    ('T74.2', 'Sexual abuse',                 'GENDER_BASED_VIOLENCE', 'Gender-based and intimate partner violence'),
    ('Z69', 'Counselling for problems related to abuse', 'GENDER_BASED_VIOLENCE', 'Gender-based and intimate partner violence'),
    ('Z91.41', 'Personal history of adult abuse', 'GENDER_BASED_VIOLENCE', 'Gender-based and intimate partner violence')
) AS m(source_code, source_display, target_code, target_display)
WHERE NOT EXISTS (
    SELECT 1 FROM zibo_mappings_index x
     WHERE x.tenant_id = '00000000-0000-0000-0000-000000000001'
       AND x.source_system = 'http://hl7.org/fhir/sid/icd-10'
       AND x.source_code = m.source_code
       AND x.target_system = 'https://impilo.gov.zw/fhir/CodeSystem/confidential-clinical-category'
);

CREATE INDEX IF NOT EXISTS idx_zibo_mappings_target_system_code
    ON zibo_mappings_index (tenant_id, target_system, target_code);
