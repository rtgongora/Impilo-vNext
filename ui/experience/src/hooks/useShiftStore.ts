/**
 * Experience UI — Shift Context Store (Zustand)
 *
 * Provider tree position: QueryClient > Auth > Facility > Workspace > [ShiftProvider] > Router
 * Persistence key: exp:shift (sessionStorage)
 */

import { create } from "zustand";

export interface ShiftContext {
  id: string;
  startedAt: string;
  workspaceId: string;
  facilityId: string;
}

interface ShiftState {
  shift: ShiftContext | null;
  hasShift: boolean;
  startShift: (shift: ShiftContext) => void;
  endShift: () => void;
}

export const useShiftStore = create<ShiftState>((set) => ({
  shift: null,
  hasShift: false,

  startShift: (shift) => {
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:shift", JSON.stringify(shift));
    }
    set({ shift, hasShift: true });
  },

  endShift: () => {
    if (typeof window !== "undefined") {
      sessionStorage.removeItem("exp:shift");
    }
    set({ shift: null, hasShift: false });
  },
}));
