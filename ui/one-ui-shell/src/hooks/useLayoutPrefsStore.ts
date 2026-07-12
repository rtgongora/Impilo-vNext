"use client";

import { create } from "zustand";

/**
 * Layout preferences — adaptive workspace behaviour (nav rail width, taskbar
 * minimise, focus mode). Manual preferences persist across sessions in
 * localStorage; focus mode is deliberately per-session so a user can never be
 * "stuck" in an immersive state after signing back in.
 *
 * Pure chrome state: changing any of these must never touch workflow state,
 * entered data, or the active clinical/facility context.
 */

export type NavRailPref = "auto" | "expanded" | "compact";

const STORAGE_NAV_RAIL = "exp:layout_nav_rail";
const STORAGE_TASKBAR_MIN = "exp:layout_taskbar_min";

function loadString(key: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function saveString(key: string, value: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // ignore quota / privacy mode
  }
}

export interface LayoutPrefsStore {
  hydrated: boolean;
  /** Nav rail width preference: `auto` follows the route context. */
  navRailPref: NavRailPref;
  /** Taskbar minimised to a small restore handle. */
  taskbarMinimized: boolean;
  /** Immersive focus mode: rail hidden + taskbar minimised. Session-only. */
  focusMode: boolean;

  hydrateFromStorage: () => void;
  setNavRailPref: (pref: NavRailPref) => void;
  setTaskbarMinimized: (minimized: boolean) => void;
  toggleTaskbarMinimized: () => void;
  setFocusMode: (on: boolean) => void;
  toggleFocusMode: () => void;
}

export const useLayoutPrefsStore = create<LayoutPrefsStore>()((set, get) => ({
  hydrated: false,
  navRailPref: "auto",
  taskbarMinimized: false,
  focusMode: false,

  hydrateFromStorage: () => {
    if (get().hydrated) return;
    const rail = loadString(STORAGE_NAV_RAIL);
    const taskbar = loadString(STORAGE_TASKBAR_MIN);
    set({
      hydrated: true,
      navRailPref: rail === "expanded" || rail === "compact" ? rail : "auto",
      taskbarMinimized: taskbar === "true",
    });
  },

  setNavRailPref: (pref) => {
    set({ navRailPref: pref });
    saveString(STORAGE_NAV_RAIL, pref);
  },

  setTaskbarMinimized: (minimized) => {
    set({ taskbarMinimized: minimized });
    saveString(STORAGE_TASKBAR_MIN, String(minimized));
  },

  toggleTaskbarMinimized: () => {
    get().setTaskbarMinimized(!get().taskbarMinimized);
  },

  setFocusMode: (on) => set({ focusMode: on }),
  toggleFocusMode: () => set((s) => ({ focusMode: !s.focusMode })),
}));
