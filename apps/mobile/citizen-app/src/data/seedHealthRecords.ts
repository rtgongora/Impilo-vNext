/**
 * Seed data for the Health tab sections.
 *
 * USAGE:
 *   Set USE_SEED_DATA = true  → sections render from this file (no API needed)
 *   Set USE_SEED_DATA = false → sections call the real backend services
 *
 * All shapes are typed against src/types/index.ts so swapping to the real API
 * requires only changing USE_SEED_DATA and removing the seed import — the
 * section component logic stays identical.
 */

import type {
  Allergy,
  Condition,
  Immunization,
  MedicalRecord,
  TimelineEntry,
  Assessment,
  HealthId,
  Appointment,
  Prescription,
  LabResult,
  Referral,
  CarePlan,
  CareTeamMember,
  Reminder,
  MonitoringDevice,
  QueueTicket,
  WellnessActivity,
  WellnessVital,
  MoodEntry,
  WellnessChallenge,
  WellnessGoal,
  WellnessProgram,
  Enrollment,
  CoverageInfo,
  Balance,
  PendingCharge,
  Transaction,
  HealthWallet,
  WalletTransaction,
} from "../types";

// ─── Toggle ───────────────────────────────────────────────────────────────────
export const USE_SEED_DATA = true;

// ─── Helpers ─────────────────────────────────────────────────────────────────
const daysAgo = (n: number) =>
  new Date(Date.now() - n * 86_400_000).toISOString();

const PARIRENYATWA = "Parirenyatwa Group of Hospitals";
const MPILO        = "Mpilo Central Hospital";
const HARARE_CITY  = "Harare City Health Clinic";
const CHITUNGWIZA  = "Chitungwiza Central Hospital";

// ─── Health ID ────────────────────────────────────────────────────────────────
export const SEED_HEALTH_ID: HealthId = {
  id:                   "hid-001",
  healthIdNumber:       "ZW-NHI-2025-00123456",
  status:               "ACTIVE",
  issuedAt:             daysAgo(730),
  expiresAt:            new Date(Date.now() + 365 * 2 * 86_400_000).toISOString(),
  qrCodeData:           "ZW-NHI-2025-00123456|dev-citizen-001|CITIZEN",
  bloodType:            "O+",
  allergiesSummary:     "Penicillin (SEVERE), Sulfonamides (MODERATE)",
  emergencyContactName: "Grace Citizen",
  emergencyContactPhone:"+263 77 234 5678",
};

// ─── Allergies ────────────────────────────────────────────────────────────────
export const SEED_ALLERGIES: Allergy[] = [
  {
    id:          "allergy-001",
    allergen:    "Penicillin",
    reaction:    "Anaphylaxis, urticaria, throat swelling",
    severity:    "SEVERE",
    onsetDate:   daysAgo(3650),
    recordedAt:  daysAgo(730),
    recordedBy:  "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    notes:       "Patient carries EpiPen. Avoid all penicillin-class antibiotics.",
  },
  {
    id:          "allergy-002",
    allergen:    "Sulfonamides (Sulfa drugs)",
    reaction:    "Skin rash, fever, nausea",
    severity:    "MODERATE",
    onsetDate:   daysAgo(1825),
    recordedAt:  daysAgo(365),
    recordedBy:  "Dr. S. Ndlovu",
    facilityName: MPILO,
    notes:       "Developed during TB prophylaxis course.",
  },
  {
    id:          "allergy-003",
    allergen:    "Pollen (seasonal)",
    reaction:    "Rhinitis, watery eyes, sneezing",
    severity:    "MILD",
    onsetDate:   daysAgo(2190),
    recordedAt:  daysAgo(180),
    recordedBy:  "Dr. R. Chikwanda",
    facilityName: HARARE_CITY,
  },
  {
    id:          "allergy-004",
    allergen:    "Latex",
    reaction:    "Contact dermatitis, localised swelling",
    severity:    "MODERATE",
    onsetDate:   daysAgo(900),
    recordedAt:  daysAgo(90),
    recordedBy:  "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    notes:       "Inform surgical team prior to any procedure.",
  },
];

// ─── Conditions ───────────────────────────────────────────────────────────────
export const SEED_CONDITIONS: Condition[] = [
  {
    id:          "cond-001",
    name:        "Type 2 Diabetes Mellitus",
    code:        "E11",
    status:      "ACTIVE",
    onsetDate:   daysAgo(1460),
    recordedAt:  daysAgo(1460),
    recordedBy:  "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    notes:       "Well-controlled on Metformin 500mg BD. Last HbA1c: 7.2% (3 months ago).",
    severity:    "Moderate",
    category:    "Endocrine",
  },
  {
    id:          "cond-002",
    name:        "Essential Hypertension",
    code:        "I10",
    status:      "ACTIVE",
    onsetDate:   daysAgo(1095),
    recordedAt:  daysAgo(1095),
    recordedBy:  "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    notes:       "Target BP <130/80. Currently on Amlodipine 5mg OD.",
    severity:    "Mild",
    category:    "Cardiovascular",
  },
  {
    id:          "cond-003",
    name:        "Iron-Deficiency Anaemia",
    code:        "D50.9",
    status:      "RESOLVED",
    onsetDate:   daysAgo(730),
    recordedAt:  daysAgo(730),
    resolvedAt:  daysAgo(540),
    recordedBy:  "Dr. S. Ndlovu",
    facilityName: MPILO,
    notes:       "Resolved following 3-month iron supplementation course.",
    category:    "Haematology",
  },
  {
    id:          "cond-004",
    name:        "Gastroesophageal Reflux Disease (GERD)",
    code:        "K21.0",
    status:      "ACTIVE",
    onsetDate:   daysAgo(365),
    recordedAt:  daysAgo(365),
    recordedBy:  "Dr. R. Chikwanda",
    facilityName: HARARE_CITY,
    notes:       "Managed with lifestyle changes and Omeprazole 20mg OD PRN.",
    severity:    "Mild",
    category:    "Gastroenterology",
  },
  {
    id:          "cond-005",
    name:        "Malaria (uncomplicated P. falciparum)",
    code:        "B50.9",
    status:      "RESOLVED",
    onsetDate:   daysAgo(180),
    recordedAt:  daysAgo(180),
    resolvedAt:  daysAgo(167),
    recordedBy:  "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    notes:       "Completed full ACT course (Artemether-Lumefantrine). Full recovery.",
    category:    "Infectious Disease",
  },
];

