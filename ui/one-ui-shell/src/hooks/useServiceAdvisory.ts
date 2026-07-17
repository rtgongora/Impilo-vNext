"use client";

/**
 * Nompilo Service Advisory resolver hook (public gateway lane).
 *
 * Advisories are DATA — this hook NEVER hard-codes advisory text. It resolves the active,
 * in-window, audience=public advisory set for the caller's context (journey stage, device,
 * language) from the guidance service via the experience-BFF public lane, filters out any
 * advisory already dismissed anonymously on this device, and records a best-effort VIEW
 * impression. Every network call is non-blocking and fail-soft: an outage yields an empty
 * advisory set, never an error surfaced to the citizen.
 *
 * Backend contract (do not guess):
 *   GET  /internal/v1/public/gateway/advisory/resolve  -> { data: { audience, advisories: [...] } }
 *   POST /internal/v1/public/gateway/advisory/impression { advisoryId, event, anonDismissalKey }
 *   POST /internal/v1/public/gateway/advisory/dismiss    { advisoryId, anonDismissalKey }
 */

import { useCallback, useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";
import {
  dismissAdvisory as persistDismissal,
  dismissedAdvisoryIds,
  getAnonDismissalKey,
} from "@/lib/advisory-dismissal";

export type AdvisorySeverity =
  | "INFORMATIONAL"
  | "SERVICE_NOTICE"
  | "IMPORTANT_DISRUPTION"
  | "EMERGENCY_ALERT";

export type AdvisoryVariant = "MODAL" | "BANNER" | "BUBBLE" | "INLINE" | "EMERGENCY";

export interface AdvisoryAction {
  label: string;
  href: string;
}

export interface ServiceAdvisory {
  id: string;
  title: string;
  body: string;
  severity: AdvisorySeverity;
  variant: AdvisoryVariant;
  primaryActions: AdvisoryAction[];
  startsAt?: string | null;
  expiresAt?: string | null;
}

interface ResolveResponse {
  data?: { audience?: string; advisories?: unknown[] };
  advisories?: unknown[];
}

function normalizeAction(raw: unknown): AdvisoryAction | null {
  if (!raw || typeof raw !== "object") return null;
  const a = raw as Record<string, unknown>;
  const label = typeof a.label === "string" ? a.label : null;
  const href = typeof a.href === "string" ? a.href : null;
  if (!label || !href) return null;
  return { label, href };
}

function normalizeAdvisory(raw: unknown): ServiceAdvisory | null {
  if (!raw || typeof raw !== "object") return null;
  const a = raw as Record<string, unknown>;
  if (typeof a.id !== "string" || typeof a.title !== "string") return null;
  const actions = Array.isArray(a.primaryActions)
    ? a.primaryActions.map(normalizeAction).filter((x): x is AdvisoryAction => x !== null)
    : [];
  return {
    id: a.id,
    title: a.title,
    body: typeof a.body === "string" ? a.body : "",
    severity: (typeof a.severity === "string" ? a.severity : "INFORMATIONAL") as AdvisorySeverity,
    variant: (typeof a.variant === "string" ? a.variant : "INLINE") as AdvisoryVariant,
    primaryActions: actions,
    startsAt: typeof a.startsAt === "string" ? a.startsAt : null,
    expiresAt: typeof a.expiresAt === "string" ? a.expiresAt : null,
  };
}

function deviceType(): string {
  if (typeof window === "undefined") return "web";
  return window.matchMedia?.("(max-width: 640px)")?.matches ? "mobile" : "web";
}

export interface UseServiceAdvisoryOptions {
  /** Journey stage from classifyRouteJourney(pathname) — targeting selector. */
  journeyStage?: string;
  /** Optional overrides for future context-aware targeting. */
  language?: string;
  serviceKey?: string;
}

export interface UseServiceAdvisoryResult {
  advisories: ServiceAdvisory[];
  loading: boolean;
  /** Dismiss anonymously: persist locally and fire a best-effort backend dismiss event. */
  dismiss: (advisoryId: string) => void;
}

export function useServiceAdvisory(
  options: UseServiceAdvisoryOptions = {},
): UseServiceAdvisoryResult {
  const { journeyStage, language, serviceKey } = options;
  const [advisories, setAdvisories] = useState<ServiceAdvisory[]>([]);
  const [loading, setLoading] = useState(true);
  const [dismissedLocal, setDismissedLocal] = useState<Set<string>>(new Set());

  // Hydrate device-local dismissals once (client only).
  useEffect(() => {
    setDismissedLocal(dismissedAdvisoryIds());
  }, []);

  useEffect(() => {
    let cancelled = false;
    const params = new URLSearchParams({ audience: "public", deviceType: deviceType() });
    if (journeyStage) params.set("journeyStage", journeyStage);
    if (language) params.set("language", language);
    if (serviceKey) params.set("serviceKey", serviceKey);

    (async () => {
      setLoading(true);
      try {
        const res = await apiClient.get<ResolveResponse>(
          `/internal/v1/public/gateway/advisory/resolve?${params.toString()}`,
        );
        const list = res?.data?.advisories ?? res?.advisories ?? [];
        const resolved = list
          .map(normalizeAdvisory)
          .filter((x): x is ServiceAdvisory => x !== null);
        if (!cancelled) setAdvisories(resolved);
      } catch {
        // Fail-soft: an advisory outage must never block or error a public page.
        if (!cancelled) setAdvisories([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [journeyStage, language, serviceKey]);

  const dismiss = useCallback((advisoryId: string) => {
    persistDismissal(advisoryId);
    setDismissedLocal((prev) => new Set(prev).add(advisoryId));
    // Best-effort backend signal — never awaited, never surfaced.
    void apiClient
      .post("/internal/v1/public/gateway/advisory/dismiss", {
        advisoryId,
        anonDismissalKey: getAnonDismissalKey(),
      })
      .catch(() => undefined);
  }, []);

  const visible = advisories.filter((a) => !dismissedLocal.has(a.id));
  return { advisories: visible, loading, dismiss };
}

/** Record a best-effort VIEW impression for an advisory (non-blocking). */
export function recordAdvisoryView(advisoryId: string): void {
  void apiClient
    .post("/internal/v1/public/gateway/advisory/impression", {
      advisoryId,
      event: "VIEW",
      anonDismissalKey: getAnonDismissalKey(),
    })
    .catch(() => undefined);
}
