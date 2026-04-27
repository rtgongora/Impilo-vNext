/**
 * COSTA service access decisions via BFF {@code /internal/v1/finance/service-access-decisions}.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

type BffEnvelope<T> = { data: T; meta?: { correlation_id: string } };

export type ServiceAccessDecisionRow = {
  service_access_decision_id: string;
  access_status: string;
  billing_timing_mode?: string | null;
  encounter_id?: string | null;
  patient_cpid?: string | null;
  decision_timestamp?: string | null;
  estimated_cost?: number | string | null;
  requested_service_name?: string | null;
};

function listUrl(encounterId: string | null | undefined) {
  const q = encounterId?.trim()
    ? `?encounter_id=${encodeURIComponent(encounterId.trim())}`
    : "";
  return `/internal/v1/finance/service-access-decisions${q}`;
}

export function useServiceAccessDecisionsList(encounterId: string | null | undefined) {
  return useQuery<ServiceAccessDecisionRow[]>({
    queryKey: ["finance", "service-access-decisions", encounterId ?? ""],
    queryFn: async () => {
      const res = await apiClient.get<BffEnvelope<ServiceAccessDecisionRow[]>>(listUrl(encounterId));
      const raw = res.data;
      return Array.isArray(raw) ? raw : [];
    },
  });
}

export function useRegisterServiceAccessDecision() {
  const qc = useQueryClient();
  return useMutation<ServiceAccessDecisionRow, unknown, Record<string, unknown>>({
    mutationFn: async (body) => {
      const res = await apiClient.post<BffEnvelope<ServiceAccessDecisionRow>>(
        "/internal/v1/finance/service-access-decisions",
        body,
      );
      if (!res.data || typeof res.data !== "object") {
        throw new Error("Unexpected response from service access decision register");
      }
      return res.data as ServiceAccessDecisionRow;
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "service-access-decisions"] });
    },
  });
}