// ─── Immunizations ────────────────────────────────────────────────────────────
export const SEED_IMMUNIZATIONS: Immunization[] = [
  {
    id:              "imm-001",
    vaccineName:     "COVID-19 (Oxford-AstraZeneca)",
    vaccineCode:     "CVX-212",
    doseNumber:      2,
    totalDoses:      2,
    administeredAt:  daysAgo(730),
    administeredBy:  "Nurse P. Sibanda",
    facilityName:    PARIRENYATWA,
    lotNumber:       "AZ-2021-LOT-0039",
    status:          "COMPLETED",
    notes:           "Full primary series complete.",
  },
  {
    id:              "imm-002",
    vaccineName:     "COVID-19 Booster (Sinopharm)",
    vaccineCode:     "CVX-510",
    doseNumber:      1,
    totalDoses:      1,
    administeredAt:  daysAgo(365),
    administeredBy:  "Nurse P. Sibanda",
    facilityName:    HARARE_CITY,
    lotNumber:       "SP-2022-LOT-0187",
    status:          "COMPLETED",
  },
  {
    id:              "imm-003",
    vaccineName:     "Tetanus Toxoid (Td)",
    vaccineCode:     "CVX-09",
    doseNumber:      1,
    totalDoses:      1,
    administeredAt:  daysAgo(548),
    administeredBy:  "Dr. S. Ndlovu",
    facilityName:    MPILO,
    nextDoseDate:    new Date(Date.now() + 365 * 9 * 86_400_000).toISOString(),
    status:          "COMPLETED",
    notes:           "Booster due in 10 years.",
  },
  {
    id:              "imm-004",
    vaccineName:     "Yellow Fever",
    vaccineCode:     "CVX-184",
    doseNumber:      1,
    totalDoses:      1,
    administeredAt:  daysAgo(912),
    administeredBy:  "Dr. R. Chikwanda",
    facilityName:    HARARE_CITY,
    expirationDate:  new Date(Date.now() + 365 * 7 * 86_400_000).toISOString(),
    status:          "COMPLETED",
    notes:           "International travel certificate issued.",
  },
  {
    id:              "imm-005",
    vaccineName:     "Influenza (Seasonal)",
    vaccineCode:     "CVX-141",
    doseNumber:      1,
    totalDoses:      1,
    administeredAt:  daysAgo(400),
    administeredBy:  "Nurse M. Dube",
    facilityName:    HARARE_CITY,
    status:          "DUE",
    nextDoseDate:    daysAgo(-30), // 30 days from now
    notes:           "Annual dose due.",
  },
  {
    id:              "imm-006",
    vaccineName:     "Hepatitis B (HBV)",
    vaccineCode:     "CVX-08",
    doseNumber:      3,
    totalDoses:      3,
    administeredAt:  daysAgo(1825),
    administeredBy:  "Nurse P. Sibanda",
    facilityName:    CHITUNGWIZA,
    status:          "COMPLETED",
    notes:           "Full 3-dose series completed.",
  },
];

// ─── Medical Records ──────────────────────────────────────────────────────────
export const SEED_RECORDS: MedicalRecord[] = [
  {
    id:           "rec-001",
    title:        "HbA1c & Fasting Blood Glucose",
    type:         "LAB_REPORT",
    date:         daysAgo(90),
    provider:     "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    summary:      "HbA1c: 7.2% (target <7%). FBG: 6.8 mmol/L. Renal function: normal.",
    createdAt:    daysAgo(88),
  },
  {
    id:           "rec-002",
    title:        "Diabetes & Hypertension Review — Q2 2025",
    type:         "CLINICAL_NOTE",
    date:         daysAgo(90),
    provider:     "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    summary:      "Stable DM2 and HTN. Continue current regimen. Lifestyle advice given. Follow-up in 3 months.",
    createdAt:    daysAgo(88),
  },
  {
    id:           "rec-003",
    title:        "Malaria Treatment Discharge",
    type:         "DISCHARGE_SUMMARY",
    date:         daysAgo(167),
    provider:     "Dr. T. Moyo",
    facilityName: PARIRENYATWA,
    summary:      "Admitted for uncomplicated P. falciparum malaria. Treated with AL 6-dose regimen. Discharged well. RDT negative on day 3.",
    createdAt:    daysAgo(167),
  },
  {
    id:           "rec-004",
    title:        "Chest X-Ray (PA view)",
    type:         "IMAGING",
    date:         daysAgo(200),
    provider:     "Dr. S. Ndlovu",
    facilityName: MPILO,
    summary:      "Cardiomegaly: borderline. Lung fields clear. No pleural effusion. Recommend echocardiogram.",
    createdAt:    daysAgo(198),
  },
  {
    id:           "rec-005",
    title:        "Full Blood Count",
    type:         "LAB_REPORT",
    date:         daysAgo(270),
    provider:     "Lab — PAGH",
    facilityName: PARIRENYATWA,
    summary:      "Haemoglobin: 13.1 g/dL (normal). WBC: 5.8 × 10⁹/L. Platelets: 220 × 10⁹/L.",
    createdAt:    daysAgo(268),
  },
  {
    id:           "rec-006",
    title:        "Annual Health Review",
    type:         "CLINICAL_NOTE",
    date:         daysAgo(365),
    provider:     "Dr. R. Chikwanda",
    facilityName: HARARE_CITY,
    summary:      "BMI 26.4. BP 128/82 mmHg. Cardiovascular risk moderate. Recommend annual FBC and lipid screen.",
    createdAt:    daysAgo(363),
  },
];

