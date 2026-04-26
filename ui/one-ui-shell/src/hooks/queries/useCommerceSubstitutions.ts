/**
 * Substitution inbox and decisions via Experience BFF.
 *
 * - GET  /internal/v1/commerce/substitutions
 * - POST /internal/v1/commerce/rx/{orderId}/substitution/approve
 * - POST /internal/v1/commerce/rx/{orderId}/substitution/reject
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

export type CommerceSubstitutionJson = unknown;

export function useCommerceSubstitutions(params: { page?: number; size?: number }, enabled: boolean) {
  const query = new URLSearchParams();
  if (params.page != null) query.set("page", String(params.page));
  if (params.size != null) query.set("size", String(params.size));
  const suffix = query.toString();

  return useQuery<CommerceSubstitutionJson>({
    queryKey: ["commerce", "substitutions", params.page ?? "", params.size ?? ""],
    queryFn: () =>
      apiClient.get<CommerceSubstitutionJson>(
        `/internal/v1/commerce/substitutions${suffix ? `?${suffix}` : ""}`,
      ),
    enabled,
  });
}

function invalidateSubstitutions(qc: ReturnType<typeof useQueryClient>) {
  void qc.invalidateQueries({ queryKey: ["commerce", "substitutions"] });
}

export function useApproveCommerceSubstitution() {
  const qc = useQueryClient();
  return useMutation<CommerceSubstitutionJson, unknown, { orderId: string; body: unknown }>({
    mutationFn: ({ orderId, body }) =>
      apiClient.post<CommerceSubstitutionJson>(
        `/internal/v1/commerce/rx/${encodeURIComponent(orderId)}/substitution/approve`,
        body,
      ),
    onSuccess: () => invalidateSubstitutions(qc),
  });
}

export function useRejectCommerceSubstitution() {
  const qc = useQueryClient();
  return useMutation<CommerceSubstitutionJson, unknown, { orderId: string; body: unknown }>({
    mutationFn: ({ orderId, body }) =>
      apiClient.post<CommerceSubstitutionJson>(
        `/internal/v1/commerce/rx/${encodeURIComponent(orderId)}/substitution/reject`,
        body,
      ),
    onSuccess: () => invalidateSubstitutions(qc),
  });
}
