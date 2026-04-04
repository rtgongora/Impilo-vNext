/**
 * Experience UI — Store Hydration Hook
 *
 * Runs once on mount to rehydrate all Zustand stores from sessionStorage.
 * This fixes the hydration gap where stores are empty after a page refresh
 * until the provider tree re-fetches data.
 *
 * SessionStorage keys:
 *   exp:auth_token, exp:auth_user  -> useAuthStore.setAuth
 *   exp:facility                   -> useFacilityStore.setFacility
 *   exp:workspace                  -> useWorkspaceStore.setWorkspace
 *   exp:shift                      -> useShiftStore.startShift
 */

"use client";

import { useEffect, useRef } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

export function useHydration(): void {
  const hydrated = useRef(false);

  useEffect(() => {
    if (hydrated.current) return;
    hydrated.current = true;

    // --- Auth ---
    const token = sessionStorage.getItem("exp:auth_token");
    const userJson = sessionStorage.getItem("exp:auth_user");
    const refreshToken = sessionStorage.getItem("exp:refresh_token");
    const expiresAt = sessionStorage.getItem("exp:expires_at");
    if (token && userJson) {
      try {
        const user = JSON.parse(userJson);
        useAuthStore.getState().setAuth(user, token, refreshToken, expiresAt);
      } catch {
        // Corrupted data — skip auth hydration
      }
    }

    // --- Facility ---
    const facilityJson = sessionStorage.getItem("exp:facility");
    if (facilityJson) {
      try {
        const facility = JSON.parse(facilityJson);
        useFacilityStore.getState().setFacility(facility);
      } catch {
        // Corrupted data — skip facility hydration
      }
    }

    // --- Workspace ---
    const workspaceJson = sessionStorage.getItem("exp:workspace");
    if (workspaceJson) {
      try {
        const workspace = JSON.parse(workspaceJson);
        useWorkspaceStore.getState().setWorkspace(workspace);
      } catch {
        // Corrupted data — skip workspace hydration
      }
    }

    // --- Shift ---
    const shiftJson = sessionStorage.getItem("exp:shift");
    if (shiftJson) {
      try {
        const shift = JSON.parse(shiftJson);
        useShiftStore.getState().startShift(shift);
      } catch {
        // Corrupted data — skip shift hydration
      }
    }
  }, []);
}
