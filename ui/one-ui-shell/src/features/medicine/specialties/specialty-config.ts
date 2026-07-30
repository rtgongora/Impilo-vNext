import type { MedicineCdsTopic } from "@/hooks/queries/useMedicineCds";

/**
 * The thirteen adult-medicine specialties of brief.md §8.
 *
 * ## Why this is a config and not thirteen pages
 *
 * §8 names thirteen specialties, each with a disease list and a tooling list. Written as thirteen
 * hand-built screens they would each invent their own version of the problem list, the examination,
 * the decision support and the registers — which is the "folder of specialist forms" the brief
 * exists to prevent. Here a specialty is a *view onto shared machinery*: which problems it cares
 * about, which governed CDS topics apply, which registers, which examination regions.
 *
 * ## Why every specialty carries a `notBuilt` list
 *
 * §8's "Include:" lists ask for tooling most of which does not exist — ECG workflow, spirometry,
 * dialysis access planning, EEG. A workspace that renders those as tiles is the exact defect an
 * earlier wave had to remove from this estate: buttons that look available and do nothing. So each
 * specialty states what §8 asked for that is **not built**, on screen, next to what is. A clinician
 * who can see the gap can work around it; one who cannot will assume the system is doing it.
 *
 * `notBuilt` is therefore load-bearing. Shortening it without building the thing is how this
 * surface starts lying.
 */

/** A chronic-disease register key, as `useChronicRegister` and pct's programme vocabulary name them. */
export type RegisterKey =
  | "HYPERTENSION"
  | "DIABETES"
  | "CHRONIC_KIDNEY_DISEASE"
  | "CHRONIC_RESPIRATORY";

export interface SpecialtyDefinition {
  key: string;
  label: string;
  /** The brief section this specialty is specified by, so a reader can check the claim. */
  section: string;
  summary: string;
  /** §8's "Support:" list, verbatim in meaning. */
  conditions: string[];
  /**
   * Fragments matched against a problem's code or display to decide whether it belongs to this
   * specialty. Deliberately coarse: this filters a view, it never changes a record, and a
   * false positive costs a clinician one glance while a false negative hides a diagnosis.
   */
  problemMatchers: string[];
  /** Governed CDS packs that genuinely apply. Only topics the evaluator actually serves. */
  cdsTopics: MedicineCdsTopic[];
  /** Chronic registers this specialty runs. */
  registers: RegisterKey[];
  /** Examination regions from §7 that this specialty's assessment centres on. */
  examinationRegions: string[];
  /**
   * Honest deep links into shared spine that §8 asked for as specialty tooling. These are not
   * new SoRs — they compose procedures catalogue, OROS worklists, vitals fluid balance, etc.
   * Shortening `notBuilt` is only allowed when a matching spineLinks entry (or real build) exists.
   */
  spineLinks: { label: string; href: (patientId: string) => string; note?: string }[];
  /** What §8 asks for here that this pack has NOT built. Never shortened without building it. */
  notBuilt: string[];
}

