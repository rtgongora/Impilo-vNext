import { create } from "zustand";
import type { PurposeOfUse, WorkContext } from "@/lib/contracts";

interface ContextState {
  workContext: WorkContext | null;
  purposeOfUse: PurposeOfUse;
  deviceFingerprint: string | null;
  setWorkContext: (ctx: WorkContext) => void;
  setPurposeOfUse: (purpose: PurposeOfUse) => void;
  setDeviceFingerprint: (fp: string) => void;
  clearContext: () => void;
}

export const useWorkContext = create<ContextState>((set) => ({
  workContext: null,
  purposeOfUse: "TREATMENT",
  deviceFingerprint: null,

  setWorkContext: (ctx) => set({ workContext: ctx }),

  setPurposeOfUse: (purpose) => set({ purposeOfUse: purpose }),

  setDeviceFingerprint: (fp) => set({ deviceFingerprint: fp }),

  clearContext: () =>
    set({
      workContext: null,
      purposeOfUse: "TREATMENT",
      deviceFingerprint: null,
    }),
}));
