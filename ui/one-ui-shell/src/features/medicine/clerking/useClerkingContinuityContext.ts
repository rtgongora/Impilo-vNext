"use client";

import { useMemo } from "react";
import { useConditions, type ConditionResource } from "@/hooks/queries/useConditions";
import { useAllergies } from "@/hooks/queries/useAllergies";
import { useImmunizations } from "@/hooks/queries/useImmunizations";
import { usePrescriptions } from "@/hooks/queries/usePharmacy";
import {
  useAdvanceDirectives,
  useFamilyHistory,
  useFunctionalAssessments,
  usePatientProcedures,
} from "@/hooks/queries/useStructuredHistory";
import { useCarePlans, usePatientGoalsFromCarePlans } from "@/hooks/queries/useCareContinuity";
import { useAdmissions } from "@/hooks/queries/useInpatient";
import { useMedicalEpisodes } from "@/hooks/queries/useMedicalEpisodes";
import { useMaternitySummary } from "@/hooks/queries/useMaternitySummary";
import {
  useMedicationReconciliationItems,
  useMedicationReconciliations,
} from "@/hooks/queries/useMedicationReconciliation";

/**
 * Client-side compose for §6 INTEGRATE clerking continuity.
 * Each block degrades independently — failed read ≠ empty history.
 */
export interface ClerkingContinuityContext {
  patientId: string;
  activeProblems: ConditionResource[];
  resolvedProblems: ConditionResource[];
  episodes: ReturnType<typeof useMedicalEpisodes>["data"] extends { data?: infer D } ? NonNullable<D> : never;
  medications: unknown[];
  adherenceFindings: { display: string; finding: string; patientSays: string | null }[];
  allergies: unknown[];
  immunizations: unknown[];
  procedures: unknown[];
  functional: unknown[];
  family: unknown[];
  carePlans: unknown[];
  goals: unknown[];
  directives: unknown[];
  admissions: unknown[];
  maternity: Record<string, unknown> | null;
  unavailable: string[];
  isLoading: boolean;
}

function isActiveStatus(status: unknown): boolean {
  const u = String(status ?? "").toUpperCase();
  return u === "ACTIVE" || u === "PENDING";
}

export function useClerkingContinuityContext(patientId: string): ClerkingContinuityContext {
  const conditions = useConditions(patientId);
  const episodes = useMedicalEpisodes(patientId);
  const prescriptions = usePrescriptions({ patientId });
  const reconciliations = useMedicationReconciliations(patientId);
  const latestRecId = reconciliations.data?.data?.[0]?.reconciliation_id ?? null;
  const recItems = useMedicationReconciliationItems(latestRecId);
  const allergies = useAllergies(patientId);
  const immunizations = useImmunizations(patientId);
  const procedures = usePatientProcedures(patientId);
  const functional = useFunctionalAssessments(patientId);
  const family = useFamilyHistory(patientId);
  const carePlans = useCarePlans(patientId);
  const goals = usePatientGoalsFromCarePlans(patientId);
  const directives = useAdvanceDirectives(patientId);
  const admissions = useAdmissions(patientId);
  const maternity = useMaternitySummary(patientId);

  return useMemo(() => {
    const conditionList: ConditionResource[] = conditions.data?.data ?? [];
    const activeProblems = conditionList.filter((c) =>
      isActiveStatus(c.attributes.clinicalStatus)
    );
    const resolvedProblems = conditionList.filter(
      (c) => String(c.attributes.clinicalStatus ?? "").toUpperCase() === "RESOLVED"
    );

    const meds = (prescriptions.data?.data ?? []).filter((m) =>
      isActiveStatus((m as { attributes?: { status?: string } }).attributes?.status)
    );

    const adherenceFindings = (recItems.data?.data ?? [])
      .filter((item) => item.finding === "NOT_TAKING" || Boolean(item.patient_says))
      .map((item) => ({
        display: item.display,
        finding: item.finding ?? "NOTED",
        patientSays: item.patient_says,
      }));

    const unavailable: string[] = [];
    if (conditions.isError) unavailable.push("problem list");
    if (episodes.isError) unavailable.push("medical episodes");
    if (prescriptions.isError) unavailable.push("medications");
    if (reconciliations.isError || recItems.isError) unavailable.push("medication reconciliation");
    if (allergies.isError) unavailable.push("allergies");
    if (immunizations.isError) unavailable.push("immunizations");
    if (procedures.isError) unavailable.push("surgical history");
    if (functional.isError) unavailable.push("functional status");
    if (family.isError) unavailable.push("family history");
    if (carePlans.isError) unavailable.push("care plans");
    if (goals.isError) unavailable.push("goals");
    if (directives.isError) unavailable.push("advance directives");
    if (admissions.isError) unavailable.push("admissions");
    if (maternity.isError) unavailable.push("pregnancy / maternity context");

    return {
      patientId,
      activeProblems,
      resolvedProblems,
      episodes: episodes.data?.data ?? [],
      medications: meds,
      adherenceFindings,
      allergies: allergies.data?.data ?? [],
      immunizations: immunizations.data?.data ?? [],
      procedures: procedures.data?.data ?? [],
      functional: functional.data?.data ?? [],
      family: family.data?.data ?? [],
      carePlans: carePlans.data?.data ?? [],
      goals: goals.data?.data ?? [],
      directives: directives.data?.data ?? [],
      admissions: Array.isArray(admissions.data?.data)
        ? (admissions.data.data as unknown[])
        : [],
      maternity: (maternity.data?.data as Record<string, unknown> | undefined) ?? null,
      unavailable,
      isLoading:
        conditions.isLoading ||
        episodes.isLoading ||
        prescriptions.isLoading ||
        allergies.isLoading ||
        immunizations.isLoading ||
        procedures.isLoading ||
        functional.isLoading ||
        family.isLoading ||
        carePlans.isLoading ||
        directives.isLoading ||
        admissions.isLoading,
    };
  }, [
    patientId,
    conditions.data,
    conditions.isError,
    conditions.isLoading,
    episodes.data,
    episodes.isError,
    episodes.isLoading,
    prescriptions.data,
    prescriptions.isError,
    prescriptions.isLoading,
    reconciliations.isError,
    recItems.data,
    recItems.isError,
    allergies.data,
    allergies.isError,
    allergies.isLoading,
    immunizations.data,
    immunizations.isError,
    immunizations.isLoading,
    procedures.data,
    procedures.isError,
    procedures.isLoading,
    functional.data,
    functional.isError,
    functional.isLoading,
    family.data,
    family.isError,
    family.isLoading,
    carePlans.data,
    carePlans.isError,
    carePlans.isLoading,
    goals.data,
    goals.isError,
    directives.data,
    directives.isError,
    directives.isLoading,
    admissions.data,
    admissions.isError,
    admissions.isLoading,
    maternity.data,
    maternity.isError,
  ]);
}
