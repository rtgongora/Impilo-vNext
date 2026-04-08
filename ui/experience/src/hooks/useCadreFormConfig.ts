/**
 * Cadre-Aware Form Configuration Hook
 *
 * Determines form complexity based on:
 * 1. Clinical cadre (Doctor/Nurse/CHW)
 * 2. Visit type (Emergency/ANC/Chronic/General)
 * 3. Acuity level (Red/Orange/Yellow/Green)
 */

import { useState } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";

// ── Medical Practitioners ──
// doctor, specialist, intern_doctor, registrar, consultant, dentist, dental_therapist
// ── Nursing & Midwifery ──
// nurse, nurse_practitioner, enrolled_nurse, midwife
// ── Allied Health Professionals ──
// physiotherapist, occupational_therapist, speech_therapist, dietitian,
// psychologist, social_worker, audiologist, optometrist, podiatrist,
// biokinetician, orthotist_prosthetist, respiratory_therapist, radiotherapist
// ── Diagnostic & Technical ──
// radiographer, sonographer, lab_tech, pharmacist, pharmacy_tech
// ── Emergency ──
// paramedic, emt
// ── Dental ──
// oral_hygienist
// ── Community & Public Health ──
// chw, env_health, health_promoter
// ── Administrative ──
// admin, health_info_officer, receptionist

export type ClinicalCadre =
  // Medical practitioners
  | "doctor" | "specialist" | "intern_doctor" | "registrar" | "consultant"
  | "dentist" | "dental_therapist"
  // Nursing & Midwifery
  | "nurse" | "nurse_practitioner" | "enrolled_nurse" | "midwife"
  // Allied Health
  | "physiotherapist" | "occupational_therapist" | "speech_therapist"
  | "dietitian" | "psychologist" | "social_worker" | "audiologist"
  | "optometrist" | "podiatrist" | "biokinetician" | "orthotist_prosthetist"
  | "respiratory_therapist" | "radiotherapist"
  // Diagnostic & Technical
  | "radiographer" | "sonographer" | "lab_tech" | "pharmacist" | "pharmacy_tech"
  // Emergency
  | "paramedic" | "emt"
  // Dental
  | "oral_hygienist"
  // Community & Public Health
  | "chw" | "env_health" | "health_promoter"
  // Administrative
  | "admin" | "health_info_officer" | "receptionist";

export type FormComplexity = "comprehensive" | "focused" | "simplified";
export type VisitType = "emergency" | "anc" | "pnc" | "chronic" | "general" | "surgical" | "pediatric" | "psychiatric" | "rehab" | "dental" | "mental_health" | "nutrition" | "occupational_health";
export type AcuityLevel = "red" | "orange" | "yellow" | "green";

// ── Dev Override State (singleton) ──────────────
let _devCadreOverride: ClinicalCadre | null = null;
let _devVisitOverride: VisitType | null = null;
let _devAcuityOverride: AcuityLevel | null = null;
let _devListeners: (() => void)[] = [];

function notifyDevListeners() {
  _devListeners.forEach((fn) => fn());
}

export function setDevCadreOverride(cadre: ClinicalCadre | null) {
  _devCadreOverride = cadre;
  notifyDevListeners();
}
export function setDevVisitOverride(visit: VisitType | null) {
  _devVisitOverride = visit;
  notifyDevListeners();
}
export function setDevAcuityOverride(acuity: AcuityLevel | null) {
  _devAcuityOverride = acuity;
  notifyDevListeners();
}
export function getDevOverrides() {
  return { cadre: _devCadreOverride, visit: _devVisitOverride, acuity: _devAcuityOverride };
}
export function useDevOverrideListener() {
  const [, setTick] = useState(0);
  useState(() => {
    const fn = () => setTick((t) => t + 1);
    _devListeners.push(fn);
    return () => {
      _devListeners = _devListeners.filter((l) => l !== fn);
    };
  });
}

export interface CadreFormConfig {
  cadre: ClinicalCadre;
  complexity: FormComplexity;
  visitType: VisitType;
  acuity: AcuityLevel;

  // History sections visibility
  history: {
    showSOCRATES: boolean;
    showFullROS: boolean;
    showObsGyn: boolean;
    showPsychiatric: boolean;
    showFamilyHistory: boolean;
    showSocialHistory: boolean;
    showSurgicalHistory: boolean;
    showDrugHistory: boolean;
    showAllergies: boolean;
    showFunctionalStatus: boolean;
    showDangerSigns: boolean;
    showReferDecision: boolean;
    hpiFormat: "socrates" | "focused" | "danger-signs-only";
    pmhFormat: "coded-icd10" | "checklist" | "yes-no-screen";
  };

  // Exam sections visibility
  exam: {
    showFullSystemsExam: boolean;
    showGCS: boolean;
    showCranialNerves: boolean;
    showDetailedNeuro: boolean;
    showDetailedAbdo: boolean;
    showMusculoskeletal: boolean;
    showDermatological: boolean;
    showENT: boolean;
    showObstetricExam: boolean;
    showPediatricGrowth: boolean;
    showMentalStateExam: boolean;
    showFocusedAssessment: boolean;
    examFormat: "full-systems" | "focused-nursing" | "danger-sign-screen";
  };

  // Labels and prompts
  labels: {
    historyTabLabel: string;
    examTabLabel: string;
    saveLabel: string;
    guidanceText: string;
  };
}

