/**
 * Experience UI — Store Hydration Hook
 *
 * Runs once on mount to rehydrate all Zustand stores from session continuity.
 * This fixes the hydration gap where stores are empty after a page refresh
 * until the provider tree re-fetches data.
 *
 * Auth continuity:
 *   exp:auth_user, exp:expires_at and exp_has_session cookie -> useAuthStore auth hydration
 *   access token remains memory-only and is refreshed through the HttpOnly cookie path
 *
 * SessionStorage keys:
 *   exp:facility                   -> useFacilityStore.setFacility
 *   exp:workspace                  -> useWorkspaceStore.setWorkspace
 *   exp:shift                      -> useShiftStore.startShift
 */

"use client";

import { useEffect, useRef } from "react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";

export function useHydration(): void {
  const hydrated = useRef(false);

  useEffect(() => {
    if (hydrated.current) return;
    hydrated.current = true;

    // --- Auth ---
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
