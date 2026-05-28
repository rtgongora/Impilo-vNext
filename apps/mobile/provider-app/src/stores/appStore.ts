/**
 * App Store — Global application state for mode, connectivity, and notifications.
 *
 * Manages:
 * - Current app mode (Provider/Outreach/Supervisor/Offline)
 * - Online/offline status
 * - Facility/workspace context
 * - Notification badge counts
 * - Global error state
 */

import { createStore } from "zustand/vanilla";
import type { AppMode, ProviderTabKey } from "../types";

export interface AppState {
  mode: AppMode;
  isOnline: boolean;
  facilityId: string | null;
  facilityName: string | null;
  workspaceId: string | null;
  workspaceName: string | null;
  shiftId: string | null;
  providerTab: ProviderTabKey;
  unreadNotifications: number;
  pendingSyncCount: number;
  globalError: { code: string; message: string } | null;
  /** When opening Clinical Tools, select this tab once then clear (e.g. telemedicine). */
  clinicalToolsInitialTab: string | null;
  /** When switching to Supervisor mode, open this tab once then clear (e.g. escalations). */
  supervisorEntryTab: "dashboard" | "team" | "stock" | "inventory" | "escalations" | null;
  /** Optional focus to carry into Apps surface (e.g. Fundo overdue). */
  appsFocus: "required" | "overdue" | "cpd" | null;
  /** Active learning subject identity for Fundo parity flows. */
  learningSubjectType: string | null;
  learningSubjectId: string | null;

  setMode: (mode: AppMode) => void;
  setOnlineStatus: (online: boolean) => void;
  setFacilityContext: (id: string, name: string) => void;
  setWorkspaceContext: (id: string, name: string) => void;
  setShiftId: (id: string) => void;
  setProviderTab: (tab: ProviderTabKey) => void;
  setUnreadNotifications: (count: number) => void;
  setPendingSyncCount: (count: number) => void;
  setGlobalError: (error: { code: string; message: string } | null) => void;
  setClinicalToolsInitialTab: (tab: string | null) => void;
  setSupervisorEntryTab: (tab: AppState["supervisorEntryTab"]) => void;
  setAppsFocus: (focus: AppState["appsFocus"]) => void;
  setLearningSubject: (subjectType: string | null, subjectId: string | null) => void;
  clearContext: () => void;
}

export const appStore = createStore<AppState>((set) => ({
  mode: "provider",
  isOnline: true,
  facilityId: null,
  facilityName: null,
  workspaceId: null,
  workspaceName: null,
  shiftId: null,
  providerTab: "dashboard",
  unreadNotifications: 0,
  pendingSyncCount: 0,
  globalError: null,
  clinicalToolsInitialTab: null,
  supervisorEntryTab: null,
  appsFocus: null,
  learningSubjectType: null,
  learningSubjectId: null,

  setMode: (mode) =>
    set((state) => ({
      mode,
      // Enforce worklist-first entry whenever the clinician returns to Provider mode.
      providerTab: mode === "provider" ? "dashboard" : state.providerTab,
    })),
  setOnlineStatus: (isOnline) => set({ isOnline }),
  setFacilityContext: (id, name) => set({ facilityId: id, facilityName: name }),
  setWorkspaceContext: (id, name) => set({ workspaceId: id, workspaceName: name }),
  setShiftId: (id) => set({ shiftId: id }),
  setProviderTab: (providerTab) => set({ providerTab }),
  setUnreadNotifications: (count) => set({ unreadNotifications: count }),
  setPendingSyncCount: (count) => set({ pendingSyncCount: count }),
  setGlobalError: (error) => set({ globalError: error }),
  setClinicalToolsInitialTab: (clinicalToolsInitialTab) => set({ clinicalToolsInitialTab }),
  setSupervisorEntryTab: (supervisorEntryTab) => set({ supervisorEntryTab }),
  setAppsFocus: (appsFocus) => set({ appsFocus }),
  setLearningSubject: (learningSubjectType, learningSubjectId) => set({ learningSubjectType, learningSubjectId }),
  clearContext: () =>
    set({
      facilityId: null,
      facilityName: null,
      workspaceId: null,
      workspaceName: null,
      shiftId: null,
    }),
}));

export function useAppStore(): AppState {
  // eslint-disable-next-line react-hooks/rules-of-hooks -- intentional vanilla store
  const { useSyncExternalStore } = require("react");
  return useSyncExternalStore(
    appStore.subscribe,
    () => appStore.getState(),
    () => appStore.getState()
  );
}
