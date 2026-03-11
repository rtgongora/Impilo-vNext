/**
 * Experience UI — Facility Context Store (Zustand)
 *
 * Provider tree position: QueryClient > Auth > [FacilityProvider] > Workspace > Shift > Router
 * Persistence key: exp:facility (sessionStorage)
 */

import { create } from "zustand";

export interface FacilityContext {
  id: string;
  name: string;
  code: string;
  facilityType: string;
  capabilities: string[];
}

interface FacilityState {
  facility: FacilityContext | null;
  hasFacility: boolean;
  setFacility: (facility: FacilityContext) => void;
  clearFacility: () => void;
}

export const useFacilityStore = create<FacilityState>((set: (partial: Partial<FacilityState>) => void) => ({
  facility: null,
  hasFacility: false,

  setFacility: (facility: FacilityContext) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:facility", JSON.stringify(facility));
    }
    set({ facility, hasFacility: true });
  },

  clearFacility: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:facility");
    }
    set({ facility: null, hasFacility: false });
  },
}));
