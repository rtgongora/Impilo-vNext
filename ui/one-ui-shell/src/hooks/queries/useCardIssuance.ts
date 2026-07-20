/**
 * Citizen fresh-physical card request (B3). Submits a request on the governed
 * PORTAL issuance channel (auto-routes to PROOFING) and tracks its state.
 */
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface IssuanceRequest {
  id: number;
  state: string;
  type?: string;
  channel?: string;
}

// The request itself is a one-shot POST issued inline by RequestPhysicalCard
// (direct apiClient in try/catch); this hook polls its resulting state.

/** Poll a fresh-card issuance request's state (PROOFING → APPROVED → ISSUED → DELIVERED). */
export function useIssuanceStatus(requestId: number | null) {
  return useQuery({
    queryKey: ["card", "issuance", requestId],
    queryFn: () =>
      apiClient.get<{ data: IssuanceRequest }>(
        `/internal/v1/wallet/cards/issuance/${requestId}`,
      ),
    enabled: requestId != null,
  });
}
