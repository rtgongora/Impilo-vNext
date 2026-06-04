"use client";

import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import { emitShellEvent } from "@/lib/shell/shell-events";
import { randomUUID } from "@/lib/uuid";
import type { AppDefinition, RecentItem, RunningTask, RunningTaskStatus, RunningTaskType } from "@/lib/shell/types";

const STORAGE_PINNED = "exp:shell_pins";
const STORAGE_TASKS = "exp:shell_tasks";
const STORAGE_RECENT = "exp:shell_recent";

const MAX_OPEN_TASKS = 24;
const MAX_RECENT = 30;
const MAX_PINS = 32;

function recordedAtMs(iso: string): number {
  const t = Date.parse(iso);
  return Number.isFinite(t) ? t : 0;
}

function loadJson<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

function saveJson(key: string, value: unknown) {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore quota
  }
}

function newId(): string {
  return randomUUID();
}

export interface ShellStore {
  pinnedAppCodes: string[];
  openTasks: RunningTask[];
  activeTaskId: string | null;
  recentItems: RecentItem[];
  startOpen: boolean;
  searchOpen: boolean;
  taskManagerOpen: boolean;
  /** Full zone navigation drawer (legacy sidebar content) — off-canvas at all breakpoints. */
  navDrawerOpen: boolean;
  /** SOS escalation dialog (shell taskbar + command palette). */
  sosDialogOpen: boolean;
  /** Incremented when the search palette should focus its input (toggle, Ctrl+K, or quick action). */
  searchPaletteFocusTick: number;

  setStartOpen: (open: boolean) => void;
  setSearchOpen: (open: boolean) => void;
  setTaskManagerOpen: (open: boolean) => void;
  setNavDrawerOpen: (open: boolean) => void;
  setSosDialogOpen: (open: boolean) => void;
  toggleNavDrawer: () => void;
  toggleStart: () => void;
  toggleSearch: () => void;
  /** Opens search (if closed) and requests input focus — idempotent for already-open palette. */
  focusSearchPalette: () => void;

  hydrateFromSession: () => void;

  /** Replace pins + recents from BFF (Redis) after visibility filtering — also mirrors to sessionStorage. */
  applyRemoteWorkspaceSnapshot: (snap: { pinnedAppCodes: string[]; recentItems: RecentItem[] }) => void;

  /**
   * Merge server workspace into hydrated session: pin union (local order first, capped),
   * recents by refKey — newer `recordedAt` wins; on tie, local wins.
   */
  mergeRemoteWorkspaceWithSession: (snap: { pinnedAppCodes: string[]; recentItems: RecentItem[] }) => void;

  pinApp: (appCode: string) => void;
  unpinApp: (appCode: string) => void;
  togglePinApp: (appCode: string) => void;

  /** Upsert task for route, mark active, bump recents */
  touchRouteTask: (args: {
    route: string;
    title: string;
    appId: string;
    taskType: RunningTaskType;
    contextRef?: string;
  }) => void;
  setActiveTask: (id: string | null) => void;
  minimizeTask: (id: string) => void;
  restoreTask: (id: string) => void;
  closeTask: (id: string) => void;
  clearClosedTasks: () => void;

  /** Refine task title when async context resolves (e.g. patient display name). */
  updateTaskTitleForRoute: (route: string, title: string) => void;

  recordRecent: (item: Omit<RecentItem, "id" | "recordedAt">) => void;
  launchApp: (app: AppDefinition, routerPush: (href: string) => void) => void;

  clearSessionShellState: () => void;
}

function sortTasks(tasks: RunningTask[]): RunningTask[] {
  return [...tasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );
}

