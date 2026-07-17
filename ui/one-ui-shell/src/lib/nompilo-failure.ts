"use client";

import { create } from "zustand";
import { recordClientObservation } from "@/lib/client-observability";

/**
 * Nompilo failure signal — the lightweight bridge that lets a citizen action failure
 * flow into Nompilo so it can offer context-aware recovery ("that didn't work — here's
 * what you can do") instead of a dead end.
 *
 * PRIVACY: this signal is deliberately minimal and NEVER carries PII, clinical content,
 * credentials, request bodies, or free text from the user. It captures only:
 *   - the page route the person was on (pathname, no query string),
 *   - the machine error code (e.g. VALIDATION_FAILED, SERVICE_UNAVAILABLE),
 *   - an optional short action label describing the attempted step (e.g. "Book a service"),
 *   - the correlation/request ids that already travel on the wire for support tracing,
 *   - the HTTP status and a timestamp.
 * These are the same ids the api-client already stamps on every request; nothing new or
 * sensitive is exposed. The correlation id lets a support agent find the trace without any
 * personal data being surfaced in the UI.
 *
 * In-memory only (like the assistant conversation) — not persisted to storage.
 */

export interface NompiloFailureSignal {
  /** Page route the person was on (pathname only — never query/PII). */
  route: string;
  /** Machine error code from the API error envelope, when available. */
  code: string;
  /** Short human label for the attempted action, when the caller supplies one. */
  action?: string;
  /** Correlation id for support tracing (already on the wire). */
  correlationId?: string;
  /** Request id for support tracing (already on the wire). */
  requestId?: string;
  /** HTTP status, when it was an HTTP failure. */
  status?: number;
  /** ms epoch. */
  at: number;
}

interface NompiloFailureStore {
  lastFailure: NompiloFailureSignal | null;
  record: (signal: Omit<NompiloFailureSignal, "at"> & { at?: number }) => void;
  clear: () => void;
}

/** How long a recorded failure stays "fresh" enough for Nompilo to proactively surface it. */
export const FAILURE_FRESHNESS_MS = 90_000;

export const useNompiloFailureStore = create<NompiloFailureStore>()((set) => ({
  lastFailure: null,
  record: (signal) =>
    set({
      lastFailure: {
        route: signal.route,
        code: signal.code,
        action: signal.action,
        correlationId: signal.correlationId,
        requestId: signal.requestId,
        status: signal.status,
        at: signal.at ?? Date.now(),
      },
    }),
  clear: () => set({ lastFailure: null }),
}));

/**
 * Record a failure from a non-React context (api-client, error boundary).
 * Guards against PII by construction: callers only pass code/ids/route/status.
 */
export function recordNompiloFailure(signal: Omit<NompiloFailureSignal, "at">): void {
  try {
    useNompiloFailureStore.getState().record(signal);
  } catch {
    // Never let failure-signal recording break the primary error path.
  }
  // Fan out to the batched, PII-free network sink from this single record site, so every failure
  // choke point (api-client, ShellErrorBoundary, dead-end capture) is shipped without duplicating
  // the feed. Drops `action` (the only free-text-ish field) — allow-list is enforced again in the
  // sink. This module imports the sink one-way (sink imports nothing from the app), so no cycle.
  try {
    recordClientObservation({
      route: signal.route,
      code: signal.code,
      correlationId: signal.correlationId,
      requestId: signal.requestId,
      status: signal.status,
      at: Date.now(),
    });
  } catch {
    // Sink is best-effort; never let it break the primary error path.
  }
}

/** Whether a recorded failure is recent enough to proactively surface. */
export function isFailureFresh(failure: NompiloFailureSignal | null, now = Date.now()): boolean {
  return !!failure && now - failure.at < FAILURE_FRESHNESS_MS;
}
