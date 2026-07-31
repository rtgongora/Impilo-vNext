"use client";

import { useMemo } from "react";
import { useObservations, type ObservationRow } from "@/hooks/queries/useObservations";

/**
 * Patient eGFR from observation chart entries when recorded.
 *
 * Does not derive eGFR from creatinine in the browser — that arithmetic lives in
 * {@code libs/medicine-domain}. When no EGFR observation exists, {@code egfr} is null and callers
 * must treat renal dosing as unavailable rather than assuming normal function.
 */
export interface PatientEgfr {
  egfr: number | null;
  source: string | null;
  isLoading: boolean;
  isError: boolean;
}

function parseParameters(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null;
  if (typeof raw === "object" && !Array.isArray(raw)) {
    return raw as Record<string, unknown>;
  }
  if (typeof raw === "string") {
    try {
      const parsed = JSON.parse(raw) as unknown;
      return typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)
        ? (parsed as Record<string, unknown>)
        : null;
    } catch {
      return null;
    }
  }
  return null;
}

function extractEgfrFromRow(row: ObservationRow): number | null {
  const params = parseParameters(row.parameters);
  if (!params) return null;

  const candidates = [
    params.egfr,
    params.eGFR,
    params.EGFR,
    params.value,
  ];
  for (const candidate of candidates) {
    if (typeof candidate === "number" && Number.isFinite(candidate) && candidate > 0) {
      return candidate;
    }
    if (typeof candidate === "string") {
      const parsed = Number.parseFloat(candidate);
      if (Number.isFinite(parsed) && parsed > 0) {
        return parsed;
      }
    }
  }

  const code = String(params.code ?? params.parameter_code ?? params.parameterCode ?? "").toUpperCase();
  if (code === "EGFR" || code.includes("EGFR")) {
    const val = params.result ?? params.numeric_value ?? params.numericValue;
    if (typeof val === "number" && Number.isFinite(val) && val > 0) return val;
    if (typeof val === "string") {
      const parsed = Number.parseFloat(val);
      if (Number.isFinite(parsed) && parsed > 0) return parsed;
    }
  }
  return null;
}

export function usePatientEgfr(patientId: string | undefined): PatientEgfr {
  const observations = useObservations(patientId);

  return useMemo(() => {
    if (!patientId) {
      return { egfr: null, source: null, isLoading: false, isError: false };
    }
    if (observations.isLoading) {
      return { egfr: null, source: null, isLoading: true, isError: false };
    }
    if (observations.isError) {
      return { egfr: null, source: null, isLoading: false, isError: true };
    }

    const rows = observations.data?.data ?? [];
    for (const row of rows) {
      const chartType = String(row.chart_type ?? row.chartType ?? "").toUpperCase();
      if (chartType && !chartType.includes("LAB") && !chartType.includes("RENAL") && !chartType.includes("RFT")) {
        const egfr = extractEgfrFromRow(row);
        if (egfr != null) {
          return { egfr, source: "observation", isLoading: false, isError: false };
        }
        continue;
      }
      const egfr = extractEgfrFromRow(row);
      if (egfr != null) {
        return { egfr, source: "observation", isLoading: false, isError: false };
      }
    }

    return { egfr: null, source: null, isLoading: false, isError: false };
  }, [patientId, observations.data, observations.isError, observations.isLoading]);
}
