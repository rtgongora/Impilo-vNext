/**
 * Phase G1 — resolves and mints a real work-context token once a session has
 * an active facility + workspace, mirroring web's `useSwitchWorkContext` /
 * `ActiveWorkContextBar` auto-selection (prefer the context matching the
 * selected facility, else the BFF's `recommendedContextId`, else the first
 * resolved context; mode = `context.defaultMode ?? context.availableModes[0]`).
 *
 * When facility/workspace changes, remints with previousJti so the old duty
 * token is revoked — never keeps a stale token for a different workplace.
 *
 * Deliberately best-effort and non-blocking: a session that resolves to zero
 * contexts is an expected case for some accounts, not a hard failure.
 */
import { useEffect, useRef, useState } from "react";
import { useAuth } from "@impilo/mobile-auth";
import type { ResolvedWorkContextsAttributes, ResolvedWorkContextView } from "@impilo/mobile-trust";
import { appStore, useAppStore } from "../stores/appStore";
import { listResolvedWorkContexts, mintWorkContextSession } from "../services/workContextService";

export type WorkContextResolutionStatus = "idle" | "resolving" | "resolved" | "unavailable" | "failed";

/**
 * Pure selection rule, extracted so it can be unit-tested without rendering a
 * component or mocking the effect timing: prefer the context matching the
 * facility the app already selected (best UX alignment with what the user
 * picked in SelectFacilityScreen), else the BFF's own `recommendedContextId`,
 * else the first resolved context. Returns `null` when there is nothing to
 * mint (an expected outcome for accounts with no WorkMode-mapped context yet
 * — e.g. outreach/courier-only accounts — not an error).
 */
export function selectPreferredWorkContext(
  resolved: ResolvedWorkContextsAttributes,
  currentFacilityId: string | null
): ResolvedWorkContextView | null {
  const contexts = resolved.contexts ?? [];
  return (
    contexts.find((c) => currentFacilityId && c.facilityId === currentFacilityId) ??
    contexts.find((c) => c.contextId === resolved.recommendedContextId) ??
    contexts[0] ??
    null
  );
}

export function useAutoResolveWorkContext(): WorkContextResolutionStatus {
  const auth = useAuth();
  const { facilityId, workspaceId } = useAppStore();
  const [status, setStatus] = useState<WorkContextResolutionStatus>("idle");
  const attemptedFor = useRef<string | null>(null);

  useEffect(() => {
    const readyKey = facilityId && workspaceId ? `${facilityId}:${workspaceId}` : null;
    // Remint whenever facility+workspace identity changes — including when a
    // prior token exists for a different workplace (pass previousJti below).
    if (!readyKey || attemptedFor.current === readyKey) {
      return;
    }
    attemptedFor.current = readyKey;
    let cancelled = false;

    (async () => {
      setStatus("resolving");
      try {
        const resolved = await listResolvedWorkContexts();
        if (cancelled) return;

        // Persisted regardless of whether a mint follows — ModeSwitcher (Phase G3)
        // reads this to unlock mode buttons backed by a real proven assignment,
        // independent of whether the preferred context's mint succeeds below.
        appStore.getState().setResolvedWorkContexts(resolved.contexts ?? []);

        const preferred = selectPreferredWorkContext(resolved, facilityId);

        if (!preferred) {
          setStatus("unavailable");
          return;
        }

        const workMode = preferred.defaultMode ?? preferred.availableModes[0];
        if (!workMode) {
          setStatus("unavailable");
          return;
        }

        const previousJti = auth.session?.workContextJti ?? undefined;
        const minted = await mintWorkContextSession(preferred.contextId, workMode, previousJti);
        if (cancelled) return;

        if (!minted.token || !minted.jti) {
          setStatus("failed");
          return;
        }

        auth.setWorkContext({
          token: minted.token,
          jti: minted.jti,
          expiresAt: minted.expiresAt ? Date.parse(minted.expiresAt) : Date.now() + 15 * 60_000,
          contextId: minted.contextId ?? preferred.contextId,
          workMode: minted.workMode ?? workMode,
        });
        setStatus("resolved");
      } catch {
        if (!cancelled) setStatus("failed");
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [auth, facilityId, workspaceId]);

  return status;
}
