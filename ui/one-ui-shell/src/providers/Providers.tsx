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
  const [hydrated, setHydrated] = useState(false);
  const { setAuth, hydrateSession } = useAuthStore();
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

      const hasSessionCookie = document.cookie.includes("exp_has_session=1");

      if (userStr && (token || hasSessionCookie)) {
        const user = JSON.parse(userStr);
        if (token) {
          setAuth(user, token, null, expiresAt);
        } else {
          hydrateSession(user, null, expiresAt);
        }
        useOperationalContextStore.getState().ensureDefaultFromUser(user);
        useConsentStore.getState().hydrate(user.id);
        usePrivacyDisplayStore.getState().hydrate();
        hasAuthenticatedSession = true;
      } else {
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
      // Silently ignore parse errors in stored state
    }

    setHydrated(true);
  }, [setAuth, hydrateSession, setFacility, setWorkspace, startShift]);

  if (!hydrated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="text-muted-foreground text-sm">Loading...</div>
      </div>
    );
  }

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
