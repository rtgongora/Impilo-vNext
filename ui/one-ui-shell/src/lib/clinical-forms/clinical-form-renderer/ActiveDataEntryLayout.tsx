"use client";

import type { ReactNode } from "react";

/**
 * When a structured form is primary, collapse contextual chrome into a compact side rail
 * so active fields are never buried below dashboard-style cards.
 */

export function ActiveDataEntryLayout(props: {
  active: boolean;
  /** Main structured form */
  primary: ReactNode;
  /** Patient / journey context — full when inactive, compact rail when active */
  contextRail: ReactNode;
  /** Optional alerts strip (always slim) */
  alerts?: ReactNode;
  onRequestExit?: () => void;
}) {
  const { active, primary, contextRail, alerts, onRequestExit } = props;
  return (
    <div className={active ? "flex flex-col gap-3 lg:flex-row lg:items-start" : "space-y-4"}>
      {active && (
        <div className="lg:w-56 shrink-0 order-2 lg:order-1 space-y-2">
          {onRequestExit && (
            <button
              type="button"
              onClick={onRequestExit}
              className="w-full text-xs font-medium text-primary hover:text-impilo-800 py-1.5 px-2 rounded border border-primary/25 bg-card"
            >
              Exit focused entry
            </button>
          )}
          <div className="rounded-lg border border-border bg-background/80 p-2 text-xs text-foreground max-h-[70vh] overflow-y-auto">
            {contextRail}
          </div>
        </div>
      )}
      <div className={`min-w-0 flex-1 order-1 ${active ? "lg:order-2" : ""}`}>
        {!active && alerts}
        {!active && <div className="mb-3">{contextRail}</div>}
        {active && alerts && <div className="mb-2">{alerts}</div>}
        <div className={active ? "rounded-xl border-2 border-impilo-300 shadow-sm bg-card p-4 ring-1 ring-impilo-100" : ""}>{primary}</div>
      </div>
    </div>
  );
}
