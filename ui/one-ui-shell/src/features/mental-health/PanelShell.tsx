"use client";

/**
 * One card per clinical record type, with the three states kept visibly distinct.
 *
 * The distinction this component exists for: **"nothing recorded" and "we could not read it" must
 * never look the same.** On a mental-health record they are opposite clinical facts. An empty
 * restraint list means the patient was not restrained; an unreadable one means nobody knows. A
 * panel that renders both as blank space invites a clinician to conclude the first when the truth
 * is the second — and mental-health-service is undeployed today, so the second is what actually
 * happens in the preview estate.
 */

import type { ReactNode } from "react";

import { bffErrorMessage } from "@/lib/bff-errors";

export interface PanelShellProps {
  title: string;
  /** What the record is, in a clinician's words — used verbatim in the failure sentence. */
  subject: string;
  description?: string;
  isLoading: boolean;
  isError: boolean;
  error?: unknown;
  /** True when the read succeeded and returned nothing. */
  isEmpty: boolean;
  /** Said when the read succeeded and there is genuinely nothing — a clinical fact, not a blank. */
  emptyMessage: string;
  /** Recording controls, shown regardless of whether the read succeeded. */
  actions?: ReactNode;
  children?: ReactNode;
  "data-testid"?: string;
}

export function PanelShell({
  title,
  subject,
  description,
  isLoading,
  isError,
  error,
  isEmpty,
  emptyMessage,
  actions,
  children,
  "data-testid": testId,
}: PanelShellProps) {
  return (
    <section className="rounded-xl border border-border bg-card p-4" data-testid={testId}>
      <header className="mb-3">
        <h2 className="text-sm font-semibold text-foreground">{title}</h2>
        {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
      </header>

      {isLoading ? (
        <p className="text-sm text-muted-foreground" data-testid={testId ? `${testId}-loading` : undefined}>
          Loading…
        </p>
      ) : isError ? (
        <p
          className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger"
          data-testid={testId ? `${testId}-unreadable` : undefined}
        >
          {bffErrorMessage(error, subject)}
        </p>
      ) : isEmpty ? (
        <p
          className="rounded border border-dashed border-border px-3 py-2 text-sm text-muted-foreground"
          data-testid={testId ? `${testId}-empty` : undefined}
        >
          {emptyMessage}
        </p>
      ) : (
        children
      )}

      {actions ? <div className="mt-4 border-t border-border pt-4">{actions}</div> : null}
    </section>
  );
}
