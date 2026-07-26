"use client";

import { useMemo } from "react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useGrowth } from "@/hooks/queries/useGrowth";
import { useImmunizations } from "@/hooks/queries/useImmunizations";
import { useAllergies } from "@/hooks/queries/useAllergies";
import { ageFactsFor, recommendJourney, type AgeFacts, type PresentationContext } from "./paediatric-age";
import { computeDueToday, sortDueItems, type DueItem } from "./due-today";

/**
 * Everything the paediatric workspace needs about one child.
 *
 * Composed on the client from endpoints that already exist and already work, rather than
 * from a new backend aggregate. That is a deliberate choice: this repository has three
 * separate examples of a UI wired to a composition endpoint that was never built, where the
 * failure returned an empty success and the screen looked fine while showing nothing. A
 * server-side aggregate is a worthwhile optimisation later — it is not a prerequisite, and
 * building the screen against it first would have meant shipping a fourth such vertical.
 *
 * Each block degrades independently and says so, so a failed immunisation query cannot make
 * the whole workspace look empty.
 */
export interface PaediatricContext {
  patientId: string;
  patientName: string | null;
  age: AgeFacts;
  dosingWeightKg: number | null;
  dosingWeightMeasuredAt: string | null;
  latestWeightForAgeZ: number | null;
  allergyLabels: string[];
  dueItems: DueItem[];
  journey: ReturnType<typeof recommendJourney>;
  /** Blocks that could not be loaded, so the screen can say what is missing. */
  unavailable: string[];
  isLoading: boolean;
}

export function usePaediatricContext(
  patientId: string,
  presentation: PresentationContext = "ROUTINE",
): PaediatricContext {
  const patient = usePatient(patientId);
  const growth = useGrowth(patientId);
  const immunizations = useImmunizations(patientId);
  const allergies = useAllergies(patientId);

  return useMemo(() => {
    const attributes = (patient.data?.data as { attributes?: Record<string, unknown> } | undefined)?.attributes;
    const dateOfBirth = typeof attributes?.dateOfBirth === "string" ? attributes.dateOfBirth : null;
    const firstName = typeof attributes?.firstName === "string" ? attributes.firstName : "";
    const lastName = typeof attributes?.lastName === "string" ? attributes.lastName : "";
    const patientName = [firstName, lastName].filter(Boolean).join(" ") || null;

    const age = ageFactsFor(dateOfBirth);

    const measurements = growth.data ?? [];
    // Most recent first: the dosing weight must be the newest one, never whichever the
    // server happened to return first.
    const ordered = [...measurements].sort(
      (a, b) => new Date(b.measuredAt).getTime() - new Date(a.measuredAt).getTime(),
    );
    const latestWithWeight = ordered.find((m) => typeof m.weightKg === "number");
    const latestScored = ordered.find((m) => m.derived?.weightForAge);

    const allergyLabels = (allergies.data?.data ?? [])
      .map((entry) => {
        const attrs = (entry as { attributes?: Record<string, unknown> }).attributes;
        const allergen = attrs?.allergen ?? attrs?.substance;
        return typeof allergen === "string" ? allergen : null;
      })
      .filter((label): label is string => Boolean(label));

    const immunisationCount = (immunizations.data?.data ?? []).length;

    const unavailable: string[] = [];
    if (patient.isError) unavailable.push("patient record");
    if (growth.isError) unavailable.push("growth measurements");
    if (immunizations.isError) unavailable.push("immunisations");
    if (allergies.isError) unavailable.push("allergies");

    const dueItems = sortDueItems(
      computeDueToday({
        patientId,
        age,
        lastMeasuredAt: ordered[0]?.measuredAt ?? null,
        hasAnyGrowthMeasurement: ordered.length > 0,
        latestWeightForAgeZ: latestScored?.derived?.weightForAge?.zScore ?? null,
        immunisationCount,
      }),
    );

    return {
      patientId,
      patientName,
      age,
      dosingWeightKg: latestWithWeight?.weightKg ?? null,
      dosingWeightMeasuredAt: latestWithWeight?.measuredAt ?? null,
      latestWeightForAgeZ: latestScored?.derived?.weightForAge?.zScore ?? null,
      allergyLabels,
      dueItems,
      journey: recommendJourney(age, presentation),
      unavailable,
      isLoading:
        patient.isLoading || growth.isLoading || immunizations.isLoading || allergies.isLoading,
    };
  }, [
    patientId,
    presentation,
    patient.data,
    patient.isError,
    patient.isLoading,
    growth.data,
    growth.isError,
    growth.isLoading,
    immunizations.data,
    immunizations.isError,
    immunizations.isLoading,
    allergies.data,
    allergies.isError,
    allergies.isLoading,
  ]);
}
