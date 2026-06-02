"use client";

import { create } from "zustand";
import type { PendingApiRoute } from "@/lib/experience/pending-api-routes";

interface PendingToastState {
  visible: boolean;
  route: PendingApiRoute | string;
  label?: string;
  show: (route: PendingApiRoute | string, label?: string) => void;
  dismiss: () => void;
}

let dismissTimer: ReturnType<typeof setTimeout> | undefined;

export const useBackendPendingToast = create<PendingToastState>((set) => ({
  visible: false,
  route: "",
  label: undefined,
  show: (route, label) => {
    if (dismissTimer) clearTimeout(dismissTimer);
    set({ visible: true, route, label });
    dismissTimer = setTimeout(() => set({ visible: false }), 6000);
  },
  dismiss: () => {
    if (dismissTimer) clearTimeout(dismissTimer);
    set({ visible: false });
  },
}));

/** Surfaces a non-blocking toast when a Lovable-parity API is not yet on the BFF. */
export function showBackendPendingToast(
  route: PendingApiRoute | string,
  label?: string,
): void {
  useBackendPendingToast.getState().show(route, label);
}