// ─── Health Timeline ──────────────────────────────────────────────────────────
export const SEED_TIMELINE: TimelineEntry[] = [
  {
    id:           "tl-001",
    type:         "ENCOUNTER",
    title:        "Diabetes & Hypertension Review",
    description:  "Quarterly follow-up. HbA1c 7.2%. BP 128/80. Medications continued.",
    date:         daysAgo(90),
    facilityName: PARIRENYATWA,
    provider:     "Dr. T. Moyo",
  },
  {
    id:           "tl-002",
    type:         "LAB_RESULT",
    title:        "HbA1c Result: 7.2%",
    description:  "Within target range. Fasting glucose 6.8 mmol/L.",
    date:         daysAgo(90),
    facilityName: PARIRENYATWA,
    provider:     "PAGH Laboratory",
  },
  {
    id:           "tl-003",
    type:         "PRESCRIPTION",
    title:        "Metformin 500mg BD renewed",
    description:  "3-month supply. Metformin 500mg twice daily with meals.",
    date:         daysAgo(90),
    facilityName: PARIRENYATWA,
    provider:     "Dr. T. Moyo",
  },
  {
    id:           "tl-004",
    type:         "ENCOUNTER",
    title:        "Malaria Treatment — Admission",
    description:  "Uncomplicated P. falciparum. Started Artemether-Lumefantrine.",
    date:         daysAgo(180),
    facilityName: PARIRENYATWA,
    provider:     "Dr. T. Moyo",
  },
  {
    id:           "tl-005",
    type:         "IMMUNIZATION",
    title:        "COVID-19 Booster Administered",
    description:  "Sinopharm booster dose. No adverse reactions.",
    date:         daysAgo(365),
    facilityName: HARARE_CITY,
    provider:     "Nurse P. Sibanda",
  },
  {
    id:           "tl-006",
    type:         "LAB_RESULT",
    title:        "Annual Full Blood Count",
    description:  "All values within normal range. Anaemia resolved.",
    date:         daysAgo(270),
    facilityName: PARIRENYATWA,
    provider:     "PAGH Laboratory",
  },
  {
    id:           "tl-007",
    type:         "ENCOUNTER",
    title:        "Annual Health Review",
    description:  "BMI 26.4. Moderate cardiovascular risk. Lifestyle counselling given.",
    date:         daysAgo(365),
    facilityName: HARARE_CITY,
    provider:     "Dr. R. Chikwanda",
  },
  {
    id:           "tl-008",
    type:         "PRESCRIPTION",
    title:        "Amlodipine 5mg OD — Initiated",
    description:  "Calcium channel blocker started for essential hypertension.",
    date:         daysAgo(1095),
    facilityName: PARIRENYATWA,
    provider:     "Dr. T. Moyo",
  },
  {
    id:           "tl-009",
    type:         "IMMUNIZATION",
    title:        "Yellow Fever Vaccine",
    description:  "Travel certificate issued. Valid for 10 years.",
    date:         daysAgo(912),
    facilityName: HARARE_CITY,
    provider:     "Dr. R. Chikwanda",
  },
  {
    id:           "tl-010",
    type:         "APPOINTMENT",
    title:        "Next Diabetes Review Scheduled",
    description:  "Follow-up in 3 months for repeat HbA1c and renal function panel.",
    date:         new Date(Date.now() + 90 * 86_400_000).toISOString(),
    facilityName: PARIRENYATWA,
    provider:     "Dr. T. Moyo",
  },
];

// ─── Assessments ─────────────────────────────────────────────────────────────
export const SEED_ASSESSMENTS: Assessment[] = [
  {
    id:             "assess-001",
    name:           "PHQ-9 Depression Screening",
    description:    "9-item patient health questionnaire to screen for depression severity.",
    category:       "Mental Health",
    status:         "COMPLETED",
    completedAt:    daysAgo(90),
    score:          4,
    maxScore:       27,
    resultSummary:  "Minimal depression (score 4/27). No intervention required at this time.",
  },
  {
    id:             "assess-002",
    name:           "Diabetes Self-Care Assessment",
    description:    "Evaluates diet adherence, activity, medication compliance, and monitoring.",
    category:       "Chronic Disease",
    status:         "COMPLETED",
    completedAt:    daysAgo(90),
    score:          78,
    maxScore:       100,
    resultSummary:  "Good self-management (78%). Areas for improvement: dietary carbohydrate tracking.",
  },
  {
    id:             "assess-003",
    name:           "Cardiovascular Risk Score (Framingham)",
    description:    "10-year cardiovascular event risk assessment.",
    category:       "Cardiovascular",
    status:         "COMPLETED",
    completedAt:    daysAgo(180),
    score:          12,
    maxScore:       100,
    resultSummary:  "Moderate 10-year risk (12%). Recommend statins consideration at next review.",
  },
  {
    id:             "assess-004",
    name:           "Annual Wellness Assessment",
    description:    "Comprehensive self-reported health and lifestyle questionnaire.",
    category:       "General Wellness",
    status:         "IN_PROGRESS",
    startedAt:      daysAgo(2),
    resultSummary:  "Partially completed — 6 of 10 sections done.",
  },
  {
    id:             "assess-005",
    name:           "Falls Risk Screening (STEADI)",
    description:    "Screening tool for fall risk in adults.",
    category:       "Safety",
    status:         "NOT_STARTED",
  },
];

// ═══════════════════════════════════════════════════════════════════════════════
// APPOINTMENTS & CARE
// ═══════════════════════════════════════════════════════════════════════════════

