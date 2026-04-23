"use client";

import { create } from "zustand";
import { subscribeWithSelector } from "zustand/middleware";
import type { AppDefinition, RecentItem, RunningTask, RunningTaskStatus, RunningTaskType } from "@/lib/shell/types";

const STORAGE_PINNED = "exp:shell_pins";
const STORAGE_TASKS = "exp:shell_tasks";
const STORAGE_RECENT = "exp:shell_recent";

const MAX_OPEN_TASKS = 24;
const MAX_RECENT = 30;

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
  return crypto.randomUUID();
}

export interface ShellStore {
  pinnedAppCodes: string[];
  openTasks: RunningTask[];
  activeTaskId: string | null;
  recentItems: RecentItem[];
  startOpen: boolean;
  searchOpen: boolean;
  taskManagerOpen: boolean;

  setStartOpen: (open: boolean) => void;
  setSearchOpen: (open: boolean) => void;
  setTaskManagerOpen: (open: boolean) => void;
  toggleStart: () => void;
  toggleSearch: () => void;

  hydrateFromSession: () => void;

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

    hydrateFromSession: () => {
      set({
        pinnedAppCodes: loadJson(STORAGE_PINNED, [] as string[]),
        openTasks: loadJson(STORAGE_TASKS, [] as RunningTask[]),
        recentItems: loadJson(STORAGE_RECENT, [] as RecentItem[]),
      });
    },

    setStartOpen: (open) => set({ startOpen: open }),
    setSearchOpen: (open) => set({ searchOpen: open }),
    setTaskManagerOpen: (open) => set({ taskManagerOpen: open }),
    toggleStart: () => set((s) => ({ startOpen: !s.startOpen, searchOpen: false })),
    toggleSearch: () => set((s) => ({ searchOpen: !s.searchOpen, startOpen: false })),

    pinApp: (appCode) => {
      const next = Array.from(new Set([...get().pinnedAppCodes, appCode]));
      set({ pinnedAppCodes: next });
      saveJson(STORAGE_PINNED, next);
    },
    unpinApp: (appCode) => {
      const next = get().pinnedAppCodes.filter((c) => c !== appCode);
      set({ pinnedAppCodes: next });
      saveJson(STORAGE_PINNED, next);
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

      get().recordRecent({
        kind: "route",
        title,
        subtitle: route,
        href: route,
        refKey: `route:${route}`,
        sensitivity: taskType === "patient_chart" ? "clinical" : "normal",
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
    },

    updateTaskTitleForRoute: (route, title) => {
      const next = get().openTasks.map((t) => (t.route === route ? { ...t, title } : t));
      set({ openTasks: next });
      saveJson(STORAGE_TASKS, next);
    },

    clearClosedTasks: () => {
      /* placeholder — same as close all minimized optional */
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
      routerPush(app.href);
      set({ startOpen: false, searchOpen: false });
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
      });
      if (typeof window !== "undefined") {
        sessionStorage.removeItem(STORAGE_TASKS);
        sessionStorage.removeItem(STORAGE_RECENT);
        sessionStorage.removeItem(STORAGE_PINNED);
      }
    },
  })),
);
