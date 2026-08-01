"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLayoutPrefsStore } from "@/hooks/useLayoutPrefsStore";
import { useShellStore } from "@/hooks/useShellStore";
import { shouldShowExperienceShell } from "@/lib/shell/shell-visibility";
import { EmergencyHelpButton } from "@/components/public/EmergencyHelpButton";
import { ShellEhrTaskEnricher } from "./ShellEhrTaskEnricher";
import { ShellRouteSync } from "./ShellRouteSync";
import { ShellScrollRestoration } from "./ShellScrollRestoration";
import { ShellSearchPalette } from "./ShellSearchPalette";
import { ShellStartMenu } from "./ShellStartMenu";
import { ShellTaskbar } from "./ShellTaskbar";
import { ShellTaskManagerModal } from "./ShellTaskManagerModal";
import { ProactiveAssistant } from "@/components/intelligent/ProactiveAssistant";
import { HealthIdStatusChip } from "./HealthIdStatusChip";

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
  const setNavDrawerOpen = useShellStore((s) => s.setNavDrawerOpen);
  const setSosDialogOpen = useShellStore((s) => s.setSosDialogOpen);

  const show = shouldShowExperienceShell(pathname, isAuthenticated);

  useEffect(() => {
    if (!show) {
      setStartOpen(false);
      setSearchOpen(false);
      setNavDrawerOpen(false);
      setSosDialogOpen(false);
    }
  }, [show, setSearchOpen, setStartOpen, setNavDrawerOpen, setSosDialogOpen]);

  useEffect(() => {
    if (!show) return;

    function onKeyDown(e: KeyboardEvent) {
      // Taskbar minimise/restore — works during data entry too (fields keep focus).
      if ((e.key === "b" || e.key === "B") && e.ctrlKey && e.altKey) {
        e.preventDefault();
        useLayoutPrefsStore.getState().toggleTaskbarMinimized();
        return;
      }

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
        setNavDrawerOpen(false);
        setSosDialogOpen(false);
        useShellStore.getState().setTaskManagerOpen(false);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [show, setSearchOpen, setStartOpen, setNavDrawerOpen, setSosDialogOpen]);

  // Persistent Emergency Help (gateway doctrine §7): visible before and after login on
  // every surface this chrome reaches. /welcome/** already mounts it via PublicShell.
  // The public header carries a persistent Emergency action, so the floating button is a
  // duplicate on those routes. /welcome/** was already excluded; the landing root renders
  // the same PublicHeader and was not, which is why "/" showed two of them on desktop.
  // PublicShell still mounts its own for narrow viewports, where the header condenses.
  const showEmergencyHelp = !pathname?.startsWith("/welcome") && pathname !== "/";

  return (
    <>
      <ShellRouteSync />
      <ShellScrollRestoration />
      {showEmergencyHelp ? <EmergencyHelpButton raised={show} className={show ? "lg:hidden" : ""} /> : null}
      {show ? (
        <>
          <ShellEhrTaskEnricher />
          <HealthIdStatusChip />
          <ShellTaskbar />
        </>
      ) : null}
      {show && startOpen ? <ShellStartMenu /> : null}
      {show && searchOpen ? <ShellSearchPalette /> : null}
      {show ? <ShellTaskManagerModal /> : null}
      {show ? <ProactiveAssistant /> : null}
    </>
  );
}