// ─── Appointments ─────────────────────────────────────────────────────────────
export const SEED_APPOINTMENTS: Appointment[] = [
  {
    id:              "appt-001",
    facilityId:      "fac-pagh",
    facilityName:    PARIRENYATWA,
    providerId:      "prov-tmoyo",
    providerName:    "Dr. T. Moyo",
    appointmentType: "Diabetes & Hypertension Review",
    status:          "SCHEDULED",
    scheduledAt:     daysAgo(-14),
    duration:        30,
    reason:          "Quarterly diabetes and hypertension follow-up review.",
    createdAt:       daysAgo(5),
  },
  {
    id:              "appt-002",
    facilityId:      "fac-pagh",
    facilityName:    PARIRENYATWA,
    providerId:      "prov-tmoyo",
    providerName:    "Dr. T. Moyo",
    appointmentType: "Cardiology Consultation",
    status:          "CONFIRMED",
    scheduledAt:     daysAgo(-21),
    duration:        45,
    reason:          "Echocardiogram follow-up and cardiovascular risk assessment.",
    createdAt:       daysAgo(10),
  },
  {
    id:              "appt-003",
    facilityId:      "fac-harare",
    facilityName:    HARARE_CITY,
    providerId:      "prov-rchikwanda",
    providerName:    "Dr. R. Chikwanda",
    appointmentType: "General Practice",
    status:          "COMPLETED",
    scheduledAt:     daysAgo(90),
    duration:        20,
    reason:          "Annual health review and wellness check.",
    createdAt:       daysAgo(100),
  },
  {
    id:              "appt-004",
    facilityId:      "fac-mpilo",
    facilityName:    MPILO,
    providerId:      "prov-sndlovu",
    providerName:    "Dr. S. Ndlovu",
    appointmentType: "Ophthalmology Referral",
    status:          "COMPLETED",
    scheduledAt:     daysAgo(180),
    duration:        30,
    reason:          "Diabetic retinopathy screening.",
    createdAt:       daysAgo(190),
  },
  {
    id:              "appt-005",
    facilityId:      "fac-pagh",
    facilityName:    PARIRENYATWA,
    providerId:      "prov-tmoyo",
    providerName:    "Dr. T. Moyo",
    appointmentType: "Diabetes Review",
    status:          "CANCELLED",
    scheduledAt:     daysAgo(60),
    duration:        30,
    reason:          "Routine diabetes check — rescheduled.",
    createdAt:       daysAgo(70),
  },
];

// ─── Prescriptions ────────────────────────────────────────────────────────────
export const SEED_PRESCRIPTIONS: Prescription[] = [
  {
    id:               "rx-001",
    medicationName:   "Metformin 500mg",
    dosage:           "500mg",
    frequency:        "Twice daily with meals",
    duration:         "3 months",
    quantity:         180,
    status:           "ACTIVE",
    prescribedBy:     "Dr. T. Moyo",
    facilityName:     PARIRENYATWA,
    refillsRemaining: 2,
    lastFilledAt:     daysAgo(90),
    createdAt:        daysAgo(450),
  },
  {
    id:               "rx-002",
    medicationName:   "Amlodipine 5mg",
    dosage:           "5mg",
    frequency:        "Once daily in the morning",
    duration:         "3 months",
    quantity:         90,
    status:           "ACTIVE",
    prescribedBy:     "Dr. T. Moyo",
    facilityName:     PARIRENYATWA,
    refillsRemaining: 1,
    lastFilledAt:     daysAgo(90),
    createdAt:        daysAgo(365),
  },
  {
    id:               "rx-003",
    medicationName:   "Omeprazole 20mg",
    dosage:           "20mg",
    frequency:        "Once daily before breakfast (PRN)",
    duration:         "1 month",
    quantity:         30,
    status:           "ACTIVE",
    prescribedBy:     "Dr. R. Chikwanda",
    facilityName:     HARARE_CITY,
    refillsRemaining: 0,
    lastFilledAt:     daysAgo(30),
    createdAt:        daysAgo(365),
  },
  {
    id:               "rx-004",
    medicationName:   "Artemether-Lumefantrine 20/120mg",
    dosage:           "4 tablets",
    frequency:        "Twice daily for 3 days",
    duration:         "3 days",
    quantity:         24,
    status:           "DISPENSED",
    prescribedBy:     "Dr. T. Moyo",
    facilityName:     PARIRENYATWA,
    refillsRemaining: 0,
    lastFilledAt:     daysAgo(180),
    createdAt:        daysAgo(180),
  },
  {
    id:               "rx-005",
    medicationName:   "Ferrous Sulphate 200mg",
    dosage:           "200mg",
    frequency:        "Once daily",
    duration:         "3 months",
    quantity:         90,
    status:           "CANCELLED",
    prescribedBy:     "Dr. S. Ndlovu",
    facilityName:     MPILO,
    refillsRemaining: 0,
    lastFilledAt:     daysAgo(730),
    createdAt:        daysAgo(730),
  },
];

