import type { AgeBand, ClinicalFormPatientContext, Sex } from "./types";
import type { PatientResource } from "@/hooks/queries/usePatients";

function monthsBetween(from: Date, to: Date): number {
  let months = (to.getFullYear() - from.getFullYear()) * 12 + (to.getMonth() - from.getMonth());
  if (to.getDate() < from.getDate()) months -= 1;
  return Math.max(0, months);
}

export function ageBandFromMonths(ageMonths: number): AgeBand {
  if (ageMonths < 1) return "NEONATAL";
  if (ageMonths < 24) return "INFANT";
  if (ageMonths < 144) return "CHILD"; // <12y
  if (ageMonths < 216) return "ADOLESCENT"; // <18y
  if (ageMonths < 780) return "ADULT"; // <65y
  return "OLDER_ADULT";
}

function normalizeSex(raw: string | undefined): Sex {
  const g = (raw ?? "").toUpperCase();
  if (g === "MALE" || g === "M") return "MALE";
  if (g === "FEMALE" || g === "F") return "FEMALE";
  if (g === "OTHER" || g === "O") return "OTHER";
  return "UNKNOWN";
}

/**
 * Build runtime patient slice for DAK visibility from canonical patient resource.
 */
export function clinicalFormPatientContextFromPatient(
  patient: PatientResource,
  extras?: Partial<Pick<ClinicalFormPatientContext, "pregnant" | "programmes" | "knownConditionCodes">>,
): ClinicalFormPatientContext {
  const dob = new Date(patient.attributes.dateOfBirth);
  const now = new Date();
  const ageMonths = monthsBetween(dob, now);
  return {
    patientId: patient.id,
    ageMonths,
    ageBand: ageBandFromMonths(ageMonths),
    sex: normalizeSex(patient.attributes.gender),
    pregnant: extras?.pregnant ?? null,
    programmes: extras?.programmes ?? [],
    knownConditionCodes: extras?.knownConditionCodes ?? [],
  };
}
