"use client";

import { useMemo } from "react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useActiveAdmission, useWardRounds } from "@/hooks/queries/useInpatient";
import { useConditions, type ConditionResource } from "@/hooks/queries/useConditions";
import { useProgrammeEnrolments, type ProgrammeEnrolment } from "@/hooks/queries/usePrograms";
import { useAllergies } from "@/hooks/queries/useAllergies";
import { usePrescriptions, type PrescriptionResource } from "@/hooks/queries/usePharmacy";
import { useConsultations, type Consultation } from "@/hooks/queries/useConsultations";
import { useFluidBalance, type FluidBalanceRow, type FluidBalanceSummary } from "@/hooks/queries/useFluidBalance";
import { groupProblems, openEnrolments, type ProblemGroups } from "../workspace/medicine-summary";

/**
 * What a doctor needs in front of them on a medical ward round.
 *
 * Composed from services that each own their half of the truth, and deliberately does not mint a
 * third: **PCT owns the admission decision, inpatient-service owns the physical census** (the split
 * is written into pct V018 and inpatient V013/V014). This reads both and joins nothing.
 *
 * Wave 5.3 extends the compose to brief.md §13's eighteen ward concerns. Items without an existing
 * read hook are left honestly uncomposed — never shown as empty.
 */

/** Distinguishes "this patient is not admitted" from "we could not read whether they are". */
export type AdmissionState = "ADMITTED" | "NOT_ADMITTED" | "UNKNOWN";

export interface ActiveProblemSeverity {
  id: string;
  conditionName: string;
  severity: string;
}

export interface MedicalWardContext {
  patientId: string;
  facilityId: string | null;
  admissionState: AdmissionState;
  admissionRef: string | null;
  admission: Record<string, unknown> | null;
  rounds: unknown[];
  problems: ProblemGroups;
  /** Active problems with severity from the condition record (same SoR as the problem list). */
  activeSeverity: ActiveProblemSeverity[];
  openProgrammes: ProgrammeEnrolment[];
  allergyLabels: string[];
  prescriptions: PrescriptionResource[];
  consultations: Consultation[];
  fluidBalance: {
    entries: FluidBalanceRow[];
    summary: FluidBalanceSummary | null;
    date: string;
  };
  unavailable: string[];
  isLoading: boolean;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function todayIsoDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function activeSeverityFromConditions(conditions: ConditionResource[]): ActiveProblemSeverity[] {
  const { active } = groupProblems(conditions);
  return active.map((c) => ({
    id: c.id,
    conditionName: c.attributes.conditionName,
    severity: c.attributes.severity || "—",
  }));
}

export function useMedicalWardContext(patientId: string): MedicalWardContext {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? null;
  const fluidDate = todayIsoDate();

  const admission = useActiveAdmission(patientId, facilityId ?? undefined);
  const conditions = useConditions(patientId);
  const programmes = useProgrammeEnrolments(patientId);
  const allergies = useAllergies(patientId);
  const prescriptions = usePrescriptions({ patientId });
  const consultations = useConsultations(patientId);
  const fluidBalance = useFluidBalance(patientId, fluidDate);

  const admissionRecord = asRecord(admission.data?.data);
  const admissionRef =
    admissionRecord == null
      ? null
      : String(admissionRecord.admissionRef ?? admissionRecord.admission_ref ?? admissionRecord.id ?? "") || null;

  const rounds = useWardRounds(admissionRef);

  return useMemo(() => {
    const unavailable: string[] = [];
    if (admission.isError) unavailable.push("admission");
    if (conditions.isError) unavailable.push("problem list");
    if (programmes.isError) unavailable.push("care programmes");
    if (rounds.isError) unavailable.push("ward rounds");
    if (allergies.isError) unavailable.push("allergies");
    if (prescriptions.isError) unavailable.push("current medicines");
    if (consultations.isError) unavailable.push("consultations");
    if (fluidBalance.isError) unavailable.push("fluid balance");

    let admissionState: AdmissionState;
    if (admission.isError || !facilityId) {
      admissionState = "UNKNOWN";
    } else if (admissionRecord && admissionRef) {
      admissionState = "ADMITTED";
    } else if (admission.isLoading) {
      admissionState = "UNKNOWN";
    } else {
      admissionState = "NOT_ADMITTED";
    }

    const roundList = Array.isArray(rounds.data?.data) ? (rounds.data?.data as unknown[]) : [];
    const conditionList = (conditions.data?.data ?? []) as ConditionResource[];

    const allergyLabels = (allergies.data?.data ?? [])
      .map((entry) => {
        const attrs = (entry as { attributes?: Record<string, unknown> }).attributes;
        const allergen = attrs?.allergen ?? attrs?.substance;
        const severity = attrs?.severity;
        const label = typeof allergen === "string" ? allergen : null;
        if (!label) return null;
        return typeof severity === "string" && severity ? `${label} (${severity})` : label;
      })
      .filter((label): label is string => Boolean(label));

    return {
      patientId,
      facilityId,
      admissionState,
      admissionRef,
      admission: admissionRecord,
      rounds: roundList,
      problems: groupProblems(conditionList),
      activeSeverity: conditions.isError ? [] : activeSeverityFromConditions(conditionList),
      openProgrammes: openEnrolments(programmes.data?.data ?? []),
      allergyLabels,
      prescriptions: (prescriptions.data?.data ?? []) as PrescriptionResource[],
      consultations: (consultations.data?.data ?? []) as Consultation[],
      fluidBalance: {
        entries: fluidBalance.data?.data ?? [],
        summary: fluidBalance.data?.summary ?? null,
        date: fluidDate,
      },
      unavailable,
      isLoading:
        admission.isLoading ||
        conditions.isLoading ||
        programmes.isLoading ||
        allergies.isLoading ||
        prescriptions.isLoading ||
        consultations.isLoading ||
        fluidBalance.isLoading,
    };
  }, [
    patientId,
    facilityId,
    fluidDate,
    admissionRecord,
    admissionRef,
    admission.isError,
    admission.isLoading,
    conditions.data,
    conditions.isError,
    conditions.isLoading,
    programmes.data,
    programmes.isError,
    programmes.isLoading,
    rounds.data,
    rounds.isError,
    allergies.data,
    allergies.isError,
    allergies.isLoading,
    prescriptions.data,
    prescriptions.isError,
    prescriptions.isLoading,
    consultations.data,
    consultations.isError,
    consultations.isLoading,
    fluidBalance.data,
    fluidBalance.isError,
    fluidBalance.isLoading,
  ]);
}
