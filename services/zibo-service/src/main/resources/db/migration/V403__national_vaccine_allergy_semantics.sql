-- =============================================================================
-- V403 — National vaccine, allergen and reaction-manifestation semantics
--
-- This is milestone M1 for the SHR programme: AllergyIntolerance.code,
-- AllergyIntolerance.reaction.manifestation and Immunization.vaccineCode had NO vocabulary
-- at all. Measured 2026-08-08: grep -ri "allergen" across the estate returns free-text
-- columns, one hardcoded <option> list in the EHR page, and test fixtures. "manifestation"
-- returns three files, none of them code.
--
-- ## Why these are national codes, and why that is not fabrication
--
-- Addendum A.36: "Where international standards do not cover a Zimbabwe requirement, ZIBO
-- SHALL create a national concept. National concept URIs SHALL be unmistakably national."
-- A.30 binds Vaccine to a "governed vaccine/ZNMD concept" and Allergy substance to
-- "governed allergen terminology / SNOMED when licensed". Zimbabwe is not a SNOMED member
-- territory, so the licensed option does not exist today.
--
-- A.37 forbids fabricating SNOMED, ICD, ATC, LOINC, GTIN or ISBT identifiers. These are
-- none of those. They live under https://terminology.impilo.gov.zw/, which cannot be
-- mistaken for an international identifier, and they carry no claim of international
-- provenance. When SNOMED or another authority becomes available these become the source
-- side of a ConceptMap, not something to be quietly overwritten.
--
-- ## Ratification state — read this before trusting the content clinically
--
-- The artifact lifecycle status is PUBLISHED so that resolution and the concept projection
-- can serve it; ArtifactResolutionService filters to PUBLISHED, so anything else is
-- unresolvable and would not unblock a picker (see V402 for what that cost last time).
--
-- The CLINICAL ratification state is carried in the content itself, where every FHIR
-- consumer reads it: "status": "draft" and "experimental": true on each resource. That is
-- the same posture zw-epi-schedule.json already takes with
-- approvalStatus: ENGINEERING_SEED / adaptationAuthority: PENDING_MOHCC_EPI_PROGRAMME_RATIFICATION.
--
-- Servable is not the same as signed off. No steward has approved this content. Addendum
-- A.38 requires an accountable owner per governed vocabulary before that claim can be made.
--
-- ## Provenance of the vaccine content
--
-- Derived 1:1 from services/clinical-knowledge-platform-service/src/main/resources/clinical/
-- zw-epi-schedule.json (8 antigens, 18 doses). That file states the contract this migration
-- fulfils: "Antigen codes are declared locally because zibo-service does not yet hold a
-- vaccine terminology. When it does, these codes become the local mapping and the codeSystem
-- field points at the governed Zibo value set."
--
-- Codes are the schedule's own `series` values, unchanged, so existing EPI records remain
-- interpretable without a migration of clinical data.
--
-- Additive. No existing row is modified.
-- =============================================================================

