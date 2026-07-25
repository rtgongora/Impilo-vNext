"use client";

/**
 * Experience UI — Provider Tree
 *
 * Provider tree order (from 05_state_and_storage.md):
 *   QueryClient > Auth > Facility > Workspace > Shift > Router
 *
 * Includes store hydration from session continuity on mount:
 * user metadata + continuity state in sessionStorage, access token in memory,
 * and session presence via cookie.
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

function StoreHydrator({ children }: { children: ReactNode }) {
  const [, setHydrated] = useState(false);
  const { setAuth, clearAuth } = useAuthStore();
  const { setFacility } = useFacilityStore();
  const { setWorkspace } = useWorkspaceStore();
  const { startShift } = useShiftStore();

  useEffect(() => {
    if (typeof window === "undefined") return;

    try {
      const token = sessionStorage.getItem("exp:auth_token");
      const userStr = sessionStorage.getItem("exp:auth_user");
      const expiresAt = sessionStorage.getItem("exp:expires_at");
      let hasAuthenticatedSession = false;

      // Only hydrate session if a token exists and is valid
      const isExpired = expiresAt ? new Date(expiresAt).getTime() - 60000 < Date.now() : false;

      if (userStr && token && !isExpired) {
        const user = JSON.parse(userStr);
        setAuth(user, token, null, expiresAt);
        useOperationalContextStore.getState().ensureDefaultFromUser(user);
        useConsentStore.getState().hydrate(user.id);
        usePrivacyDisplayStore.getState().hydrate();
        hasAuthenticatedSession = true;
      } else {
        // Clear stale unauthenticated session leftovers to prevent 401 redirect loops
        clearAuth();
        resetExperienceContinuity();
      }

      if (hasAuthenticatedSession) {
        const { facility, workspace, shift } = loadHydratedExperienceContinuity();
        if (facility) {
          setFacility(facility);
        }
        if (workspace) {
          setWorkspace(workspace);
        }
        if (shift) {
          startShift(shift);
        }
      }
    } catch {
      clearAuth();
    }

    setHydrated(true);
  }, [setAuth, clearAuth, setFacility, setWorkspace, startShift]);

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
