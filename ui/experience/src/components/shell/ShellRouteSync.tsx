"use client";

import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { deriveRouteTaskMeta } from "@/lib/shell/route-task-meta";
import { shouldShowExperienceShell } from "@/lib/shell/shell-visibility";
import { SHELL_TASKBAR_HEIGHT_PX } from "@/lib/shell/app-registry";

/**
 * Hydrates shell persistence, syncs open tasks with routing, clears shell on logout,
 * and publishes taskbar height as a CSS variable for other overlays.
 */
export function ShellRouteSync() {
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const hydrateFromSession = useShellStore((s) => s.hydrateFromSession);
  const touchRouteTask = useShellStore((s) => s.touchRouteTask);
  const clearSessionShellState = useShellStore((s) => s.clearSessionShellState);
  const wasAuthenticated = useRef(false);

  useEffect(() => {
    hydrateFromSession();
  }, [hydrateFromSession]);

  useEffect(() => {
    if (wasAuthenticated.current && !isAuthenticated) {
      clearSessionShellState();
    }
    wasAuthenticated.current = isAuthenticated;
  }, [isAuthenticated, clearSessionShellState]);

  useEffect(() => {
    const show = shouldShowExperienceShell(pathname, isAuthenticated);
    if (typeof document !== "undefined") {
      document.documentElement.style.setProperty(
        "--shell-taskbar-height",
        show ? `${SHELL_TASKBAR_HEIGHT_PX}px` : "0px",
      );
    }
    if (!show || !pathname) return;

    const meta = deriveRouteTaskMeta(pathname);
    touchRouteTask({
      route: pathname,
      title: meta.title,
      appId: meta.appId,
      taskType: meta.taskType,
      contextRef: meta.contextRef,
    });
  }, [pathname, isAuthenticated, touchRouteTask]);

  return null;
}
