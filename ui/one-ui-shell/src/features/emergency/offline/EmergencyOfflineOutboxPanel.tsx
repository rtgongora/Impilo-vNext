"use client";

/**
 * Local IndexedDB outbox for emergency / ED writes queued while offline (W16b).
 * Complements OfflineIittPreviewPanel (advisory only) — this surfaces queued facts.
 */

import { useCallback, useEffect, useState } from "react";
import { apiClient } from "@/lib/api-client";
import { listQueue } from "@/lib/offline";
import type { QueuedOperation } from "@/lib/offline/types";

function isEmergencyPath(path: string): boolean {
  return (
    path.includes("/internal/v1/ed") ||
    path.includes("/internal/v1/emergency") ||
    path.includes("/internal/v1/emergency-episodes")
  );
}

export function EmergencyOfflineOutboxPanel() {
  const [rows, setRows] = useState<QueuedOperation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [flushing, setFlushing] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const all = await listQueue();
      setRows(all.filter((op) => isEmergencyPath(op.path)));
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not read offline outbox");
      setRows([]);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const onOnline = () => void refresh();
    window.addEventListener("online", onOnline);
    window.addEventListener("offline", onOnline);
    const id = window.setInterval(() => void refresh(), 8_000);
    return () => {
      window.removeEventListener("online", onOnline);
      window.removeEventListener("offline", onOnline);
      window.clearInterval(id);
    };
  }, [refresh]);

  const pending = rows.filter((r) => r.status === "pending" || r.status === "failed");

  return (
    <div
      className="rounded-xl border border-border p-4 space-y-3"
      data-testid="emergency-offline-outbox"
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="font-semibold text-sm">Offline outbox (ED / emergency)</h3>
          <p className="text-xs text-muted-foreground">
            Queued writes from IndexedDB — not the of-record triage path.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span
            className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium"
            data-testid="emergency-outbox-badge"
          >
            {pending.length} pending
          </span>
          <button
            type="button"
            className="rounded border px-2 py-1 text-xs disabled:opacity-50"
            disabled={flushing || pending.length === 0}
            data-testid="emergency-outbox-flush"
            onClick={() => {
              setFlushing(true);
              void apiClient
                .flushOfflineQueue()
                .then(() => refresh())
                .catch((e) =>
                  setError(e instanceof Error ? e.message : "Flush failed"),
                )
                .finally(() => setFlushing(false));
            }}
          >
            {flushing ? "Flushing…" : "Flush now"}
          </button>
        </div>
      </div>

      {error ? (
        <p className="text-sm text-destructive" role="alert">
          {error}
        </p>
      ) : null}

      {rows.length === 0 ? (
        <p className="text-sm text-muted-foreground">No emergency writes queued locally.</p>
      ) : (
        <ul className="max-h-48 space-y-1 overflow-y-auto text-xs">
          {rows.map((op) => (
            <li
              key={op.id}
              className="flex flex-wrap justify-between gap-2 rounded border border-border/60 px-2 py-1"
            >
              <span>
                <strong>{op.method}</strong> {op.path}
              </span>
              <span className="text-muted-foreground">
                {op.status} · {new Date(op.createdAt).toLocaleString()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
