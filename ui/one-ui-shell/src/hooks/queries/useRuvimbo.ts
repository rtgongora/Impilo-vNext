/**
 * Ruvimbo (Coverage / health-financing) operations hooks — TanStack Query over the
 * experience BFF. Covers Waves 1-4: payer registry, plan hierarchy, membership
 * verification, benefits, eligibility v2, authorisations, referrals, liability
 * estimation, claims v2 + adjudication, coordination of benefits, employer rosters,
 * fraud, capitation, and the claims switch / payer gateway.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/coverage";

/** Envelope-tolerant list unwrap ({data:[...]} | [...] | {data:{...}} → rows). */
export function ruvimboRows(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  const obj = payload as { data?: unknown };
  if (obj && Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
  return [];
}

/** Unwrap a single object ({data:{...}} | {...}). */
export function ruvimboObject(payload: unknown): Record<string, unknown> {
  const obj = payload as { data?: unknown };
  if (obj && obj.data && typeof obj.data === "object" && !Array.isArray(obj.data)) {
    return obj.data as Record<string, unknown>;
  }
  return (payload && typeof payload === "object" ? payload : {}) as Record<string, unknown>;
}

function listQuery(key: unknown[], path: string, enabled = true) {
  return useQuery({
    queryKey: key,
    queryFn: async () => ruvimboRows(await apiClient.get<unknown>(path)),
    enabled,
  });
}

function objectQuery(key: unknown[], path: string, enabled = true) {
  return useQuery({
    queryKey: key,
    queryFn: async () => ruvimboObject(await apiClient.get<unknown>(path)),
    enabled,
  });
}

function useWrite(invalidate: unknown[][]) {
  const qc = useQueryClient();
  return {
    onSuccess: () => invalidate.forEach((k) => qc.invalidateQueries({ queryKey: k })),
  };
}

// ── Payers ────────────────────────────────────────────────────────────────
export function usePayers(status?: string) {
  const q = status ? `?status=${encodeURIComponent(status)}` : "";
  return listQuery(["ruvimbo-payers", status ?? null], `${BASE}/payers${q}`);
}
export function useCreatePayer() {
  const w = useWrite([["ruvimbo-payers"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/payers`, body), ...w });
}
export function useSuspendPayer() {
  const w = useWrite([["ruvimbo-payers"]]);
  return useMutation({ mutationFn: (id: string) => apiClient.post(`${BASE}/payers/${id}/suspend`, {}), ...w });
}
export function useReactivatePayer() {
  const w = useWrite([["ruvimbo-payers"]]);
  return useMutation({ mutationFn: (id: string) => apiClient.post(`${BASE}/payers/${id}/reactivate`, {}), ...w });
}

// ── Plan hierarchy ──────────────────────────────────────────────────────────
export function useSchemes(payerId?: string) {
  return listQuery(["ruvimbo-schemes", payerId ?? null], `${BASE}/payers/${payerId}/schemes`, Boolean(payerId));
}
export function useCreateScheme(payerId: string) {
  const w = useWrite([["ruvimbo-schemes", payerId]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/payers/${payerId}/schemes`, body), ...w });
}
export function useProducts(schemeId?: string) {
  return listQuery(["ruvimbo-products", schemeId ?? null], `${BASE}/schemes/${schemeId}/products`, Boolean(schemeId));
}
export function useCreateProduct(schemeId: string) {
  const w = useWrite([["ruvimbo-products", schemeId]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/schemes/${schemeId}/products`, body), ...w });
}
export function usePlanVersions(productId?: string) {
  return listQuery(["ruvimbo-versions", productId ?? null], `${BASE}/products/${productId}/versions`, Boolean(productId));
}
export function useCreatePlanVersion(productId: string) {
  const w = useWrite([["ruvimbo-versions", productId]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/products/${productId}/versions`, body ?? {}), ...w });
}
export function usePublishPlanVersion(productId: string) {
  const w = useWrite([["ruvimbo-versions", productId]]);
  return useMutation({ mutationFn: (versionId: string) => apiClient.post(`${BASE}/plan-versions/${versionId}/publish`, {}), ...w });
}

