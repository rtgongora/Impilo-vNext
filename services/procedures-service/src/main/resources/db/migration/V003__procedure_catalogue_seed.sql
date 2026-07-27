-- =====================================================================================
-- Procedures :: V003 — national procedure catalogue seed
--
-- Seeds 66 definitions spanning the specification's procedure classes, for the default
-- tenant. Idempotent on (tenant_id, definition_code, version).
--
-- HONESTY ABOUT DEPTH. Breadth is not depth and the difference is recorded rather than
-- glossed. Every entry here carries real classification, coding and governance metadata.
-- Requirement sets are seeded at two levels:
--
--   * DEEP — the ten specification demonstration procedures plus the three WHO bellwethers.
--     Full requirement sets: cadre, privilege, equipment, consumables, IPC, monitoring,
--     recovery, blood and laboratory where they apply.
--   * CORE — everything else. Identity, consent, site-and-side where lateralised, cadre
--     and privilege only.
--
-- A CORE entry is safe to route and schedule against, and will produce a thinner readiness
-- verdict than the procedure deserves. That is a known gap, not a finished catalogue, and
-- deepening it is content work per specialty in the S15 waves — not a code change.
--
-- CLINICAL AUTHORITY. Nothing here is ratified. Every row carries
-- approving_authority = 'PENDING_MOHCC_RATIFICATION' because the Ministry is the clinical
-- authority and this programme is not. The column is deliberately non-nullable on
-- PUBLISHED rows so that unratified content must say so out loud rather than appear
-- approved by having an empty authority.
--
-- CODING: SNOMED CT IS DELIBERATELY NULL EVERYWHERE.
--
-- An earlier draft of this seed carried a snomed_ct_code on every row. I could not verify
-- a single one against a SNOMED CT release, and several were visibly malformed — one read
-- "ystoscopy", two were the wrong length for a concept id. Sixty unverified codes that
-- look authoritative are worse than sixty nulls: a null is a gap somebody fills, and a
-- wrong concept id is a mapping somebody trusts and integrates against.
--
-- ICD-9-CM procedure codes are kept ONLY for the ten operations zibo V004 already
-- publishes, because those are verifiable inside this repository. Everything else is NULL.
--
-- Real terminology binding is ZIBO's to own (ADR decision 3) and needs a licensed SNOMED
-- release to validate against. It is recorded as a P1 follow-up, not quietly skipped.
-- =====================================================================================

-- Default tenant, matching the estate's seeded preview tenant.
\set tenant '00000000-0000-4000-8000-000000000001'

INSERT INTO procedures.procedure_definition (
    tenant_id, definition_code, version, clinical_name, synonyms, category, owning_specialty,
    purpose, permitted_settings, anatomical_site, laterality_applicability,
    age_min_days, age_max_days, pregnancy_applicability,
    expected_duration_min, recovery_required, observation_minutes,
    consent_type, safety_pause_template, aftercare_template,
    zibo_code, snomed_ct_code, icd9cm_procedure_code,
    status, approving_authority, approved_by, approved_at, effective_from, source_citation)