// ─── Lab Results ──────────────────────────────────────────────────────────────
export const SEED_LAB_RESULTS: LabResult[] = [
  {
    id:              "lab-001",
    testName:        "HbA1c (Glycated Haemoglobin)",
    category:        "Endocrinology",
    status:          "COMPLETED",
    value:           "7.2",
    unit:            "%",
    referenceRange:  "<7.0% (target for diabetics)",
    interpretation:  "Slightly above target. Continue current regimen. Recheck in 3 months.",
    orderedBy:       "Dr. T. Moyo",
    facilityName:    PARIRENYATWA,
    collectedAt:     daysAgo(91),
    resultAt:        daysAgo(90),
    createdAt:       daysAgo(91),
  },
  {
    id:              "lab-002",
    testName:        "Fasting Blood Glucose",
    category:        "Endocrinology",
    status:          "COMPLETED",
    value:           "6.8",
    unit:            "mmol/L",
    referenceRange:  "3.9–6.1 mmol/L",
    interpretation:  "Mildly elevated. Consistent with known DM2. Dietary review advised.",
    orderedBy:       "Dr. T. Moyo",
    facilityName:    PARIRENYATWA,
    collectedAt:     daysAgo(91),
    resultAt:        daysAgo(90),
    createdAt:       daysAgo(91),
  },
  {
    id:              "lab-003",
    testName:        "Full Blood Count (FBC)",
    category:        "Haematology",
    status:          "COMPLETED",
    value:           "13.1",
    unit:            "g/dL (Hb)",
    referenceRange:  "13.0–17.0 g/dL",
    interpretation:  "All values within normal limits. Previous anaemia fully resolved.",
    orderedBy:       "Dr. T. Moyo",
    facilityName:    PARIRENYATWA,
    collectedAt:     daysAgo(271),
    resultAt:        daysAgo(270),
    createdAt:       daysAgo(271),
  },
  {
    id:              "lab-004",
    testName:        "Lipid Panel",
    category:        "Biochemistry",
    status:          "COMPLETED",
    value:           "5.8",
    unit:            "mmol/L (Total Chol)",
    referenceRange:  "<5.0 mmol/L",
    interpretation:  "Total cholesterol borderline high. LDL 3.6 mmol/L. Lifestyle modification advised. Consider statin at next review.",
    orderedBy:       "Dr. R. Chikwanda",
    facilityName:    HARARE_CITY,
    collectedAt:     daysAgo(366),
    resultAt:        daysAgo(365),
    createdAt:       daysAgo(366),
  },
  {
    id:              "lab-005",
    testName:        "Urine Albumin-Creatinine Ratio",
    category:        "Nephrology",
    status:          "PROCESSING",
    orderedBy:       "Dr. T. Moyo",
    facilityName:    PARIRENYATWA,
    collectedAt:     daysAgo(2),
    createdAt:       daysAgo(2),
  },
  {
    id:              "lab-006",
    testName:        "Serum Creatinine & eGFR",
    category:        "Nephrology",
    status:          "ORDERED",
    orderedBy:       "Dr. T. Moyo",
    facilityName:    PARIRENYATWA,
    createdAt:       daysAgo(1),
  },
];

// ─── Referrals ────────────────────────────────────────────────────────────────
export const SEED_REFERRALS: Referral[] = [
  {
    id:              "ref-001",
    fromFacilityId:  "fac-pagh",
    fromFacilityName: PARIRENYATWA,
    toFacilityId:    "fac-pagh",
    toFacilityName:  PARIRENYATWA,
    toProviderName:  "Dr. M. Chikasha",
    specialty:       "Cardiology",
    reason:          "Borderline cardiomegaly on CXR. Echocardiogram required to assess cardiac function and rule out hypertensive heart disease.",
    status:          "ACCEPTED",
    requestedAt:     daysAgo(200),
    acceptedAt:      daysAgo(195),
    notes:           "Patient is on Amlodipine for hypertension. BP well-controlled.",
  },
  {
    id:              "ref-002",
    fromFacilityId:  "fac-pagh",
    fromFacilityName: PARIRENYATWA,
    toFacilityId:    "fac-mpilo",
    toFacilityName:  MPILO,
    toProviderName:  "Dr. P. Muroyiwa",
    specialty:       "Ophthalmology",
    reason:          "Annual diabetic retinopathy screening. DM2 patient, 4 years duration.",
    status:          "COMPLETED",
    requestedAt:     daysAgo(190),
    acceptedAt:      daysAgo(185),
    completedAt:     daysAgo(180),
    notes:           "No diabetic retinopathy detected. Rescan in 12 months.",
  },
  {
    id:              "ref-003",
    fromFacilityId:  "fac-harare",
    fromFacilityName: HARARE_CITY,
    toFacilityId:    "fac-pagh",
    toFacilityName:  PARIRENYATWA,
    specialty:       "Dietetics",
    reason:          "Nutritional counselling for type 2 diabetes and elevated cholesterol. Request dietary assessment and meal planning guidance.",
    status:          "PENDING",
    requestedAt:     daysAgo(10),
    notes:           "Patient motivated to improve diet. BMI 26.4.",
  },
];

// ─── Care Plans ───────────────────────────────────────────────────────────────
export const SEED_CARE_PLANS: CarePlan[] = [
  {
    id:          "cp-001",
    name:        "Type 2 Diabetes Management",
    description: "Structured plan to achieve and maintain glycaemic control through medication adherence, lifestyle modification, and regular monitoring.",
    status:      "ACTIVE",
    startDate:   daysAgo(365),
    endDate:     daysAgo(-365),
    createdBy:   "Dr. T. Moyo",
    createdAt:   daysAgo(365),
    updatedAt:   daysAgo(90),
    goals: [
      { id: "g-001", description: "Achieve HbA1c < 7.0%",            status: "IN_PROGRESS", targetDate: daysAgo(-90),  currentValue: 7.2,  targetValue: 7.0  },
      { id: "g-002", description: "Maintain fasting glucose 4–7 mmol/L", status: "IN_PROGRESS", currentValue: 6.8, targetValue: 6.5 },
      { id: "g-003", description: "Daily foot inspection routine",    status: "ACHIEVED"   },
      { id: "g-004", description: "30 min moderate exercise 5×/week", status: "IN_PROGRESS" },
    ],
    interventions: [
      { id: "i-001", description: "Metformin 500mg BD", type: "MEDICATION", frequency: "Twice daily", status: "ACTIVE"  },
      { id: "i-002", description: "Dietitian consultation", type: "REFERRAL", frequency: "Every 6 months", status: "PENDING" },
      { id: "i-003", description: "HbA1c monitoring",  type: "MONITORING", frequency: "Every 3 months", status: "ACTIVE" },
    ],
  },
  {
    id:          "cp-002",
    name:        "Hypertension Control",
    description: "Blood pressure management plan targeting BP < 130/80 mmHg with antihypertensive therapy and lifestyle changes.",
    status:      "ACTIVE",
    startDate:   daysAgo(365),
    createdBy:   "Dr. T. Moyo",
    createdAt:   daysAgo(365),
    updatedAt:   daysAgo(90),
    goals: [
      { id: "g-005", description: "BP target < 130/80 mmHg",        status: "IN_PROGRESS" },
      { id: "g-006", description: "Reduce dietary sodium intake",    status: "IN_PROGRESS" },
      { id: "g-007", description: "No smoking / alcohol reduction",  status: "ACHIEVED"    },
    ],
    interventions: [
      { id: "i-004", description: "Amlodipine 5mg OD", type: "MEDICATION", frequency: "Once daily", status: "ACTIVE" },
      { id: "i-005", description: "Home BP monitoring", type: "MONITORING", frequency: "Daily",      status: "ACTIVE" },
    ],
  },
];

