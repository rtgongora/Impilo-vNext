"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShellStore } from "@/hooks/useShellStore";
import { shouldShowExperienceShell } from "@/lib/shell/shell-visibility";
import { ShellRouteSync } from "./ShellRouteSync";
import { ShellSearchPalette } from "./ShellSearchPalette";
import { ShellStartMenu } from "./ShellStartMenu";
import { ShellTaskbar } from "./ShellTaskbar";
import { ShellTaskManagerModal } from "./ShellTaskManagerModal";

/**
 * Global OS-like shell: route sync + bottom taskbar + Start + search + task manager overlay.
 */
export function ShellChrome() {
  const pathname = usePathname();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const startOpen = useShellStore((s) => s.startOpen);
  const searchOpen = useShellStore((s) => s.searchOpen);
  const setStartOpen = useShellStore((s) => s.setStartOpen);
  const setSearchOpen = useShellStore((s) => s.setSearchOpen);

  const show = shouldShowExperienceShell(pathname, isAuthenticated);

  useEffect(() => {
    if (!show) {
      setStartOpen(false);
      setSearchOpen(false);
    }
  }, [show, setSearchOpen, setStartOpen]);

  useEffect(() => {
    if (!show) return;

    function onKeyDown(e: KeyboardEvent) {
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) {
        if ((e.key === "k" || e.key === "K") && (e.ctrlKey || e.metaKey)) {
          e.preventDefault();
          useShellStore.getState().toggleSearch();
        }
        return;
      }

      if ((e.key === "k" || e.key === "K") && (e.ctrlKey || e.metaKey)) {
        e.preventDefault();
        useShellStore.getState().toggleSearch();
      }

      if (e.key === "Escape") {
        setStartOpen(false);
        setSearchOpen(false);
        useShellStore.getState().setTaskManagerOpen(false);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [show, setSearchOpen, setStartOpen]);

  return (
    <>
      <ShellRouteSync />
      {show ? <ShellTaskbar /> : null}
      {show && startOpen ? <ShellStartMenu /> : null}
      {show && searchOpen ? <ShellSearchPalette /> : null}
      {show ? <ShellTaskManagerModal /> : null}
    </>
  );
}
