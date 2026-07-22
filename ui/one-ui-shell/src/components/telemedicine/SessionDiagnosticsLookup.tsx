"use client";

/**
 * TM-B19: helpdesk per-session failure diagnostics (journey: failed call → helpdesk).
 *
 * Looks up a session's transport timeline (rtc.session_events) and participant media aggregates
 * (participant_stats) via the governed BFF diagnostics endpoint. Transport telemetry ONLY — the
 * API carries no clinical content by construction, which is what makes this surface safe for a
 * helpdesk operator.
 */

import { useState } from "react";
import { Search } from "lucide-react";
import { apiClient } from "@/lib/api-client";

interface DiagnosticsEvent {
  eventType?: string;
  participantIdentity?: string | null;
  roomName?: string | null;
  occurredAt?: string | null;
}

interface Diagnostics {
  sessionId: string;
  events?: DiagnosticsEvent[];
  eventsError?: string;
  stats?: Record<string, unknown> | null;
  statsError?: string;
}

export function SessionDiagnosticsLookup() {
  const [sessionId, setSessionId] = useState("");
  const [result, setResult] = useState<Diagnostics | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function lookup() {
    const id = sessionId.trim();
    if (!id || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await apiClient.get<{ data: Diagnostics }>(
        `/internal/v1/teleconsult/ops/session-diagnostics/${id}`,
      );
      setResult(res.data);
    } catch {
      setError("Diagnostics unavailable for that session id.");
      setResult(null);
    } finally {
      setBusy(false);
    }
  }

  const stats = (result?.stats ?? {}) as Record<string, unknown>;
  const disconnects = (stats.disconnectReasons ?? stats.disconnect_reasons ?? {}) as Record<string, number>;

  return (
    <div data-testid="session-diagnostics-lookup">
      <div className="flex gap-2">
        <input
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookup()}
          placeholder="Session / referral id"
          className="min-w-0 flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <button
          type="button"
          disabled={busy || !sessionId.trim()}
          onClick={lookup}
          className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm disabled:opacity-50"
        >
          <Search className="h-4 w-4" /> {busy ? "Looking…" : "Diagnose"}
        </button>
      </div>
      {error && <p className="mt-2 text-sm text-destructive">{error}</p>}

      {result && (
        <div className="mt-3 space-y-3">
          <p className="text-[11px] text-muted-foreground">
            Transport telemetry only — no clinical content is available on this surface.
          </p>
          {Object.keys(disconnects).length > 0 && (
            <div className="rounded-md border border-border bg-background p-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Disconnect reasons</p>
              <ul className="mt-1 space-y-0.5 text-sm">
                {Object.entries(disconnects).map(([reason, count]) => (
                  <li key={reason} className="flex justify-between">
                    <span>{reason}</span>
                    <span className="text-muted-foreground">{count}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          <div className="rounded-md border border-border bg-background p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
              Event timeline {result.eventsError ? "(unavailable)" : ""}
            </p>
            {(result.events ?? []).length === 0 ? (
              <p className="mt-1 text-sm text-muted-foreground">
                No transport events recorded for this session (never provisioned, or predates telemetry).
              </p>
            ) : (
              <ul className="mt-1 max-h-64 space-y-0.5 overflow-y-auto text-xs font-mono">
                {(result.events ?? []).map((ev, i) => (
                  <li key={i} className="flex flex-wrap gap-2">
                    <span className="text-muted-foreground">{ev.occurredAt ?? "—"}</span>
                    <span className="font-semibold text-foreground">{ev.eventType}</span>
                    {ev.participantIdentity && <span>{ev.participantIdentity}</span>}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
