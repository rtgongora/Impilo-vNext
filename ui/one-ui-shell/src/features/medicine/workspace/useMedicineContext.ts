"use client";

import { useMemo } from "react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useConditions, type ConditionResource } from "@/hooks/queries/useConditions";
import { useAllergies } from "@/hooks/queries/useAllergies";
import { useProgrammeEnrolments, type ProgrammeEnrolment } from "@/hooks/queries/usePrograms";
import { groupProblems, openEnrolments, exitedEnrolments, type ProblemGroups } from "./medicine-summary";

/**
 * Everything the adult medicine workspace needs about one patient.
 *
 * Composed on the client from endpoints that already exist, following `usePaediatricContext` —
 * deliberately, and for the reason recorded there: this repository has several examples of a UI
 * wired to a composition endpoint that was never built, where the failure returned an empty success
 * and the screen looked fine while showing nothing. A server-side aggregate is a worthwhile
 * optimisation later, not a prerequisite.
 *
 * Each block degrades independently and says so. That matters more here than almost anywhere: a
 * failed programme read must never render as "not in any programme", because for an HIV or TB
 * patient that is an affirmative clinical claim — and the wrong one.
 */
export interface MedicineContext {
  patientId: string;
  patientName: string | null;
  /** Programmes the person is still in, most recent first. */
  openProgrammes: ProgrammeEnrolment[];
  /** Programmes that ended — surfaced, never silently dropped (lost-to-follow-up is a finding). */
  exitedProgrammes: ProgrammeEnrolment[];
  problems: ProblemGroups;
  allergyLabels: string[];
  /** Blocks that could not be read, so the screen can name what is missing rather than imply none. */
  unavailable: string[];
  isLoading: boolean;
}

export function useMedicineContext(patientId: string): MedicineContext {
  const patient = usePatient(patientId);
  const conditions = useConditions(patientId);
  const allergies = useAllergies(patientId);
  const programmes = useProgrammeEnrolments(patientId);

  return useMemo(() => {
    const attributes = (patient.data?.data as { attributes?: Record<string, unknown> } | undefined)?.attributes;
    const firstName = typeof attributes?.firstName === "string" ? attributes.firstName : "";
    const lastName = typeof attributes?.lastName === "string" ? attributes.lastName : "";
    const patientName = [firstName, lastName].filter(Boolean).join(" ") || null;

    const enrolments = programmes.data?.data ?? [];
    const conditionList: ConditionResource[] = conditions.data?.data ?? [];

    const allergyLabels = (allergies.data?.data ?? [])
      .map((entry) => {
        const attrs = (entry as { attributes?: Record<string, unknown> }).attributes;
        const allergen = attrs?.allergen ?? attrs?.substance;
        return typeof allergen === "string" ? allergen : null;
      })
      .filter((label): label is string => Boolean(label));

    // Named in clinician's terms, because these strings are shown verbatim on the screen.
    const unavailable: string[] = [];
    if (patient.isError) unavailable.push("patient record");
    if (programmes.isError) unavailable.push("care programmes");
    if (conditions.isError) unavailable.push("problem list");
    if (allergies.isError) unavailable.push("allergies");

    return {
      patientId,
      patientName,
      openProgrammes: openEnrolments(enrolments),
      exitedProgrammes: exitedEnrolments(enrolments),
      problems: groupProblems(conditionList),
      allergyLabels,
      unavailable,
      isLoading:
        patient.isLoading || conditions.isLoading || allergies.isLoading || programmes.isLoading,
    };
  }, [
    patientId,
    patient.data,
    patient.isError,
    patient.isLoading,
    conditions.data,
    conditions.isError,
    conditions.isLoading,
    allergies.data,
    allergies.isError,
    allergies.isLoading,
    programmes.data,
    programmes.isError,
    programmes.isLoading,
  ]);
}
