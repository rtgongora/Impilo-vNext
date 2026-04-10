"use client";

/**
 * Experience UI — Provider Tree
 *
 * Provider tree order (from 05_state_and_storage.md):
 *   QueryClient > Auth > Facility > Workspace > Shift > Router
 *
 * Includes store hydration from sessionStorage on mount.
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { AuthGuardProvider } from "./AuthGuardProvider";
import { ExperienceEntryProvider } from "./ExperienceEntryProvider";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { loadHydratedExperienceContinuity, resetExperienceContinuity } from "@/lib/session-continuity";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

function StoreHydrator({ children }: { children: ReactNode }) {
  const [hydrated, setHydrated] = useState(false);
  const { setAuth } = useAuthStore();
  const { setFacility } = useFacilityStore();
  const { setWorkspace } = useWorkspaceStore();
  const { startShift } = useShiftStore();

  useEffect(() => {
    if (typeof window === "undefined") return;

    try {
      const token = sessionStorage.getItem("exp:auth_token");
      const userStr = sessionStorage.getItem("exp:auth_user");
      const refreshToken = sessionStorage.getItem("exp:refresh_token");
      const expiresAt = sessionStorage.getItem("exp:expires_at");
      let hasAuthenticatedSession = false;

      if (token && userStr) {
        const user = JSON.parse(userStr);
        setAuth(user, token, refreshToken, expiresAt);
        useOperationalContextStore.getState().ensureDefaultFromUser(user);
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
  }, [setAuth, setFacility, setWorkspace, startShift]);

  if (!hydrated) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="text-gray-400 text-sm">Loading...</div>
      </div>
    );
  }

  return <>{children}</>;
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
    <QueryClientProvider client={queryClient}>
      <StoreHydrator>
        <ExperienceEntryProvider>
          <AuthGuardProvider>{children}</AuthGuardProvider>
        </ExperienceEntryProvider>
      </StoreHydrator>
    </QueryClientProvider>
  );
}
