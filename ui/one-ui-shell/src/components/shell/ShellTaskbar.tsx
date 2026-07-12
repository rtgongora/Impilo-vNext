"use client";

import { type ReactNode, useCallback, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import {
  Building2,
  ChevronDown,
  ChevronUp,
  FolderOpen,
  Headphones,
  Layers,
  LayoutGrid,
  LifeBuoy,
  MessageSquare,
  PanelBottomClose,
  Search,
  Siren,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLayoutPrefsStore } from "@/hooks/useLayoutPrefsStore";
import { useShellStore } from "@/hooks/useShellStore";
import {
  findShellAppByCode,
  listVisibleShellApps,
  SHELL_DOCK_BOTTOM_INSET_PX,
  SHELL_DOCK_HEIGHT_PX,
} from "@/lib/shell/app-registry";
import { deriveRouteTaskMeta } from "@/lib/shell/route-task-meta";
import type { AppDefinition, RunningTask } from "@/lib/shell/types";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { ShellIcon } from "./ShellIcon";
import { ShellSosDialog } from "./ShellSosDialog";
import { ShellAccessibilityMenu } from "./ShellAccessibilityMenu";
import { ShellDockContextMenu, type ShellDockMenuItem } from "./ShellDockContextMenu";
import { ShellTaskPreview } from "./ShellTaskPreview";

function DockButton({
  onClick,
  ariaLabel,
  children,
  className = "",
}: {
  onClick?: () => void;
  ariaLabel: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-10 shrink-0 items-center gap-1.5 rounded-xl border border-transparent px-2 transition hover:bg-[color:var(--primary-soft)]/80 ${className}`}
      aria-label={ariaLabel}
    >
      {children}
    </button>
  );
}

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
  const closeOtherTasks = useShellStore((s) => s.closeOtherTasks);
  const togglePinApp = useShellStore((s) => s.togglePinApp);
  const pinApp = useShellStore((s) => s.pinApp);
  const openNewTaskInstance = useShellStore((s) => s.openNewTaskInstance);
  const launchApp = useShellStore((s) => s.launchApp);

  const sosDialogOpen = useShellStore((s) => s.sosDialogOpen);
  const setSosDialogOpen = useShellStore((s) => s.setSosDialogOpen);

  const taskbarMinimized = useLayoutPrefsStore((s) => s.taskbarMinimized);
  const focusMode = useLayoutPrefsStore((s) => s.focusMode);
  const setTaskbarMinimized = useLayoutPrefsStore((s) => s.setTaskbarMinimized);
  const setFocusMode = useLayoutPrefsStore((s) => s.setFocusMode);

  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    items: ShellDockMenuItem[];
  } | null>(null);
  const [preview, setPreview] = useState<{ task: RunningTask; x: number; y: number } | null>(null);

  const apps = listVisibleShellApps(hasRole);
  const pinnedApps = apps.filter((a) => pinnedAppCodes.includes(a.appCode));
  const openOrdered = [...openTasks].sort(
    (a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime(),
  );

  const openContextMenu = useCallback((e: React.MouseEvent, items: ShellDockMenuItem[]) => {
    e.preventDefault();
    setPreview(null);
    setContextMenu({ x: e.clientX, y: e.clientY, items });
  }, []);

  const pinnedMenuItems = useCallback(
    (app: AppDefinition): ShellDockMenuItem[] => {
      const pinned = pinnedAppCodes.includes(app.appCode);
      return [
        {
          id: "open",
          label: "Open",
          onSelect: () => launchApp(app, router.push),
        },
        {
          id: "open-another",
          label: "Open another instance",
          onSelect: () => {
            const meta = deriveRouteTaskMeta(app.href);
            openNewTaskInstance({
              route: app.href,
              title: app.name,
              appId: meta.appId,
              taskType: meta.taskType,
              contextRef: meta.contextRef,
            });
            router.push(app.href);
          },
        },
        {
          id: "pin",
          label: pinned ? "Unpin from dock" : "Pin to dock",
          onSelect: () => (pinned ? togglePinApp(app.appCode) : pinApp(app.appCode)),
        },
      ];
    },
    [launchApp, openNewTaskInstance, pinApp, pinnedAppCodes, router, togglePinApp],
  );

  const taskMenuItems = useCallback(
    (task: RunningTask): ShellDockMenuItem[] => {
      const meta = deriveRouteTaskMeta(task.route);
      return [
        {
          id: "open",
          label: "Open",
          onSelect: () => {
            if (task.status === "minimized") {
              useShellStore.getState().restoreTask(task.id);
            }
            setActiveTask(task.id);
            router.push(task.route);
          },
        },
        {
          id: "open-another",
          label: "Open another instance",
          onSelect: () => {
            openNewTaskInstance({
              route: task.route,
              title: task.title,
              appId: task.appId,
              taskType: task.taskType,
              contextRef: task.contextRef ?? meta.contextRef,
            });
            router.push(task.route);
          },
        },
        {
          id: "minimize",
          label: "Minimize",
          onSelect: () => minimizeTask(task.id),
        },
        {
          id: "close",
          label: "Close",
          destructive: true,
          onSelect: () => closeTask(task.id),
        },
        {
          id: "close-others",
          label: "Close others",
          destructive: true,
          onSelect: () => closeOtherTasks(task.id),
        },
      ];
    },
    [closeOtherTasks, closeTask, minimizeTask, openNewTaskInstance, router, setActiveTask],
  );

  // Minimised: dock collapses to a small restore handle so focused work
  // (data entry, consultations, wizards) gets the full viewport height.
  if (taskbarMinimized || focusMode) {
    return (
      <>
        <ShellSosDialog open={sosDialogOpen} onClose={() => setSosDialogOpen(false)} />
        <div
          className="fixed right-3 z-[10000]"
          style={{ bottom: `max(6px, env(safe-area-inset-bottom, 0px))` }}
        >
          <button
            type="button"
            onClick={() => {
              setTaskbarMinimized(false);
              setFocusMode(false);
            }}
            aria-label="Show taskbar (Ctrl+Alt+B)"
            title="Show taskbar (Ctrl+Alt+B)"
            data-testid="shell-taskbar-handle"
            className="flex h-8 items-center gap-1 rounded-full border border-[color:var(--border-soft)] bg-[color:var(--surface)]/95 px-2.5 shadow-impilo-floating backdrop-blur-xl transition hover:bg-[color:var(--primary-soft)]"
          >
            <ImpiloBrandLogo variant="mark" size={16} className="h-4 w-4" />
            <ChevronUp className="h-3.5 w-3.5 text-muted-foreground" />
          </button>
        </div>
      </>
    );
  }

  return (
    <>
      <ShellSosDialog open={sosDialogOpen} onClose={() => setSosDialogOpen(false)} />
      <ShellDockContextMenu
        open={contextMenu !== null}
        x={contextMenu?.x ?? 0}
        y={contextMenu?.y ?? 0}
        items={contextMenu?.items ?? []}
        onClose={() => setContextMenu(null)}
      />
      {preview ? <ShellTaskPreview task={preview.task} x={preview.x} y={preview.y} /> : null}

      <div
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[10000] flex justify-center px-2 sm:px-4"
        style={{ paddingBottom: `max(${SHELL_DOCK_BOTTOM_INSET_PX}px, env(safe-area-inset-bottom, 0px))` }}
      >
        <nav
          className="shell-floating-dock pointer-events-auto flex w-full max-w-[min(96vw,90rem)] items-center gap-0.5 overflow-x-auto rounded-2xl border border-[color:var(--border-soft)] bg-[color:var(--surface)]/92 px-1.5 text-foreground shadow-impilo-floating backdrop-blur-xl sm:gap-1 sm:px-2"
          style={{ height: SHELL_DOCK_HEIGHT_PX }}
          role="navigation"
          aria-label="Experience shell"
          data-testid="shell-floating-dock"
        >
          <DockButton onClick={() => toggleStart()} ariaLabel="Start menu">
            <ImpiloBrandLogo variant="mark" size={24} className="h-6 w-6" />
            <span className="hidden text-xs font-semibold text-foreground sm:inline">Start</span>
          </DockButton>

          <DockButton onClick={() => toggleSearch()} ariaLabel="Open search and commands">
            <Search className="h-4 w-4 text-muted-foreground" />
            <span className="hidden text-xs font-medium text-foreground sm:inline">Search</span>
          </DockButton>

          <details className="relative shrink-0">
            <summary className="flex h-10 cursor-pointer list-none items-center gap-1 rounded-xl border border-transparent px-2 marker:hidden hover:bg-[color:var(--primary-soft)]/80 [&::-webkit-details-marker]:hidden">
              <Building2 className="h-4 w-4 text-muted-foreground" />
              <span className="hidden text-xs font-medium text-foreground lg:inline">Context</span>
              <ChevronDown className="hidden h-3.5 w-3.5 text-muted-foreground lg:inline" aria-hidden />
            </summary>
            <div className="absolute bottom-full left-0 z-[10001] mb-2 min-w-[200px] rounded-xl border border-border bg-card py-1 text-sm shadow-xl">
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-background"
                onClick={() => {
                  router.push("/facility");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Facility…
              </button>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-background"
                onClick={() => {
                  router.push("/workspace");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Workspace…
              </button>
              <button
                type="button"
                className="block w-full px-3 py-2 text-left hover:bg-background"
                onClick={() => {
                  router.push("/shift");
                  (document.activeElement as HTMLElement | null)?.blur?.();
                }}
              >
                Shift…
              </button>
            </div>
          </details>

          <DockButton
            onClick={() => router.push("/communication/secure-messaging")}
            ariaLabel="Open communications hub"
          >
            <MessageSquare className="h-4 w-4 text-muted-foreground" />
            <span className="hidden text-xs font-medium text-foreground xl:inline">Comms</span>
          </DockButton>

          <DockButton onClick={() => router.push("/support/knowledge-base")} ariaLabel="Open help">
            <LifeBuoy className="h-4 w-4 text-muted-foreground" />
            <span className="hidden text-xs font-medium text-foreground xl:inline">Help</span>
          </DockButton>

          <DockButton onClick={() => router.push("/ask")} ariaLabel="Open Nompilo Ask">
            <span className="text-xs font-semibold text-primary">Nompilo</span>
          </DockButton>

          <DockButton onClick={() => router.push("/support/tickets")} ariaLabel="Open system support">
            <Headphones className="h-4 w-4 text-muted-foreground" />
            <span className="hidden text-xs font-medium text-foreground 2xl:inline">Support</span>
          </DockButton>

          <DockButton onClick={() => setSosDialogOpen(true)} ariaLabel="Open SOS">
            <Siren className="h-4 w-4 text-red-600" />
            <span className="hidden text-xs font-semibold text-danger sm:inline">SOS</span>
          </DockButton>

          <ShellAccessibilityMenu />

          <div className="mx-0.5 hidden h-7 w-px shrink-0 bg-border sm:block" />

          <DockButton onClick={() => setTaskManagerOpen(true)} ariaLabel="Open modules">
            <Layers className="h-4 w-4 text-muted-foreground" />
            <span className="hidden text-xs font-medium text-foreground lg:inline">Modules</span>
          </DockButton>

          {(hasRole("SUPER_ADMIN") || hasRole("SYSTEM_ADMIN") || hasRole("DEVELOPER")) && (
            <DockButton onClick={() => router.push("/platform/all-features")} ariaLabel="Open all features catalog">
              <LayoutGrid className="h-4 w-4 text-muted-foreground" />
              <span className="hidden text-xs font-medium text-foreground xl:inline">Features</span>
            </DockButton>
          )}

          <DockButton onClick={() => router.push("/shell/file-manager")} ariaLabel="Open file manager">
            <FolderOpen className="h-4 w-4 text-muted-foreground" />
          </DockButton>

          <div className="mx-0.5 hidden h-7 w-px shrink-0 bg-border md:block" />

          <div className="hidden shrink-0 items-center gap-0.5 sm:flex" aria-label="Pinned apps">
            {pinnedApps.length === 0 ? (
              <span className="px-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">Pins</span>
            ) : (
              pinnedApps.map((app) => {
                const active = pathname === app.href || pathname.startsWith(`${app.href}/`);
                return (
                  <button
                    key={app.appCode}
                    type="button"
                    aria-label={`${app.name}${active ? " (active)" : ""}`}
                    onClick={() => launchApp(app, router.push)}
                    onContextMenu={(e) => openContextMenu(e, pinnedMenuItems(app))}
                    className={`flex h-9 w-9 items-center justify-center rounded-lg border transition-colors ${
                      active
                        ? "border-impilo-400 bg-primary-soft text-impilo-800"
                        : "border-transparent hover:bg-primary-soft"
                    }`}
                  >
                    <ShellIcon name={app.icon} className="h-4 w-4" />
                  </button>
                );
              })
            )}
          </div>

          <div className="mx-0.5 hidden h-7 w-px shrink-0 bg-border md:block" />

          <div className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto py-0.5" aria-label="Open tasks">
            <LayoutGrid className="mx-0.5 hidden h-3.5 w-3.5 shrink-0 text-muted-foreground sm:mx-1 sm:block" aria-hidden />
            {openOrdered.map((task) => {
              const active =
                (task.id === activeTaskId && task.status === "open") ||
                (task.status === "open" && pathname === task.route);
              return (
                <button
                  key={task.id}
                  type="button"
                  aria-label={`${task.title}${active ? " (active)" : task.status === "minimized" ? " (minimized)" : ""}`}
                  onClick={() => {
                    if (task.status === "minimized") {
                      useShellStore.getState().restoreTask(task.id);
                    }
                    setActiveTask(task.id);
                    router.push(task.route);
                  }}
                  onContextMenu={(e) => openContextMenu(e, taskMenuItems(task))}
                  onMouseEnter={(e) => setPreview({ task, x: e.clientX, y: e.clientY })}
                  onMouseLeave={() => setPreview(null)}
                  className={`group flex max-w-[240px] shrink-0 items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-left text-xs transition-colors ${
                    active
                      ? "border-impilo-500 bg-primary-hover text-white"
                      : task.status === "minimized"
                        ? "border-dashed border-border bg-background text-muted-foreground"
                        : "border-border bg-card hover:border-border"
                  }`}
                >
                  <ShellIcon name="LayoutGrid" className="h-3.5 w-3.5 shrink-0 opacity-70" />
                  <span className="truncate font-medium">{task.title}</span>
                  <span
                    role="button"
                    tabIndex={0}
                    className="ml-auto rounded p-0.5 opacity-60 hover:bg-black/10 group-hover:opacity-100"
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
                    aria-label={`Close ${task.title}`}
                  >
                    ×
                  </span>
                </button>
              );
            })}
          </div>

          <button
            type="button"
            className="hidden shrink-0 rounded-md px-1.5 py-1 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground hover:text-foreground xl:block"
            aria-label="Pin default apps to dock"
            onClick={() => {
              const home = findShellAppByCode("home");
              const fm = findShellAppByCode("shell_file_manager");
              if (home) pinApp(home.appCode);
              if (fm) pinApp(fm.appCode);
            }}
          >
            Pin defaults
          </button>

          <DockButton
            onClick={() => setTaskbarMinimized(true)}
            ariaLabel="Minimize taskbar (Ctrl+Alt+B)"
          >
            <PanelBottomClose className="h-4 w-4 text-muted-foreground" />
          </DockButton>
        </nav>
      </div>
    </>
  );
}
