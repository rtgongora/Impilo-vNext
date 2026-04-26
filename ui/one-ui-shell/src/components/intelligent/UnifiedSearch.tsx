"use client";

/**
 * Legacy federated search overlay — superseded by the Experience shell search palette (`ShellSearchPalette`),
 * opened globally via Ctrl/Cmd+K from `ShellChrome`.
 *
 * Mounting `UnifiedSearch` opens the shell search surface. Prefer calling
 * `useShellStore.getState().setSearchOpen(true)` directly in new code.
 */

import { useEffect } from "react";
import { useShellStore } from "@/hooks/useShellStore";

interface UnifiedSearchProps {
  onClose?: () => void;
}

export function UnifiedSearch({ onClose }: UnifiedSearchProps) {
  useEffect(() => {
    useShellStore.getState().setSearchOpen(true);
    return () => {
      useShellStore.getState().setSearchOpen(false);
      onClose?.();
    };
  }, [onClose]);

  return null;
}

/**
 * Optional local Ctrl/Cmd+K hook when a page needs its own handler.
 * The global shell already registers Ctrl/Cmd+K when the shell is visible — avoid double registration.
 */
export function useSearchShortcut(onOpen: () => void) {
  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (!(e.metaKey || e.ctrlKey) || (e.key !== "k" && e.key !== "K")) return;
      const target = e.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) {
        return;
      }
      e.preventDefault();
      onOpen();
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onOpen]);
}
