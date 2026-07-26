"use client";

import React, { useEffect, useMemo, useRef } from "react";
import { useInterpretClinical } from "@/hooks/queries/useGuidance";
import { vitalsToObservationInputs, type VitalValues } from "@/lib/clinical/vitals-to-observations";
import { InterpretedObservationFlags } from "./InterpretedObservationFlags";

export interface InterpretedVitalsPanelProps {
  patientId: string;
  vitals: VitalValues;
  ageYears?: number;
  sex?: string;
  encounterId?: string;
}

/**
 * Sends captured vitals to the CDS interpretation engine and renders the context-aware interpreted
 * flags. Server-side interpretation (governed reference ranges + audit trace) complementing the
 * client-side plausibility checks. An interpretation that returns no flags means the governed
 * reference ranges found nothing abnormal; an interpretation that could not run means nothing was
 * checked. Rendering nothing for both would present an outage as a normal result.
 */
export function InterpretedVitalsPanel({ patientId, vitals, ageYears, sex, encounterId }: InterpretedVitalsPanelProps) {
  const observations = useMemo(() => vitalsToObservationInputs(vitals), [vitals]);
  const interpret = useInterpretClinical();
  const { mutate, data, isError } = interpret;

  // Re-interpret whenever the (stable) observation set changes and is non-empty.
  const lastKey = useRef<string>("");
  const key = useMemo(() => JSON.stringify({ patientId, observations, ageYears, sex }), [patientId, observations, ageYears, sex]);
  useEffect(() => {
    if (!observations.length || key === lastKey.current) return;
    lastKey.current = key;
    const context: Record<string, unknown> = {};
    if (ageYears != null) context.ageYears = ageYears;
    if (sex) context.sex = sex;
    mutate({ patient_id: patientId, encounter_id: encounterId, context, observations });
  }, [key, observations, ageYears, sex, patientId, encounterId, mutate]);

  if (isError) {
    return (
      <div
        className="rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-warning-foreground"
        data-testid="interpreted-vitals-unavailable"
      >
        Vitals interpretation could not run. These vitals have not been checked against the governed
        reference ranges — the absence of flags here is not a normal result.
      </div>
    );
  }

  const result = data?.data;
  if (!result) return null;

  return (
    <InterpretedObservationFlags
      observations={result.interpreted_observations ?? []}
      traceId={result.trace_id}
    />
  );
}
