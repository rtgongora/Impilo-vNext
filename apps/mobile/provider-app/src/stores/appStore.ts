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
import type { ResolvedWorkContextView } from "@impilo/mobile-trust";
import type { AppMode, GovernedAppMode, ProviderTabKey, UngovernedAppMode } from "../types";

export interface AppState {
  mode: AppMode;
  isOnline: boolean;
  /**
   * Phase G3 — the real resolved work contexts from
   * GET /internal/v1/work-context/resolved (populated best-effort by
   * useAutoResolveWorkContext once facility+workspace are set). Null until
   * the first resolution attempt completes; empty array is a real "resolved
   * to nothing yet" result, not the same as null/not-yet-tried. Feeds
   * ModeSwitcher's deriveAvailableAppModes so a real backend-proven
   * assignment can unlock a mode button even when the Keycloak role string
   * hasn't caught up — see modeAvailability.ts for why this is additive-only.
   */
  resolvedWorkContexts: ResolvedWorkContextView[] | null;
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
  supervisorEntryTab: "workhome" | "dashboard" | "team" | "stock" | "inventory" | "escalations" | null;
  /** Optional focus to carry into Apps surface (e.g. Fundo overdue). */
  appsFocus: "required" | "overdue" | "cpd" | null;
  /** Active learning subject identity for Fundo parity flows. */
  learningSubjectType: string | null;
  learningSubjectId: string | null;
  /**
   * Deep-link / in-app request to open a surface under the Professional tab
   * (e.g. "regulatory" → My Regulatory Affairs). Consumed once then cleared.
   */
  professionalSurfaceRequest: string | null;
  /** Student-registration contributor invite id from a deep link. */
  contributeInviteRequest: string | null;

  /**
   * Enters an UNGOVERNED workspace (outreach/offline/courier) — free local
   * navigation, no duty token involved because the resolver models no WorkMode
   * for these.
   *
   * Governed modes are deliberately NOT accepted here; use `setGrantedMode`
   * via `useSwitchAppMode`. This is a type-level fence: before it, four call
   * sites jumped straight into supervisor mode while the person still held a
   * CLINICAL_CARE token, so the UI changed posture and the access envelope did
   * not.
   */
  setMode: (mode: UngovernedAppMode) => void;
  /**
   * Enters a GOVERNED workspace. Only `useSwitchAppMode` may call this, and
   * only after `switchWorkContext` has minted a token for the target mode —
   * the visible mode must never move ahead of the authority behind it.
   */
  setGrantedMode: (mode: GovernedAppMode) => void;
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
  setResolvedWorkContexts: (contexts: ResolvedWorkContextView[]) => void;
  setProfessionalSurfaceRequest: (surface: string | null) => void;
  setContributeInviteRequest: (inviteId: string | null) => void;
  clearContext: () => void;
}

/**
 * Shared mode-entry effect for both setters. Split from them so the governed
 * and ungoverned paths cannot drift in behaviour — the only difference between
 * them is who is allowed to call which, enforced by the argument types.
 */
function applyMode(state: AppState, mode: AppMode): Partial<AppState> {
  return {
    mode,
    // Work-Home-first entry whenever the clinician returns to Provider mode —
    // the mobile counterpart of web's F6 flip from /provider-workspace to /work.
    // Work Home is the role- and context-aware composition (worklist included,
    // alongside professional alerts and the other governed sections); the
    // Worklist tab remains one tap away for anyone who wants only that.
    providerTab: mode === "provider" ? "workhome" : state.providerTab,
  };
}

export const appStore = createStore<AppState>((set) => ({
  mode: "provider",
  isOnline: true,
  resolvedWorkContexts: null,
  facilityId: null,
  facilityName: null,
  workspaceId: null,
  workspaceName: null,
  shiftId: null,
  providerTab: "workhome",
  unreadNotifications: 0,
  pendingSyncCount: 0,
  globalError: null,
  clinicalToolsInitialTab: null,
  supervisorEntryTab: null,
  appsFocus: null,
  learningSubjectType: null,
  learningSubjectId: null,
  professionalSurfaceRequest: null,
  contributeInviteRequest: null,

  setMode: (mode) => set((state) => applyMode(state, mode)),
  setGrantedMode: (mode) => set((state) => applyMode(state, mode)),
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
  setResolvedWorkContexts: (resolvedWorkContexts) => set({ resolvedWorkContexts }),
  setProfessionalSurfaceRequest: (professionalSurfaceRequest) => set({ professionalSurfaceRequest }),
  setContributeInviteRequest: (contributeInviteRequest) => set({ contributeInviteRequest }),
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
