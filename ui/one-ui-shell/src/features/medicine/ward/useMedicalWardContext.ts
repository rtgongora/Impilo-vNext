"use client";

import { useMemo } from "react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useActiveAdmission, useWardRounds } from "@/hooks/queries/useInpatient";
import { useConditions, type ConditionResource } from "@/hooks/queries/useConditions";
import { useProgrammeEnrolments, type ProgrammeEnrolment } from "@/hooks/queries/usePrograms";
import { groupProblems, openEnrolments, type ProblemGroups } from "../workspace/medicine-summary";

/**
 * What a doctor needs in front of them on a medical ward round.
 *
 * Composed from services that each own their half of the truth, and deliberately does not mint a
 * third: **PCT owns the admission decision, inpatient-service owns the physical census** (the split
 * is written into pct V018 and inpatient V013/V014). This reads both and joins nothing.
 *
 * The gap it closes: the existing inpatient round surfaces are ward-centred and show free-text
 * assessment/plan with **no sight of the problem list**. A medical round is a review of the
 * patient's problems, so the problems belong on the same screen as the entry being written.
 */

/** Distinguishes "this patient is not admitted" from "we could not read whether they are". */
export type AdmissionState = "ADMITTED" | "NOT_ADMITTED" | "UNKNOWN";

export interface MedicalWardContext {
  patientId: string;
  facilityId: string | null;
  admissionState: AdmissionState;
  admissionRef: string | null;
  admission: Record<string, unknown> | null;
  rounds: unknown[];
  problems: ProblemGroups;
  openProgrammes: ProgrammeEnrolment[];
  unavailable: string[];
  isLoading: boolean;
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

export function useMedicalWardContext(patientId: string): MedicalWardContext {
  const facility = useFacilityStore((s) => s.facility);
  const facilityId = facility?.id ?? null;

  const admission = useActiveAdmission(patientId, facilityId ?? undefined);
  const conditions = useConditions(patientId);
  const programmes = useProgrammeEnrolments(patientId);

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

    // Three states, not two. A failed admission read must never render as "not admitted" — that
    // would send a doctor away from a patient who is in a bed. And with no facility in context we
    // have not asked the question at all, which is also not an answer.
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

    return {
      patientId,
      facilityId,
      admissionState,
      admissionRef,
      admission: admissionRecord,
      rounds: roundList,
      problems: groupProblems((conditions.data?.data ?? []) as ConditionResource[]),
      openProgrammes: openEnrolments(programmes.data?.data ?? []),
      unavailable,
      isLoading: admission.isLoading || conditions.isLoading || programmes.isLoading,
    };
  }, [
    patientId, facilityId, admissionRecord, admissionRef,
    admission.isError, admission.isLoading,
    conditions.data, conditions.isError, conditions.isLoading,
    programmes.data, programmes.isError, programmes.isLoading,
    rounds.data, rounds.isError,
  ]);
}
