/**
 * Telemedicine lifecycle analytics — BFF proxy to analytics-pipeline-service.
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface TelemedicineSlaAggregates {
  facilityId?: string;
  from?: string;
  to?: string;
  eventsAccepted?: number;
  eventsByType?: Record<string, number>;
  generatedAt?: string;
}

export function useTelemedicineSlaAggregates(params?: {
  facilityId?: string;
  from?: string;
  to?: string;
}) {
  const searchParams = new URLSearchParams();
  if (params?.facilityId) searchParams.set("facility_id", params.facilityId);
  if (params?.from) searchParams.set("from", params.from);
  if (params?.to) searchParams.set("to", params.to);
  const qs = searchParams.toString();

  return useQuery({
    queryKey: ["telemedicine-analytics-sla", params ?? null],
    queryFn: () =>
      apiClient.get<ApiResponse<TelemedicineSlaAggregates>>(
        `/internal/v1/telemedicine/sla${qs ? `?${qs}` : ""}`,
      ),
  });
}

export function useIngestTelemedicineAnalyticsEvent() {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      apiClient.post<ApiResponse<Record<string, unknown>>>(
        "/internal/v1/telemedicine/events",
        body,
      ),
  });
}
