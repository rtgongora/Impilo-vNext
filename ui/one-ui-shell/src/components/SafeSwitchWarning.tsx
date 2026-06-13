"use client";

/**
 * Safe Switch Warning (Screen 16)
 *
 * Modal dialog shown when the user attempts to switch context
 * while an active work session (shift) exists.
 *
 * Health OS Doctrine: graduated friction — context switches during
 * active clinical shifts require explicit acknowledgement to prevent
 * accidental data loss or orphaned patient records.
 */

import { AlertTriangle, X } from "lucide-react";

interface SafeSwitchWarningProps {
  open: boolean;
  facilityName: string;
  workspaceName?: string;
  shiftStartedAt?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function SafeSwitchWarning({
  open,
  facilityName,
  workspaceName,
  shiftStartedAt,
  onConfirm,
  onCancel,
}: SafeSwitchWarningProps) {
  if (!open) return null;

  const formattedTime = shiftStartedAt
    ? new Date(shiftStartedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : null;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center">
      {/* Backdrop */}
      <button
        type="button"
        className="absolute inset-0 bg-card/60 backdrop-blur-sm"
        onClick={onCancel}
        aria-label="Close warning"
      />

      {/* Dialog */}
      <div className="relative z-10 w-full max-w-md rounded-2xl border border-border bg-card p-6 shadow-2xl">
        {/* Close button */}
        <button
          type="button"
          onClick={onCancel}
          className="absolute right-4 top-4 rounded-lg p-1 text-muted-foreground transition hover:bg-neutral-100 hover:text-muted-foreground"
          aria-label="Close"
        >
          <X className="h-4 w-4" />
        </button>

        {/* Warning icon */}
        <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-amber-100">
          <AlertTriangle className="h-6 w-6 text-amber-600" />
        </div>

        {/* Title */}
        <h2 className="text-lg font-semibold text-foreground">Unsaved Work</h2>

        {/* Description */}
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          You have an active work session at{" "}
          <span className="font-medium text-foreground">{facilityName}</span>.
          {workspaceName ? (
            <>
              {" "}Workspace: <span className="font-medium text-foreground">{workspaceName}</span>.
            </>
          ) : null}
        </p>

        {/* Consequences */}
        <div className="mt-4 rounded-xl border border-warning/35 bg-warning-soft p-4">
          <p className="text-sm font-medium text-warning-foreground">
            Switching context will:
          </p>
          <ul className="mt-2 space-y-1 text-sm text-warning-foreground">
            <li className="flex items-start gap-2">
              <span className="mt-0.5 block h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
              End your current shift
              {formattedTime && (
                <span className="text-amber-600"> (started at {formattedTime})</span>
              )}
            </li>
            <li className="flex items-start gap-2">
              <span className="mt-0.5 block h-1.5 w-1.5 shrink-0 rounded-full bg-amber-500" />
              Close any open patient records
            </li>
          </ul>
        </div>

        <p className="mt-4 text-sm text-muted-foreground">
          Make sure all work is saved before continuing.
        </p>

        {/* Actions */}
        <div className="mt-6 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-xl border border-border px-4 py-2.5 text-sm font-medium text-foreground transition hover:bg-background"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-xl bg-amber-500 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-amber-600 focus:outline-none focus:ring-2 focus:ring-amber-400 focus:ring-offset-2"
          >
            End Session &amp; Switch
          </button>
        </div>
      </div>
    </div>
  );
}