const CADRE_MAP: Record<string, ClinicalCadre> = {
  doctor: "doctor", specialist: "specialist", intern_doctor: "intern_doctor",
  registrar: "registrar", consultant: "consultant",
  dentist: "dentist", dental_therapist: "dental_therapist",
  nurse: "nurse", nurse_practitioner: "nurse_practitioner",
  enrolled_nurse: "enrolled_nurse", midwife: "midwife",
  physiotherapist: "physiotherapist", occupational_therapist: "occupational_therapist",
  speech_therapist: "speech_therapist", dietitian: "dietitian",
  psychologist: "psychologist", social_worker: "social_worker",
  audiologist: "audiologist", optometrist: "optometrist",
  podiatrist: "podiatrist", biokinetician: "biokinetician",
  orthotist_prosthetist: "orthotist_prosthetist",
  respiratory_therapist: "respiratory_therapist", radiotherapist: "radiotherapist",
  radiographer: "radiographer", sonographer: "sonographer",
  lab_tech: "lab_tech", pharmacist: "pharmacist", pharmacy_tech: "pharmacy_tech",
  paramedic: "paramedic", emt: "emt",
  oral_hygienist: "oral_hygienist",
  chw: "chw", env_health: "env_health", health_promoter: "health_promoter",
  admin: "admin", health_info_officer: "health_info_officer", receptionist: "receptionist",
  patient: "chw", radiologist: "radiographer",
};

function getCadreComplexity(cadre: ClinicalCadre, _acuity: AcuityLevel): FormComplexity {
  const comprehensive: ClinicalCadre[] = [
    "doctor", "specialist", "consultant", "registrar", "intern_doctor",
    "dentist", "nurse_practitioner",
  ];
  const focused: ClinicalCadre[] = [
    "nurse", "enrolled_nurse", "midwife", "pharmacist",
    "physiotherapist", "occupational_therapist", "speech_therapist",
    "dietitian", "psychologist", "social_worker", "audiologist",
    "optometrist", "podiatrist", "biokinetician", "orthotist_prosthetist",
    "respiratory_therapist", "radiotherapist", "radiographer", "sonographer",
    "paramedic", "dental_therapist", "oral_hygienist",
  ];
  if (comprehensive.includes(cadre)) return "comprehensive";
  if (focused.includes(cadre)) return "focused";
  return "simplified";
}

export function useCadreFormConfig(
  visitType: VisitType = "general",
  acuity: AcuityLevel = "green"
): CadreFormConfig {
  const { user } = useAuthStore();
  const role = user?.roles?.[0] ?? "";
  useDevOverrideListener();

  const overrides = getDevOverrides();
  const effectiveVisitType = overrides.visit || visitType;
  const effectiveAcuity = overrides.acuity || acuity;

  const profileRole = (role as string) || "doctor";
  const cadre: ClinicalCadre = overrides.cadre || CADRE_MAP[profileRole] || "doctor";
  const complexity = getCadreComplexity(cadre, effectiveAcuity);

  const isComprehensive = complexity === "comprehensive";
  const isFocused = complexity === "focused";
  const isSimplified = complexity === "simplified";

  const isEmergency = effectiveVisitType === "emergency";
  const isANC = effectiveVisitType === "anc" || effectiveVisitType === "pnc";
  const isPsych = effectiveVisitType === "psychiatric";
  const isPediatric = effectiveVisitType === "pediatric";
  const isHighAcuity = effectiveAcuity === "red" || effectiveAcuity === "orange";

  return {
    cadre,
    complexity,
    visitType: effectiveVisitType,
    acuity: effectiveAcuity,

    history: {
      showSOCRATES: isComprehensive,
      showFullROS: isComprehensive && !isHighAcuity,
      showObsGyn: (isComprehensive || isFocused) && (isANC || visitType === "general"),
      showPsychiatric: isComprehensive && isPsych,
      showFamilyHistory: isComprehensive,
      showSocialHistory: isComprehensive || isFocused,
      showSurgicalHistory: isComprehensive,
      showDrugHistory: isComprehensive || isFocused,
      showAllergies: true,
      showFunctionalStatus: isComprehensive && !isEmergency,
      showDangerSigns: isFocused || isSimplified,
      showReferDecision: isSimplified,
      hpiFormat: isComprehensive ? "socrates" : isFocused ? "focused" : "danger-signs-only",
      pmhFormat: isComprehensive ? "coded-icd10" : isFocused ? "checklist" : "yes-no-screen",
    },

    exam: {
      showFullSystemsExam: isComprehensive,
      showGCS: isComprehensive || (isFocused && isHighAcuity),
      showCranialNerves: isComprehensive,
      showDetailedNeuro: isComprehensive,
      showDetailedAbdo: isComprehensive,
      showMusculoskeletal: isComprehensive,
      showDermatological: isComprehensive,
      showENT: isComprehensive,
      showObstetricExam: (isComprehensive || isFocused) && isANC,
      showPediatricGrowth: (isComprehensive || isFocused) && isPediatric,
      showMentalStateExam: isComprehensive && isPsych,
      showFocusedAssessment: isFocused || isSimplified,
      examFormat: isComprehensive ? "full-systems" : isFocused ? "focused-nursing" : "danger-sign-screen",
    },

    labels: {
      historyTabLabel: isSimplified ? "Screening" : isFocused ? "Nursing Hx" : "History",
      examTabLabel: isSimplified ? "Check" : isFocused ? "Assessment" : "Exam",
      saveLabel: isSimplified ? "Submit & Refer" : isFocused ? "Save Assessment" : "Save & Sign",
      guidanceText: isSimplified
        ? "Screen for danger signs and decide: Refer or Reassure"
        : isFocused
        ? "Complete focused nursing assessment"
        : "Document comprehensive clinical assessment",
    },
  };
}
