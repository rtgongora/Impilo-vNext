/**
 * App Store — Global application state for the citizen app.
 *
 * Manages:
 * - Active tab / navigation state
 * - Online/offline status
 * - Profile context
 * - Notification badge counts
 * - Global error state
 */

import { createStore } from "zustand/vanilla";
import type { CitizenTab, CitizenProfile } from "../types";

export interface AppState {
  activeTab: CitizenTab;
  isOnline: boolean;
  profile: CitizenProfile | null;
  /**
   * Back-compat convenience fields (older screens select these directly).
   */
  cpid?: string;
  givenName?: string;
  familyName?: string;
  unreadNotifications: number;
  unreadMessages: number;
  globalError: { code: string; message: string } | null;

  setActiveTab: (tab: CitizenTab) => void;
  setOnlineStatus: (online: boolean) => void;
  /**
   * Back-compat alias for `setOnlineStatus`.
   */
  setIsOnline: (online: boolean) => void;
  setProfile: (profile: CitizenProfile | null) => void;
  setUnreadNotifications: (count: number) => void;
  setUnreadMessages: (count: number) => void;
  setGlobalError: (error: { code: string; message: string } | null) => void;
}

export const appStore = createStore<AppState>((set) => ({
  activeTab: "home",
  isOnline: true,
  profile: null,
  cpid: undefined,
  givenName: undefined,
  familyName: undefined,
  unreadNotifications: 0,
  unreadMessages: 0,
  globalError: null,

  setActiveTab: (tab) => set({ activeTab: tab }),
  setOnlineStatus: (isOnline) => set({ isOnline }),
  setIsOnline: (isOnline) => set({ isOnline }),
  setProfile: (profile) =>
    set({
      profile,
      cpid: profile?.cpid,
      givenName: profile?.givenName,
      familyName: profile?.familyName,
    }),
  setUnreadNotifications: (count) => set({ unreadNotifications: count }),
  setUnreadMessages: (count) => set({ unreadMessages: count }),
  setGlobalError: (error) => set({ globalError: error }),
}));

export function useAppStore<T = AppState>(selector?: (state: AppState) => T): T {
  const { useSyncExternalStore } = require("react");
  const state = useSyncExternalStore(
    appStore.subscribe,
    () => appStore.getState(),
    () => appStore.getState()
  );
  return selector ? selector(state) : (state as unknown as T);
}