// ── Benefits ────────────────────────────────────────────────────────────────
export function usePlanBenefits(planVersionId?: string) {
  return listQuery(["ruvimbo-benefits", planVersionId ?? null], `${BASE}/plan-versions/${planVersionId}/benefits`, Boolean(planVersionId));
}
export function useCreateBenefit(planVersionId: string) {
  const w = useWrite([["ruvimbo-benefits", planVersionId]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/plan-versions/${planVersionId}/benefits`, body), ...w });
}

// ── Membership verification ────────────────────────────────────────────────
export function usePendingVerification() {
  return listQuery(["ruvimbo-pending-verification"], `${BASE}/members/pending-verification`);
}
export function useMemberDependants(membershipId?: string) {
  return listQuery(["ruvimbo-dependants", membershipId ?? null], `${BASE}/members/${membershipId}/dependants`, Boolean(membershipId));
}
export function useVerifyMembership() {
  const w = useWrite([["ruvimbo-pending-verification"]]);
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) => apiClient.post(`${BASE}/members/${id}/verify`, body),
    ...w,
  });
}
export function useTransitionMembership() {
  const w = useWrite([["ruvimbo-pending-verification"]]);
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/members/${id}/${action}`, body ?? {}),
    ...w,
  });
}
export function useAddDependant() {
  const w = useWrite([["ruvimbo-dependants"]]);
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) => apiClient.post(`${BASE}/members/${id}/dependants`, body),
    ...w,
  });
}

// ── Eligibility v2 + tokens ─────────────────────────────────────────────────
export function useCheckEligibilityV2() {
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/eligibility/v2`, body) });
}
export function useVerifyEligibilityToken() {
  return useMutation({ mutationFn: (token: string) => apiClient.post(`${BASE}/eligibility/tokens/verify`, { token }) });
}

// ── Authorisations ──────────────────────────────────────────────────────────
export function useAuthorisations(params: { memberCpid?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params.memberCpid) qs.set("member_cpid", params.memberCpid);
  if (params.status) qs.set("status", params.status);
  const s = qs.toString();
  return listQuery(["ruvimbo-auths", params.memberCpid ?? null, params.status ?? null],
    `${BASE}/authorisations${s ? `?${s}` : ""}`, Boolean(params.memberCpid || params.status));
}
export function useAuthorisation(id?: string) {
  return objectQuery(["ruvimbo-auth", id ?? null], `${BASE}/authorisations/${id}`, Boolean(id));
}
export function useCreateAuthorisation() {
  const w = useWrite([["ruvimbo-auths"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/authorisations`, body), ...w });
}
export function useAuthorisationAction() {
  const w = useWrite([["ruvimbo-auths"], ["ruvimbo-auth"]]);
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/authorisations/${id}/${action}`, body ?? {}),
    ...w,
  });
}

// ── Referrals ───────────────────────────────────────────────────────────────
export function useReferrals(memberCpid?: string) {
  return listQuery(["ruvimbo-referrals", memberCpid ?? null], `${BASE}/referrals?member_cpid=${encodeURIComponent(memberCpid ?? "")}`, Boolean(memberCpid));
}
export function useCreateReferral() {
  const w = useWrite([["ruvimbo-referrals"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/referrals`, body), ...w });
}
export function useConsumeReferral() {
  const w = useWrite([["ruvimbo-referrals"]]);
  return useMutation({ mutationFn: (id: string) => apiClient.post(`${BASE}/referrals/${id}/consume`, {}), ...w });
}