// ─── Care Team ────────────────────────────────────────────────────────────────
export const SEED_CARE_TEAM: CareTeamMember[] = [
  {
    id:          "ct-001",
    name:        "Dr. T. Moyo",
    role:        "Primary Care Physician",
    specialty:   "Internal Medicine",
    facilityName: PARIRENYATWA,
    phone:       "+263 4 794 411",
    email:       "t.moyo@pagh.gov.zw",
    isPrimary:   true,
  },
  {
    id:          "ct-002",
    name:        "Nurse P. Sibanda",
    role:        "Primary Care Nurse",
    specialty:   "Chronic Disease Management",
    facilityName: PARIRENYATWA,
    phone:       "+263 4 794 412",
    isPrimary:   false,
  },
  {
    id:          "ct-003",
    name:        "Dr. M. Chikasha",
    role:        "Specialist",
    specialty:   "Cardiology",
    facilityName: PARIRENYATWA,
    phone:       "+263 4 794 450",
    isPrimary:   false,
  },
  {
    id:          "ct-004",
    name:        "Dr. R. Chikwanda",
    role:        "General Practitioner",
    specialty:   "Family Medicine",
    facilityName: HARARE_CITY,
    phone:       "+263 4 705 533",
    isPrimary:   false,
  },
];

// ─── Reminders ────────────────────────────────────────────────────────────────
const tomorrow8am = new Date(); tomorrow8am.setDate(tomorrow8am.getDate() + 1); tomorrow8am.setHours(8, 0, 0, 0);
const tomorrow7pm = new Date(); tomorrow7pm.setDate(tomorrow7pm.getDate() + 1); tomorrow7pm.setHours(19, 0, 0, 0);
const nextWeek    = new Date(); nextWeek.setDate(nextWeek.getDate() + 7); nextWeek.setHours(9, 0, 0, 0);

export const SEED_REMINDERS: Reminder[] = [
  {
    id:          "rem-001",
    title:       "Metformin — Morning Dose",
    type:        "MEDICATION",
    description: "Take Metformin 500mg with breakfast.",
    dateTime:    tomorrow8am.toISOString(),
    recurrence:  "DAILY",
    enabled:     true,
    createdAt:   daysAgo(365),
    updatedAt:   daysAgo(90),
  },
  {
    id:          "rem-002",
    title:       "Metformin — Evening Dose",
    type:        "MEDICATION",
    description: "Take Metformin 500mg with dinner.",
    dateTime:    tomorrow7pm.toISOString(),
    recurrence:  "DAILY",
    enabled:     true,
    createdAt:   daysAgo(365),
    updatedAt:   daysAgo(90),
  },
  {
    id:          "rem-003",
    title:       "Amlodipine — Morning",
    type:        "MEDICATION",
    description: "Take Amlodipine 5mg before breakfast.",
    dateTime:    tomorrow8am.toISOString(),
    recurrence:  "DAILY",
    enabled:     true,
    createdAt:   daysAgo(365),
    updatedAt:   daysAgo(90),
  },
  {
    id:          "rem-004",
    title:       "Diabetes Review Appointment",
    type:        "APPOINTMENT",
    description: "Quarterly review with Dr. T. Moyo at Parirenyatwa.",
    dateTime:    daysAgo(-14),
    recurrence:  "ONCE",
    enabled:     true,
    createdAt:   daysAgo(5),
    updatedAt:   daysAgo(5),
  },
  {
    id:          "rem-005",
    title:       "Influenza Vaccine Due",
    type:        "FOLLOW_UP",
    description: "Annual influenza vaccine is due this month. Contact your facility to schedule.",
    dateTime:    nextWeek.toISOString(),
    recurrence:  "ONCE",
    enabled:     true,
    createdAt:   daysAgo(7),
    updatedAt:   daysAgo(7),
  },
  {
    id:          "rem-006",
    title:       "HbA1c Lab Check",
    type:        "LAB_CHECK",
    description: "3-month HbA1c and renal function panel at PAGH laboratory.",
    dateTime:    daysAgo(-7),
    recurrence:  "ONCE",
    enabled:     false,
    createdAt:   daysAgo(90),
    updatedAt:   daysAgo(1),
  },
];

// ─── Monitoring Devices ───────────────────────────────────────────────────────
export const SEED_MONITORING_DEVICES: MonitoringDevice[] = [
  {
    id:             "dev-001",
    deviceName:     "Omron HEM-7156T",
    deviceType:     "BLOOD_PRESSURE",
    manufacturer:   "Omron",
    model:          "HEM-7156T",
    connectionType: "Bluetooth",
    status:         "CONNECTED",
    lastSyncAt:     daysAgo(0),
    batteryLevel:   78,
  },
  {
    id:             "dev-002",
    deviceName:     "Accu-Chek Guide",
    deviceType:     "GLUCOSE_METER",
    manufacturer:   "Roche",
    model:          "Accu-Chek Guide",
    connectionType: "Bluetooth",
    status:         "CONNECTED",
    lastSyncAt:     daysAgo(1),
    batteryLevel:   45,
  },
];

// ─── Queue Tickets ────────────────────────────────────────────────────────────
export const SEED_QUEUE_TICKETS: QueueTicket[] = [
  {
    id:            "q-001",
    facilityName:  PARIRENYATWA,
    ticketNumber:  "A-047",
    serviceType:   "Internal Medicine — OPD",
    status:        "WAITING",
    position:      3,
    estimatedWait: 25,
    joinedAt:      new Date(Date.now() - 40 * 60_000).toISOString(),
  },
];

