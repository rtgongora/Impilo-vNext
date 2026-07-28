/**
 * Professional-standing alerts (Phase F4) — licence expiry/suspension, outstanding CPD,
 * assignments awaiting acceptance. Source: BFF GET /internal/v1/professional/alerts, which
 * reuses the Phase E5 ProfessionalAlertsComposer standalone (no active work context required).
 * Degrades honestly: a network/BFF failure surfaces as `isError`, exactly like the rest of
 * /professional's sections (registration, affiliations, notices) — it is never silently
 * folded into an EMPTY "no alerts" state, which would read as a false all-clear on a
 * provider's regulatory standing.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export interface ProfessionalAlertItem {
  id: string;
  kind?: string;
  source?: string;
  title?: string;
  description?: string;
  priority?: string;
  status?: string;
  due_at?: string | null;
  href?: string;
  [key: string]: unknown;
}

export interface ProfessionalAlertsAttributes {
  status: "OK" | "EMPTY" | "DEGRADED";
  items: ProfessionalAlertItem[];
  summary: Record<string, unknown>;
  note?: string | null;
  generatedAt: string;
}

interface ProfessionalAlertsResource {
  id: string;
  type: "professional-alerts";
  attributes: ProfessionalAlertsAttributes;
}

type ProfessionalAlertsResponse = ApiResponse<ProfessionalAlertsResource>;

const EMPTY: ProfessionalAlertsAttributes = {
  status: "EMPTY",
  items: [],
  summary: {},
  note: null,
  generatedAt: "",
};

export function useProfessionalAlerts() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const query = useQuery<ProfessionalAlertsAttributes>({
    queryKey: ["professional-alerts", user?.id],
    queryFn: async () => {
      const res = await apiClient.get<ProfessionalAlertsResponse>("/internal/v1/professional/alerts");
      return res.data.attributes;
    },
    enabled: isAuthenticated && !!user,
    staleTime: 60_000,
    refetchOnWindowFocus: false,
  });

  return {
    alerts: query.data ?? EMPTY,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