// ── Liability estimation ────────────────────────────────────────────────────
export function useLiabilityEstimates(memberCpid?: string) {
  return listQuery(["ruvimbo-estimates", memberCpid ?? null], `${BASE}/liability-estimates?member_cpid=${encodeURIComponent(memberCpid ?? "")}`, Boolean(memberCpid));
}
export function useCreateLiabilityEstimate() {
  const w = useWrite([["ruvimbo-estimates"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/liability-estimates`, body), ...w });
}

// ── Claims v2 + adjudication ────────────────────────────────────────────────
export function useClaimV2(id?: string) {
  return objectQuery(["ruvimbo-claim", id ?? null], `${BASE}/v2/claims/${id}`, Boolean(id));
}
export function useClaimEob(id?: string) {
  return objectQuery(["ruvimbo-claim-eob", id ?? null], `${BASE}/v2/claims/${id}/explanation-of-benefits`, Boolean(id));
}
export function useClaimEvents(id?: string) {
  return listQuery(["ruvimbo-claim-events", id ?? null], `${BASE}/v2/claims/${id}/events`, Boolean(id));
}
export function useCreateClaimV2() {
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/v2/claims`, body) });
}
export function useClaimAction() {
  const w = useWrite([["ruvimbo-claim"], ["ruvimbo-claim-events"]]);
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/v2/claims/${id}/${action}`, body ?? {}),
    ...w,
  });
}

// ── Coordination of benefits ────────────────────────────────────────────────
export function useCobList(memberCpid?: string) {
  return listQuery(["ruvimbo-cob", memberCpid ?? null], `${BASE}/cob?member_cpid=${encodeURIComponent(memberCpid ?? "")}`, Boolean(memberCpid));
}
export function useCoordinateCob() {
  const w = useWrite([["ruvimbo-cob"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/cob/coordinate`, body), ...w });
}

// ── Employer / group rosters ────────────────────────────────────────────────
export function useEmployers() {
  return listQuery(["ruvimbo-employers"], `${BASE}/employers`);
}
export function useRegisterEmployer() {
  const w = useWrite([["ruvimbo-employers"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/employers`, body), ...w });
}
export function useRosterBatch(batchId?: string) {
  return objectQuery(["ruvimbo-roster-batch", batchId ?? null], `${BASE}/employers/roster-batches/${batchId}`, Boolean(batchId));
}
export function useRosterRows(batchId?: string) {
  return listQuery(["ruvimbo-roster-rows", batchId ?? null], `${BASE}/employers/roster-batches/${batchId}/rows`, Boolean(batchId));
}
export function useStageRoster() {
  return useMutation({
    mutationFn: ({ employerId, body }: { employerId: string; body: Record<string, unknown> }) =>
      apiClient.post(`${BASE}/employers/${employerId}/roster-batches`, body),
  });
}
export function useRosterAction() {
  const w = useWrite([["ruvimbo-roster-batch"], ["ruvimbo-roster-rows"]]);
  return useMutation({
    mutationFn: ({ batchId, action }: { batchId: string; action: string }) =>
      apiClient.post(`${BASE}/employers/roster-batches/${batchId}/${action}`, {}),
    ...w,
  });
}

// ── Fraud, waste & abuse ────────────────────────────────────────────────────
export function useFraudFlags(status?: string) {
  const q = status ? `?status=${encodeURIComponent(status)}` : "";
  return listQuery(["ruvimbo-fraud", status ?? null], `${BASE}/fraud-flags${q}`);
}
export function useScreenClaim() {
  const w = useWrite([["ruvimbo-fraud"]]);
  return useMutation({ mutationFn: (claimId: string) => apiClient.post(`${BASE}/fraud-flags/screen-claim/${claimId}`, {}), ...w });
}
export function useReviewFraudFlag() {
  const w = useWrite([["ruvimbo-fraud"]]);
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) => apiClient.post(`${BASE}/fraud-flags/${id}/review`, body),
    ...w,
  });
}

// ── Capitation ──────────────────────────────────────────────────────────────
export function useCapitationReports(providerId?: string) {
  return listQuery(["ruvimbo-capitation", providerId ?? null], `${BASE}/capitation-reports?provider_id=${encodeURIComponent(providerId ?? "")}`, Boolean(providerId));
}
export function useCreateCapitation() {
  const w = useWrite([["ruvimbo-capitation"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/capitation-reports`, body), ...w });
}

// ── Claims switch / payer gateway ───────────────────────────────────────────
export function useGatewayTransactions(status = "ROUTED") {
  return listQuery(["ruvimbo-gateway", status], `${BASE}/gateway/transactions?status=${encodeURIComponent(status)}`);
}
export function useRouteGateway() {
  const w = useWrite([["ruvimbo-gateway"]]);
  return useMutation({ mutationFn: (body: Record<string, unknown>) => apiClient.post(`${BASE}/gateway/transactions`, body), ...w });
}
