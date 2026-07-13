/**
 * PCT sorting desk (R7/G12) — the pre-triage step that assigns a journey a visit type
 * (context + visit type + arrival reason) between ARRIVED and TRIAGE, feeding the Cadre Engine.
 * Routes under the experience-bff sorting-desk facade.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/sorting-desk";

export interface VisitType {
  code: string;
  label: string;
  fastTrackDefault: boolean;
}

/** { OUTPATIENT: VisitType[], CASUALTY: VisitType[], ... } */
export type VisitTypeCatalog = Record<string, VisitType[]>;

function unwrap<T>(res: unknown): T | null {
  return ((res as { data?: unknown })?.data ?? null) as T | null;
}

export function useVisitTypes() {
  return useQuery<VisitTypeCatalog>({
    queryKey: ["sorting-desk", "visit-types"],
    queryFn: async () => (unwrap<VisitTypeCatalog>(await apiClient.get(`${BASE}/visit-types`)) ?? {}),
    staleTime: 5 * 60_000,
  });
}

export interface SortRecord {
  journeyId?: string;
  context?: string;
  visitType?: string;
  arrivalReason?: string;
  [key: string]: unknown;
}

export function useLatestSort(journeyId?: string) {
  return useQuery<SortRecord | null>({
    queryKey: ["sorting-desk", "latest", journeyId ?? null],
    queryFn: async () => {
      try {
        return unwrap<SortRecord>(await apiClient.get(`${BASE}/journeys/${encodeURIComponent(journeyId!)}`));
      } catch {
        return null; // 404 = not yet sorted
      }
    },
    enabled: !!journeyId,
  });
}

export function useSortJourney() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ journeyId, ...body }: { journeyId: string; context: string; visitType: string; arrivalReason?: string }) =>
      apiClient.post(`${BASE}/journeys/${encodeURIComponent(journeyId)}/sort`, body),
    onSuccess: (_r, vars) =>
      qc.invalidateQueries({ queryKey: ["sorting-desk", "latest", vars.journeyId] }),
  });
}
