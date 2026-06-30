/**
 * Nompilo Intelligent Layer — mobile service.
 *
 * Reads the Experience BFF NompiloGuidanceController (/internal/v1/nompilo/**), which resolves the
 * caller's context + real service signals and asks the guidance-service (Nompilo SoR) for the
 * contextual guidance set. Mirrors the web `useNompilo` hooks so web↔mobile stay in parity. Nompilo
 * owns no transaction — every CTA routes to a real owner; nothing here is mock/demo guidance.
 */
import { apiClient } from "@impilo/mobile-api-client";

export type NompiloSeverity = "INFO" | "RECOMMEND" | "WARN" | "CRITICAL";

export interface NompiloGuidanceItem {
  key: string;
  domain: string;
  type: string;
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
  accessibilityNote?: string | null;
}

export interface NompiloContextResult {
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
  safetyBoundary: string;
}

/** Resolve contextual guidance for the current mobile route. */
export async function fetchNompiloContext(
  routePath: string,
  extra: Record<string, unknown> = {},
): Promise<NompiloContextResult> {
  const response = await apiClient.post<{ data?: NompiloContextResult }>(
    "/internal/v1/nompilo/context",
    { routePath, ...extra },
  );
  return response.data?.data ?? { guidance: [] };
}

/** Explain a denied/locked action in safe plain language (no sensitive leak). */
export async function explainLockedAction(
  reasonCode: string,
  routePath?: string,
): Promise<NompiloLockedExplanation | null> {
  const response = await apiClient.post<{ data?: NompiloLockedExplanation }>(
    "/internal/v1/nompilo/explain-locked",
    { reasonCode, routePath },
  );
  return response.data?.data ?? null;
}

/** Dismiss a guidance item for the current person (display state only). */
export async function dismissGuidance(guidanceKey: string): Promise<void> {
  await apiClient.post("/internal/v1/nompilo/dismiss", { guidanceKey });
}

/** Request a Khuluma follow-up via the handoff lifecycle (Nompilo requests; Khuluma sends). */
export async function requestNompiloFollowUp(body: {
  guidanceKey?: string;
  reason: string;
  routePath?: string;
}): Promise<void> {
  await apiClient.post("/internal/v1/nompilo/follow-up", body);
}
