import React from "react";

/**
 * Full-height workspace — lets an opened application (table, board, map,
 * course builder, clinical panel) occupy the full remaining viewport instead
 * of flowing as a long document. Pair with `WorkspaceScrollPane` for the one
 * internal region that scrolls; everything else (headers, action bars) stays
 * put, avoiding nested/competing scrollbars.
 *
 * Requires an ancestor that constrains height (the shell's `<main>` does).
 */
export interface FullHeightWorkspaceProps {
  children: React.ReactNode;
  className?: string;
  "data-testid"?: string;
}

export function FullHeightWorkspace({
  children,
  className = "",
  "data-testid": testId,
}: FullHeightWorkspaceProps) {
  return (
    <section className={`flex h-full min-h-0 flex-1 flex-col ${className}`} data-testid={testId}>
      {children}
    </section>
  );
}

export interface WorkspaceScrollPaneProps {
  children: React.ReactNode;
  className?: string;
  /** Accessible name when the pane is a standalone scroll region. */
  "aria-label"?: string;
}

/** The single scrolling region inside a FullHeightWorkspace. */
export function WorkspaceScrollPane({
  children,
  className = "",
  "aria-label": ariaLabel,
}: WorkspaceScrollPaneProps) {
  return (
    <div
      className={`min-h-0 flex-1 overflow-auto ${className}`}
      aria-label={ariaLabel}
      // Scroll regions must be keyboard-reachable when they can overflow.
      tabIndex={ariaLabel ? 0 : undefined}
      role={ariaLabel ? "region" : undefined}
    >
      {children}
    </div>
  );
}
