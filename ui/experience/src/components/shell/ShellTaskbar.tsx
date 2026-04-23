"use client";

import { usePathname, useRouter } from "next/navigation";
import { Bell, FolderOpen, Layers, LayoutGrid, Search } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { findShellAppByCode, listVisibleShellApps, SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { ShellIcon } from "./ShellIcon";

export function ShellTaskbar() {
  const router = useRouter();
  const pathname = usePathname();
  const hasRole = useAuthStore((s) => s.hasRole);
  const toggleStart = useShellStore((s) => s.toggleStart);
  const toggleSearch = useShellStore((s) => s.toggleSearch);
  const setTaskManagerOpen = useShellStore((s) => s.setTaskManagerOpen);
  const pinnedAppCodes = useShellStore((s) => s.pinnedAppCodes);
  const openTasks = useShellStore((s) => s.openTasks);
  const activeTaskId = useShellStore((s) => s.activeTaskId);
  const setActiveTask = useShellStore((s) => s.setActiveTask);
  const minimizeTask = useShellStore((s) => s.minimizeTask);
  const closeTask = useShellStore((s) => s.closeTask);
  const togglePinApp = useShellStore((s) => s.togglePinApp);

  const apps = listVisibleShellApps(hasRole);
  const pinnedApps = apps.filter((a) => pinnedAppCodes.includes(a.appCode));
  const openOrdered = [...openTasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );

  return (
    <div
      className="pointer-events-auto fixed bottom-0 left-0 right-0 z-[10000] border-t border-slate-200/90 bg-white/95 text-slate-800 shadow-[0_-4px_24px_rgba(15,23,42,0.08)] backdrop-blur-md dark:border-slate-700 dark:bg-slate-950/95 dark:text-slate-100"
      style={{ height: SHELL_TASKBAR_HEIGHT_PX }}
      role="navigation"
      aria-label="Experience shell"
    >
      <div className="mx-auto flex h-full max-w-[1920px] items-center gap-1 px-2">
        <button
          type="button"
          onClick={() => toggleStart()}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-slate-200 bg-slate-50 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800"
          title="Start — apps and recent work"
          aria-label="Start menu"
        >
          <ImpiloBrandLogo variant="mark" size={28} className="h-7 w-7" />
        </button>

        <button
          type="button"
          onClick={() => toggleSearch()}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-transparent hover:bg-slate-100 dark:hover:bg-slate-900"
          title="Search (Ctrl+K)"
          aria-label="Open search"
        >
          <Search className="h-5 w-5 text-slate-600 dark:text-slate-300" />
        </button>

        <button
          type="button"
          onClick={() => setTaskManagerOpen(true)}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-transparent hover:bg-slate-100 dark:hover:bg-slate-900"
          title="Task manager"
          aria-label="Open task manager"
        >
          <Layers className="h-5 w-5 text-slate-600 dark:text-slate-300" />
        </button>

        <button
          type="button"
          onClick={() => router.push("/shell/file-manager")}
          className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border border-transparent hover:bg-slate-100 dark:hover:bg-slate-900"
          title="File manager"
          aria-label="Open file manager"
        >
          <FolderOpen className="h-5 w-5 text-slate-600 dark:text-slate-300" />
        </button>

        <div className="mx-1 hidden h-8 w-px bg-slate-200 dark:bg-slate-700 sm:block" />

        <div className="hidden shrink-0 items-center gap-0.5 sm:flex" title="Pinned apps">
          {pinnedApps.length === 0 ? (
            <span className="px-2 text-[10px] font-medium uppercase tracking-wide text-slate-400">
              Pin from Start
            </span>
          ) : (
            pinnedApps.map((app) => {
              const active = pathname === app.href || pathname.startsWith(`${app.href}/`);
              return (
                <button
                  key={app.appCode}
                  type="button"
                  title={`${app.name} — long-press idea: unpin from Start`}
                  onClick={() => router.push(app.href)}
                  onContextMenu={(e) => {
                    e.preventDefault();
                    togglePinApp(app.appCode);
                  }}
                  className={`flex h-10 w-10 items-center justify-center rounded-md border transition-colors ${
                    active
                      ? "border-impilo-400 bg-impilo-50 text-impilo-800 dark:border-impilo-600 dark:bg-impilo-950/40"
                      : "border-transparent hover:bg-slate-100 dark:hover:bg-slate-900"
                  }`}
                >
                  <ShellIcon name={app.icon} className="h-5 w-5" />
                </button>
              );
            })
          )}
        </div>

        <div className="mx-1 hidden h-8 w-px bg-slate-200 dark:bg-slate-700 md:block" />

        <div className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto py-1">
          <LayoutGrid className="mx-1 hidden h-4 w-4 shrink-0 text-slate-400 sm:block" aria-hidden />
          {openOrdered.map((task) => {
            const active =
              (task.id === activeTaskId && task.status === "open") ||
              (task.status === "open" && pathname === task.route);
            return (
              <button
                key={task.id}
                type="button"
                title={task.route}
                onClick={() => {
                  if (task.status === "minimized") {
                    useShellStore.getState().restoreTask(task.id);
                  }
                  setActiveTask(task.id);
                  router.push(task.route);
                }}
                onContextMenu={(e) => {
                  e.preventDefault();
                  minimizeTask(task.id);
                }}
                className={`group flex max-w-[200px] shrink-0 items-center gap-1 rounded-lg border px-2 py-1 text-left text-xs transition-colors ${
                  active
                    ? "border-impilo-500 bg-impilo-600 text-white dark:border-impilo-400"
                    : task.status === "minimized"
                      ? "border-dashed border-slate-300 bg-slate-50 text-slate-500 dark:border-slate-600 dark:bg-slate-900"
                      : "border-slate-200 bg-white hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900"
                }`}
              >
                <span className="truncate font-medium">{task.title}</span>
                <span
                  role="button"
                  tabIndex={0}
                  className="ml-1 rounded p-0.5 opacity-0 hover:bg-black/10 group-hover:opacity-100"
                  onClick={(ev) => {
                    ev.stopPropagation();
                    closeTask(task.id);
                  }}
                  onKeyDown={(ev) => {
                    if (ev.key === "Enter" || ev.key === " ") {
                      ev.preventDefault();
                      ev.stopPropagation();
                      closeTask(task.id);
                    }
                  }}
                  title="Close task"
                >
                  ×
                </span>
              </button>
            );
          })}
        </div>

        <div className="ml-auto hidden shrink-0 items-center gap-2 lg:flex" title="System tray (reserved)">
          <span className="flex items-center gap-1 rounded-lg border border-dashed border-slate-200 px-1.5 py-1 dark:border-slate-700">
            <span
              className="flex h-8 w-8 items-center justify-center rounded-md bg-slate-100 text-slate-400 dark:bg-slate-900 dark:text-slate-500"
              title="Notifications (planned)"
            >
              <Bell className="h-4 w-4" />
            </span>
            <span
              className="flex h-8 w-8 items-center justify-center rounded-md bg-slate-100 text-slate-400 dark:bg-slate-900 dark:text-slate-500"
              title="Background jobs (planned)"
            >
              <span className="text-[10px] font-bold">···</span>
            </span>
          </span>
          <button
            type="button"
            className="rounded-md px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
            title="Default pins: Home + File manager"
            onClick={() => {
              const home = findShellAppByCode("home");
              const fm = findShellAppByCode("shell_file_manager");
              if (home) useShellStore.getState().pinApp(home.appCode);
              if (fm) useShellStore.getState().pinApp(fm.appCode);
            }}
          >
            Pin defaults
          </button>
        </div>
      </div>
    </div>
  );
}
