/**
 * Adult medicine BFF clients — same internal paths as web, mobile trust headers via apiClient.
 *
 * No mobile-specific /internal/v1/mobile/provider/medicine/* surface exists; these hit the
 * shared experience BFF composition endpoints the web medicine workspace uses.
 */

import { apiClient } from "@impilo/mobile-api-client";

export interface ConditionResource {
  id: string;
  type: string;
  attributes: {
    patientId: string;
    conditionName: string;
    icdCode: string | null;
    clinicalStatus: string;
    severity?: string;
    category?: string;
  };
}

export interface AllergyResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export interface ProgrammeEnrolment {
  enrolment_id: string;
  subject_cpid: string;
  programme: string;
  status: string;
  exit_reason: string | null;
  enrolled_on: string | null;
  exit_on?: string | null;
  programme_number?: string | null;
  managing_facility_id?: string | null;
  confidential?: boolean;
}

export interface ProgrammeAlert {
  code: string;
  name: string;
  severity: string;
  message: string;
  required_action: string;
  referral_required: boolean;
}

export interface ProgrammeGuidance {
  assessed: boolean;
  referral_required: boolean;
  incomplete: boolean;
  alerts: ProgrammeAlert[];
  not_assessed: string[];
  content_version: string | null;
  fact_provenance?: Record<string, string>;
  derivation_note?: string;
}

export interface VisitAttestation {
  attestation_id?: string;
  section?: string;
  stance?: string;
  problem_id?: string;
  notes?: string;
  attested_at?: string;
  [key: string]: unknown;
}

export type ControlStatus =
  | "CONTROLLED"
  | "PARTIALLY_CONTROLLED"
  | "NOT_CONTROLLED"
  | "TARGET_NOT_SET";

export interface RegisterEntry {
  enrolment_id: string;
  subject_cpid: string;
  programme_number: string | null;
  status: string;
  enrolled_on: string;
  managing_facility_id: string | null;
  control_status: ControlStatus | null;
  control_assessed_on: string | null;
  individualised_target: string | null;
}

export interface ChronicRegister {
  programme: string;
  facility_id: string | null;
  entries: RegisterEntry[];
  total: number;
  never_assessed: number;
  control_status_null_means_never_assessed: string;
}

/** Governed adult-medicine CDS topics (kebab-case wire form). */
export const MEDICINE_CDS_TOPICS = [
  { topic: "cvd-risk", label: "Cardiovascular risk" },
  { topic: "deprescribing", label: "Deprescribing" },
  { topic: "procedure-indication", label: "Procedure indication" },
  { topic: "icope", label: "Geriatrics (ICOPE)" },
  { topic: "mhgap", label: "Mental health (mhGAP)" },
  { topic: "antimicrobial-stewardship", label: "Antimicrobial stewardship" },
  { topic: "palliative", label: "Palliative care" },
  { topic: "oncology", label: "Oncology early diagnosis" },
] as const;

export const CHRONIC_REGISTERS = [
  { programme: "HYPERTENSION", label: "Hypertension" },
  { programme: "DIABETES", label: "Diabetes" },
  { programme: "CHRONIC_KIDNEY_DISEASE", label: "Chronic kidney disease" },
  { programme: "CHRONIC_RESPIRATORY", label: "Asthma and COPD" },
] as const;

export async function fetchConditions(patientId: string): Promise<ConditionResource[]> {
  const response = await apiClient.get<{ data: ConditionResource[] }>(
    `/internal/v1/conditions?patient_id=${encodeURIComponent(patientId)}`,
  );
  return response.data.data ?? [];
}

export async function fetchAllergies(patientId: string): Promise<AllergyResource[]> {
  const response = await apiClient.get<{ data: AllergyResource[] }>(
    `/internal/v1/allergies?patient_id=${encodeURIComponent(patientId)}`,
  );
  return response.data.data ?? [];
}

export async function fetchProgrammeEnrolments(patientId: string): Promise<ProgrammeEnrolment[]> {
  const response = await apiClient.get<{ data: ProgrammeEnrolment[] }>(
    `/internal/v1/programmes?patient_id=${encodeURIComponent(patientId)}&open_only=false`,
  );
  return response.data.data ?? [];
}

export async function evaluateMedicineCds(
  topic: string,
  facts: Record<string, unknown>,
  subjectCpid?: string,
): Promise<ProgrammeGuidance> {
  const qs = subjectCpid ? `?subject_cpid=${encodeURIComponent(subjectCpid)}` : "";
  const response = await apiClient.post<{ data: ProgrammeGuidance }>(
    `/internal/v1/medicine/cds/${encodeURIComponent(topic)}/evaluate${qs}`,
    facts,
  );
  return response.data.data;
}

export async function fetchVisitAttestations(
  subjectCpid: string,
  encounterId: string,
): Promise<VisitAttestation[]> {
  const response = await apiClient.get<{ data: VisitAttestation[] }>(
    `/internal/v1/clerking/visit-attestations?subject_cpid=${encodeURIComponent(subjectCpid)}&encounter_id=${encodeURIComponent(encounterId)}`,
  );
  const raw = response.data.data ?? [];
  return Array.isArray(raw) ? raw : [];
}

export async function fetchChronicRegister(
  programme: string,
  facilityId: string | null,
): Promise<ChronicRegister> {
  const qs = facilityId ? `&facility_id=${encodeURIComponent(facilityId)}` : "";
  const response = await apiClient.get<{ data: ChronicRegister }>(
    `/internal/v1/programmes/register?programme=${encodeURIComponent(programme)}${qs}`,
  );
  return response.data.data;
}
