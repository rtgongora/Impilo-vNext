/**
 * Trust Administration Query Hooks — Health OS §6 (Privacy by Architecture)
 *
 * Provides TanStack Query hooks for the TSHEPO trust administration surface:
 *   - Policy rule CRUD
 *   - Break-glass event review
 *   - Device profile management (block/unblock)
 *   - Trust audit log and chain integrity verification
 *
 * All calls route through the Experience BFF, which forwards to
 * tshepo-service with full v1.2 header injection.
 */

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Resource types ─────────────────────────────────────────────────

export interface PolicyRuleResource {
  id: string;
  type: "policy_rule";
  attributes: {
    name: string;
    description: string;
    resourceType: string;
    action: string;
    effect: "ALLOW" | "DENY";
    conditions: Record<string, unknown>[];
    priority: number;
    enabled: boolean;
    createdAt: string;
    updatedAt: string;
    [key: string]: unknown;
  };
}

export interface BreakGlassReviewResource {
  id: string;
  type: "break_glass_review";
  attributes: {
    eventId: string;
    timestamp: string;
    actorId: string;
    actorName: string;
    reason: string;
    patientId: string;
    resourcesAccessed: string[];
    duration: string;
    reviewStatus: "PENDING" | "APPROVED" | "FLAGGED" | "ESCALATED";
    reviewedBy?: string;
    reviewedAt?: string;
    reviewNotes?: string;
    [key: string]: unknown;
  };
}

export interface DeviceProfileResource {
  id: string;
  type: "device_profile";
  attributes: {
    fingerprint: string;
    deviceType: string;
    platform: string;
    lastSeenAt: string;
    trustScore: number;
    blocked: boolean;
    blockedAt?: string;
    blockedReason?: string;
    associatedActors: string[];
    [key: string]: unknown;
  };
}

export interface TrustAuditLogResource {
  id: string;
  type: "trust_audit_entry";
  attributes: {
    action: string;
    actorId: string;
    actorType: string;
    resourceType: string;
    resourceId: string;
    decision: string;
    policyId?: string;
    timestamp: string;
    details: Record<string, unknown>;
    [key: string]: unknown;
  };
}

export interface ChainIntegrityResult {
  valid: boolean;
  entriesVerified: number;
  firstEntry: string;
  lastEntry: string;
  verifiedAt: string;
  brokenLinks: string[];
}

// ── Response aliases ───────────────────────────────────────────────

type PolicyRulesResponse = ApiResponse<PolicyRuleResource[]>;
type PolicyRuleResponse = ApiResponse<PolicyRuleResource>;
type BreakGlassReviewsResponse = ApiResponse<BreakGlassReviewResource[]>;
type BreakGlassReviewResponse = ApiResponse<BreakGlassReviewResource>;
type DeviceProfileResponse = ApiResponse<DeviceProfileResource>;
type TrustAuditLogResponse = ApiResponse<TrustAuditLogResource[]>;
type ChainIntegrityResponse = ApiResponse<ChainIntegrityResult>;

// ── Policy management ──────────────────────────────────────────────

/** List all trust policy rules. */
export function usePolicyRules() {
  return useQuery<PolicyRulesResponse>({
    queryKey: ["trust-admin", "policies"],
    queryFn: () =>
      apiClient.get<PolicyRulesResponse>("/internal/v1/admin/trust/policies"),
  });
}

/** Create a new trust policy rule. */
export function useCreatePolicyRule() {
  const queryClient = useQueryClient();
  return useMutation<
    PolicyRuleResponse,
    unknown,
    {
      name: string;
      description: string;
      resourceType: string;
      action: string;
      effect: "ALLOW" | "DENY";
      conditions?: Record<string, unknown>[];
      priority?: number;
    }
  >({
    mutationFn: (body) =>
      apiClient.post<PolicyRuleResponse>(
        "/internal/v1/admin/trust/policies",
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["trust-admin", "policies"],
      });
    },
  });
}

// ── Break-glass reviews ────────────────────────────────────────────

/** List break-glass events pending review. */
export function useBreakGlassReviews() {
  return useQuery<BreakGlassReviewsResponse>({
    queryKey: ["trust-admin", "break-glass"],
    queryFn: () =>
      apiClient.get<BreakGlassReviewsResponse>(
        "/internal/v1/admin/trust/break-glass",
      ),
  });
}

/** Submit a review decision for a break-glass event. */
export function useReviewBreakGlass() {
  const queryClient = useQueryClient();
  return useMutation<
    BreakGlassReviewResponse,
    unknown,
    {
      id: string;
      decision: "APPROVED" | "FLAGGED" | "ESCALATED";
      notes: string;
    }
  >({
    mutationFn: ({ id, ...body }) =>
      apiClient.post<BreakGlassReviewResponse>(
        `/internal/v1/admin/trust/break-glass/${id}/review`,
        body,
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ["trust-admin", "break-glass"],
      });
    },
  });
}

// ── Device management ──────────────────────────────────────────────

/** Get the trust profile for a device by fingerprint. */
export function useDeviceProfile(fingerprint: string | undefined) {
  return useQuery<DeviceProfileResponse>({
    queryKey: ["trust-admin", "devices", fingerprint],
    queryFn: () =>
      apiClient.get<DeviceProfileResponse>(
        `/internal/v1/admin/trust/devices/${fingerprint}`,
      ),
    enabled: !!fingerprint,
  });
}

/** Block a device by fingerprint. */
export function useBlockDevice() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<{ status: string }>,
    unknown,
    { fingerprint: string; reason: string }
  >({
    mutationFn: ({ fingerprint, reason }) =>
      apiClient.post<ApiResponse<{ status: string }>>(
        `/internal/v1/admin/trust/devices/${fingerprint}/block`,
        { reason },
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({
        queryKey: ["trust-admin", "devices", variables.fingerprint],
      });
    },
  });
}

/** Unblock a device by fingerprint. */
export function useUnblockDevice() {
  const queryClient = useQueryClient();
  return useMutation<
    ApiResponse<{ status: string }>,
    unknown,
    { fingerprint: string }
  >({
    mutationFn: ({ fingerprint }) =>
      apiClient.delete<ApiResponse<{ status: string }>>(
        `/internal/v1/admin/trust/devices/${fingerprint}/block`,
      ),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({
        queryKey: ["trust-admin", "devices", variables.fingerprint],
      });
    },
  });
}

// ── Trust audit ────────────────────────────────────────────────────

/** Paginated trust audit log. */
export function useTrustAuditLogs(page?: number, size?: number) {
  const p = page ?? 0;
  const s = size ?? 20;
  return useQuery<TrustAuditLogResponse>({
    queryKey: ["trust-admin", "audit", { page: p, size: s }],
    queryFn: () => {
      const searchParams = new URLSearchParams();
      searchParams.set("page", String(p));
      searchParams.set("size", String(s));
      return apiClient.get<TrustAuditLogResponse>(
        `/internal/v1/admin/trust/audit?${searchParams.toString()}`,
      );
    },
  });
}

/** Verify the integrity of the trust audit chain. */
export function useChainIntegrity() {
  return useQuery<ChainIntegrityResponse>({
    queryKey: ["trust-admin", "audit", "chain-integrity"],
    queryFn: () =>
      apiClient.get<ChainIntegrityResponse>(
        "/internal/v1/admin/trust/audit/chain/verify",
      ),
  });
}
