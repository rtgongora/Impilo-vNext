"use client";

import { useState } from "react";
import { bffErrorMessage } from "@/lib/bff-errors";
import type { EmergencyAlertRow } from "@/hooks/queries/useEmergencyEpisode";

export interface EpisodeAlertsPanelProps {
  alerts: EmergencyAlertRow[] | undefined;
  isLoading: boolean;
  isError: boolean;
  actor: string;
  onAcknowledge: (body: { alertId: string; acknowledgedBy: string }) => Promise<unknown>;
  onRespond: (body: { alertId: string; respondedBy: string; responseNote: string }) => Promise<unknown>;
  onClose: (body: { alertId: string; closedBy: string; closureReason?: string }) => Promise<unknown>;
  isMutating?: boolean;
}

export function EpisodeAlertsPanel({
  alerts,
  isLoading,
  isError,
  actor,
  onAcknowledge,
  onRespond,
  onClose,
  isMutating,
}: EpisodeAlertsPanelProps) {
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const run = async (fn: () => Promise<unknown>) => {
    setError(null);
    try {
      await fn();
    } catch (e) {
      setError(bffErrorMessage(e, "alert action"));
    }
  };

  return (
    <div className="space-y-3 rounded border p-4" data-testid="episode-alerts-panel">
      <h3 className="text-sm font-semibold">Episode alerts</h3>
      {isLoading ? <p className="text-sm text-muted-foreground">Loading alerts…</p> : null}
      {isError ? (
        <p className="text-sm text-danger" data-testid="episode-alerts-unreadable">
          Alerts could not be read.
        </p>
      ) : null}
      {!isLoading && !isError && (alerts ?? []).length === 0 ? (
        <p className="text-sm text-muted-foreground" data-testid="episode-alerts-empty">
          No open alerts on this episode.
        </p>
      ) : null}
      <ul className="space-y-3">
        {(alerts ?? []).map((alert) => {
          const id = String(alert.alert_id);
          const status = String(alert.status ?? "");
          return (
            <li key={id} className="rounded border border-border p-2 text-sm">
              <p className="font-medium">
                {String(alert.alert_type ?? "Alert")} — {String(alert.severity ?? "")} ({status})
              </p>
              {alert.detail ? <p className="text-xs text-muted-foreground">{String(alert.detail)}</p> : null}
              <div className="mt-2 flex flex-wrap gap-2">
                {status === "RAISED" || status === "OPEN" ? (
                  <button
                    type="button"
                    className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                    disabled={!actor || isMutating}
                    onClick={() => run(() => onAcknowledge({ alertId: id, acknowledgedBy: actor }))}
                  >
                    Acknowledge
                  </button>
                ) : null}
                <input
                  className="min-w-[8rem] flex-1 rounded border px-1 py-0.5 text-xs"
                  placeholder="Response / close note"
                  value={notes[id] ?? ""}
                  onChange={(e) => setNotes((prev) => ({ ...prev, [id]: e.target.value }))}
                />
                <button
                  type="button"
                  className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                  disabled={!actor || !(notes[id] ?? "").trim() || isMutating}
                  onClick={() =>
                    run(() =>
                      onRespond({
                        alertId: id,
                        respondedBy: actor,
                        responseNote: (notes[id] ?? "").trim(),
                      }),
                    )
                  }
                >
                  Respond
                </button>
                <button
                  type="button"
                  className="rounded border px-2 py-0.5 text-xs disabled:opacity-50"
                  disabled={!actor || isMutating}
                  onClick={() =>
                    run(() =>
                      onClose({
                        alertId: id,
                        closedBy: actor,
                        closureReason: (notes[id] ?? "").trim() || undefined,
                      }),
                    )
                  }
                >
                  Close
                </button>
              </div>
            </li>
          );
        })}
      </ul>
      {error ? (
        <p className="text-sm text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
