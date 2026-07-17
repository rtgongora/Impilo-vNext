"use client";

import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";

const SCROLLER_SELECTOR = '[data-shell-scroll="main"]';

function historyKey(): string {
  try {
    const key = (window.history.state as { key?: string } | null)?.key;
    return key ?? window.location.pathname;
  } catch {
    return window.location.pathname;
  }
}

function getScroller(): HTMLElement | null {
  return document.querySelector<HTMLElement>(SCROLLER_SELECTOR);
}

/**
 * ShellScrollRestoration — restores the scroll position of the custom AppLayout
 * workspace scroller (`<main data-shell-scroll="main">`) across browser back/forward.
 *
 * Why this exists: Next.js App Router only restores the WINDOW scroll position on
 * back/forward. AppLayout's `<main>` is a nested `overflow-auto` scroll container, so
 * navigating back to a long list inside it would otherwise reset to the top. Public
 * pages (PublicShell) scroll the window and are already handled by Next's default —
 * this component only manages the marked custom scroller and no-ops when it's absent.
 *
 * Strategy: continuously (throttled via rAF) save the scroller's scrollTop into a map
 * keyed by the current history entry (`history.state.key`, falling back to pathname).
 * On `popstate` (back/forward), restore the saved value for the entry after a rAF so
 * the new content has laid out. On a push navigation (no popstate fired), scroll the
 * scroller to the top.
 *
 * Limitation: positions live in-memory only, so they are not restored across a full
 * page reload or a new browser session.
 */
export function ShellScrollRestoration() {
  const pathname = usePathname();
  const positionsRef = useRef<Map<string, number>>(new Map());
  // Set true by the popstate listener so the per-navigation effect restores the
  // saved position instead of scrolling to the top. Consumed (reset) each navigation.
  const poppedRef = useRef(false);

  // Listener effect: subscribe once. Continuously record the scroller position for
  // the current history entry, and flag back/forward navigations.
  useEffect(() => {
    if (typeof window === "undefined") return;

    let rafId = 0;
    const positions = positionsRef.current;

    const onScroll = () => {
      if (rafId) return;
      rafId = window.requestAnimationFrame(() => {
        rafId = 0;
        const scroller = getScroller();
        if (scroller) positions.set(historyKey(), scroller.scrollTop);
      });
    };

    const onPopState = () => {
      poppedRef.current = true;
    };

    // Capture phase so scrolls inside the nested scroller are observed (scroll does
    // not bubble to window in the normal phase).
    window.addEventListener("scroll", onScroll, { capture: true, passive: true });
    window.addEventListener("popstate", onPopState);

    return () => {
      if (rafId) window.cancelAnimationFrame(rafId);
      window.removeEventListener("scroll", onScroll, { capture: true } as EventListenerOptions);
      window.removeEventListener("popstate", onPopState);
    };
  }, []);

  // Per-navigation effect: after the new route commits, restore (on back/forward) or
  // reset (on push) the scroller. A rAF lets the new content lay out first, and we
  // re-query the scroller because AppLayout can mount/unmount between route groups.
  useEffect(() => {
    if (typeof window === "undefined") return;

    const wasPopped = poppedRef.current;
    poppedRef.current = false;
    const positions = positionsRef.current;

    const raf = window.requestAnimationFrame(() => {
      const scroller = getScroller();
      if (!scroller) return;
      if (wasPopped) {
        scroller.scrollTop = positions.get(historyKey()) ?? 0;
      } else {
        scroller.scrollTop = 0;
      }
    });

    return () => window.cancelAnimationFrame(raf);
  }, [pathname]);

  return null;
}
