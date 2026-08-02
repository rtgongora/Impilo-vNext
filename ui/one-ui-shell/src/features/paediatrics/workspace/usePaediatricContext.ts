"use client";

import { useMemo } from "react";
import { usePatient } from "@/hooks/queries/usePatients";
import { useGrowth } from "@/hooks/queries/useGrowth";
import { useImmunizations } from "@/hooks/queries/useImmunizations";
import { useAllergies } from "@/hooks/queries/useAllergies";
import { useImamEpisodes } from "@/hooks/queries/useImam";
import { useImmunisationForecast } from "@/hooks/queries/usePaediatricDecisionSupport";
import { programmeLabel } from "@/features/paediatrics/imam/imam-presentation";
import { forecastSummary } from "@/features/paediatrics/immunisation/forecast-presentation";
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
  const imam = useImamEpisodes(patientId);

  // Derived ahead of the forecast call, because the schedule engine is age-routed and stateless:
  // it needs the date of birth and the doses already on the record, both of which are PCT's.
  const patientAttributes = (patient.data?.data as { attributes?: Record<string, unknown> } | undefined)
    ?.attributes;
  const patientDateOfBirth =
    typeof patientAttributes?.dateOfBirth === "string" ? patientAttributes.dateOfBirth : null;
  const patientSex = typeof patientAttributes?.sex === "string" ? patientAttributes.sex : null;

  const recordedDoses = (immunizations.data?.data ?? []).map((entry) => ({
    vaccineCode: entry.attributes?.vaccineCode ?? entry.attributes?.vaccineName ?? null,
    doseNumber: entry.attributes?.doseNumber ?? null,
    administeredAt: entry.attributes?.administeredAt ?? null,
    status: entry.attributes?.status ?? null,
  }));

  // Held back until the dose history has actually loaded. A forecast over an unread history would
  // report the child as due for doses they have already had — a confident wrong answer, which is
  // worse than the honest "could not be established" the workspace shows instead.
  const forecast = useImmunisationForecast(
    {
      patientId,
      dateOfBirth: patientDateOfBirth,
      sex: patientSex,
      doses: recordedDoses,
    },
    !immunizations.isLoading && !immunizations.isError,
  );

  return useMemo(() => {
    const attributes = patientAttributes;
    const dateOfBirth = patientDateOfBirth;
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
    if (imam.isError) unavailable.push("nutrition treatment");
    if (forecast.isError) unavailable.push("immunisation forecast");

    const activeImam = (imam.data ?? []).find((episode) => episode.status === "ACTIVE") ?? null;

    // Three distinct states, and the workspace must not merge them. No date of birth is a records
    // gap the clinician can close; an unreachable engine is not; and a forecast that ran and found
    // nothing is the only one of the three that may show as nothing outstanding.
    const forecastUnavailable = forecast.isError || immunizations.isError || !dateOfBirth;
    const forecastUnavailableReason = !dateOfBirth
      ? "This child's date of birth is not recorded, so no dose can be forecast — every gate in the schedule is a function of age. Record it to see what is due."
      : immunizations.isError
        ? "The doses already given could not be read, so no forecast was run. A forecast over an unread history would report doses the child may already have had."
        : null;

    const dueItems = sortDueItems(
      computeDueToday({
        patientId,
        age,
        lastMeasuredAt: ordered[0]?.measuredAt ?? null,
        hasAnyGrowthMeasurement: ordered.length > 0,
        latestWeightForAgeZ: latestScored?.derived?.weightForAge?.zScore ?? null,
        immunisationCount,
        immunisation: {
          summary: forecast.data ? forecastSummary(forecast.data) : null,
          evaluated: !!forecast.data && !forecastUnavailable,
          unavailable: forecastUnavailable,
          unavailableReason: forecastUnavailableReason,
        },
        imam: {
          enrolled: !!activeImam,
          programme: activeImam ? programmeLabel(activeImam.programme) : null,
          nextReviewDue: activeImam?.nextReviewDue ?? null,
          unavailable: imam.isError,
        },
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
        patient.isLoading ||
        growth.isLoading ||
        immunizations.isLoading ||
        allergies.isLoading ||
        imam.isLoading ||
        // Included deliberately: while the schedule is still running there is no immunisation row,
        // and a due-today panel rendered without one reads as "nothing outstanding".
        forecast.isLoading,
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
    imam.data,
    imam.isError,
    imam.isLoading,
    patientAttributes,
    patientDateOfBirth,
    forecast.data,
    forecast.isError,
    forecast.isLoading,
  ]);
}
