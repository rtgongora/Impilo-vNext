/**
 * COSTA intel — thin TanStack Query hooks for read-only finance signals.
 *
 * <p>Used by the canonical COSTA hub page ({@code /finance/costa}). The
 * upstream is reached via Experience BFF {@code /internal/v1/finance/costa-intel/**},
 * which proxies to the costing-engine ({@code /api/costa/**}). All requests
 * pick up v1.2 trust headers via {@link apiClient}.</p>
 *
 * <p>The tariff-lists query key intentionally matches the one used by the
 * {@code /finance/tariffs} page so the cache is shared and a refresh in one
 * place is visible in the other.</p>
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { CostaTariffListRow } from "shared-ui";
import { apiClient } from "@/lib/api-client";

type TariffListEnvelope = { data: CostaTariffListRow[] };

export function useCostaIntelTariffLists() {
  return useQuery({
    queryKey: ["finance", "costa-intel", "tariff-lists", "full"],
    queryFn: async () => {
      const res = await apiClient.get<TariffListEnvelope>(
        "/internal/v1/finance/costa-intel/tariff-lists",
      );
      return Array.isArray(res.data) ? res.data : [];
    },
  });
}

function costEventsUrl(encounterId?: string | null, patientCpid?: string | null) {
  const params = new URLSearchParams();
  if (encounterId && encounterId.trim()) params.set("encounter_id", encounterId.trim());
  if (patientCpid && patientCpid.trim()) params.set("patient_cpid", patientCpid.trim());
  const qs = params.toString();
  return `/internal/v1/finance/costa-intel/cost-events${qs ? `?${qs}` : ""}`;
}

type CostEventsEnvelope = { data: unknown } | unknown[] | null;

export function useCostaIntelCostEvents(
  encounterId?: string | null,
  patientCpid?: string | null,
) {
  const hasScope = Boolean(
    (encounterId && encounterId.trim()) || (patientCpid && patientCpid.trim()),
  );
  return useQuery({
    queryKey: [
      "finance",
      "costa-intel",
      "cost-events",
      encounterId ?? null,
      patientCpid ?? null,
    ],
    enabled: hasScope,
    queryFn: async () => {
      const res = await apiClient.get<CostEventsEnvelope>(costEventsUrl(encounterId, patientCpid));
      if (Array.isArray(res)) return res;
      if (res && typeof res === "object" && "data" in res && Array.isArray((res as { data: unknown }).data)) {
        return (res as { data: unknown[] }).data;
      }
      return [];
    },
  });
}

/**
 * Phase 5 controlled action:
 * POST /internal/v1/finance/costa-intel/invoices/from-cost-estimate
 * (BFF passthrough to COSTA /api/costa/invoices/from-cost-estimate).
 */
export function useCostaIntelInvoiceFromEstimate() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, Record<string, unknown>>({
    mutationFn: (body) =>
      apiClient.post<unknown>("/internal/v1/finance/costa-intel/invoices/from-cost-estimate", body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["finance", "costa-intel"] });
    },
  });
}
