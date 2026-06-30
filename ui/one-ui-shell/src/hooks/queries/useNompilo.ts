"use client";

/**
 * Nompilo Intelligent Layer hooks — the on-platform guide.
 *
 * Nompilo guides the user once they land in Impilo: it explains where they are, what's next, why
 * something is locked, and routes to the sovereign owner. These hooks read the Experience BFF
 * NompiloGuidanceController (/internal/v1/nompilo/**), which resolves context + the real service
 * signals and asks the guidance-service (the Nompilo SoR) for the contextual guidance set. Nompilo
 * owns no transaction — every CTA routes to a real owner; nothing here is mock/demo guidance.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type NompiloSeverity = "INFO" | "RECOMMEND" | "WARN" | "CRITICAL";

export interface NompiloGuidanceItem {
  key: string;
  domain: string;
  type: string; // ORIENTATION | NEXT_BEST_ACTION | LOCKED_STATE | CHECKLIST_ITEM | ...
  title: string;
  body: string;
  ctaLabel?: string | null;
  ctaRoute?: string | null;
  ownerService?: string | null;
  severity: NompiloSeverity;
  priority: number;
  khulumaFollowUpAvailable?: boolean;
  fundoContentRef?: string | null;
  auditRequired?: boolean;
  i18nKey?: string | null;
  accessibilityNote?: string | null;
}

export interface NompiloContextResponse {
  context?: {
    userType: string;
    trustLoa: number;
    routePath: string;
    relationship: string;
  };
  guidance: NompiloGuidanceItem[];
  degraded?: boolean;
}

export interface NompiloLockedExplanation {
  reasonCode: string;
  title: string;
  explanation: string;
  ctaLabel?: string | null;
  ctaRoute?: string | null;
  ownerService?: string | null;
  khulumaFollowUpAvailable?: boolean;
  auditRequired?: boolean;
  guidanceKey?: string;
  safetyBoundary: string;
}

/** Resolve contextual guidance for the current route. */
export function useNompiloContext(routePath: string, extra?: Record<string, unknown>) {
  return useQuery({
    queryKey: ["nompilo", "context", routePath, extra],
    queryFn: () =>
      apiClient.post<ApiResponse<NompiloContextResponse>>("/internal/v1/nompilo/context", {
        routePath,
        ...extra,
      }),
  });
}

/** Next-best-actions for the signed-in citizen (signals auto-derived by the BFF). */
export function useNompiloNextActions(routePath = "/citizen/wallet") {
  return useQuery({
    queryKey: ["nompilo", "next-actions", routePath],
    queryFn: () =>
      apiClient.get<ApiResponse<NompiloContextResponse>>(
        `/internal/v1/nompilo/next-actions?routePath=${encodeURIComponent(routePath)}`,
      ),
  });
}

/** Explain a denied/locked action in safe plain language (no sensitive leak). */
export function useExplainLocked() {
  return useMutation({
    mutationFn: (body: { reasonCode: string; routePath?: string }) =>
      apiClient.post<ApiResponse<NompiloLockedExplanation>>("/internal/v1/nompilo/explain-locked", body),
  });
}

/** Dismiss a guidance item for the current person (display state only). */
export function useDismissGuidance() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (guidanceKey: string) =>
      apiClient.post<ApiResponse<{ dismissed: boolean }>>("/internal/v1/nompilo/dismiss", { guidanceKey }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["nompilo"] });
    },
  });
}

/** Request a Khuluma follow-up via the handoff lifecycle (Nompilo requests, Khuluma sends). */
export function useRequestFollowUp() {
  return useMutation({
    mutationFn: (body: { guidanceKey?: string; reason: string; routePath?: string }) =>
      apiClient.post<ApiResponse<{ status: string }>>("/internal/v1/nompilo/follow-up", body),
  });
}
