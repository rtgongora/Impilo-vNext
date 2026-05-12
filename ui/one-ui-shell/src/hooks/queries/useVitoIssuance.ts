/**
 * VITO Issuance Hooks — Health OS §1 (Identity & Trust Services)
 *
 * Provides hooks for managing the identity document issuance workflow
 * (Submit, Proofing, Approval, Issuance, Delivery).
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface PagedItems<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type IssuanceState =
  | "SUBMITTED"
  | "PROOFING"
  | "APPROVED"
  | "ISSUED"
  | "DELIVERED"
  | "REJECTED";

export type IssuanceType = "FIRST_ISSUE" | "REPLACEMENT" | "RENEWAL";

export type IssuanceChannel = "ONLINE" | "IN_PERSON";

export interface IssuanceRequest {
  id: string;
  healthId: string;
  state: IssuanceState;
  type: IssuanceType;
  channel: IssuanceChannel;
  requestedAt: string;
  updatedAt: string;
  notes?: string;
}

const QUERY_KEY_PREFIX = ["vito", "issuance"];

// ── Queries ─────────────────────────────────────────────────────────

/**
 * List issuance requests in the queue, optionally filtered by state.
 */
export function useIssuanceQueue(state?: IssuanceState, page = 0, size = 20) {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "queue", state, page, size],
    queryFn: () => {
      const params = new URLSearchParams();
      if (state) params.append("state", state);
      params.append("page", page.toString());
      params.append("size", size.toString());
      return apiClient.get<ApiResponse<PagedItems<IssuanceRequest>>>(
        `/internal/v1/vito/issuance/queue?${params.toString()}`
      );
    },
  });
}

/**
 * Fetch a single issuance request by its ID.
 */
export function useIssuanceRequest(requestId: string | undefined) {
  return useQuery({
    queryKey: [...QUERY_KEY_PREFIX, "request", requestId],
    queryFn: () =>
      apiClient.get<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}`
      ),
    enabled: !!requestId,
  });
}

// ── Mutations ───────────────────────────────────────────────────────

/**
 * Submit a new issuance request for a client.
 */
export function useSubmitIssuance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: {
      healthId: string;
      type: IssuanceType;
      channel: IssuanceChannel;
      notes?: string;
    }) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        "/internal/v1/vito/issuance/submit",
        body
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
    },
  });
}

/**
 * Move a request into the PROOFING state.
 */
export function useStartProofing() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}/proofing`,
        {}
      ),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      if (res.data?.id) {
        void queryClient.invalidateQueries({
          queryKey: [...QUERY_KEY_PREFIX, "request", res.data.id],
        });
      }
    },
  });
}

/**
 * Approve an issuance request after successful proofing.
 */
export function useApproveIssuance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}/approve`,
        {}
      ),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      if (res.data?.id) {
        void queryClient.invalidateQueries({
          queryKey: [...QUERY_KEY_PREFIX, "request", res.data.id],
        });
      }
    },
  });
}

/**
 * Mark a request as ISSUED (document printed/generated).
 */
export function useIssue() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}/issue`,
        {}
      ),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      if (res.data?.id) {
        void queryClient.invalidateQueries({
          queryKey: [...QUERY_KEY_PREFIX, "request", res.data.id],
        });
      }
    },
  });
}

/**
 * Finalize the workflow by marking the document as DELIVERED to the person.
 */
export function useDeliverIssuance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}/deliver`,
        {}
      ),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      if (res.data?.id) {
        void queryClient.invalidateQueries({
          queryKey: [...QUERY_KEY_PREFIX, "request", res.data.id],
        });
      }
    },
  });
}

/**
 * Reject an issuance request with a mandatory reason.
 */
export function useRejectIssuance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, reason }: { requestId: string; reason: string }) =>
      apiClient.post<ApiResponse<IssuanceRequest>>(
        `/internal/v1/vito/issuance/${requestId}/reject`,
        { reason }
      ),
    onSuccess: (res) => {
      void queryClient.invalidateQueries({ queryKey: QUERY_KEY_PREFIX });
      if (res.data?.id) {
        void queryClient.invalidateQueries({
          queryKey: [...QUERY_KEY_PREFIX, "request", res.data.id],
        });
      }
    },
  });
}
