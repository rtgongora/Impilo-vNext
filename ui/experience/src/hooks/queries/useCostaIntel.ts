/**
 * COSTA /api/costa via Experience BFF {@code /internal/v1/finance/costa-intel/*}.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type Envelope<T> = { data: T };

export type CostaIntelJson = unknown;

export function useCostaTariffLibraries() {
  return useQuery<CostaIntelJson>({
    queryKey: ["finance", "costa-intel", "libraries"],
    queryFn: async () => {
      const res = await apiClient.get<Envelope<CostaIntelJson>>("/internal/v1/finance/costa-intel/tariff-libraries");
      return res.data;
    },
  });
}

export function useCostaTariffLists() {
  return useQuery<CostaIntelJson>({
    queryKey: ["finance", "costa-intel", "tariff-lists"],
    queryFn: async () => {
      const res = await apiClient.get<Envelope<CostaIntelJson>>("/internal/v1/finance/costa-intel/tariff-lists");
      return res.data;
    },
  });
}

export function useCostaCostEvents(encounterId: string | undefined, patientCpid: string | undefined) {
  const q = new URLSearchParams();
  if (encounterId) q.set("encounter_id", encounterId);
  if (patientCpid) q.set("patient_cpid", patientCpid);
  const suffix = q.toString();

  return useQuery<CostaIntelJson>({
    queryKey: ["finance", "costa-intel", "cost-events", encounterId, patientCpid],
    queryFn: async () => {
      const res = await apiClient.get<Envelope<CostaIntelJson>>(
        `/internal/v1/finance/costa-intel/cost-events${suffix ? `?${suffix}` : ""}`,
      );
      return res.data;
    },
    enabled: Boolean(encounterId?.trim() || patientCpid?.trim()),
  });
}

export function useCostaCostEstimate() {
  const qc = useQueryClient();
  return useMutation<CostaIntelJson, unknown, Record<string, unknown>>({
    mutationFn: async (body) => {
      const res = await apiClient.post<Envelope<CostaIntelJson>>(
        "/internal/v1/finance/costa-intel/cost-estimate",
        body,
      );
      return res.data;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "costa-intel"] });
    },
  });
}
