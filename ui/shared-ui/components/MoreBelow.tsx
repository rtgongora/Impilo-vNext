"use client";

import React, { useCallback, useEffect, useState } from "react";

/**
 * "More below" overflow cue — makes unavoidable scrolling obvious. Shows a
 * compact, non-obstructive chip while scrollable content remains below the
 * fold and disappears when the user reaches the end. Tapping it scrolls the
 * surface forward (keyboard/touch friendly — never hover-dependent).
 */
export interface MoreBelowProps {
  /**
   * Scroll container to observe. Omit to observe the document scrolling
   * element (the single primary page scroll surface).
   */
  targetRef?: React.RefObject<HTMLElement>;
  /** Remaining-content threshold (px) below which the cue hides. */
  threshold?: number;
  label?: string;
  /** `fixed` for page-level scroll; `absolute` inside a relative scroll wrapper. */
  variant?: "fixed" | "absolute";
  className?: string;
}

function remainingBelow(el: HTMLElement): number {
  return el.scrollHeight - el.scrollTop - el.clientHeight;
}

export function MoreBelow({
  targetRef,
  threshold = 48,
  label = "More below",
  variant = "fixed",
  className = "",
}: MoreBelowProps) {
  const [visible, setVisible] = useState(false);

  const resolveTarget = useCallback((): HTMLElement | null => {
    if (targetRef) return targetRef.current;
    if (typeof document === "undefined") return null;
    return (document.scrollingElement as HTMLElement | null) ?? document.documentElement;
  }, [targetRef]);

  useEffect(() => {
    const el = resolveTarget();
    if (!el) return;

    const update = () => setVisible(remainingBelow(el) > threshold);
    update();

    // Page-level scroll events fire on window/document, container scroll on the element.
    const scrollHost: EventTarget = targetRef ? el : window;
    scrollHost.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);

    // Content growth/shrink (async data, collapsing sections) re-evaluates the cue.
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(update) : null;
    ro?.observe(el);

    return () => {
      scrollHost.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
      ro?.disconnect();
    };
  }, [resolveTarget, targetRef, threshold]);

  if (!visible) return null;

  const positionClass =
    variant === "fixed"
      ? "fixed inset-x-0 z-40 flex justify-center"
      : "absolute inset-x-0 bottom-2 z-10 flex justify-center";
  const positionStyle: React.CSSProperties =
    variant === "fixed"
      ? { bottom: "calc(max(var(--shell-taskbar-height, 0px), env(safe-area-inset-bottom, 0px)) + 8px)" }
      : {};

  return (
    <div className={`pointer-events-none ${positionClass} ${className}`} style={positionStyle}>
      <button
        type="button"
        onClick={() => {
          const el = resolveTarget();
          if (!el) return;
          const top = el.scrollTop + el.clientHeight * 0.8;
          if (typeof el.scrollTo === "function") {
            el.scrollTo({ top, behavior: "smooth" });
          } else {
            el.scrollTop = top;
          }
        }}
        aria-label="More content below — scroll down"
        data-testid="more-below-cue"
        className={
          "pointer-events-auto inline-flex items-center gap-1.5 rounded-full border border-[color:var(--border-soft)] " +
          "bg-[color:var(--surface)]/95 px-3 py-1.5 text-[11px] font-medium text-[color:var(--text-secondary)] " +
          "shadow-impilo-floating backdrop-blur-glass transition hover:text-[color:var(--primary-hover)] " +
          "[.low-blur_&]:bg-[color:var(--surface)] [.low-blur_&]:backdrop-blur-none"
        }
      >
        {label}
        <span aria-hidden="true">↓</span>
      </button>
    </div>
  );
}
