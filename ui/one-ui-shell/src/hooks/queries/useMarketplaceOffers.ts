/**
 * OF-B7 — patient offer-comparison + selection hooks over the BFF marketplace
 * proxy (`/internal/v1/marketplace/**` → msika-flow `/v1/marketplace-requests`).
 *
 * Envelope note: msika-flow replies with its own ApiResponse shape
 * `{success, data, error:{code,message,status}, correlationId}` which the BFF
 * forwards verbatim — so these hooks unwrap `data` (not the BFF `{data,meta}`
 * shape) via `unwrapFlowData`.
 *
 * Idempotency: `select`/`select-split` are RC-1 idempotent commits. The caller
 * supplies ONE key per selection attempt (created when the confirm dialog
 * opens) so a retried tap replays instead of double-committing.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { OfferView, RequestView } from "@/lib/marketplace/offer-comparison";

// msika-flow ApiResponse envelope (forwarded verbatim by the BFF)
export interface FlowEnvelope<T> {
  success: boolean;
  data: T | null;
  error?: { code: string; message: string; status: number } | null;
  correlationId?: string | null;
}

export interface SelectionView {
  selectionId: string;
  requestId: string;
  offerId: string;
  status: string;
  outcomeCode: string;
  committed: boolean;
  replayed: boolean;
  prescriptionClaimId: string | null;
  committedAt: string | null;
  stepLog: unknown;
  financials: unknown;
  paymentIntentId: string | null;
  paymentStatus: string | null;
  shortfallAmount: number | null;
  shortfallCurrency: string | null;
  paStatus: string | null;
  paReference: string | null;
  splitGroupId: string | null;
  orosOrderId: string | null;
  collectionMode: string | null;
  collectionWindowStart: string | null;
  collectionWindowEnd: string | null;
}

export interface SplitMemberView {
  offerId: string;
  selectionId: string | null;
  status: string | null;
  outcomeCode: string;
  committed: boolean;
  replayed: boolean;
}

export interface SplitSelectionView {
  splitGroupId: string;
  requestId: string;
  /** COMMITTED | PARTIAL_COMMIT | AWAITING_PAYMENT | FAILED — honest rollup. */
  outcome: string;
  coverage: {
    complete: boolean;
    coveredLineRefs: string[];
    uncoveredLineRefs: string[];
    underSuppliedLineRefs: string[];
  };
  members: SplitMemberView[];
}

export interface PathwayView {
  requestId: string;
  fulfilmentPathway: string | null;
  deliveryDetailsPresent: boolean;
}

export interface PathwayElectionPayload {
  pathway: "PICKUP" | "DELIVERY";
  deliveryName?: string;
  deliveryPhone?: string;
  deliveryAddress?: string;
  deliveryLat?: number;
  deliveryLng?: number;
}

export const marketplaceOfferKeys = {
  request: (requestId: string) => ["marketplace-request", requestId] as const,
  offers: (requestId: string) => ["marketplace-request-offers", requestId] as const,
};

export function useMarketplaceRequest(requestId: string | undefined) {
  return useQuery<FlowEnvelope<RequestView>>({
    queryKey: marketplaceOfferKeys.request(requestId ?? "unknown"),
    enabled: !!requestId,
    queryFn: () =>
      apiClient.get<FlowEnvelope<RequestView>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId!)}`,
      ),
  });
}

/**
 * Ranked comparison listing. Refetches once a minute so TTL expiry and late
 * offers surface without a manual reload (the true TTL is still rendered
 * factually per offer — the poll is freshness, not pressure).
 */
export function useMarketplaceOffers(requestId: string | undefined) {
  return useQuery<FlowEnvelope<OfferView[]>>({
    queryKey: marketplaceOfferKeys.offers(requestId ?? "unknown"),
    enabled: !!requestId,
    refetchInterval: 60_000,
    queryFn: () =>
      apiClient.get<FlowEnvelope<OfferView[]>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId!)}/offers`,
      ),
  });
}

/** RC-1 idempotent single-offer commit — one caller-owned key per attempt. */
export function useSelectOffer() {
  const queryClient = useQueryClient();
  return useMutation<
    FlowEnvelope<SelectionView>,
    unknown,
    { requestId: string; offerId: string; idempotencyKey: string }
  >({
    mutationFn: ({ requestId, offerId, idempotencyKey }) =>
      apiClient.post<FlowEnvelope<SelectionView>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId)}/select`,
        { offerId },
        { extraHeaders: { "Idempotency-Key": idempotencyKey } },
      ),
    onSuccess: (_data, { requestId }) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.offers(requestId) });
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.request(requestId) });
    },
  });
}

/** OF-B14 split commit — honest per-member outcomes; committed members STAND. */
export function useSelectSplit() {
  const queryClient = useQueryClient();
  return useMutation<
    FlowEnvelope<SplitSelectionView>,
    unknown,
    { requestId: string; offerIds: string[]; idempotencyKey: string }
  >({
    mutationFn: ({ requestId, offerIds, idempotencyKey }) =>
      apiClient.post<FlowEnvelope<SplitSelectionView>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId)}/select-split`,
        { offerIds },
        { extraHeaders: { "Idempotency-Key": idempotencyKey } },
      ),
    onSuccess: (_data, { requestId }) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.offers(requestId) });
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.request(requestId) });
    },
  });
}

/** OF-B17 fulfilment-pathway election (PICKUP | DELIVERY + delivery-minimum block). */
export function useElectPathway() {
  const queryClient = useQueryClient();
  return useMutation<FlowEnvelope<PathwayView>, unknown, { requestId: string } & PathwayElectionPayload>({
    mutationFn: ({ requestId, ...payload }) =>
      apiClient.put<FlowEnvelope<PathwayView>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId)}/fulfilment-pathway`,
        payload,
      ),
    onSuccess: (_data, { requestId }) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.request(requestId) });
    },
  });
}

export function useCancelMarketplaceRequest() {
  const queryClient = useQueryClient();
  return useMutation<FlowEnvelope<RequestView>, unknown, { requestId: string; reason?: string }>({
    mutationFn: ({ requestId, reason }) =>
      apiClient.post<FlowEnvelope<RequestView>>(
        `/internal/v1/marketplace/requests/${encodeURIComponent(requestId)}/cancel`,
        reason ? { reason } : {},
      ),
    onSuccess: (_data, { requestId }) => {
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.request(requestId) });
      void queryClient.invalidateQueries({ queryKey: marketplaceOfferKeys.offers(requestId) });
    },
  });
}