VALUES
-- ── Bedside / ward diagnostic and therapeutic ────────────────────────────────────────
(:'tenant','PROC-LUMBAR-PUNCTURE',1,'Lumbar puncture','["LP","spinal tap"]','BEDSIDE','INTERNAL_MEDICINE','DIAGNOSTIC','["BEDSIDE","WARD","CRITICAL_CARE","CLINIC"]','Lumbar spine','MIDLINE',NULL,NULL,'NO_CONSTRAINT',30,TRUE,60,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE','AFTERCARE-LUMBAR-PUNCTURE','LP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §28 demonstration 1'),
(:'tenant','PROC-CHEST-DRAIN',1,'Intercostal chest drain insertion','["tube thoracostomy","chest tube"]','EMERGENCY','EMERGENCY_MEDICINE','THERAPEUTIC','["BEDSIDE","EMERGENCY","CRITICAL_CARE","WARD"]','Pleural cavity','LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,TRUE,120,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE','AFTERCARE-CHEST-DRAIN','CD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §28 demonstration 2'),
(:'tenant','PROC-NEEDLE-THORACOSTOMY',1,'Needle thoracostomy','["needle decompression"]','EMERGENCY','EMERGENCY_MEDICINE','THERAPEUTIC','["BEDSIDE","EMERGENCY"]','Pleural cavity','LATERALISED',NULL,NULL,'NO_CONSTRAINT',5,TRUE,60,'CONSENT-EMERGENCY-EXCEPTION','SAFETY-PAUSE-BEDSIDE','AFTERCARE-CHEST-DRAIN','NT-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'WHO ECO emergency capability'),
(:'tenant','PROC-CENTRAL-LINE',1,'Central venous catheter insertion','["central line","CVC"]','BEDSIDE','CRITICAL_CARE','THERAPEUTIC','["BEDSIDE","CRITICAL_CARE","THEATRE"]','Central vein','LATERALISED',NULL,NULL,'NO_CONSTRAINT',45,TRUE,60,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE','AFTERCARE-CENTRAL-LINE','CVC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'IPC.CORE.ASEPTIC_TECHNIQUE'),
(:'tenant','PROC-ARTERIAL-LINE',1,'Arterial line insertion','["art line"]','BEDSIDE','CRITICAL_CARE','MONITORING','["BEDSIDE","CRITICAL_CARE","THEATRE"]','Radial artery','LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,FALSE,30,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE',NULL,'AL-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'WFSA-WHO monitoring standards'),
(:'tenant','PROC-ASCITIC-TAP',1,'Ascitic tap (paracentesis)','["paracentesis","abdominal tap"]','BEDSIDE','INTERNAL_MEDICINE','BOTH','["BEDSIDE","WARD","CLINIC"]','Peritoneal cavity','MIDLINE',NULL,NULL,'CAUTION',30,FALSE,60,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE','AFTERCARE-PARACENTESIS','AT-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Core medical procedure set'),
(:'tenant','PROC-PLEURAL-ASPIRATION',1,'Pleural aspiration','["thoracentesis"]','BEDSIDE','INTERNAL_MEDICINE','BOTH','["BEDSIDE","WARD","CLINIC"]','Pleural cavity','LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,FALSE,60,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-BEDSIDE',NULL,'PA-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Core medical procedure set'),
(:'tenant','PROC-URINARY-CATHETER',1,'Urinary catheterisation','["Foley","IDC"]','BEDSIDE','GENERAL_MEDICINE','THERAPEUTIC','["BEDSIDE","WARD","EMERGENCY","CLINIC"]','Urinary bladder','MIDLINE',NULL,NULL,'CAUTION',15,FALSE,NULL,'CONSENT-VERBAL','SAFETY-PAUSE-BEDSIDE','AFTERCARE-CATHETER','UC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'IPC core set'),
(:'tenant','PROC-NG-TUBE',1,'Nasogastric tube insertion','["NGT"]','BEDSIDE','GENERAL_MEDICINE','THERAPEUTIC','["BEDSIDE","WARD","EMERGENCY"]','Stomach','MIDLINE',NULL,NULL,'NO_CONSTRAINT',15,FALSE,NULL,'CONSENT-VERBAL','SAFETY-PAUSE-BEDSIDE',NULL,'NG-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Core nursing procedure set'),
(:'tenant','PROC-BONE-MARROW-BIOPSY',1,'Bone marrow aspirate and trephine','["BMAT"]','BEDSIDE','INTERNAL_MEDICINE','DIAGNOSTIC','["BEDSIDE","CLINIC","WARD"]','Posterior iliac crest','LATERALISED',NULL,NULL,'CAUTION',45,TRUE,60,'CONSENT-INVASIVE-BEDSIDE','SAFETY-PAUSE-SPECIMEN','AFTERCARE-BIOPSY','BM-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specimen chain-of-custody set'),
-- ── Endoscopy ────────────────────────────────────────────────────────────────────────
(:'tenant','PROC-OGD',1,'Oesophagogastroduodenoscopy','["OGD","upper GI endoscopy","gastroscopy"]','ENDOSCOPY','SURGERY','BOTH','["ENDOSCOPY","THEATRE"]','Upper gastrointestinal tract','NOT_APPLICABLE',NULL,NULL,'CAUTION',30,TRUE,120,'CONSENT-SEDATION-PROCEDURE','SAFETY-PAUSE-ENDOSCOPY','AFTERCARE-ENDOSCOPY','OGD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §28 demonstration 5'),
(:'tenant','PROC-COLONOSCOPY',1,'Colonoscopy','["lower GI endoscopy"]','ENDOSCOPY','SURGERY','BOTH','["ENDOSCOPY","THEATRE"]','Colon','NOT_APPLICABLE',NULL,NULL,'CAUTION',45,TRUE,120,'CONSENT-SEDATION-PROCEDURE','SAFETY-PAUSE-ENDOSCOPY','AFTERCARE-ENDOSCOPY','COL-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Colorectal surveillance pathway'),
(:'tenant','PROC-FLEXIBLE-SIGMOIDOSCOPY',1,'Flexible sigmoidoscopy','[]','ENDOSCOPY','SURGERY','DIAGNOSTIC','["ENDOSCOPY","CLINIC"]','Sigmoid colon','NOT_APPLICABLE',NULL,NULL,'CAUTION',20,TRUE,60,'CONSENT-PROCEDURE','SAFETY-PAUSE-ENDOSCOPY','AFTERCARE-ENDOSCOPY','SIG-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Colorectal pathway'),
(:'tenant','PROC-BRONCHOSCOPY',1,'Flexible bronchoscopy','[]','ENDOSCOPY','INTERNAL_MEDICINE','BOTH','["ENDOSCOPY","CRITICAL_CARE","THEATRE"]','Tracheobronchial tree','NOT_APPLICABLE',NULL,NULL,'CAUTION',30,TRUE,120,'CONSENT-SEDATION-PROCEDURE','SAFETY-PAUSE-ENDOSCOPY','AFTERCARE-ENDOSCOPY','BR-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Respiratory procedure set'),
(:'tenant','PROC-CYSTOSCOPY',1,'Cystoscopy','[]','ENDOSCOPY','UROLOGY','BOTH','["ENDOSCOPY","THEATRE","CLINIC"]','Urinary bladder','NOT_APPLICABLE',NULL,NULL,'CAUTION',20,TRUE,60,'CONSENT-PROCEDURE','SAFETY-PAUSE-ENDOSCOPY','AFTERCARE-ENDOSCOPY','CY-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Urology pathway'),
-- ── Dialysis, infusion, transfusion ───────────────────────────────────────────────────
(:'tenant','PROC-HAEMODIALYSIS',1,'Haemodialysis session','["HD"]','DIALYSIS','NEPHROLOGY','THERAPEUTIC','["DIALYSIS"]','Vascular access','NOT_APPLICABLE',NULL,NULL,'CAUTION',240,TRUE,30,'CONSENT-PROGRAMME','SAFETY-PAUSE-DIALYSIS','AFTERCARE-DIALYSIS','HD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §28 demonstration 6'),
(:'tenant','PROC-PERITONEAL-DIALYSIS',1,'Peritoneal dialysis exchange','["PD"]','DIALYSIS','NEPHROLOGY','THERAPEUTIC','["DIALYSIS","WARD"]','Peritoneal cavity','MIDLINE',NULL,NULL,'CAUTION',60,FALSE,NULL,'CONSENT-PROGRAMME','SAFETY-PAUSE-DIALYSIS','AFTERCARE-DIALYSIS','PD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Renal programme'),
(:'tenant','PROC-BLOOD-TRANSFUSION',1,'Blood component transfusion','["transfusion"]','TRANSFUSION','GENERAL_MEDICINE','THERAPEUTIC','["WARD","THEATRE","EMERGENCY","CRITICAL_CARE","INFUSION"]',NULL,'NOT_APPLICABLE',NULL,NULL,'NO_CONSTRAINT',120,FALSE,240,'CONSENT-TRANSFUSION','SAFETY-PAUSE-TRANSFUSION','AFTERCARE-TRANSFUSION','TX-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'BLOOD.TRANSFUSION.APPROPRIATE_USE'),
(:'tenant','PROC-IV-INFUSION',1,'Intravenous infusion therapy','[]','INFUSION','GENERAL_MEDICINE','THERAPEUTIC','["INFUSION","WARD","CLINIC"]',NULL,'NOT_APPLICABLE',NULL,NULL,'CAUTION',60,FALSE,60,'CONSENT-VERBAL','SAFETY-PAUSE-BEDSIDE',NULL,'IV-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Core therapeutic set'),
(:'tenant','PROC-CHEMOTHERAPY-INFUSION',1,'Systemic anticancer therapy infusion','["chemotherapy"]','INFUSION','ONCOLOGY','THERAPEUTIC','["INFUSION"]',NULL,'NOT_APPLICABLE',NULL,NULL,'CONTRAINDICATED',180,TRUE,120,'CONSENT-PROCEDURE','SAFETY-PAUSE-INFUSION','AFTERCARE-CHEMOTHERAPY','CT-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Oncology pathway'),
-- ── Image-guided and interventional ──────────────────────────────────────────────────
(:'tenant','PROC-US-GUIDED-BIOPSY',1,'Ultrasound-guided core biopsy','["image-guided biopsy"]','INTERVENTIONAL_RADIOLOGY','RADIOLOGY','DIAGNOSTIC','["INTERVENTIONAL_RADIOLOGY","CLINIC"]',NULL,'LATERALISED',NULL,NULL,'CAUTION',45,TRUE,120,'CONSENT-PROCEDURE','SAFETY-PAUSE-INTERVENTIONAL','AFTERCARE-BIOPSY','IGB-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §28 demonstration 7'),
(:'tenant','PROC-CT-GUIDED-DRAINAGE',1,'CT-guided abscess drainage','[]','INTERVENTIONAL_RADIOLOGY','RADIOLOGY','THERAPEUTIC','["INTERVENTIONAL_RADIOLOGY"]',NULL,'LATERALISED',NULL,NULL,'CONTRAINDICATED',60,TRUE,180,'CONSENT-PROCEDURE','SAFETY-PAUSE-INTERVENTIONAL','AFTERCARE-DRAIN','CTD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'IR service set'),
(:'tenant','PROC-CORONARY-ANGIOGRAPHY',1,'Coronary angiography','["cardiac catheterisation"]','CATH_LAB','CARDIOLOGY','DIAGNOSTIC','["CATH_LAB"]','Coronary arteries','NOT_APPLICABLE',NULL,NULL,'CONTRAINDICATED',60,TRUE,240,'CONSENT-PROCEDURE','SAFETY-PAUSE-INTERVENTIONAL','AFTERCARE-ANGIOGRAPHY','CA-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Cardiac service set'),
-- ── WHO bellwether operations ────────────────────────────────────────────────────────
(:'tenant','PROC-LAPAROTOMY',1,'Exploratory laparotomy','["ex lap","emergency laparotomy"]','THEATRE','SURGERY','BOTH','["THEATRE"]','Abdominal cavity','MIDLINE',NULL,NULL,'CAUTION',120,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-LAPAROTOMY','54.11',NULL,'54.11','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'LCOGS.BELLWETHER.LAPAROTOMY'),
(:'tenant','PROC-CAESAREAN-SECTION',1,'Caesarean section','["LSCS","C-section"]','THEATRE','OBSTETRICS_GYNAECOLOGY','THERAPEUTIC','["THEATRE"]','Uterus','MIDLINE',NULL,NULL,'NO_CONSTRAINT',60,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-CAESAREAN','74.1',NULL,'74.1','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'LCOGS.BELLWETHER.CAESAREAN'),
(:'tenant','PROC-OPEN-FRACTURE-CARE',1,'Open fracture debridement and stabilisation','["open fracture care"]','THEATRE','ORTHOPAEDICS','THERAPEUTIC','["THEATRE"]',NULL,'LATERALISED',NULL,NULL,'CAUTION',120,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ORTHOPAEDIC','79.36',NULL,'79.36','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'LCOGS.BELLWETHER.OPEN_FRACTURE'),
-- ── General and acute-care surgery ───────────────────────────────────────────────────
(:'tenant','PROC-APPENDICECTOMY',1,'Appendicectomy','["appendectomy"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Appendix','MIDLINE',NULL,NULL,'CAUTION',60,TRUE,180,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','47.0',NULL,'47.0','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Acute-care surgery set'),
(:'tenant','PROC-INGUINAL-HERNIA-REPAIR',1,'Inguinal hernia repair','["herniorrhaphy"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Inguinal canal','LATERALISED',NULL,NULL,'CAUTION',60,TRUE,120,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-HERNIA','53.00',NULL,'53.00','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 1'),
(:'tenant','PROC-CHOLECYSTECTOMY',1,'Laparoscopic cholecystectomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Gallbladder','MIDLINE',NULL,NULL,'CAUTION',90,TRUE,180,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','51.23',NULL,'51.23','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'HPB set'),
(:'tenant','PROC-ABSCESS-DRAINAGE',1,'Incision and drainage of abscess','["I&D"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","CLINIC","EMERGENCY"]',NULL,'LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,TRUE,60,'CONSENT-PROCEDURE','SAFETY-PAUSE-SURGERY','AFTERCARE-WOUND','ID-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Minor surgery set'),
(:'tenant','PROC-LAPAROSCOPY-DIAGNOSTIC',1,'Diagnostic laparoscopy','[]','THEATRE','SURGERY','DIAGNOSTIC','["THEATRE"]','Abdominal cavity','MIDLINE',NULL,NULL,'CAUTION',60,TRUE,180,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','LAP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Acute abdomen pathway'),
(:'tenant','PROC-BOWEL-RESECTION',1,'Small bowel resection and anastomosis','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Small intestine','MIDLINE',NULL,NULL,'CAUTION',150,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','BR-002',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Acute-care surgery set'),
(:'tenant','PROC-UMBILICAL-HERNIA-REPAIR',1,'Umbilical hernia repair','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Umbilicus','MIDLINE',NULL,NULL,'CAUTION',45,TRUE,120,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-HERNIA','UH-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Abdominal wall set'),
-- ── Colorectal, UGI/HPB, breast and endocrine ────────────────────────────────────────
(:'tenant','PROC-HEMICOLECTOMY',1,'Hemicolectomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Colon','LATERALISED',NULL,NULL,'CAUTION',180,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','HC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Colorectal cancer pathway'),
(:'tenant','PROC-STOMA-FORMATION',1,'Stoma formation','["colostomy","ileostomy"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Abdominal wall','LATERALISED',NULL,NULL,'CAUTION',90,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-STOMA','ST-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Colorectal / stoma set'),
(:'tenant','PROC-HAEMORRHOIDECTOMY',1,'Haemorrhoidectomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","DAY_CASE"]','Anal canal','NOT_APPLICABLE',NULL,NULL,'CAUTION',45,TRUE,120,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ANORECTAL','HA-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Proctology set'),
(:'tenant','PROC-MASTECTOMY',1,'Simple mastectomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Breast','LATERALISED',NULL,NULL,'CONTRAINDICATED',120,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-BREAST','85.41',NULL,'85.41','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 3'),
(:'tenant','PROC-BREAST-CORE-BIOPSY',1,'Breast core biopsy','["triple assessment biopsy"]','CLINIC','SURGERY','DIAGNOSTIC','["CLINIC","INTERVENTIONAL_RADIOLOGY"]','Breast','LATERALISED',NULL,NULL,'CAUTION',30,FALSE,30,'CONSENT-PROCEDURE','SAFETY-PAUSE-SPECIMEN','AFTERCARE-BIOPSY','BB-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Breast triple assessment'),
(:'tenant','PROC-THYROIDECTOMY',1,'Thyroidectomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Thyroid gland','MIDLINE',NULL,NULL,'CAUTION',150,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-NECK','TH-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Endocrine surgery set'),
-- ── Vascular and urology ─────────────────────────────────────────────────────────────
(:'tenant','PROC-AV-FISTULA',1,'Arteriovenous fistula formation','["AVF"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Forearm','LATERALISED',NULL,NULL,'CAUTION',120,TRUE,180,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-VASCULAR-ACCESS','AVF-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Vascular access / renal'),
(:'tenant','PROC-LOWER-LIMB-AMPUTATION',1,'Lower limb amputation','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Lower limb','LATERALISED',NULL,NULL,'CAUTION',120,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-AMPUTATION','AMP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 4'),
(:'tenant','PROC-DIABETIC-FOOT-DEBRIDEMENT',1,'Diabetic foot debridement','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","CLINIC"]','Foot','LATERALISED',NULL,NULL,'NO_CONSTRAINT',45,TRUE,120,'CONSENT-PROCEDURE','SAFETY-PAUSE-SURGERY','AFTERCARE-WOUND','DFD-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 4'),
(:'tenant','PROC-VARICOSE-VEIN-SURGERY',1,'Varicose vein surgery','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","DAY_CASE"]','Leg veins','LATERALISED',NULL,NULL,'CAUTION',60,TRUE,120,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-VASCULAR','VV-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Venous disease set'),
(:'tenant','PROC-TURP',1,'Transurethral resection of prostate','["TURP"]','THEATRE','UROLOGY','THERAPEUTIC','["THEATRE"]','Prostate','MIDLINE',NULL,NULL,'NO_CONSTRAINT',90,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-UROLOGY','TURP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Urology set'),
(:'tenant','PROC-URETERIC-STENT',1,'Ureteric stent insertion','["JJ stent"]','THEATRE','UROLOGY','THERAPEUTIC','["THEATRE","ENDOSCOPY"]','Ureter','LATERALISED',NULL,NULL,'CAUTION',45,TRUE,120,'CONSENT-PROCEDURE','SAFETY-PAUSE-SURGERY','AFTERCARE-UROLOGY','US-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Urology stone pathway'),
(:'tenant','PROC-CIRCUMCISION',1,'Circumcision','[]','THEATRE','UROLOGY','THERAPEUTIC','["THEATRE","CLINIC","DAY_CASE"]','Penis','NOT_APPLICABLE',NULL,NULL,'NO_CONSTRAINT',30,TRUE,60,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-WOUND','CIRC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Urology / public health set'),
-- ── Orthopaedics ─────────────────────────────────────────────────────────────────────
(:'tenant','PROC-ORIF',1,'Open reduction and internal fixation','["ORIF"]','THEATRE','ORTHOPAEDICS','THERAPEUTIC','["THEATRE"]',NULL,'LATERALISED',NULL,NULL,'CAUTION',150,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-IMPLANT','AFTERCARE-ORTHOPAEDIC','79.36',NULL,'79.36','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 7'),
(:'tenant','PROC-TOTAL-HIP-REPLACEMENT',1,'Total hip arthroplasty','["THR"]','THEATRE','ORTHOPAEDICS','THERAPEUTIC','["THEATRE"]','Hip joint','LATERALISED',NULL,NULL,'CONTRAINDICATED',180,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-IMPLANT','AFTERCARE-ARTHROPLASTY','THR-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Arthroplasty / implant traceability'),
(:'tenant','PROC-JOINT-REDUCTION',1,'Closed reduction of dislocated joint','[]','EMERGENCY','ORTHOPAEDICS','THERAPEUTIC','["EMERGENCY","BEDSIDE","THEATRE"]',NULL,'LATERALISED',NULL,NULL,'CAUTION',30,TRUE,60,'CONSENT-SEDATION-PROCEDURE','SAFETY-PAUSE-SEDATION','AFTERCARE-ORTHOPAEDIC','JR-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Emergency orthopaedics'),
(:'tenant','PROC-SKIN-TRACTION',1,'Application of skin traction','[]','WARD','ORTHOPAEDICS','THERAPEUTIC','["WARD","EMERGENCY"]',NULL,'LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,FALSE,NULL,'CONSENT-VERBAL','SAFETY-PAUSE-BEDSIDE','AFTERCARE-ORTHOPAEDIC','TR-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Ward orthopaedics'),
-- ── Neurosurgery, cardiothoracic, ENT, ophthalmic, maxillofacial, plastics ────────────
(:'tenant','PROC-BURR-HOLE',1,'Burr hole drainage','[]','THEATRE','NEUROLOGY','THERAPEUTIC','["THEATRE","EMERGENCY"]','Cranium','LATERALISED',NULL,NULL,'CAUTION',60,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-NEUROSURGICAL','BH-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Neurosurgical emergency'),
(:'tenant','PROC-VP-SHUNT',1,'Ventriculoperitoneal shunt insertion','["VP shunt"]','THEATRE','NEUROLOGY','THERAPEUTIC','["THEATRE"]','Cerebral ventricle','LATERALISED',NULL,NULL,'CAUTION',120,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-IMPLANT','AFTERCARE-NEUROSURGICAL','VP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Hydrocephalus pathway'),
(:'tenant','PROC-THORACOTOMY',1,'Thoracotomy','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","EMERGENCY"]','Thoracic cavity','LATERALISED',NULL,NULL,'CAUTION',180,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-THORACIC','TC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Cardiothoracic set'),
(:'tenant','PROC-TRACHEOSTOMY',1,'Tracheostomy','[]','THEATRE','ENT','THERAPEUTIC','["THEATRE","CRITICAL_CARE","EMERGENCY"]','Trachea','MIDLINE',NULL,NULL,'NO_CONSTRAINT',60,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-TRACHEOSTOMY','TS-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Airway set'),
(:'tenant','PROC-TONSILLECTOMY',1,'Tonsillectomy','[]','THEATRE','ENT','THERAPEUTIC','["THEATRE","DAY_CASE"]','Tonsils','NOT_APPLICABLE',NULL,NULL,'CAUTION',45,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-ENT','TON-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'ENT set'),
(:'tenant','PROC-CATARACT-SURGERY',1,'Cataract extraction with intraocular lens','["phaco"]','THEATRE','OPHTHALMOLOGY','THERAPEUTIC','["THEATRE","DAY_CASE"]','Eye','LATERALISED',NULL,NULL,'CAUTION',45,TRUE,60,'CONSENT-SURGICAL','SAFETY-PAUSE-IMPLANT','AFTERCARE-OPHTHALMIC','CAT-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Ophthalmic set — high-volume lateralised'),
(:'tenant','PROC-DENTAL-EXTRACTION',1,'Dental extraction','[]','DENTAL','DERMATOLOGY','THERAPEUTIC','["DENTAL","CLINIC"]','Tooth','MULTI_SITE',NULL,NULL,'CAUTION',30,FALSE,30,'CONSENT-PROCEDURE','SAFETY-PAUSE-BEDSIDE','AFTERCARE-DENTAL','DE-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Dental set — site is a tooth, not a side'),
(:'tenant','PROC-SPLIT-SKIN-GRAFT',1,'Split-thickness skin graft','["SSG"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]',NULL,'LATERALISED',NULL,NULL,'CAUTION',90,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-GRAFT','SSG-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Plastics / burns reconstruction — definitive management side of the burns boundary'),
(:'tenant','PROC-ESCHAROTOMY',1,'Escharotomy','[]','EMERGENCY','SURGERY','THERAPEUTIC','["EMERGENCY","THEATRE","CRITICAL_CARE"]',NULL,'LATERALISED',NULL,NULL,'NO_CONSTRAINT',30,TRUE,120,'CONSENT-EMERGENCY-EXCEPTION','SAFETY-PAUSE-SURGERY','AFTERCARE-WOUND','ES-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Acute burns — emergency lane owns resuscitation, surgery owns definitive care'),
-- ── Obstetric and gynaecological ─────────────────────────────────────────────────────
(:'tenant','PROC-EVACUATION-UTERUS',1,'Evacuation of retained products of conception','["ERPC","MVA"]','THEATRE','OBSTETRICS_GYNAECOLOGY','THERAPEUTIC','["THEATRE","CLINIC"]','Uterus','MIDLINE',NULL,NULL,'NO_CONSTRAINT',30,TRUE,120,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-GYNAECOLOGICAL','EV-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Reproductive pack invocation'),
(:'tenant','PROC-HYSTERECTOMY',1,'Total abdominal hysterectomy','["TAH"]','THEATRE','OBSTETRICS_GYNAECOLOGY','THERAPEUTIC','["THEATRE"]','Uterus','MIDLINE',NULL,NULL,'CONTRAINDICATED',150,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-GYNAECOLOGICAL','68.49',NULL,'68.49','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Gynaecological oncology / benign'),
(:'tenant','PROC-ECTOPIC-SURGERY',1,'Surgery for ectopic pregnancy','["salpingectomy"]','THEATRE','OBSTETRICS_GYNAECOLOGY','THERAPEUTIC','["THEATRE"]','Fallopian tube','LATERALISED',NULL,NULL,'NO_CONSTRAINT',90,TRUE,240,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-GYNAECOLOGICAL','EC-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Emergency gynaecology'),
(:'tenant','PROC-FISTULA-REPAIR',1,'Obstetric fistula repair','["VVF repair"]','THEATRE','OBSTETRICS_GYNAECOLOGY','THERAPEUTIC','["THEATRE"]','Vagina','MIDLINE',NULL,NULL,'CAUTION',180,TRUE,300,'CONSENT-SURGICAL','SAFETY-PAUSE-SURGERY','AFTERCARE-GYNAECOLOGICAL','FR-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Zimbabwe NSOAS fistula care priority'),
-- ── Paediatric surgery ───────────────────────────────────────────────────────────────
(:'tenant','PROC-PAED-HERNIA-REPAIR',1,'Paediatric inguinal hernia repair','[]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE","DAY_CASE"]','Inguinal canal','LATERALISED',0,5844,'NO_CONSTRAINT',45,TRUE,180,'CONSENT-SURGICAL-GUARDIAN','SAFETY-PAUSE-SURGERY','AFTERCARE-HERNIA','PH-001',NULL,'53.00','PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Specification §24 demonstration 5 — invokes the Paediatric Pack'),
(:'tenant','PROC-PAED-PYLOROMYOTOMY',1,'Pyloromyotomy','["Ramstedt"]','THEATRE','SURGERY','THERAPEUTIC','["THEATRE"]','Pylorus','MIDLINE',0,365,'NO_CONSTRAINT',60,TRUE,240,'CONSENT-SURGICAL-GUARDIAN','SAFETY-PAUSE-SURGERY','AFTERCARE-ABDOMINAL','PY-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Neonatal surgery — age-bounded'),
(:'tenant','PROC-PAED-LUMBAR-PUNCTURE',1,'Paediatric lumbar puncture','[]','BEDSIDE','PAEDIATRICS','DIAGNOSTIC','["BEDSIDE","WARD","EMERGENCY"]','Lumbar spine','MIDLINE',0,5844,'NO_CONSTRAINT',30,TRUE,60,'CONSENT-INVASIVE-GUARDIAN','SAFETY-PAUSE-BEDSIDE','AFTERCARE-LUMBAR-PUNCTURE','PLP-001',NULL,NULL,'PUBLISHED','PENDING_MOHCC_RATIFICATION','system-seeder',NOW(),NOW(),'Paediatric Pack invocation — guardian consent plus child assent')
ON CONFLICT (tenant_id, definition_code, version) DO NOTHING;

-- =====================================================================================
-- Requirements
--
-- CORE set, applied to every definition: patient identity, consent, cadre and privilege.
-- Plus SITE_SIDE_VERIFICATION for every LATERALISED or MULTI_SITE procedure — which is the
-- point of modelling laterality applicability at all. It is never overridable in an
-- emergency (enforced by CHECK in V002), because the lateralised procedures done fastest
-- and with least supervision are precisely the ones where wrong-side harm happens.
-- =====================================================================================
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'PATIENT_IDENTITY', 'IDENTITY-CONFIRMED', 'Patient identity confirmed against the record',
       'MANDATORY', 'PERFORMING_CLINICIAN', 'vito-service', FALSE, 'BLOCK', 10
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'CONSENT', COALESCE(d.consent_type, 'CONSENT-PROCEDURE'),
       'Consent recorded for ' || d.clinical_name,
       'MANDATORY', 'PERFORMING_CLINICIAN', 'mvumo-service',
       -- Consent is overridable only under the audited emergency-exception path, which is
       -- itself a consent record (two-doctor / deferred / proxy) rather than an absence.
       TRUE, 'BLOCK', 20
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'SITE_SIDE_VERIFICATION', 'SITE-SIDE-CONFIRMED',
       'Anatomical site and side confirmed and recorded',
       'MANDATORY', 'PERFORMING_CLINICIAN', NULL, FALSE, 'BLOCK', 15
FROM procedures.procedure_definition d
WHERE d.tenant_id = :'tenant'
  AND d.laterality_applicability IN ('LATERALISED','MULTI_SITE')
ON CONFLICT DO NOTHING;

INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     owner_role, resolver_service, overridable_in_emergency, on_resolver_unavailable, display_order)
SELECT d.id, 'PRIVILEGE', 'PRIVILEGE-' || d.owning_specialty,
       'Operator privileged for ' || d.owning_specialty,
       'MANDATORY', 'CLINICAL_LEAD', 'varapi-service',
       -- An emergency may be performed by whoever is present; the override is audited and
       -- the fact that it was used is part of the record.
       TRUE, 'BLOCK', 30
FROM procedures.procedure_definition d WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- DEEP requirement sets for the demonstration and bellwether procedures. Illustrative of
-- the depth every entry eventually needs; the rest are honestly CORE-only for now.
INSERT INTO procedures.procedure_requirement
    (definition_id, requirement_kind, requirement_code, requirement_label, obligation,
     condition_expression, owner_role, resolver_service, overridable_in_emergency,
     on_resolver_unavailable, display_order)
SELECT d.id, r.kind, r.code, r.label, r.obligation, r.cond, r.owner, r.resolver, r.override, r.unavail, r.ord
FROM procedures.procedure_definition d
JOIN (VALUES
  -- Lumbar puncture (§28 demo 1)
  ('PROC-LUMBAR-PUNCTURE','LABORATORY','LAB-COAG','Coagulation screen within 24 hours','CONDITIONAL','patient.onAnticoagulant OR patient.bleedingDisorder','PERFORMING_CLINICIAN','oros-service',FALSE,'BLOCK',40),
  ('PROC-LUMBAR-PUNCTURE','IMAGING','IMG-CT-HEAD','CT head excluding raised intracranial pressure','CONDITIONAL','patient.focalNeurology OR patient.papilloedema OR patient.reducedGCS','PERFORMING_CLINICIAN','oros-service',FALSE,'BLOCK',41),
  ('PROC-LUMBAR-PUNCTURE','IPC','IPC-ASEPTIC','Aseptic technique and sterile field','MANDATORY',NULL,'PERFORMING_CLINICIAN',NULL,FALSE,'BLOCK',42),
  ('PROC-LUMBAR-PUNCTURE','EQUIPMENT','EQ-LP-SET','Lumbar puncture set with manometer','MANDATORY',NULL,'WARD_MANAGER','inventory-service',FALSE,'BLOCK',43),
  ('PROC-LUMBAR-PUNCTURE','MONITORING','MON-NEURO','Neurological observations post-procedure','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,FALSE,'WARN',44),
  -- Chest drain (§28 demo 2)
  ('PROC-CHEST-DRAIN','IMAGING','IMG-CXR','Chest radiograph confirming side and pathology','CONDITIONAL','NOT patient.tensionPneumothorax','PERFORMING_CLINICIAN','oros-service',TRUE,'BLOCK',40),
  ('PROC-CHEST-DRAIN','EQUIPMENT','EQ-DRAIN-SET','Chest drain set and underwater seal','MANDATORY',NULL,'WARD_MANAGER','inventory-service',FALSE,'BLOCK',41),
  ('PROC-CHEST-DRAIN','IPC','IPC-ASEPTIC','Aseptic technique and sterile field','MANDATORY',NULL,'PERFORMING_CLINICIAN',NULL,FALSE,'BLOCK',42),
  ('PROC-CHEST-DRAIN','MONITORING','MON-RESP','Continuous respiratory and oxygen saturation monitoring','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,FALSE,'BLOCK',43),
  ('PROC-CHEST-DRAIN','IMAGING','IMG-CXR-POST','Post-procedure chest radiograph confirming position','MANDATORY',NULL,'PERFORMING_CLINICIAN','oros-service',FALSE,'WARN',44),
  -- Endoscopy with sedation and biopsy (§28 demo 5)
  ('PROC-OGD','SEDATION','SED-MODERATE','Moderate sedation with rescue capability for deep sedation','MANDATORY',NULL,'SEDATION_PROVIDER',NULL,FALSE,'BLOCK',40),
  ('PROC-OGD','FASTING','FAST-6H','Fasted six hours for solids, two for clear fluids','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,TRUE,'BLOCK',41),
  ('PROC-OGD','MONITORING','MON-PULSE-OX','Continuous pulse oximetry throughout','MANDATORY',NULL,'SEDATION_PROVIDER',NULL,FALSE,'BLOCK',42),
  ('PROC-OGD','STERILE_PROCESSING','CSSD-SCOPE','Endoscope reprocessed with a traceable cycle','MANDATORY',NULL,'CSSD_MANAGER','tuso-service',FALSE,'BLOCK',43),
  ('PROC-OGD','RECOVERY_AREA','REC-BAY','Recovery bay with monitoring available','MANDATORY',NULL,'UNIT_MANAGER','tuso-service',FALSE,'BLOCK',44),
  -- Dialysis (§28 demo 6)
  ('PROC-HAEMODIALYSIS','FACILITY_CAPABILITY','CAP-DIALYSIS','Facility provides haemodialysis','MANDATORY',NULL,'UNIT_MANAGER','tuso-service',FALSE,'BLOCK',40),
  ('PROC-HAEMODIALYSIS','EQUIPMENT','EQ-DIALYSER','Dialyser and functioning machine','MANDATORY',NULL,'UNIT_MANAGER','inventory-service',FALSE,'BLOCK',41),
  ('PROC-HAEMODIALYSIS','DATA','DATA-DRY-WEIGHT','Target dry weight recorded','MANDATORY',NULL,'PERFORMING_CLINICIAN',NULL,FALSE,'BLOCK',42),
  ('PROC-HAEMODIALYSIS','MONITORING','MON-HAEMODYNAMIC','Blood pressure monitoring through the session','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,FALSE,'BLOCK',43),
  -- Image-guided biopsy (§28 demo 7)
  ('PROC-US-GUIDED-BIOPSY','LABORATORY','LAB-COAG','Coagulation screen within 24 hours','MANDATORY',NULL,'PERFORMING_CLINICIAN','oros-service',FALSE,'BLOCK',40),
  ('PROC-US-GUIDED-BIOPSY','ANTICOAGULATION','ANTICOAG-HELD','Anticoagulation withheld per protocol','CONDITIONAL','patient.onAnticoagulant','PERFORMING_CLINICIAN',NULL,FALSE,'BLOCK',41),
  ('PROC-US-GUIDED-BIOPSY','EQUIPMENT','EQ-BIOPSY-DEVICE','Core biopsy device and ultrasound','MANDATORY',NULL,'UNIT_MANAGER','inventory-service',FALSE,'BLOCK',42),
  ('PROC-US-GUIDED-BIOPSY','DATA','DATA-SPECIMEN-PLAN','Specimen destination and fixative declared before collection','MANDATORY',NULL,'PERFORMING_CLINICIAN','oros-service',FALSE,'BLOCK',43),
  -- Implant traceability (§28 demo 8)
  ('PROC-TOTAL-HIP-REPLACEMENT','EQUIPMENT','EQ-ARTHROPLASTY-SET','Arthroplasty instrument set','MANDATORY',NULL,'THEATRE_MANAGER','tuso-service',FALSE,'BLOCK',40),
  ('PROC-TOTAL-HIP-REPLACEMENT','CONSUMABLE','IMPL-HIP-PROSTHESIS','Hip prosthesis of the planned size, in date, not recalled','MANDATORY',NULL,'THEATRE_MANAGER','inventory-service',FALSE,'BLOCK',41),
  ('PROC-TOTAL-HIP-REPLACEMENT','BLOOD','BLOOD-GROUP-SCREEN','Group and screen valid','MANDATORY',NULL,'PERFORMING_CLINICIAN','madi-service',TRUE,'BLOCK',42),
  ('PROC-TOTAL-HIP-REPLACEMENT','MEDICINE','ABX-PROPHYLAXIS','Surgical antibiotic prophylaxis within 60 minutes of incision','MANDATORY',NULL,'ANAESTHETIST','inventory-service',FALSE,'BLOCK',43),
  ('PROC-TOTAL-HIP-REPLACEMENT','STERILE_PROCESSING','CSSD-SET','Instrument set sterilised with a traceable cycle','MANDATORY',NULL,'CSSD_MANAGER','tuso-service',FALSE,'BLOCK',44),
  -- Bellwethers
  ('PROC-LAPAROTOMY','BLOOD','BLOOD-CROSSMATCH','Crossmatched blood available','MANDATORY',NULL,'PERFORMING_CLINICIAN','madi-service',TRUE,'BLOCK',40),
  ('PROC-LAPAROTOMY','FACILITY_CAPABILITY','CAP-THEATRE','Theatre with anaesthesia available','MANDATORY',NULL,'THEATRE_MANAGER','tuso-service',FALSE,'BLOCK',41),
  ('PROC-LAPAROTOMY','POST_PROCEDURE_BED','BED-HDU','High-dependency or ward bed identified','MANDATORY',NULL,'BED_MANAGER','tuso-service',TRUE,'BLOCK',42),
  ('PROC-LAPAROTOMY','MEDICINE','ABX-PROPHYLAXIS','Surgical antibiotic prophylaxis within 60 minutes of incision','MANDATORY',NULL,'ANAESTHETIST','inventory-service',FALSE,'BLOCK',43),
  ('PROC-CAESAREAN-SECTION','BLOOD','BLOOD-GROUP-SCREEN','Group and screen valid','MANDATORY',NULL,'PERFORMING_CLINICIAN','madi-service',TRUE,'BLOCK',40),
  ('PROC-CAESAREAN-SECTION','STAFFING','STAFF-NEONATAL','Neonatal resuscitation-trained attendant present','MANDATORY',NULL,'MIDWIFE_IN_CHARGE','vashandi-workforce-service',FALSE,'BLOCK',41),
  ('PROC-CAESAREAN-SECTION','MEDICINE','ABX-PROPHYLAXIS','Surgical antibiotic prophylaxis before incision','MANDATORY',NULL,'ANAESTHETIST','inventory-service',FALSE,'BLOCK',42),
  ('PROC-OPEN-FRACTURE-CARE','MEDICINE','ABX-OPEN-FRACTURE','Antibiotics within one hour of injury','MANDATORY',NULL,'PERFORMING_CLINICIAN','inventory-service',FALSE,'BLOCK',40),
  ('PROC-OPEN-FRACTURE-CARE','FACILITY_CAPABILITY','CAP-ORTHOPAEDIC','Facility provides orthopaedic surgery','MANDATORY',NULL,'THEATRE_MANAGER','tuso-service',FALSE,'BLOCK',41),
  -- Paediatric (§28 demo 3, §24 demo 5)
  ('PROC-PAED-LUMBAR-PUNCTURE','CONSENT','CONSENT-CHILD-ASSENT','Child assent sought and recorded where the child is capable','MANDATORY',NULL,'PERFORMING_CLINICIAN','mvumo-service',TRUE,'BLOCK',40),
  ('PROC-PAED-LUMBAR-PUNCTURE','EQUIPMENT','EQ-LP-SET-PAED','Paediatric-sized lumbar puncture set','MANDATORY',NULL,'WARD_MANAGER','inventory-service',FALSE,'BLOCK',41),
  ('PROC-PAED-HERNIA-REPAIR','CONSENT','CONSENT-CHILD-ASSENT','Child assent sought and recorded where the child is capable','MANDATORY',NULL,'PERFORMING_CLINICIAN','mvumo-service',TRUE,'BLOCK',40),
  ('PROC-PAED-HERNIA-REPAIR','STAFFING','STAFF-PAED-ANAESTHETIST','Anaesthetist competent in paediatric anaesthesia','MANDATORY',NULL,'CLINICAL_LEAD','vashandi-workforce-service',FALSE,'BLOCK',41),
  -- Transfusion (bedside positive identification is the standard's own emphasis)
  ('PROC-BLOOD-TRANSFUSION','BLOOD','BLOOD-COMPATIBLE','Compatible unit reserved and issued','MANDATORY',NULL,'PERFORMING_CLINICIAN','madi-service',FALSE,'BLOCK',40),
  ('PROC-BLOOD-TRANSFUSION','PATIENT_IDENTITY','IDENTITY-BEDSIDE-CHECK','Bedside positive patient identification against the unit','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,FALSE,'BLOCK',41),
  ('PROC-BLOOD-TRANSFUSION','MONITORING','MON-TRANSFUSION','Observations at baseline, 15 minutes and completion','MANDATORY',NULL,'NURSE_IN_CHARGE',NULL,FALSE,'BLOCK',42)
) AS r(def_code, kind, code, label, obligation, cond, owner, resolver, override, unavail, ord)
  ON r.def_code = d.definition_code
WHERE d.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;