export const MEDICINE_SPECIALTIES: SpecialtyDefinition[] = [
  {
    key: "cardiology",
    label: "Cardiology",
    section: "§8.1",
    summary: "Hypertension, cardiovascular risk, ischaemic heart disease, heart failure and rhythm.",
    conditions: [
      "Hypertension", "Cardiovascular-risk assessment", "Ischaemic heart disease",
      "Acute coronary syndrome handoff from Emergency", "Heart failure", "Valvular disease",
      "Cardiomyopathy", "Arrhythmia", "Syncope", "Pericardial disease",
      "Congenital heart disease in adult care", "Anticoagulation", "Cardiac rehabilitation",
      "Device follow-up", "Pregnancy-related cardiac coordination",
    ],
    problemMatchers: ["I10", "I11", "I20", "I21", "I25", "I34", "I42", "I48", "I50", "R55",
      "HYPERTENS", "HEART FAILURE", "ISCHAEMIC HEART", "ANGINA", "ATRIAL FIBRILLATION", "SYNCOPE"],
    cdsTopics: ["cvd-risk", "deprescribing"],
    registers: ["HYPERTENSION"],
    examinationRegions: ["CARDIOVASCULAR", "VITAL_SIGNS", "OEDEMA", "PERIPHERAL_VASCULAR", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "ECG / echo procedure worklist",
        href: () => "/diagnostics/procedure-worklist",
        note: "OROS PROCEDURE fulfilment for ECG and echocardiography — not a cardiology-owned SoR.",
      },
      {
        label: "Volume status (fluid balance + oedema exam)",
        href: (patientId) => `/ehr/${patientId}/vitals`,
        note: "Inpatient fluid_balance via BFF; oedema on examination. Outpatient dialysis fluid stays telemonitoring.",
      },
      {
        label: "Record cardiovascular examination",
        href: (patientId) => `/ehr/${patientId}/examination`,
      },
    ],
    notBuilt: [
      "Ambulatory monitoring",
      "Medicine titration tracking", "Device and procedure referrals", "Longitudinal outcomes",
    ],
  },
  {
    key: "respiratory",
    label: "Respiratory and pulmonology",
    section: "§8.2",
    summary: "Asthma, COPD, pleural and interstitial disease, and the TB interface.",
    conditions: [
      "Asthma", "COPD", "Bronchiectasis", "Pneumonia follow-up", "Tuberculosis integration",
      "Interstitial lung disease", "Pleural disease", "Pulmonary hypertension",
      "Sleep-related breathing disorders", "Occupational lung disease",
      "Post-infectious lung disease", "Oxygen therapy", "Pulmonary rehabilitation",
    ],
    problemMatchers: ["J44", "J45", "J47", "J18", "J84", "J90", "I27", "A15", "A16",
      "ASTHMA", "COPD", "BRONCHIECTASIS", "PNEUMONIA", "TUBERCULOSIS", "PLEURAL"],
    cdsTopics: ["procedure-indication", "antimicrobial-stewardship"],
    registers: ["CHRONIC_RESPIRATORY"],
    examinationRegions: ["RESPIRATORY", "VITAL_SIGNS", "CYANOSIS", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "Spirometry / PFT procedure worklist",
        href: () => "/diagnostics/procedure-worklist",
        note: "OROS PROCEDURE workflow names spirometry; peak flow and inhaler technique remain unbuilt.",
      },
      {
        label: "Bronchoscopy catalogue",
        href: () => "/work/clinical/procedures",
      },
    ],
    notBuilt: [
      "Peak flow", "Inhaler technique assessment", "Symptom-control scores",
      "Exacerbation history", "Tobacco cessation (simba owns cessation — integrate, do not fork)",
      "Oxygen eligibility",
    ],
  },
  {
    key: "gastroenterology",
    label: "Gastroenterology and hepatology",
    section: "§8.3",
    summary: "Gut, liver and pancreas — including the cirrhosis and ascites pathway.",
    conditions: [
      "Dyspepsia and ulcer disease", "Gastrointestinal bleeding follow-up", "Chronic diarrhoea",
      "Malabsorption", "Inflammatory bowel disease", "Chronic liver disease", "Viral hepatitis",
      "Cirrhosis", "Ascites", "Hepatic encephalopathy", "Pancreatic disease", "Biliary disease",
      "Nutrition", "Cancer referral", "Endoscopy pathways",
    ],
    problemMatchers: ["K25", "K26", "K27", "K50", "K51", "K70", "K74", "K76", "K85", "K80", "B18", "R18",
      "ULCER", "HEPATITIS", "CIRRHOSIS", "ASCITES", "PANCREATITIS", "INFLAMMATORY BOWEL"],
    cdsTopics: ["procedure-indication", "oncology"],
    registers: [],
    examinationRegions: ["ABDOMEN", "JAUNDICE", "HYDRATION", "ANTHROPOMETRY", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "Endoscopy / patient procedures",
        href: (patientId) => `/ehr/${patientId}/procedures`,
        note: "OGD / colonoscopy / flexi-sigmoidoscopy in procedures-service — shared spine, not a gastro SoR.",
      },
      {
        label: "Procedure catalogue",
        href: () => "/work/clinical/procedures",
      },
    ],
    notBuilt: [
      "Nutrition assessment and support",
      "Child-Pugh / MELD staging (calculators compute in clinical-tools; hepatic impairment is still " +
        "null for every patient in production until a governed hepatic instrument feeds the record)",
    ],
  },
  {
    key: "nephrology",
    label: "Nephrology",
    section: "§8.4",
    summary: "Chronic kidney disease through to dialysis preparation.",
    conditions: [
      "Acute kidney injury follow-up", "Chronic kidney disease", "Proteinuria", "Haematuria",
      "Electrolyte disorders", "Hypertension", "Glomerular disease", "Nephrotic syndrome",
      "Dialysis preparation", "Haemodialysis", "Peritoneal dialysis", "Vascular access",
      "Transplant referral and follow-up", "Renal anaemia", "Mineral and bone disorder",
      "Fluid management",
    ],
    problemMatchers: ["N17", "N18", "N19", "N04", "R80", "R31", "E87", "D63",
      "KIDNEY", "RENAL", "NEPHROTIC", "PROTEINURIA", "DIALYSIS"],
    cdsTopics: ["deprescribing", "procedure-indication"],
    registers: ["CHRONIC_KIDNEY_DISEASE", "HYPERTENSION"],
    examinationRegions: ["OEDEMA", "HYDRATION", "VITAL_SIGNS", "PALLOR", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "CKD register",
        href: () => "/clinical/chronic-registers",
      },
      {
        label: "Haemodialysis / PD / AV fistula catalogue",
        href: () => "/work/clinical/procedures",
        note: "PROC-HAEMODIALYSIS, PROC-PERITONEAL-DIALYSIS, PROC-AV-FISTULA.",
      },
      {
        label: "Fluid balance / volume",
        href: (patientId) => `/ehr/${patientId}/vitals`,
      },
    ],
    notBuilt: [
      "Transplant referral tracking", "Renal anaemia and mineral-bone management",
      "Renal replacement therapy: dialysis adequacy, prescription and modality review",
    ],
  },
  {
    key: "endocrinology",
    label: "Endocrinology and metabolic medicine",
    section: "§8.5",
    summary: "Diabetes, thyroid, adrenal, bone and metabolic disease.",
    conditions: [
      "Type 1 diabetes", "Type 2 diabetes", "Secondary diabetes", "Thyroid disease",
      "Adrenal disease", "Pituitary disease", "Calcium and bone metabolism", "Osteoporosis",
      "Obesity", "Dyslipidaemia", "Metabolic syndrome", "Endocrine hypertension",
      "Reproductive-endocrine interfaces",
    ],
    problemMatchers: ["E10", "E11", "E13", "E03", "E05", "E27", "E23", "E78", "E66", "M81",
      "DIABET", "THYROID", "ADRENAL", "OSTEOPOROSIS", "DYSLIPID", "OBESITY"],
    cdsTopics: ["cvd-risk", "deprescribing"],
    registers: ["DIABETES"],
    examinationRegions: ["FEET", "EYES", "ENDOCRINE", "ANTHROPOMETRY", "PERIPHERAL_VASCULAR"],
    spineLinks: [],
    notBuilt: [
      "Glucose monitoring and device data", "HbA1c trend", "Hypoglycaemia tracking",
      "Complication screening schedule (eye, renal, foot, cardiovascular)",
      "Insulin titration",
    ],
  },
  {
    key: "neurology",
    label: "Neurology",
    section: "§8.6",
    summary: "Epilepsy, stroke prevention, headache, neuropathy and cognition.",
    conditions: [
      "Epilepsy", "Stroke follow-up and prevention", "Headache", "Neuropathy",
      "Movement disorders", "Dementia", "Cognitive impairment",
      "Multiple sclerosis and demyelinating disease", "Neuromuscular disease", "Spinal disease",
      "CNS infection follow-up", "Neurodisability", "Rehabilitation",
    ],
    problemMatchers: ["G40", "G20", "G35", "G62", "G43", "I63", "I64", "F00", "F01", "F03",
      "EPILEPSY", "STROKE", "DEMENTIA", "NEUROPATHY", "PARKINSON", "MIGRAINE"],
    cdsTopics: ["procedure-indication", "mhgap"],
    registers: [],
    examinationRegions: ["NEUROLOGY", "COGNITION", "FUNCTIONAL_STATUS", "MUSCULOSKELETAL", "EYES"],
    spineLinks: [],
    notBuilt: [
      "Seizure classification", "EEG", "Imaging workflow", "Rehabilitation goals",
      "Driving and occupational advice (requires the national policy, which is not encoded)",
    ],
  },
  {
    key: "infectious-diseases",
    label: "Infectious diseases",
    section: "§8.7",
    summary: "HIV and TB in depth, plus malaria, sepsis follow-up and resistant infection.",
    conditions: [
      "HIV (Digital Adaptation Kit, in depth)", "Tuberculosis (Digital Adaptation Kit, in depth)",
      "Malaria", "Sepsis follow-up", "Chronic or recurrent infection",
      "Opportunistic infections", "Viral hepatitis", "Sexually transmitted infections",
      "Antimicrobial-resistant infection", "Infection in immunocompromised patients",
      "Imported and travel-related infection", "Outbreak-linked care",
      "Long-term antimicrobial therapy",
    ],
    problemMatchers: ["B20", "B21", "B22", "B23", "B24", "A15", "A16", "B50", "B54", "A41", "B18",
      "HIV", "TUBERCULOSIS", "MALARIA", "SEPSIS", "HEPATITIS"],
    cdsTopics: ["antimicrobial-stewardship"],
    registers: [],
    examinationRegions: ["GENERAL_CONDITION", "VITAL_SIGNS", "LYMPH_NODES", "SKIN", "ABDOMEN"],
    spineLinks: [],
    notBuilt: [
      "Malaria pathway", "Sepsis follow-up", "Travel and outbreak-linked care",
      "Long-term antimicrobial therapy tracking",
    ],
  },
  {
    key: "rheumatology",
    label: "Rheumatology and clinical immunology",
    section: "§8.8",
    summary: "Inflammatory arthritis, connective-tissue disease and immunosuppression.",
    conditions: [
      "Inflammatory arthritis", "Connective-tissue disease", "Vasculitis", "Gout",
      "Autoimmune disease", "Immunosuppression", "Biologic or disease-modifying therapy",
      "Infection screening", "Disease-activity scores", "Functional status",
      "Pregnancy coordination", "Infusion monitoring",
    ],
    problemMatchers: ["M05", "M06", "M10", "M32", "M31", "M45",
      "ARTHRITIS", "GOUT", "LUPUS", "VASCULITIS"],
    cdsTopics: ["deprescribing", "antimicrobial-stewardship"],
    registers: [],
    examinationRegions: ["MUSCULOSKELETAL", "SKIN", "FUNCTIONAL_STATUS", "GENERAL_CONDITION"],
    spineLinks: [],
    notBuilt: [
      "Disease-activity scores", "Biologic/DMARD therapy tracking",
      "Pre-immunosuppression infection screening", "Infusion monitoring",
    ],
  },
  {
    key: "haematology",
    label: "Haematology",
    section: "§8.9",
    summary: "Anaemia, haemoglobinopathies, bleeding and thrombosis.",
    conditions: [
      "Anaemia", "Haemoglobinopathies", "Sickle-cell disease", "Bleeding disorders",
      "Thrombotic disease", "Cytopenias", "Haematological malignancy interface",
      "Transfusion planning", "Anticoagulation",
      "Bone-marrow and other diagnostic procedures", "Longitudinal laboratory trends",
    ],
    problemMatchers: ["D50", "D57", "D64", "D66", "D69", "I26", "I82", "C91", "C92",
      "ANAEMIA", "SICKLE", "THROMB", "LEUKAEMIA"],
    cdsTopics: ["procedure-indication", "oncology"],
    registers: [],
    examinationRegions: ["PALLOR", "LYMPH_NODES", "ABDOMEN", "SKIN", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "Bone-marrow biopsy catalogue",
        href: () => "/work/clinical/procedures",
        note: "PROC-BONE-MARROW-BIOPSY — shared spine, not a haematology SoR.",
      },
    ],
    notBuilt: [
      "Transfusion planning", "Anticoagulation monitoring", "Longitudinal laboratory trends",
    ],
  },
  {
    key: "oncology",
    label: "Medical oncology",
    section: "§8.10",
    summary: "Suspicion through diagnosis, MDT, systemic therapy and survivorship.",
    conditions: [
      "Suspicion and referral", "Diagnostic confirmation", "Staging", "MDT", "Treatment intent",
      "Systemic anticancer therapy", "Cycle planning", "Regimen verification", "Toxicity",
      "Response", "Survivorship", "Recurrence", "Palliative care",
      "Financial and access navigation",
    ],
    problemMatchers: ["C0", "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "D0",
      "CANCER", "CARCINOMA", "NEOPLASM", "TUMOUR", "MALIGNAN"],
    cdsTopics: ["oncology", "palliative"],
    registers: [],
    examinationRegions: ["GENERAL_CONDITION", "LYMPH_NODES", "ANTHROPOMETRY", "FUNCTIONAL_STATUS"],
    spineLinks: [
      {
        label: "Record an MDT decision",
        href: (patientId) => `/ehr/${patientId}/consultations`,
        note: "V114 decision record. Telemedicine board sessions remain a separate surface.",
      },
    ],
    notBuilt: [
      "Staging",
      "Two MDT tables remain in pct-service (telemedicine board V051 and decision record V114); " +
        "see docs/clinical/adult-medicine-domain-pack/mdt-consolidation-proposal.md",
      "Systemic anticancer therapy and cycle planning",
      "Regimen verification", "Toxicity and response tracking", "Survivorship and recurrence",
      "Financial and access navigation",
    ],
  },
  {
    key: "dermatology",
    label: "Dermatology",
    section: "§8.11",
    summary: "Structured morphology and distribution, drug reactions and skin cancer referral.",
    conditions: [
      "Structured morphology", "Body distribution", "Photography with consent",
      "Infectious dermatoses", "Inflammatory disease", "Drug reactions",
      "Autoimmune skin disease", "Ulcers", "Skin cancer referral",
      "Procedure and biopsy pathways",
    ],
    problemMatchers: ["L20", "L40", "L30", "L02", "L89", "C43", "C44",
      "DERMATITIS", "PSORIASIS", "CELLULITIS", "ULCER", "MELANOMA"],
    cdsTopics: ["oncology", "procedure-indication"],
    registers: [],
    examinationRegions: ["SKIN", "FEET", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "Biopsy catalogue (US-guided / marrow / breast core)",
        href: () => "/work/clinical/procedures",
        note: "Shared procedures-service biopsy definitions. No PROC-SKIN-BIOPSY row yet.",
      },
      {
        label: "Patient procedure history",
        href: (patientId) => `/ehr/${patientId}/procedures`,
      },
    ],
    notBuilt: [
      "Photography with consent (consent capture and image handling are not built here)",
      "Structured morphology coding",
      "Dedicated skin-biopsy catalogue code (PROC-SKIN-BIOPSY) — shared biopsy machinery exists",
    ],
  },
  {
    key: "geriatrics",
    label: "Geriatric medicine",
    section: "§8.12",
    summary: "WHO ICOPE intrinsic capacity, frailty, falls and polypharmacy.",
    conditions: [
      "Intrinsic capacity", "Cognition", "Mobility", "Nutrition", "Vision", "Hearing",
      "Psychological wellbeing", "Continence", "Frailty", "Falls", "Polypharmacy",
      "Social support", "Carer needs", "Advance-care planning", "Long-term care",
    ],
    problemMatchers: ["R54", "W19", "F03", "R32", "M81",
      "FRAIL", "FALLS", "DEMENTIA", "INCONTINENCE"],
    cdsTopics: ["icope", "deprescribing", "palliative"],
    registers: [],
    examinationRegions: ["COGNITION", "FRAILTY", "FUNCTIONAL_STATUS", "ANTHROPOMETRY", "EYES", "GENERAL_CONDITION"],
    spineLinks: [
      {
        label: "Advance directives (V106)",
        href: (patientId) => `/ehr/${patientId}/advance-directives`,
        note: "This pack already owns advance-care planning on the shared spine — not missing.",
      },
      {
        label: "Functional status",
        href: (patientId) => `/ehr/${patientId}/functional-status`,
      },
    ],
    notBuilt: [
      "Continence assessment", "Social support and carer needs",
      "Long-term care placement",
    ],
  },
  {
    key: "palliative",
    label: "Palliative medicine",
    section: "§8.13",
    summary: "Palliative-needs identification, symptom control and care preferences.",
    conditions: [
      "Palliative-needs identification", "Symptom assessment", "Pain", "Breathlessness",
      "Nausea", "Delirium", "Anxiety", "Spiritual and social concerns", "Function",
      "Care preferences", "Family and caregiver support", "Controlled medicines", "Home care",
      "End-of-life care", "Bereavement", "Advance-care planning according to law and policy",
    ],
    problemMatchers: ["Z51", "R52", "R06", "R11", "F05",
      "PALLIATIVE", "PAIN", "BREATHLESS", "DELIRIUM"],
    cdsTopics: ["palliative", "deprescribing"],
    registers: [],
    examinationRegions: ["GENERAL_CONDITION", "FUNCTIONAL_STATUS", "HYDRATION", "SKIN"],
    spineLinks: [
      {
        label: "Advance directives",
        href: (patientId) => `/ehr/${patientId}/advance-directives`,
      },
    ],
    notBuilt: [
      "Symptom-score instruments",
      "Family and caregiver support", "Home care", "Bereavement follow-up",
      "Controlled-medicine register",
    ],
  },
];

export function findSpecialty(key: string): SpecialtyDefinition | undefined {
  return MEDICINE_SPECIALTIES.find((s) => s.key === key);
}

/**
 * Whether a problem belongs to this specialty's view.
 *
 * Matching is on the code prefix or a fragment of the display text, and is deliberately generous.
 * This filters a view and never changes a record: a false positive costs a clinician one glance, a
 * false negative hides a diagnosis from the specialist looking for it.
 */
export function matchesSpecialty(
  specialty: SpecialtyDefinition,
  code: string | null | undefined,
  display: string | null | undefined,
): boolean {
  const c = (code ?? "").toUpperCase();
  const d = (display ?? "").toUpperCase();
  return specialty.problemMatchers.some((m) => {
    // An empty matcher is the dangerous one, not an empty value: "".startsWith(m) is false for any
    // real matcher, but ANY string startsWith("") is true, so one blank entry in the config would
    // quietly pull every problem in the patient's list into this specialty. Skipped explicitly, and
    // the config is asserted non-blank by test.
    // MUTATION CHECK: removing this line makes a blank matcher claim the whole problem list.
    if (m === "") return false;
    return (c !== "" && c.startsWith(m)) || (d !== "" && d.includes(m));
  });
}