// ═══════════════════════════════════════════════════════════════════════════════
// WELLNESS & LIFESTYLE
// ═══════════════════════════════════════════════════════════════════════════════

export const SEED_WELLNESS_ACTIVITIES: WellnessActivity[] = Array.from({ length: 7 }, (_, i) => ({
  id:             `act-00${i + 1}`,
  activityDate:   daysAgo(i),
  steps:          [9540, 12300, 7800, 10120, 8640, 11200, 6900][i]!,
  caloriesBurned: [380, 490, 310, 405, 345, 448, 275][i]!,
  activeMinutes:  [42, 58, 30, 47, 35, 55, 25][i]!,
  distanceKm:     [7.2, 9.3, 5.9, 7.6, 6.5, 8.4, 5.2][i]!,
  sleepHours:     [7.5, 6.8, 8.0, 7.2, 6.5, 7.8, 7.0][i],
  sleepQuality:   ["GOOD", "FAIR", "EXCELLENT", "GOOD", "FAIR", "GOOD", "FAIR"][i],
  waterMl:        [2100, 1800, 2400, 1950, 1700, 2200, 1600][i]!,
}));

export const SEED_WELLNESS_VITALS: WellnessVital[] = [
  { id: "vit-001", vitalType: "BLOOD_PRESSURE",  value: 128, unit: "mmHg (sys)", measuredAt: daysAgo(0),  source: "Manual", notes: "Diastolic: 80 mmHg" },
  { id: "vit-002", vitalType: "BLOOD_GLUCOSE",   value: 6.8, unit: "mmol/L",     measuredAt: daysAgo(0),  source: "Accu-Chek Guide" },
  { id: "vit-003", vitalType: "HEART_RATE",       value: 72,  unit: "bpm",        measuredAt: daysAgo(0),  source: "Omron HEM-7156T" },
  { id: "vit-004", vitalType: "WEIGHT",           value: 78.5,unit: "kg",         measuredAt: daysAgo(3),  source: "Manual" },
  { id: "vit-005", vitalType: "OXYGEN_SAT",       value: 98,  unit: "%",          measuredAt: daysAgo(1),  source: "Manual", notes: "Post-exercise" },
  { id: "vit-006", vitalType: "BLOOD_PRESSURE",   value: 132, unit: "mmHg (sys)", measuredAt: daysAgo(3),  source: "Manual", notes: "Diastolic: 82 mmHg" },
  { id: "vit-007", vitalType: "BLOOD_GLUCOSE",    value: 7.2, unit: "mmol/L",     measuredAt: daysAgo(3),  source: "Accu-Chek Guide", notes: "Post-meal (2h)" },
];

export const SEED_MOOD_ENTRIES: MoodEntry[] = Array.from({ length: 5 }, (_, i) => ({
  id:          `mood-00${i + 1}`,
  moodScore:   [4, 3, 5, 4, 3][i]!,
  energyLevel: [3, 2, 5, 4, 3][i],
  stressLevel: [2, 3, 1, 2, 4][i],
  notes:       ["Felt good after morning walk.", "Tired after long day.", "Best day this week — good sleep.", "Productive day.", "Work stress today."][i],
  loggedAt:    daysAgo(i),
}));

export const SEED_WELLNESS_CHALLENGES: WellnessChallenge[] = [
  {
    id:               "chal-001",
    title:            "10,000 Steps Daily",
    description:      "Walk 10,000 steps every day for 30 days. Track with your phone or wearable.",
    challengeType:    "STEPS",
    targetValue:      10000,
    targetUnit:       "steps/day",
    startDate:        daysAgo(15),
    endDate:          daysAgo(-15),
    participantCount: 1420,
    status:           "ACTIVE",
  },
  {
    id:               "chal-002",
    title:            "Drink 2L Water Daily",
    description:      "Stay hydrated by drinking at least 2 litres of water every day.",
    challengeType:    "HYDRATION",
    targetValue:      2000,
    targetUnit:       "ml/day",
    startDate:        daysAgo(10),
    endDate:          daysAgo(-20),
    participantCount: 870,
    status:           "ACTIVE",
  },
  {
    id:               "chal-003",
    title:            "30 Minutes Active Daily",
    description:      "Get at least 30 minutes of moderate physical activity each day.",
    challengeType:    "ACTIVITY",
    targetValue:      30,
    targetUnit:       "min/day",
    startDate:        daysAgo(0),
    endDate:          daysAgo(-30),
    participantCount: 653,
    status:           "UPCOMING",
  },
];

export const SEED_WELLNESS_GOALS: WellnessGoal[] = [
  { id: "goal-001", title: "Achieve HbA1c < 7.0%",       description: "Target glycaemic control as per diabetes care plan.", targetValue: 7.0,   currentValue: 7.2,  unit: "%",         priority: "HIGH",   targetDate: daysAgo(-90),  status: "IN_PROGRESS", createdAt: daysAgo(365) },
  { id: "goal-002", title: "Walk 10,000 steps per day",   description: "Daily step goal to support cardiovascular health.",   targetValue: 10000, currentValue: 9540, unit: "steps/day", priority: "MEDIUM", targetDate: daysAgo(-30),  status: "IN_PROGRESS", createdAt: daysAgo(90)  },
  { id: "goal-003", title: "Maintain BP < 130/80 mmHg",   description: "Blood pressure target for hypertension management.",  targetValue: 130,   currentValue: 128,  unit: "mmHg",      priority: "HIGH",   targetDate: daysAgo(-180), status: "IN_PROGRESS", createdAt: daysAgo(365) },
  { id: "goal-004", title: "Drink 2L water daily",        description: "Daily hydration goal.",                               targetValue: 2000,  currentValue: 2100, unit: "ml/day",    priority: "LOW",    targetDate: daysAgo(-7),   status: "ACHIEVED",    createdAt: daysAgo(30)  },
];

