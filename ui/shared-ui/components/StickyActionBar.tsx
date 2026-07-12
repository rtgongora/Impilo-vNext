import React from "react";

/**
 * Sticky workflow action bar — keeps Save/Continue/Submit reachable on long forms
 * without hiding content. Sticks to the bottom of the primary scrolling surface,
 * offset above the shell taskbar (`--shell-taskbar-height`) and mobile safe areas.
 *
 * One per screen: it is the single terminal action row for the active workflow.
 */
export interface StickyActionBarProps {
  /** Primary/secondary action buttons (right-aligned). */
  children: React.ReactNode;
  /** Contextual status — validation summary, autosave state, step count (left slot). */
  status?: React.ReactNode;
  className?: string;
  "aria-label"?: string;
  "data-testid"?: string;
}

export function StickyActionBar({
  children,
  status,
  className = "",
  "aria-label": ariaLabel = "Workflow actions",
  "data-testid": testId = "sticky-action-bar",
}: StickyActionBarProps) {
  return (
    <div
      className={`sticky z-30 -mx-1 mt-4 px-1 pb-1 ${className}`}
      style={{ bottom: "max(var(--shell-taskbar-height, 0px), env(safe-area-inset-bottom, 0px))" }}
      data-testid={testId}
    >
      <div
        role="toolbar"
        aria-label={ariaLabel}
        className={
          "flex flex-wrap items-center gap-2 rounded-xl border border-[color:var(--border-soft)] " +
          "bg-[color:var(--surface)]/95 px-3 py-2 shadow-impilo-floating backdrop-blur-glass " +
          "supports-[not(backdrop-filter:blur(0px))]:bg-[color:var(--surface)] " +
          "[.low-blur_&]:bg-[color:var(--surface)] [.low-blur_&]:backdrop-blur-none"
        }
      >
        {status ? (
          <div className="min-w-0 flex-1 text-xs text-[color:var(--text-muted)]">{status}</div>
        ) : null}
        <div className={`flex flex-wrap items-center justify-end gap-2 ${status ? "" : "ml-auto"}`}>
          {children}
        </div>
      </div>
    </div>
  );
}
