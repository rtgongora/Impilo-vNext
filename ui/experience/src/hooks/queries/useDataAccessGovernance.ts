/**
 * Data Access Governance Query Hooks — Health OS §6 (Privacy by Architecture)
 *
 * Provides TanStack Query hooks for the data access governance surface:
 *   - Governance policy CRUD (what data can be accessed, by whom, under which conditions)
 *   - Access request lifecycle (submit, approve, deny)
 *
 * These hooks enable the "Privacy by Architecture" principle: every data access
 * must be governed by policy, and exceptional access requires an auditable
 * request/approval workflow.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Resource types ─────────────────────────────────────────────────

export interface GovernancePolicyResource {
  id: string;
  type: "governance_policy";
  attributes: {
    name: string;
    description: string;
    resourceType: string;
    effect: "ALLOW" | "DENY";
    status: "ACTIVE" | "DRAFT" | "DISABLED";
    requiredPurposes: string[];
    requiredAssuranceLevel?: string;
    dataClassification: string;
    retentionDays?: number;
    createdAt: string;
    updatedAt: string;
    [key: string]: unknown;
  };
}

export interface AccessRequestResource {
  id: string;
  type: "access_request";
  attributes: {
    requesterId: string;
    requesterName: string;
    resourceType: string;
    resourceId: string;
    purpose: string;
    justification: string;
    status: "PENDING" | "APPROVED" | "DENIED" | "EXPIRED" | "REVOKED";
    requestedAt: string;
    decidedAt?: string;
    decidedBy?: string;
    decisionNotes?: string;
    expiresAt?: string;
    [key: string]: unknown;
  };
}

// ── Response aliases ───────────────────────────────────────────────

type GovernancePoliciesResponse = ApiResponse<GovernancePolicyResource[]>;
type GovernancePolicyResponse = ApiResponse<GovernancePolicyResource>;
type AccessRequestsResponse = ApiResponse<AccessRequestResource[]>;
type AccessRequestResponse = ApiResponse<AccessRequestResource>;

// ── Governance policies ────────────────────────────────────────────

/** List all governance policies. */
export function useGovernancePolicies() {
  return useQuery<GovernancePoliciesResponse>({
    queryKey: ["governance", "policies"],
    queryFn: () =>
      apiClient.get<GovernancePoliciesResponse>(
        "/internal/v1/governance/access/policies",
      ),
  });
}

/** Create a new governance policy. */
export function useCreateGovernancePolicy() {
  const queryClient = useQueryClient();
  return useMutation<
    GovernancePolicyResponse,
    unknown,
    {
      name: string;
      description: string;
      resourceType: string;
      effect: "ALLOW" | "DENY";
      requiredPurposes?: string[];
      requiredAssuranceLevel?: string;
      dataClassification: string;
      retentionDays?: number;
    }
  >({
    mutationFn: (body) =>
      apiClient.post<GovernancePolicyResponse>(
        "/internal/v1/governance/access/policies",
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["governance", "policies"],
      });
    },
  });
}

// ── Access requests ────────────────────────────────────────────────

/** List access requests with optional filtering. */
export function useAccessRequests(
  status?: string,
  page?: number,
  size?: number,
) {
  const p = page ?? 0;
  const s = size ?? 20;
  return useQuery<AccessRequestsResponse>({
    queryKey: ["governance", "access-requests", { status, page: p, size: s }],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      if (status) searchParams.set("status", status);
      searchParams.set("page", String(p));
      searchParams.set("size", String(s));
      return apiClient.get<AccessRequestsResponse>(
        `/internal/v1/governance/access/requests?${searchParams.toString()}`,
      );
    },
  });
}

/** Submit a new access request. */
export function useSubmitAccessRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    AccessRequestResponse,
    unknown,
    {
      resourceType: string;
      resourceId: string;
      purpose: string;
      justification: string;
    }
  >({
    mutationFn: (body) =>
      apiClient.post<AccessRequestResponse>(
        "/internal/v1/governance/access/requests",
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["governance", "access-requests"],
      });
    },
  });
}

/** Approve an access request. */
export function useApproveAccessRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    AccessRequestResponse,
    unknown,
    { id: string; notes?: string }
  >({
    mutationFn: ({ id, ...body }) =>
      apiClient.post<AccessRequestResponse>(
        `/internal/v1/governance/access/requests/${id}/approve`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["governance", "access-requests"],
      });
    },
  });
}

/** Deny an access request. */
export function useDenyAccessRequest() {
  const queryClient = useQueryClient();
  return useMutation<
    AccessRequestResponse,
    unknown,
    { id: string; notes?: string }
  >({
    mutationFn: ({ id, ...body }) =>
      apiClient.post<AccessRequestResponse>(
        `/internal/v1/governance/access/requests/${id}/deny`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["governance", "access-requests"],
      });
    },
  });
}