// ─── Programs ─────────────────────────────────────────────────────────────────
export const SEED_PROGRAMS: WellnessProgram[] = [
  { id: "prog-001", name: "Diabetes Self-Management",    description: "A 12-week structured programme covering nutrition, exercise, medication adherence, and blood glucose monitoring for people living with diabetes.", duration: "12 weeks", category: "Chronic Disease", status: "ACTIVE" },
  { id: "prog-002", name: "Healthy Heart Programme",     description: "Cardiovascular risk reduction through lifestyle coaching, guided exercise, and dietary counselling.", duration: "8 weeks",  category: "Cardiovascular",   status: "ACTIVE" },
  { id: "prog-003", name: "Mental Wellness & Resilience",description: "Build emotional resilience, manage stress, and improve sleep quality with guided mindfulness sessions.", duration: "6 weeks",  category: "Mental Health",     status: "ACTIVE" },
  { id: "prog-004", name: "30-Day Move More Challenge",  description: "Daily progressive exercise plan for beginners. No equipment required.", duration: "30 days", category: "Fitness", status: "ACTIVE" },
];

export const SEED_ENROLLMENTS: Enrollment[] = [
  { id: "enr-001", programId: "prog-001", programName: "Diabetes Self-Management",  progress: 58, enrolledAt: daysAgo(50), status: "ACTIVE"    },
  { id: "enr-002", programId: "prog-002", programName: "Healthy Heart Programme",   progress: 25, enrolledAt: daysAgo(14), status: "ACTIVE"    },
];

// ═══════════════════════════════════════════════════════════════════════════════
// FINANCE & COVERAGE
// ═══════════════════════════════════════════════════════════════════════════════

export const SEED_COVERAGE: CoverageInfo[] = [
  {
    id:            "cov-001",
    planName:      "PSMAS Premier",
    planType:      "Government Medical Aid",
    memberId:      "PSM-2025-00123456",
    memberNumber:  "00123456",
    status:        "ACTIVE",
    effectiveFrom: daysAgo(730),
    copay:         15,
    deductible:    0,
    outOfPocketMax:500,
    currency:      "USD",
  },
  {
    id:            "cov-002",
    planName:      "CIMAS Executive",
    planType:      "Private Medical Aid",
    memberId:      "CIM-2024-EX-78901",
    status:        "ACTIVE",
    effectiveFrom: daysAgo(365),
    copay:         10,
    deductible:    50,
    outOfPocketMax:2000,
    currency:      "USD",
  },
];

export const SEED_BALANCE: Balance = {
  amount:    1250.00,
  currency:  "ZWL",
  updatedAt: daysAgo(0),
};

export const SEED_PENDING_CHARGES: PendingCharge[] = [
  { id: "chg-001", description: "Consultation — Dr. T. Moyo",            amount: 35.00,  currency: "USD", facilityName: PARIRENYATWA, chargeDate: daysAgo(2),   status: "PENDING" },
  { id: "chg-002", description: "HbA1c + Serum Creatinine Panel",        amount: 28.50,  currency: "USD", facilityName: PARIRENYATWA, chargeDate: daysAgo(2),   status: "PENDING" },
  { id: "chg-003", description: "Urine Albumin-Creatinine Ratio (UACR)", amount: 18.00,  currency: "USD", facilityName: PARIRENYATWA, chargeDate: daysAgo(2),   status: "PENDING" },
];

export const SEED_TRANSACTIONS: Transaction[] = [
  { id: "txn-001", date: daysAgo(90),  description: "Diabetes Review — PAGH",        amount: 35.00,  currency: "USD", type: "DEBIT",  status: "SETTLED",  category: "Consultation" },
  { id: "txn-002", date: daysAgo(90),  description: "HbA1c Lab Panel",               amount: 28.50,  currency: "USD", type: "DEBIT",  status: "SETTLED",  category: "Laboratory"   },
  { id: "txn-003", date: daysAgo(91),  description: "Insurance reimbursement — PSMAS",amount: 55.00, currency: "USD", type: "CREDIT", status: "SETTLED",  category: "Insurance"    },
  { id: "txn-004", date: daysAgo(180), description: "Malaria admission — PAGH",       amount: 120.00, currency: "USD", type: "DEBIT",  status: "SETTLED",  category: "Admission"    },
  { id: "txn-005", date: daysAgo(180), description: "PSMAS claim — Malaria admission",amount: 100.00, currency: "USD", type: "CREDIT", status: "SETTLED",  category: "Insurance"    },
  { id: "txn-006", date: daysAgo(200), description: "Chest X-Ray — Mpilo",            amount: 45.00,  currency: "USD", type: "DEBIT",  status: "SETTLED",  category: "Imaging"      },
  { id: "txn-007", date: daysAgo(365), description: "Annual review — Harare City",    amount: 25.00,  currency: "USD", type: "DEBIT",  status: "SETTLED",  category: "Consultation" },
  { id: "txn-008", date: daysAgo(365), description: "CIMAS claim — Annual review",    amount: 25.00,  currency: "USD", type: "CREDIT", status: "SETTLED",  category: "Insurance"    },
];

// ─── Wallet ───────────────────────────────────────────────────────────────────
export const SEED_WALLET: HealthWallet = {
  id:       "wallet-dev-001",
  balance:  500.00,
  currency: "ZWL",
  status:   "ACTIVE",
};

export const SEED_WALLET_TRANSACTIONS: WalletTransaction[] = [
  { id: "wtxn-001", transactionType: "CREDIT", amount: 500.00, currency: "ZWL", description: "Wallet top-up via EcoCash",           reference: "ECO-20250510-4821", status: "COMPLETED", createdAt: daysAgo(10) },
  { id: "wtxn-002", transactionType: "DEBIT",  amount: 35.00,  currency: "ZWL", description: "Consultation fee — Dr. R. Chikwanda", reference: "PAY-20250415-1192", status: "COMPLETED", createdAt: daysAgo(35) },
  { id: "wtxn-003", transactionType: "CREDIT", amount: 200.00, currency: "ZWL", description: "Wallet top-up via ZIPIT",              reference: "ZIT-20250401-7763", status: "COMPLETED", createdAt: daysAgo(49) },
];
