"use client";

import { type ReactNode } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Building2,
  ChevronDown,
  FolderOpen,
  Headphones,
  Layers,
  LayoutGrid,
  LifeBuoy,
  MessageSquare,
  Search,
  Siren,
  UserCircle,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { findShellAppByCode, listVisibleShellApps, SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { ShellIcon } from "./ShellIcon";
import { ShellNotificationTray } from "./ShellNotificationTray";
import { ShellSosDialog } from "./ShellSosDialog";

function TaskbarButton({
  onClick,
  title,
  ariaLabel,
  children,
  className = "",
}: {
  onClick?: () => void;
  title: string;
  ariaLabel: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-11 shrink-0 items-center gap-1.5 rounded-lg border border-transparent px-2 hover:bg-slate-100 dark:hover:bg-slate-900 ${className}`}
      title={title}
      aria-label={ariaLabel}
    >
      {children}
    </button>
  );
}

export function ShellTaskbar() {
  const router = useRouter();
  const pathname = usePathname();
  const learningWorkbench = pathname === "/learning" || pathname.startsWith("/learning/");
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

  const sosDialogOpen = useShellStore((s) => s.sosDialogOpen);
  const setSosDialogOpen = useShellStore((s) => s.setSosDialogOpen);

  const apps = listVisibleShellApps(hasRole);
  const pinnedApps = apps.filter((a) => pinnedAppCodes.includes(a.appCode));
  const openOrdered = [...openTasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );

  return (
    <>
      <ShellSosDialog open={sosDialogOpen} onClose={() => setSosDialogOpen(false)} />
      <div
        className={`pointer-events-auto group fixed bottom-0 left-0 right-0 z-[10000] border-t border-slate-200/90 bg-white/95 text-slate-800 shadow-[0_-4px_24px_rgba(15,23,42,0.08)] backdrop-blur-md transition-[height] duration-200 dark:border-slate-700 dark:bg-slate-950/95 dark:text-slate-100 ${
          learningWorkbench ? "h-3 hover:h-[52px] focus-within:h-[52px]" : ""
        }`}
        style={learningWorkbench ? undefined : { height: SHELL_TASKBAR_HEIGHT_PX }}
        role="navigation"
        aria-label="Experience shell"
        title={learningWorkbench ? "Hover or focus to open the shell taskbar" : undefined}
      >
        <div className={`${learningWorkbench ? "opacity-0 group-hover:opacity-100 group-focus-within:opacity-100" : ""} mx-auto flex h-full max-w-[1920px] items-center gap-0.5 overflow-x-auto px-1 transition-opacity duration-150 sm:gap-1 sm:px-2`}>
          <TaskbarButton onClick={() => toggleStart()} title="Start — launcher" ariaLabel="Start menu">
            <ImpiloBrandLogo variant="mark" size={28} className="h-7 w-7" />
            <span className="hidden text-xs font-semibold text-slate-700 sm:inline dark:text-slate-200">Start</span>
          </TaskbarButton>

          <TaskbarButton onClick={() => toggleSearch()} title="Search (Ctrl+K)" ariaLabel="Open search and commands">
            <Search className="h-5 w-5 text-slate-600 dark:text-slate-300" />
            <span className="hidden text-xs font-medium text-slate-700 sm:inline dark:text-slate-200">Search</span>
          </TaskbarButton>

          <details className="relative shrink-0">
            <summary className="flex h-11 cursor-pointer list-none items-center gap-1 rounded-lg border border-transparent px-2 marker:hidden hover:bg-slate-100 dark:hover:bg-slate-900 [&::-webkit-details-marker]:hidden">
              <Building2 className="h-5 w-5 text-slate-600 dark:text-slate-300" />
              <span className="hidden text-xs font-medium text-slate-700 lg:inline dark:text-slate-200">Context</span>
              <ChevronDown className="hidden h-3.5 w-3.5 text-slate-400 lg:inline" aria-hidden />
            </summary>
            <div className="absolute bottom-full left-0 z-[10001] mb-1 min-w-[200px] rounded-xl border border-slate-200 bg-white py-1 text-sm shadow-xl dark:border-slate-700 dark:bg-slate-950">
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => {
                  router.push("/facility");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Facility…
              </button>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => {
                  router.push("/workspace");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Workspace…
              </button>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-slate-50 dark:hover:bg-slate-900"
                onClick={() => {
                  router.push("/shift");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Shift…
              </button>
            </div>
          </details>

          <TaskbarButton
            onClick={() => router.push("/communication/secure-messaging")}
            title="Comms hub — secure messaging"
            ariaLabel="Open communications hub"
          >
            <MessageSquare className="h-5 w-5 text-slate-600 dark:text-slate-300" />
            <span className="hidden text-xs font-medium text-slate-700 xl:inline dark:text-slate-200">Comms</span>
          </TaskbarButton>

          <TaskbarButton
            onClick={() => router.push("/support/knowledge-base")}
            title="Help articles and knowledge base"
            ariaLabel="Open help"
          >
            <LifeBuoy className="h-5 w-5 text-slate-600 dark:text-slate-300" />
            <span className="hidden text-xs font-medium text-slate-700 xl:inline dark:text-slate-200">Help</span>
          </TaskbarButton>

          <TaskbarButton onClick={() => router.push("/ask")} title="Ask Nompilo" ariaLabel="Open Nompilo Ask">
            <span className="text-xs font-semibold text-impilo-600 dark:text-impilo-400">Nompilo</span>
          </TaskbarButton>

          <TaskbarButton
            onClick={() => router.push("/support/tickets")}
            title="System support and tickets"
            ariaLabel="Open system support"
          >
            <Headphones className="h-5 w-5 text-slate-600 dark:text-slate-300" />
            <span className="hidden text-xs font-medium text-slate-700 2xl:inline dark:text-slate-200">Support</span>
          </TaskbarButton>

          <TaskbarButton onClick={() => setSosDialogOpen(true)} title="SOS — emergency escalation" ariaLabel="Open SOS">
            <Siren className="h-5 w-5 text-red-600 dark:text-red-400" />
            <span className="hidden text-xs font-semibold text-red-700 sm:inline dark:text-red-400">SOS</span>
          </TaskbarButton>

          <div className="mx-0.5 hidden h-8 w-px shrink-0 bg-slate-200 sm:mx-1 sm:block dark:bg-slate-700" />

          <TaskbarButton onClick={() => setTaskManagerOpen(true)} title="Task manager" ariaLabel="Open task manager">
            <Layers className="h-5 w-5 text-slate-600 dark:text-slate-300" />
          </TaskbarButton>

          <TaskbarButton
            onClick={() => router.push("/shell/file-manager")}
            title="File manager"
            ariaLabel="Open file manager"
          >
            <FolderOpen className="h-5 w-5 text-slate-600 dark:text-slate-300" />
          </TaskbarButton>

          <div className="mx-0.5 hidden h-8 w-px shrink-0 bg-slate-200 sm:mx-1 md:block dark:bg-slate-700" />

          <div className="hidden shrink-0 items-center gap-0.5 sm:flex" title="Pinned apps">
            {pinnedApps.length === 0 ? (
              <span className="px-1 text-[10px] font-medium uppercase tracking-wide text-slate-400">Pins</span>
            ) : (
              pinnedApps.map((app) => {
                const active = pathname === app.href || pathname.startsWith(`${app.href}/`);
                return (
                  <button
                    key={app.appCode}
                    type="button"
                    title={`${app.name} — right-click to unpin`}
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

          <div className="mx-0.5 hidden h-8 w-px shrink-0 bg-slate-200 md:block dark:bg-slate-700" />

          <div className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto py-1">
            <LayoutGrid className="mx-0.5 hidden h-4 w-4 shrink-0 text-slate-400 sm:mx-1 sm:block" aria-hidden />
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

          <div className="ml-auto flex shrink-0 items-center gap-1 border-l border-slate-200 pl-1 dark:border-slate-700 sm:gap-2 sm:pl-2">
            <Link
              href="/home/notifications"
              className="hidden items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-slate-600 hover:bg-slate-100 sm:inline-flex dark:text-slate-300 dark:hover:bg-slate-900"
              title="Notifications"
            >
              Alerts
            </Link>
            <span className="flex items-center rounded-lg border border-slate-200 bg-white/80 px-0.5 py-0.5 shadow-sm dark:border-slate-700 dark:bg-slate-900/80">
              <ShellNotificationTray />
            </span>
            <Link
              href="/home/profile"
              className="inline-flex h-11 items-center gap-1 rounded-lg border border-transparent px-2 hover:bg-slate-100 dark:hover:bg-slate-900"
              title="Profile and settings"
              aria-label="Profile"
            >
              <UserCircle className="h-6 w-6 text-slate-600 dark:text-slate-300" />
              <span className="hidden text-xs font-medium text-slate-700 lg:inline dark:text-slate-200">Profile</span>
            </Link>
            <button
              type="button"
              className="hidden rounded-md px-1.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-400 hover:text-slate-600 xl:block dark:hover:text-slate-300"
              title="Pin Home and File manager to the taskbar"
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
    </>
  );
}
