import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { GrowthAttribution } from "./useGrowth";

/**
 * Reference curves for the growth standard a child is actually read against.
 *
 * Which standard that is — WHO 2006 at age, or Fenton 2013 at postmenstrual age for an
 * infant born preterm — is decided server-side from gestational age and returned here.
 * The client must not choose: a preterm baby plotted on the term chart looks severely
 * malnourished while growing exactly as expected.
 */

export type GrowthAxis = "age_days" | "postmenstrual_weeks";

type RawCurvePoint = { x: number; value: number };

type RawReferenceCurves = {
  indicator: string;
  unit: string;
  standard: string | null;
  standard_label?: string | null;
  axis?: GrowthAxis;
  attribution?: {
    label: string | null;
    required_chart_label: string | null;
    citation: string | null;
    licence: string | null;
    approval_status: string | null;
    content_version: string | null;
  } | null;
  current_axis_position?: number | null;
  curves: { z: number; points: RawCurvePoint[] }[];
  unavailable_reason?: string | null;
};

export type GrowthReferenceCurves = {
  indicator: string;
  unit: string;
  standard: string | null;
  standardLabel: string | null;
  axis: GrowthAxis;
  attribution: GrowthAttribution;
  currentAxisPosition: number | null;
  curves: { z: number; points: RawCurvePoint[] }[];
  /** Why there are no curves. Present exactly when `curves` is empty. */
  unavailableReason: string | null;
};

export function useGrowthReferenceCurves(patientId: string, indicator = "weight_for_age") {
  return useQuery<GrowthReferenceCurves>({
    queryKey: ["growth-reference-curves", { patientId, indicator }],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<RawReferenceCurves>>(
        `/internal/v1/growth/reference-curves?patient_id=${encodeURIComponent(patientId)}&indicator=${encodeURIComponent(indicator)}`,
      );
      const d = response.data;
      return {
        indicator: d.indicator,
        unit: d.unit,
        standard: d.standard,
        standardLabel: d.standard_label ?? null,
        axis: d.axis ?? "age_days",
        attribution: d.attribution
          ? {
              label: d.attribution.label,
              requiredChartLabel: d.attribution.required_chart_label,
              citation: d.attribution.citation,
              licence: d.attribution.licence,
              approvalStatus: d.attribution.approval_status,
              contentVersion: d.attribution.content_version,
            }
          : null,
        currentAxisPosition: d.current_axis_position ?? null,
        curves: d.curves ?? [],
        unavailableReason: d.unavailable_reason ?? null,
      };
    },
    enabled: !!patientId,
  });
}
