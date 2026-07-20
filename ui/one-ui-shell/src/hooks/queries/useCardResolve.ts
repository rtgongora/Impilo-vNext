/**
 * Card present-to-verify (B0/B5 seam). Resolves a presented SMART card — a card
 * number or a scanned/pasted vito QR token — to its identity + status via the BFF
 * (→ VITO POST /v1/cards/resolve). Fail-closed: an unresolvable presentation is a
 * clean rejection, never a fabricated identity.
 */
import { useMutation } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export interface CardResolution {
  healthId: string;
  cpid: string;
  cardNumber?: string;
  cardStatus?: string;
  cardForm?: string;
  resolvedVia?: string;
}

export interface CardPresentation {
  cardNumber?: string;
  qrToken?: string;
  cardAssertion?: string;
}

/** Resolve a presented card to {healthId, cpid, cardStatus}. */
export function useResolveCardPresentation() {
  return useMutation({
    mutationFn: (presentation: CardPresentation) =>
      apiClient.post<{ data: CardResolution }>(
        "/internal/v1/wallet/cards/resolve-presentation",
        presentation,
      ),
  });
}
