import type { CdsFactField } from "./CdsTopicPanel";
import type { MedicineCdsTopic } from "@/hooks/queries/useMedicineCds";

/**
 * The fact each governed topic actually reads.
 *
 * These field lists are not cosmetic — they mirror the `requiredInputs` declared by the content
 * packs, so the form asks for exactly what the rules evaluate. A field the rules never read would
 * imply the answer changes the assessment when it cannot; a missing field silently caps what the
 * engine can conclude and shows up as "not assessed" with no way for the clinician to supply it.
 *
 * Every field is optional on purpose. The engine reports unstated facts as unassessed rather than
 * assuming them, and that is the behaviour a clinician needs: a blank box must mean "not stated",
 * never "no".
 */
export interface CdsTopicDefinition {
  topic: MedicineCdsTopic;
  title: string;
  description: string;
  fields: CdsFactField[];
}

const YES_NO: CdsFactField["options"] = [
  { value: "YES", label: "Yes" },
  { value: "NO", label: "No" },
];

export const CDS_TOPICS: CdsTopicDefinition[] = [
  {
    topic: "cvd-risk",
    title: "Cardiovascular risk",
    description: "WHO HEARTS — total CV risk, blood-pressure treatment thresholds and statin indication.",
    fields: [
      { key: "systolicBp", label: "Systolic BP (mmHg)", type: "number", placeholder: "e.g. 150" },
      { key: "cvdRisk10yrPercent", label: "10-year CV risk (%)", type: "number", placeholder: "e.g. 25" },
      { key: "diabetes", label: "Diabetes", type: "select", options: YES_NO },
      { key: "establishedCvd", label: "Established CVD", type: "select", options: YES_NO },
      { key: "ageMonths", label: "Age (months)", type: "number", placeholder: "e.g. 600" },
    ],
  },
  {
    topic: "deprescribing",
    title: "Medication review (renal safety)",
    description:
      "WHO PEN — eGFR-gated cautions, duplicate therapy and hepatic impairment. The medication " +
      "facts are derived from the patient's current medicine list, not asked for.",
    fields: [
      { key: "egfr", label: "eGFR (mL/min/1.73m²)", type: "number", placeholder: "e.g. 25" },
      // onMetformin / onNsaid / onNephrotoxin / onRenallyClearedDrug / duplicateTherapyDetected were
      // YES/NO dropdowns here. They were the only producers of those facts, which meant
      // DEPRX_DUPLICATE_THERAPY_REVIEW fired when a clinician had already spotted the duplicate and
      // said so — the rule reported a finding rather than making one. The server now derives all five
      // from the medicine list (MedicationFactDeriver), and the response says for each fact whether it
      // was derived or merely asserted. Asking again here would let a typed answer overwrite evidence.
      {
        key: "hepaticImpairment", label: "Hepatic impairment", type: "select",
        options: [
          { value: "NONE", label: "None" }, { value: "MILD", label: "Mild" },
          { value: "MODERATE", label: "Moderate" }, { value: "SEVERE", label: "Severe" },
        ],
      },
    ],
  },
  {
    topic: "icope",
    title: "Older person (ICOPE)",
    description: "WHO ICOPE — intrinsic-capacity screening across cognition, mobility, nutrition, vision, hearing and mood.",
    fields: [
      { key: "ageMonths", label: "Age (months)", type: "number", placeholder: "e.g. 780" },
      { key: "cognitionScreenAbnormal", label: "Cognition screen abnormal", type: "select", options: YES_NO },
      { key: "chairRiseUnable", label: "Unable to rise from chair", type: "select", options: YES_NO },
      { key: "weightLossOrLowAppetite", label: "Weight loss / low appetite", type: "select", options: YES_NO },
      { key: "visionImpaired", label: "Vision impaired", type: "select", options: YES_NO },
      { key: "hearingImpaired", label: "Hearing impaired", type: "select", options: YES_NO },
      { key: "depressiveSymptoms", label: "Depressive symptoms", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "mhgap",
    title: "Mental health (mhGAP)",
    description: "WHO mhGAP — priority conditions. The imminent self-harm rule is interruptive and cannot be overridden.",
    fields: [
      {
        key: "selfHarmRisk", label: "Self-harm risk", type: "select",
        options: [{ value: "HIGH", label: "High" }, { value: "LOW", label: "Low" }],
      },
      { key: "suicidalPlan", label: "Suicidal plan", type: "select", options: YES_NO },
      { key: "depressionModerateSevere", label: "Moderate/severe depression", type: "select", options: YES_NO },
      { key: "psychosisFeatures", label: "Psychosis features", type: "select", options: YES_NO },
      { key: "substanceUseHarmful", label: "Harmful substance use", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "antimicrobial-stewardship",
    title: "Antimicrobial stewardship",
    description: "WHO AWaRe — IV-to-oral switch, duration review, culture-directed de-escalation and Reserve-agent approval.",
    fields: [
      { key: "onIvAntibiotic", label: "On IV antibiotic", type: "select", options: YES_NO },
      { key: "clinicallyImproving", label: "Clinically improving", type: "select", options: YES_NO },
      { key: "toleratingOral", label: "Tolerating oral", type: "select", options: YES_NO },
      { key: "antibioticDurationDays", label: "Duration (days)", type: "number", placeholder: "e.g. 7" },
      { key: "cultureSusceptibilityAvailable", label: "Susceptibility available", type: "select", options: YES_NO },
      { key: "onEmpiricalBroadSpectrum", label: "On empirical broad-spectrum", type: "select", options: YES_NO },
      { key: "reserveAntibioticStarted", label: "Reserve agent started", type: "select", options: YES_NO },
      { key: "stewardshipApproval", label: "Stewardship approval", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "palliative",
    title: "Palliative care",
    description: "WHO analgesic ladder, breakthrough pain, symptom control and identifying palliative need.",
    fields: [
      { key: "painScore", label: "Pain score (0–10)", type: "number", placeholder: "e.g. 8" },
      { key: "breakthroughPain", label: "Breakthrough pain", type: "select", options: YES_NO },
      { key: "lifeLimitingIllnessAdvanced", label: "Advanced life-limiting illness", type: "select", options: YES_NO },
      { key: "breathlessnessDistressing", label: "Distressing breathlessness", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "oncology",
    title: "Cancer early diagnosis",
    description: "WHO — alarm-feature recognition and urgent referral. This is about speed of referral, not diagnosis.",
    fields: [
      { key: "unexplainedWeightLoss", label: "Unexplained weight loss", type: "select", options: YES_NO },
      { key: "breastLump", label: "Breast lump", type: "select", options: YES_NO },
      { key: "postmenopausalBleeding", label: "Postmenopausal bleeding", type: "select", options: YES_NO },
      { key: "haemoptysis", label: "Haemoptysis", type: "select", options: YES_NO },
      { key: "rectalBleeding", label: "Rectal bleeding", type: "select", options: YES_NO },
      { key: "dysphagia", label: "Dysphagia", type: "select", options: YES_NO },
      { key: "suspiciousLesion", label: "Suspicious lesion", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "procedure-indication",
    title: "Bedside procedure appropriateness",
    description: "Indication, contraindication and interpretation for LP, paracentesis and pleural aspiration. Execution is procedures-service's.",
    fields: [
      { key: "raisedIcpSigns", label: "Raised ICP signs", type: "select", options: YES_NO },
      { key: "coagulopathy", label: "Coagulopathy", type: "select", options: YES_NO },
      { key: "anticoagulated", label: "Anticoagulated", type: "select", options: YES_NO },
      { key: "suspectedCnsInfection", label: "Suspected CNS infection", type: "select", options: YES_NO },
      { key: "newAscites", label: "New/worsening ascites", type: "select", options: YES_NO },
      { key: "symptomaticEffusion", label: "Symptomatic effusion", type: "select", options: YES_NO },
      { key: "asciticPmnPerMm3", label: "Ascitic PMN (/mm³)", type: "number", placeholder: "e.g. 300" },
    ],
  },
  {
    topic: "malaria",
    title: "Malaria (adult)",
    description:
      "WHO / EDLIZ-aligned — fever in endemic areas, ACT for uncomplicated disease, severe-malaria referral. ENGINEERING_SEED.",
    fields: [
      { key: "feverPresent", label: "Fever present", type: "select", options: YES_NO },
      { key: "endemicOrRecentTravel", label: "Endemic area or recent travel", type: "select", options: YES_NO },
      {
        key: "malariaTestResult", label: "Malaria test (RDT/film)", type: "select",
        options: [{ value: "POSITIVE", label: "Positive" }, { value: "NEGATIVE", label: "Negative" }],
      },
      { key: "severeMalariaSigns", label: "Severe malaria features", type: "select", options: YES_NO },
      { key: "highClinicalSuspicion", label: "High clinical suspicion despite negative test", type: "select", options: YES_NO },
      { key: "pregnant", label: "Pregnant", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "sickle-cell",
    title: "Sickle-cell disease (adult)",
    description:
      "Crisis recognition, hydroxyurea review, Madi transfusion referral (referral only — no transfusion SoR here). ENGINEERING_SEED.",
    fields: [
      { key: "sickleCellDiagnosis", label: "Sickle-cell disease confirmed", type: "select", options: YES_NO },
      { key: "vocPainPresent", label: "Vaso-occlusive pain", type: "select", options: YES_NO },
      { key: "acuteChestSyndrome", label: "Acute chest syndrome suspected", type: "select", options: YES_NO },
      { key: "feverPresent", label: "Fever", type: "select", options: YES_NO },
      { key: "vocEpisodesPast12Months", label: "VOC episodes (past 12 months)", type: "number", placeholder: "e.g. 3" },
      { key: "transfusionIndicated", label: "Transfusion indicated", type: "select", options: YES_NO },
    ],
  },
  {
    topic: "rhd",
    title: "Rheumatic heart disease",
    description:
      "Secondary benzathine penicillin prophylaxis, echo referral, ARF recognition. ENGINEERING_SEED.",
    fields: [
      { key: "rhdDiagnosis", label: "RHD established", type: "select", options: YES_NO },
      { key: "daysSinceLastBenzathinePenicillin", label: "Days since last benzathine penicillin", type: "number", placeholder: "e.g. 30" },
      { key: "penicillinAllergy", label: "Penicillin allergy documented", type: "select", options: YES_NO },
      {
        key: "monthsSinceLastEcho", label: "Months since last echo", type: "select",
        options: [
          { value: "6", label: "≤ 6 months" },
          { value: "12", label: "12 months" },
          { value: "14", label: "> 12 months" },
          { value: "NEVER", label: "Never performed" },
        ],
      },
      { key: "acuteRheumaticFeverSuspected", label: "Acute rheumatic fever suspected", type: "select", options: YES_NO },
    ],
  },
];
