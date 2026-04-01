import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { vitoApiClient } from "@/lib/apiClient";
import type {
  IssuanceRequest,
  IssuanceStatus,
  IssuanceSubmitPayload,
  PagedResponse,
  ProofingEvent,
  DeliveryChannel,
} from "@/types/vito";

export interface ListIssuanceParams {
  page?: number;
  size?: number;
  status?: IssuanceStatus;
}

export const issuanceKeys = {
  all: ["issuance"] as const,
  list: (params: ListIssuanceParams) => [...issuanceKeys.all, "list", params] as const,
  detail: (requestId: string) => [...issuanceKeys.all, "detail", requestId] as const,
};

export function useIssuanceQueue(params: ListIssuanceParams = {}) {
  return useQuery({
    queryKey: issuanceKeys.list(params),
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (params.page !== undefined) searchParams.set("page", String(params.page));
      if (params.size !== undefined) searchParams.set("size", String(params.size));
      if (params.status) searchParams.set("status", params.status);
      const qs = searchParams.toString();
      return vitoApiClient.get<PagedResponse<IssuanceRequest>>(
        `/v1/internal/issuance/queue${qs ? `?${qs}` : ""}`
      );
    },
    staleTime: 30_000,
  });
}

export function useIssuanceDetail(requestId: string) {
  return useQuery({
    queryKey: issuanceKeys.detail(requestId),
    queryFn: () => vitoApiClient.get<IssuanceRequest>(`/v1/internal/issuance/${requestId}`),
    enabled: !!requestId,
    staleTime: 30_000,
  });
}

export function useSubmitIssuance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: IssuanceSubmitPayload) =>
      vitoApiClient.post<IssuanceRequest>("/v1/internal/issuance/submit", payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}

export function useStartProofing() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      requestId,
      event,
    }: {
      requestId: string;
      event: ProofingEvent;
    }) =>
      vitoApiClient.post<IssuanceRequest>(
        `/v1/internal/issuance/${requestId}/proofing`,
        event
      ),
    onSuccess: (_, { requestId }) => {
      qc.invalidateQueries({ queryKey: issuanceKeys.detail(requestId) });
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}

export function useApproveIssuance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, notes }: { requestId: string; notes?: string }) =>
      vitoApiClient.post<IssuanceRequest>(
        `/v1/internal/issuance/${requestId}/approve`,
        notes ? { notes } : {}
      ),
    onSuccess: (_, { requestId }) => {
      qc.invalidateQueries({ queryKey: issuanceKeys.detail(requestId) });
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}

export function useIssueIssuance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) =>
      vitoApiClient.post<IssuanceRequest>(`/v1/internal/issuance/${requestId}/issue`),
    onSuccess: (_, requestId) => {
      qc.invalidateQueries({ queryKey: issuanceKeys.detail(requestId) });
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}

export function useDeliverIssuance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      requestId,
      deliveryChannel,
      deliveredAt,
    }: {
      requestId: string;
      deliveryChannel?: DeliveryChannel;
      deliveredAt?: string;
    }) =>
      vitoApiClient.post<IssuanceRequest>(`/v1/internal/issuance/${requestId}/deliver`, {
        deliveryChannel,
        deliveredAt,
      }),
    onSuccess: (_, { requestId }) => {
      qc.invalidateQueries({ queryKey: issuanceKeys.detail(requestId) });
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}

export function useRejectIssuance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, reason }: { requestId: string; reason: string }) =>
      vitoApiClient.post<IssuanceRequest>(`/v1/internal/issuance/${requestId}/reject`, {
        reason,
      }),
    onSuccess: (_, { requestId }) => {
      qc.invalidateQueries({ queryKey: issuanceKeys.detail(requestId) });
      qc.invalidateQueries({ queryKey: issuanceKeys.all });
    },
  });
}