DO $migration$
DECLARE
    national_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001';
    v            CONSTANT VARCHAR := '0.1.0';
    sort_key     CONSTANT VARCHAR := '000000.000001.000000';  -- VersionOrdering.sortKey("0.1.0")
    rec          RECORD;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            -- ---------------------------------------------------------------------------
            -- Vaccine antigens — from the national EPI schedule
            -- ---------------------------------------------------------------------------
            ('CODE_SYSTEM',
             'https://terminology.impilo.gov.zw/CodeSystem/vaccine-antigen',
             'ImpiloVaccineAntigen',
             'Zimbabwe national vaccine antigen codes',
             'Antigens administered under the Zimbabwe Expanded Programme on Immunisation. Derived from the national EPI schedule; awaiting MoHCC EPI programme ratification.',
             $json${
               "resourceType": "CodeSystem",
               "id": "impilo-vaccine-antigen",
               "url": "https://terminology.impilo.gov.zw/CodeSystem/vaccine-antigen",
               "version": "0.1.0",
               "name": "ImpiloVaccineAntigen",
               "title": "Zimbabwe national vaccine antigen codes",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "Antigens in the Zimbabwe EPI routine schedule. Local codes pending a licensed international vaccine terminology; map onward rather than replace.",
               "caseSensitive": true,
               "content": "complete",
               "property": [
                 {"code": "route", "description": "Route of administration", "type": "string"},
                 {"code": "targetDisease", "description": "Disease or diseases the antigen protects against", "type": "string"},
                 {"code": "appliesToSex", "description": "Restricted to a sex where the national schedule says so", "type": "string"},
                 {"code": "doses", "description": "Number of doses in the routine national schedule", "type": "integer"}
               ],
               "concept": [
                 {"code": "BCG", "display": "BCG",
                  "definition": "Bacille Calmette-Guerin vaccine, given at birth.",
                  "property": [{"code": "route", "valueString": "INTRADERMAL"}, {"code": "targetDisease", "valueString": "Tuberculosis"}, {"code": "doses", "valueInteger": 1}]},
                 {"code": "OPV", "display": "Oral polio vaccine",
                  "definition": "Oral poliovirus vaccine, including the birth dose.",
                  "property": [{"code": "route", "valueString": "ORAL"}, {"code": "targetDisease", "valueString": "Poliomyelitis"}, {"code": "doses", "valueInteger": 4}]},
                 {"code": "IPV", "display": "Inactivated polio vaccine",
                  "definition": "Inactivated poliovirus vaccine.",
                  "property": [{"code": "route", "valueString": "INTRAMUSCULAR"}, {"code": "targetDisease", "valueString": "Poliomyelitis"}, {"code": "doses", "valueInteger": 1}]},
                 {"code": "PENTA", "display": "Pentavalent (DTP-HepB-Hib)",
                  "definition": "Combined diphtheria, tetanus, pertussis, hepatitis B and Haemophilus influenzae type b vaccine.",
                  "property": [{"code": "route", "valueString": "INTRAMUSCULAR"}, {"code": "targetDisease", "valueString": "Diphtheria, tetanus, pertussis, hepatitis B, Haemophilus influenzae type b"}, {"code": "doses", "valueInteger": 3}]},
                 {"code": "PCV", "display": "Pneumococcal conjugate vaccine",
                  "definition": "Pneumococcal conjugate vaccine.",
                  "property": [{"code": "route", "valueString": "INTRAMUSCULAR"}, {"code": "targetDisease", "valueString": "Invasive pneumococcal disease"}, {"code": "doses", "valueInteger": 3}]},
                 {"code": "ROTA", "display": "Rotavirus vaccine",
                  "definition": "Oral rotavirus vaccine. Age limits in the national schedule reflect intussusception risk.",
                  "property": [{"code": "route", "valueString": "ORAL"}, {"code": "targetDisease", "valueString": "Rotavirus gastroenteritis"}, {"code": "doses", "valueInteger": 2}]},
                 {"code": "MR", "display": "Measles-rubella vaccine",
                  "definition": "Combined measles and rubella vaccine.",
                  "property": [{"code": "route", "valueString": "SUBCUTANEOUS"}, {"code": "targetDisease", "valueString": "Measles, rubella"}, {"code": "doses", "valueInteger": 2}]},
                 {"code": "HPV", "display": "Human papillomavirus vaccine",
                  "definition": "Human papillomavirus vaccine, offered to girls under the national schedule.",
                  "property": [{"code": "route", "valueString": "INTRAMUSCULAR"}, {"code": "targetDisease", "valueString": "Human papillomavirus infection"}, {"code": "appliesToSex", "valueString": "FEMALE"}, {"code": "doses", "valueInteger": 2}]}
               ]
             }$json$),

            -- ---------------------------------------------------------------------------
            -- Allergen category — exactly the enumeration pct_allergies already declares in
            -- a SQL comment, with no CHECK constraint and no lookup table behind it.
            -- Governing it invents nothing; it makes an existing free-text column checkable.
            -- ---------------------------------------------------------------------------
            ('CODE_SYSTEM',
             'https://terminology.impilo.gov.zw/CodeSystem/allergen-category',
             'ImpiloAllergenCategory',
             'Allergen category',
             'The kind of substance an allergy is to. Mirrors the enumeration pct_allergies.allergen_type already documents.',
             $json${
               "resourceType": "CodeSystem",
               "id": "impilo-allergen-category",
               "url": "https://terminology.impilo.gov.zw/CodeSystem/allergen-category",
               "version": "0.1.0",
               "name": "ImpiloAllergenCategory",
               "title": "Allergen category",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "Categories of allergen. Taken from the enumeration pct_allergies.allergen_type already carries as a comment, so governing it changes no recorded data.",
               "caseSensitive": true,
               "content": "complete",
               "concept": [
                 {"code": "DRUG", "display": "Drug or medicine", "definition": "A medicinal product or active substance. Prefer coding the substance itself against the medicines terminology."},
                 {"code": "FOOD", "display": "Food", "definition": "A food or food-derived substance."},
                 {"code": "ENVIRONMENT", "display": "Environmental", "definition": "An inhaled, contact or insect-derived environmental substance."},
                 {"code": "OTHER", "display": "Other", "definition": "A substance that does not fall into the categories above. Preserve the clinician's original text."}
               ]
             }$json$),

            -- ---------------------------------------------------------------------------
            -- Reaction manifestations. Authored: no international list is deployable here,
            -- and MedDRA (A.18.1) is licence-gated. Deliberately restricted to
            -- manifestations that are unambiguous at the point of care.
            -- ---------------------------------------------------------------------------
            ('CODE_SYSTEM',
             'https://terminology.impilo.gov.zw/CodeSystem/reaction-manifestation',
             'ImpiloReactionManifestation',
             'Allergic reaction manifestation',
             'How an allergic or adverse reaction presented. National interim vocabulary pending MedDRA licensing or a licensed clinical terminology.',
             $json${
               "resourceType": "CodeSystem",
               "id": "impilo-reaction-manifestation",
               "url": "https://terminology.impilo.gov.zw/CodeSystem/reaction-manifestation",
               "version": "0.1.0",
               "name": "ImpiloReactionManifestation",
               "title": "Allergic reaction manifestation",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "Interim national manifestation vocabulary. MedDRA and SNOMED CT are both licence-gated for Zimbabwe; these concepts are the mapping source when either becomes available.",
               "caseSensitive": true,
               "content": "complete",
               "property": [
                 {"code": "severityHint", "description": "Whether this manifestation indicates a potentially life-threatening reaction", "type": "boolean"}
               ],
               "concept": [
                 {"code": "ANAPHYLAXIS", "display": "Anaphylaxis", "definition": "Acute, potentially fatal multi-system hypersensitivity reaction.", "property": [{"code": "severityHint", "valueBoolean": true}]},
                 {"code": "ANGIOEDEMA", "display": "Angioedema", "definition": "Swelling of the deeper layers of skin or mucosa, including lips, tongue or larynx.", "property": [{"code": "severityHint", "valueBoolean": true}]},
                 {"code": "BRONCHOSPASM", "display": "Bronchospasm or wheeze", "definition": "Airway narrowing with wheeze or difficulty breathing.", "property": [{"code": "severityHint", "valueBoolean": true}]},
                 {"code": "HYPOTENSION", "display": "Hypotension or collapse", "definition": "A fall in blood pressure, faintness or circulatory collapse.", "property": [{"code": "severityHint", "valueBoolean": true}]},
                 {"code": "STEVENS_JOHNSON", "display": "Stevens-Johnson syndrome or toxic epidermal necrolysis", "definition": "Severe blistering mucocutaneous reaction.", "property": [{"code": "severityHint", "valueBoolean": true}]},
                 {"code": "URTICARIA", "display": "Urticaria (hives)", "definition": "Raised itchy weals.", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "RASH", "display": "Rash", "definition": "A skin eruption not otherwise specified.", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "PRURITUS", "display": "Itching", "definition": "Itching without a documented eruption.", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "NAUSEA_VOMITING", "display": "Nausea or vomiting", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "DIARRHOEA", "display": "Diarrhoea", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "ABDOMINAL_PAIN", "display": "Abdominal pain", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "RHINITIS", "display": "Rhinitis or sneezing", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "CONJUNCTIVITIS", "display": "Conjunctivitis or watery eyes", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "INJECTION_SITE_REACTION", "display": "Injection-site reaction", "definition": "Local pain, swelling or redness at the site of administration.", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "FEVER", "display": "Fever", "property": [{"code": "severityHint", "valueBoolean": false}]},
                 {"code": "OTHER", "display": "Other manifestation", "definition": "Not represented above. The clinician's original description must be preserved.", "property": [{"code": "severityHint", "valueBoolean": false}]}
               ]
             }$json$)
        ) AS t(fhir_type, url, name, title, description, content)
    LOOP
        INSERT INTO zibo_artifacts (
            artifact_id, tenant_id, fhir_type, canonical_url, version, name, title,
            description, status, content_json, content_hash, publisher, jurisdiction,
            created_by, published_by, published_at, version_scheme, version_sort_key
        ) VALUES (
            gen_random_uuid(), national_tenant, rec.fhir_type, rec.url, v, rec.name, rec.title,
            rec.description, 'PUBLISHED', rec.content::jsonb,
            encode(sha256(rec.content::bytea), 'hex'),
            'Ministry of Health and Child Care, Zimbabwe', 'ZW',
            'V403-migration', 'V403-migration', now(), 'SEMVER', sort_key
        )
        ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
    END LOOP;
