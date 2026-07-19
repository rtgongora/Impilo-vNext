/**
 * Experience UI — Appointment self-check-in token (CJ11).
 *
 * Mints a short-lived, cryptographically-signed check-in token for the citizen's OWN
 * appointment (BFF resolves ownership server-side), rendered client-side as a scannable
 * QR the person presents at a facility kiosk. Fetched on demand (when the QR is opened).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface CheckinToken {
  appointmentId: string;
  token: string;
  expiresAt?: string;
}

export function useCheckinToken(appointmentId: string, enabled: boolean) {
  return useQuery({
    queryKey: ["checkin-token", appointmentId],
    queryFn: () =>
      apiClient.get<ApiResponse<CheckinToken>>(
        `/internal/v1/appointments/${appointmentId}/checkin-token`,
      ),
    enabled: enabled && !!appointmentId,
    // A check-in token is short-lived and single-purpose — don't cache it across mounts.
    staleTime: 0,
    gcTime: 0,
    retry: false,
  });
}
