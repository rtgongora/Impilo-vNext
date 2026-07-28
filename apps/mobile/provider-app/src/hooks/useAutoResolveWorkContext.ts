/**
 * Phase G1 — resolves and mints a real work-context token once a session has
 * an active facility + workspace, mirroring web's `useSwitchWorkContext` /
 * `ActiveWorkContextBar` auto-selection (prefer the context matching the
 * selected facility, else the BFF's `recommendedContextId`, else the first
 * resolved context; mode = `context.defaultMode ?? context.availableModes[0]`).
 *
 * Deliberately best-effort and non-blocking: this app has no resolved-context
 * picker UI yet (that is Phase G3/G4), and several existing `AppMode`s
 * (`outreach`, `courier`) have no `WorkMode` analogue in the resolver today,
 * so a session that resolves to zero contexts is an expected case for some
 * accounts, not a hard failure — the app must keep working exactly as before
 * when no context resolves or the mint fails. On success, every subsequent
 * `apiClient` call carries a real `x-work-context-token`; on failure, mobile
 * simply continues without one, same as it always has.
 *
 * Also persists the full resolved-contexts list to `appStore` (independent of
 * whether the preferred context's mint succeeds) so `ModeSwitcher` (Phase G3)
 * can unlock a mode button from a real proven assignment.
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
    if (!readyKey || auth.session?.workContextToken || attemptedFor.current === readyKey) {
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

        const minted = await mintWorkContextSession(preferred.contextId, workMode);
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
