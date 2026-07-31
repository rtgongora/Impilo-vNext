"use client";

import { AlertTriangle } from "lucide-react";

/**
 * One concern on the medical ward workspace (brief.md §13).
 *
 * Four honest states — never collapse "could not read" into "none":
 * - loaded: data rendered in children
 * - empty: read succeeded, record is genuinely empty
 * - unavailable: read failed
 * - notComposed: no SoR/read wired on this surface yet
 */
export type WardPanelStatus = "loaded" | "empty" | "unavailable" | "notComposed";

export interface WardPanelProps {
  title: string;
  testId: string;
  status: WardPanelStatus;
  emptyMessage?: string;
  unavailableMessage?: string;
  children?: React.ReactNode;
}

export function WardPanel({
  title,
  testId,
  status,
  emptyMessage = "Nothing recorded.",
  unavailableMessage = "Could not be read — this is not a statement that there is none.",
  children,
}: WardPanelProps) {
  return (
    <div
      className="rounded-lg border border-border bg-card p-4 space-y-2"
      data-testid={testId}
      data-panel-status={status}
    >
      <h3 className="font-semibold text-sm">{title}</h3>
      {status === "notComposed" && (
        <p className="text-sm text-muted-foreground" data-testid={`${testId}-not-composed`}>
          Not composed — no SoR/read on this surface yet
        </p>
      )}
      {status === "unavailable" && (
        <p className="text-sm text-danger" data-testid={`${testId}-unavailable`}>
          {unavailableMessage}
        </p>
      )}
      {status === "empty" && (
        <p className="text-sm text-muted-foreground" data-testid={`${testId}-empty`}>
          {emptyMessage}
        </p>
      )}
      {status === "loaded" && children}
    </div>
  );
}

export function WardPanelNotBuiltGrid({ items }: { items: string[] }) {
  return (
    <div className="flex items-start gap-2 rounded border border-warning/30 bg-amber-50/50 p-2">
      <AlertTriangle className="w-3.5 h-3.5 text-warning-foreground mt-0.5 shrink-0" />
      <p className="text-xs text-muted-foreground">
        {items.length} concern(s) on this ward workspace are not yet composed from a source of
        record.
      </p>
    </div>
  );
}