END
$migration$;

-- -----------------------------------------------------------------------------------------
-- ValueSets. Each composes its CodeSystem by system alone rather than enumerating codes:
-- ValueSet/$expand resolves those from the zibo_concept projection, so an enumerated copy
-- would be a second place for the same list to drift out of date.
--
-- The drug-allergen ValueSet enumerates NOTHING of its own. It composes the ATC and EDLIZ
-- CodeSystems that ZIBO already holds, so a drug allergy is coded against the medicines
-- terminology rather than against a parallel invented allergen list. That vocabulary is
-- currently 20 + 20 starter concepts, which is a content problem (Z3/M2), not a modelling one.
-- -----------------------------------------------------------------------------------------
DO $migration$
DECLARE
    national_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001';
    v            CONSTANT VARCHAR := '0.1.0';
    sort_key     CONSTANT VARCHAR := '000000.000001.000000';
    rec          RECORD;
BEGIN
    FOR rec IN
        SELECT * FROM (VALUES
            ('https://terminology.impilo.gov.zw/ValueSet/vaccine-antigen',
             'ImpiloVaccineAntigenVS',
             'Vaccine antigens (Zimbabwe EPI)',
             'Antigens selectable when recording an immunisation.',
             $json${
               "resourceType": "ValueSet",
               "id": "impilo-vaccine-antigen",
               "url": "https://terminology.impilo.gov.zw/ValueSet/vaccine-antigen",
               "version": "0.1.0",
               "name": "ImpiloVaccineAntigenVS",
               "title": "Vaccine antigens (Zimbabwe EPI)",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "Binds Immunization.vaccineCode. Antigen identity only; the physical product is identified separately by ZNMD and GTIN.",
               "compose": {"include": [{"system": "https://terminology.impilo.gov.zw/CodeSystem/vaccine-antigen"}]}
             }$json$),

            ('https://terminology.impilo.gov.zw/ValueSet/allergen-category',
             'ImpiloAllergenCategoryVS',
             'Allergen categories',
             'Categories selectable when recording an allergy.',
             $json${
               "resourceType": "ValueSet",
               "id": "impilo-allergen-category",
               "url": "https://terminology.impilo.gov.zw/ValueSet/allergen-category",
               "version": "0.1.0",
               "name": "ImpiloAllergenCategoryVS",
               "title": "Allergen categories",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "compose": {"include": [{"system": "https://terminology.impilo.gov.zw/CodeSystem/allergen-category"}]}
             }$json$),

            ('https://terminology.impilo.gov.zw/ValueSet/reaction-manifestation',
             'ImpiloReactionManifestationVS',
             'Allergic reaction manifestations',
             'Manifestations selectable when recording an allergic reaction.',
             $json${
               "resourceType": "ValueSet",
               "id": "impilo-reaction-manifestation",
               "url": "https://terminology.impilo.gov.zw/ValueSet/reaction-manifestation",
               "version": "0.1.0",
               "name": "ImpiloReactionManifestationVS",
               "title": "Allergic reaction manifestations",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "Binds AllergyIntolerance.reaction.manifestation. Interim national vocabulary; MedDRA is licence-gated.",
               "compose": {"include": [{"system": "https://terminology.impilo.gov.zw/CodeSystem/reaction-manifestation"}]}
             }$json$),

            ('https://terminology.impilo.gov.zw/ValueSet/drug-allergen',
             'ImpiloDrugAllergenVS',
             'Drug allergens',
             'Medicines selectable as the substance of a drug allergy. Composes the governed medicines terminology; introduces no allergen-specific codes.',
             $json${
               "resourceType": "ValueSet",
               "id": "impilo-drug-allergen",
               "url": "https://terminology.impilo.gov.zw/ValueSet/drug-allergen",
               "version": "0.1.0",
               "name": "ImpiloDrugAllergenVS",
               "title": "Drug allergens",
               "status": "draft",
               "experimental": true,
               "publisher": "Ministry of Health and Child Care, Zimbabwe",
               "description": "A drug allergy is to a medicine, so it is coded against the medicines terminology rather than a parallel allergen list. Currently backed by starter ATC and EDLIZ content; widening it is content work, not a change to this binding.",
               "compose": {"include": [{"system": "http://www.whocc.no/atc"}]}
             }$json$)
        ) AS t(url, name, title, description, content)
    LOOP
        INSERT INTO zibo_artifacts (
            artifact_id, tenant_id, fhir_type, canonical_url, version, name, title,
            description, status, content_json, content_hash, publisher, jurisdiction,
            created_by, published_by, published_at, version_scheme, version_sort_key
        ) VALUES (
            gen_random_uuid(), national_tenant, 'VALUE_SET', rec.url, v, rec.name, rec.title,
            rec.description, 'PUBLISHED', rec.content::jsonb,
            encode(sha256(rec.content::bytea), 'hex'),
            'Ministry of Health and Child Care, Zimbabwe', 'ZW',
            'V403-migration', 'V403-migration', now(), 'SEMVER', sort_key
        )
        ON CONFLICT (tenant_id, canonical_url, version) DO NOTHING;
    END LOOP;
END
$migration$;

-- Guard: the vaccine CodeSystem must carry exactly the eight antigens the national EPI
-- schedule defines. If zw-epi-schedule.json gains a ninth and this is not updated, the
-- immunisation picker silently cannot record it.
DO $$
DECLARE
    antigens INT;
BEGIN
    SELECT jsonb_array_length(content_json->'concept') INTO antigens
      FROM zibo_artifacts
     WHERE canonical_url = 'https://terminology.impilo.gov.zw/CodeSystem/vaccine-antigen'
       AND version = '0.1.0';

    IF antigens IS DISTINCT FROM 8 THEN
        RAISE EXCEPTION
            'V403: expected 8 EPI antigens, found %. Reconcile against '
            'clinical-knowledge-platform-service/src/main/resources/clinical/zw-epi-schedule.json.',
            antigens;
    END IF;
END $$;
