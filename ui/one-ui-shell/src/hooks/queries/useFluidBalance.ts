/**
 * Fluid balance (intake/output) — CareEmergencyInpatientController.
 * GET  /internal/v1/fluid-balance?patientId=&date=YYYY-MM-DD
 * POST /internal/v1/fluid-balance
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type FluidBalanceRow = Record<string, unknown> & {
  id?: string;
  entry_type?: string;
  category?: string;
  volume_ml?: number;
  description?: string | null;
  recorded_at?: string;
  recorded_by?: string | null;
  record_date?: string;
};

export type FluidBalanceSummary = {
  totalIntake: number;
  totalOutput: number;
  balance: number;
};

type FluidBalanceResponse = {
  data: FluidBalanceRow[];
  summary: FluidBalanceSummary;
};

export function useFluidBalance(patientId: string | undefined, date: string) {
  return useQuery<FluidBalanceResponse>({
    queryKey: ["fluid-balance", patientId, date],
    queryFn: async () => {
      const qs = new URLSearchParams({ patientId: patientId! });
      if (date) qs.set("date", date);
      return apiClient.get<FluidBalanceResponse>(`/internal/v1/fluid-balance?${qs.toString()}`);
    },
    enabled: Boolean(patientId) && Boolean(date),
    staleTime: 20_000,
  });
}

export function useRecordFluid() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      patientId: string;
      encounterId?: string | null;
      entryType: "INTAKE" | "OUTPUT";
      category: string;
      volumeMl: number;
      description?: string;
      recordedBy: string;
    }) =>
      apiClient.post<{ data: { id: string } }>("/internal/v1/fluid-balance", {
        patientId: body.patientId,
        encounterId: body.encounterId ?? null,
        entryType: body.entryType,
        category: body.category,
        volumeMl: body.volumeMl,
        description: body.description ?? "",
        recordedBy: body.recordedBy,
      }),
    onSuccess: (_, v) => {
      queryClient.invalidateQueries({ queryKey: ["fluid-balance", v.patientId] });
    },
  });
}