export const useShellStore = create<ShellStore>()(
  subscribeWithSelector((set, get) => ({
    pinnedAppCodes: [],
    openTasks: [],
    activeTaskId: null,
    recentItems: [],
    startOpen: false,
    searchOpen: false,
    taskManagerOpen: false,
    navDrawerOpen: false,
    sosDialogOpen: false,
    searchPaletteFocusTick: 0,

    hydrateFromSession: () => {
      set({
        pinnedAppCodes: loadJson(STORAGE_PINNED, [] as string[]),
        openTasks: loadJson(STORAGE_TASKS, [] as RunningTask[]),
        recentItems: loadJson(STORAGE_RECENT, [] as RecentItem[]),
      });
    },

    applyRemoteWorkspaceSnapshot: (snap) => {
      const pins = snap.pinnedAppCodes.slice(0, MAX_PINS);
      const recents = snap.recentItems.slice(0, MAX_RECENT);
      set({ pinnedAppCodes: pins, recentItems: recents });
      saveJson(STORAGE_PINNED, pins);
      saveJson(STORAGE_RECENT, recents);
    },

    mergeRemoteWorkspaceWithSession: (snap) => {
      const localPins = get().pinnedAppCodes;
      const localRecents = get().recentItems;
      const remotePins = snap.pinnedAppCodes;
      const remoteRecents = snap.recentItems;

      const seenPin = new Set<string>();
      const mergedPins: string[] = [];
      for (const p of localPins) {
        if (seenPin.has(p)) continue;
        seenPin.add(p);
        mergedPins.push(p);
      }
      for (const p of remotePins) {
        if (seenPin.has(p)) continue;
        seenPin.add(p);
        mergedPins.push(p);
      }
      const pins = mergedPins.slice(0, MAX_PINS);

      const byRef = new Map<string, RecentItem>();
      for (const r of localRecents) {
        byRef.set(r.refKey, r);
      }
      for (const r of remoteRecents) {
        const loc = byRef.get(r.refKey);
        if (!loc) {
          byRef.set(r.refKey, r);
          continue;
        }
        const tl = recordedAtMs(loc.recordedAt);
        const tr = recordedAtMs(r.recordedAt);
        // Newer recordedAt wins; on exact tie keep the local row (already in map).
        if (tr > tl) {
          byRef.set(r.refKey, r);
        }
      }
      const recents = Array.from(byRef.values())
        .sort((a, b) => recordedAtMs(b.recordedAt) - recordedAtMs(a.recordedAt))
        .slice(0, MAX_RECENT);

      set({ pinnedAppCodes: pins, recentItems: recents });
      saveJson(STORAGE_PINNED, pins);
      saveJson(STORAGE_RECENT, recents);
    },

    setStartOpen: (open) =>
      set(() => ({
        startOpen: open,
        ...(open ? { navDrawerOpen: false, searchOpen: false, sosDialogOpen: false } : {}),
      })),
    setSearchOpen: (open) =>
      set(() => ({
        searchOpen: open,
        ...(open ? { navDrawerOpen: false, startOpen: false, sosDialogOpen: false } : {}),
      })),
    setTaskManagerOpen: (open) => set({ taskManagerOpen: open }),
    setNavDrawerOpen: (open) =>
      set((s) => ({
        navDrawerOpen: open,
        startOpen: open ? false : s.startOpen,
        searchOpen: open ? false : s.searchOpen,
        sosDialogOpen: open ? false : s.sosDialogOpen,
      })),
    setSosDialogOpen: (open) =>
      set((s) => ({
        sosDialogOpen: open,
        startOpen: open ? false : s.startOpen,
        searchOpen: open ? false : s.searchOpen,
        navDrawerOpen: open ? false : s.navDrawerOpen,
      })),
    toggleNavDrawer: () =>
      set((s) => {
        const next = !s.navDrawerOpen;
        return {
          navDrawerOpen: next,
          startOpen: next ? false : s.startOpen,
          searchOpen: next ? false : s.searchOpen,
          sosDialogOpen: next ? false : s.sosDialogOpen,
        };
      }),
    toggleStart: () =>
      set((s) => ({ startOpen: !s.startOpen, searchOpen: false, navDrawerOpen: false, sosDialogOpen: false })),
    toggleSearch: () =>
      set((s) => {
        const nextOpen = !s.searchOpen;
        return {
          searchOpen: nextOpen,
          startOpen: false,
          navDrawerOpen: false,
          sosDialogOpen: false,
          searchPaletteFocusTick: nextOpen ? s.searchPaletteFocusTick + 1 : s.searchPaletteFocusTick,
        };
      }),
    focusSearchPalette: () =>
      set((s) => ({
        searchOpen: true,
        startOpen: false,
        navDrawerOpen: false,
        sosDialogOpen: false,
        searchPaletteFocusTick: s.searchPaletteFocusTick + 1,
      })),

    pinApp: (appCode) => {
      const next = Array.from(new Set([...get().pinnedAppCodes, appCode]));
      set({ pinnedAppCodes: next });
      saveJson(STORAGE_PINNED, next);
      emitShellEvent("app_pinned", { appCode });
    },
    unpinApp: (appCode) => {
      const next = get().pinnedAppCodes.filter((c) => c !== appCode);
      set({ pinnedAppCodes: next });
      saveJson(STORAGE_PINNED, next);
      emitShellEvent("app_unpinned", { appCode });
    },
    togglePinApp: (appCode) => {
      if (get().pinnedAppCodes.includes(appCode)) {
        get().unpinApp(appCode);
      } else {
        get().pinApp(appCode);
      }
    },

    touchRouteTask: ({ route, title, appId, taskType, contextRef }) => {
      const now = new Date().toISOString();
      const tasks = get().openTasks;
      const existing = tasks.find((t) => t.route === route);
      let nextTasks: RunningTask[];
      let activeId: string;

      if (existing) {
        nextTasks = tasks.map((t) =>
          t.id === existing.id
            ? { ...t, title, lastActiveAt: now, status: "open" as RunningTaskStatus, contextRef: contextRef ?? t.contextRef }
            : t,
        );
        activeId = existing.id;
      } else {
        const task: RunningTask = {
          id: newId(),
          appId,
          taskType,
          title,
          route,
          contextRef,
          openedAt: now,
          lastActiveAt: now,
          status: "open",
        };
        nextTasks = sortTasks([task, ...tasks]).slice(0, MAX_OPEN_TASKS);
        activeId = task.id;
      }

      set({ openTasks: nextTasks, activeTaskId: activeId });
      saveJson(STORAGE_TASKS, nextTasks);

      emitShellEvent(existing ? "task_activated" : "task_opened", {
        route,
        taskId: activeId,
        appId,
        taskType,
      });

      get().recordRecent({
        kind: "route",
        title,
        subtitle: route,
        href: route,
        refKey: `route:${route}`,
        sensitivity: taskType === "patient_chart" || taskType === "dicom_viewer" ? "clinical" : "normal",
      });
    },

    setActiveTask: (id) => {
      set({ activeTaskId: id });
      if (!id) return;
      const now = new Date().toISOString();
      const next = get().openTasks.map((t) =>
        t.id === id ? { ...t, lastActiveAt: now, status: "open" as const } : t,
      );
      set({ openTasks: next });
      saveJson(STORAGE_TASKS, next);
      emitShellEvent("task_activated", { taskId: id });
    },

    minimizeTask: (id) => {
      const next = get().openTasks.map((t) =>
        t.id === id ? { ...t, status: "minimized" as const } : t,
      );
      set({ openTasks: next, activeTaskId: get().activeTaskId === id ? null : get().activeTaskId });
      saveJson(STORAGE_TASKS, next);
    },

    restoreTask: (id) => {
      const now = new Date().toISOString();
      const next = get().openTasks.map((t) =>
        t.id === id ? { ...t, status: "open" as const, lastActiveAt: now } : t,
      );
      set({ openTasks: next, activeTaskId: id });
      saveJson(STORAGE_TASKS, next);
    },

    closeTask: (id) => {
      const next = get().openTasks.filter((t) => t.id !== id);
      set({
        openTasks: next,
        activeTaskId: get().activeTaskId === id ? next.find((t) => t.status === "open")?.id ?? null : get().activeTaskId,
      });
      saveJson(STORAGE_TASKS, next);
      emitShellEvent("task_closed", { taskId: id });
    },

    updateTaskTitleForRoute: (route, title) => {
      const next = get().openTasks.map((t) => (t.route === route ? { ...t, title } : t));
      set({ openTasks: next });
      saveJson(STORAGE_TASKS, next);
    },

    clearClosedTasks: () => {
      const prev = get().openTasks;
      const next = prev.filter((t) => t.status !== "minimized");
      const removed = prev.length - next.length;
      const aid = get().activeTaskId;
      const nextActive =
        aid && next.some((t) => t.id === aid) ? aid : (next.find((t) => t.status === "open")?.id ?? null);
      set({ openTasks: next, activeTaskId: nextActive });
      saveJson(STORAGE_TASKS, next);
      if (removed > 0) emitShellEvent("task_cleared_minimized", { count: removed });
    },

    recordRecent: (item) => {
      const now = new Date().toISOString();
      const entry: RecentItem = {
        ...item,
        id: newId(),
        recordedAt: now,
      };
      const deduped = get().recentItems.filter((r) => r.refKey !== item.refKey);
      const next = [entry, ...deduped].slice(0, MAX_RECENT);
      set({ recentItems: next });
      saveJson(STORAGE_RECENT, next);
    },

    launchApp: (app, routerPush) => {
      get().recordRecent({
        kind: "app",
        title: app.name,
        subtitle: app.description,
        href: app.href,
        refKey: `app:${app.appCode}`,
        sensitivity: "normal",
      });
      emitShellEvent("app_launched", { appCode: app.appCode, href: app.href });
      routerPush(app.href);
      set({ startOpen: false, searchOpen: false, navDrawerOpen: false, sosDialogOpen: false });
    },

    clearSessionShellState: () => {
      set({
        openTasks: [],
        activeTaskId: null,
        recentItems: [],
        pinnedAppCodes: [],
        startOpen: false,
        searchOpen: false,
        taskManagerOpen: false,
        navDrawerOpen: false,
        sosDialogOpen: false,
        searchPaletteFocusTick: 0,
      });
      if (typeof window !== "undefined") {
        sessionStorage.removeItem(STORAGE_TASKS);
        sessionStorage.removeItem(STORAGE_RECENT);
        sessionStorage.removeItem(STORAGE_PINNED);
      }
    },
  })),
);
