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
 * client-side plausibility checks; degrades silently when no flags are returned.
 */
export function InterpretedVitalsPanel({ patientId, vitals, ageYears, sex, encounterId }: InterpretedVitalsPanelProps) {
  const observations = useMemo(() => vitalsToObservationInputs(vitals), [vitals]);
  const interpret = useInterpretClinical();
  const { mutate, data } = interpret;

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

  const result = data?.data;
  if (!result) return null;

  return (
    <InterpretedObservationFlags
      observations={result.interpreted_observations ?? []}
      traceId={result.trace_id}
    />
  );
}
