"use client";

/**
 * Experience UI — Provider Tree
 *
 * Provider tree order (from 05_state_and_storage.md):
 *   QueryClient > Auth > Facility > Workspace > Shift > Router
 *
 * Includes store hydration from session continuity on mount:
 * user metadata + continuity state in sessionStorage, with OAuth tokens held
 * only by the BFF in its encrypted server-side session.
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { TierProvider } from "shared-ui";
import { AuthGuardProvider } from "./AuthGuardProvider";
import { ExperienceEntryProvider } from "./ExperienceEntryProvider";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";
import { InactivityLockProvider } from "./InactivityLockProvider";
import { PrivacyWatermark } from "@/components/PrivacyWatermark";
import { VisibilityContextBar } from "@/components/VisibilityContextBar";
import { ShellChrome } from "@/components/shell/ShellChrome";
import { ShellErrorBoundary } from "@/components/ShellErrorBoundary";
import { ShellWorkspaceRemoteSync } from "@/components/shell/ShellWorkspaceRemoteSync";
import { loadHydratedExperienceContinuity, resetExperienceContinuity } from "@/lib/session-continuity";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { registerImpiloServiceWorker } from "@/lib/offline";
import { apiClient } from "@/lib/api-client";
import { authUserFromWebSession, type WebSessionResponse } from "@/lib/auth/web-session";

function StoreHydrator({ children }: { children: ReactNode }) {
  const [, setHydrated] = useState(false);

  // Announce the restore during render, not in the effect below. React runs child effects
  // before parent effects, so by the time this component's effect fires the children's data
  // queries have already dispatched — and those are exactly the calls that must wait for an
  // actor. A useState initializer runs once, during this component's render, which precedes
  // every child's render and effect. Anything mounted without this provider (unit tests, SSR)
  // never raises the flag and is therefore never made to wait for a session that will not come.
  useState(() => {
    if (typeof window !== "undefined") useAuthStore.getState().markSessionRestoreStarted();
    return null;
  });

  const { hydrateSession, clearAuth } = useAuthStore();
  const { setFacility } = useFacilityStore();
  const { setWorkspace } = useWorkspaceStore();
  const { startShift } = useShiftStore();

  useEffect(() => {
    if (typeof window === "undefined") return;

    const restore = async () => {
      try {
        // The cookie is HttpOnly, so server truth—not browser storage—decides whether
        // a session exists. The BFF also refreshes its access token on this request.
        const response = await apiClient.get<WebSessionResponse>("/internal/v1/auth/oidc/session");
        const user = authUserFromWebSession(response.data);
        hydrateSession(user, null, response.data.expiresAt ?? null);
        useOperationalContextStore.getState().ensureDefaultFromUser(user);
        useConsentStore.getState().hydrate(user.id);
        usePrivacyDisplayStore.getState().hydrate();

        const { facility, workspace, shift } = loadHydratedExperienceContinuity();
        if (facility) setFacility(facility);
        if (workspace) setWorkspace(workspace);
        if (shift) startShift(shift);
      } catch {
        // No active opaque BFF session. Clear only authenticated/work continuity;
        // public journey drafts live in their own stores and remain intact.
        clearAuth();
        resetExperienceContinuity();
      } finally {
        // The route guard waits for this signal before acting on missing auth.
        useAuthStore.getState().markSessionRestoreAttempted();
        setHydrated(true);
      }
    };
    void restore();
  }, [hydrateSession, clearAuth, setFacility, setWorkspace, startShift]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    void registerImpiloServiceWorker();

    const onOnline = () => {
      void apiClient.flushOfflineQueue().catch(() => {
        // Flush errors stay in the outbox; next reconnect retries.
      });
    };
    window.addEventListener("online", onOnline);
    return () => window.removeEventListener("online", onOnline);
  }, []);

  return (
    <>
      <ShellWorkspaceRemoteSync />
      {children}
    </>
  );
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
          },
        },
      })
  );

  return (
    // TierProvider (Future-Realism §6): resolves the device/preference tier and mirrors tier-*/
    // low-blur onto <html> so glass/motion enhancements degrade to the accessible baseline.
    <TierProvider>
      <QueryClientProvider client={queryClient}>
        <StoreHydrator>
          <ExperienceEntryProvider>
            <AuthGuardProvider>
              <InactivityLockProvider>
                <PrivacyWatermark />
                <VisibilityContextBar />
                <ShellErrorBoundary>{children}</ShellErrorBoundary>
                <ShellChrome />
              </InactivityLockProvider>
            </AuthGuardProvider>
          </ExperienceEntryProvider>
        </StoreHydrator>
      </QueryClientProvider>
    </TierProvider>
  );
}
