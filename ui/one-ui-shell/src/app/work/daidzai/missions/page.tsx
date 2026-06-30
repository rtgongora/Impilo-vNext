"use client";

/**
 * Mission timeline — for a selected incident (?incident=id), shows the real mission
 * status timeline (GET /internal/v1/daidzai/incidents/{id}/missions) and lets a
 * responder record a status event. Tracking only — Nhume owns mission execution.
 * Route: /work/daidzai/missions
 */

import { Suspense, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { AlertTriangle, Loader2, RefreshCw, Radio } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { apiClient } from "@/lib/api-client";

interface MissionEvent {
  id: string;
  status?: string;
  note?: string;
  occurredAt?: string;
  nhumeMissionRef?: string;
}

const STATUSES = ["RESPONDING", "ON_SCENE", "TRANSPORTING", "HANDOVER", "RESOLVED"] as const;

function asArray(p: unknown): MissionEvent[] {
  return Array.isArray(p) ? (p as MissionEvent[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Could not load the mission timeline.";
}

function MissionsInner() {
  const params = useSearchParams();
  const incidentId = params.get("incident");
  const [events, setEvents] = useState<MissionEvent[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<(typeof STATUSES)[number]>("RESPONDING");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!incidentId) return;
    setLoading(true);
    setError(null);
    try {
      setEvents(
        asArray(
          await apiClient.get<unknown>(
            `/internal/v1/daidzai/incidents/${encodeURIComponent(incidentId)}/missions`
          )
        )
      );
    } catch (e) {
      setError(errMessage(e));
      setEvents(null);
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    void load();
  }, [load]);

  const record = async () => {
    if (!incidentId) return;
    setBusy(true);
    try {
      await apiClient.post(
        `/internal/v1/daidzai/incidents/${encodeURIComponent(incidentId)}/mission-events`,
        { status }
      );
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  if (!incidentId) {
    return (
      <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
        Select an incident from the command board to see its mission timeline.
      </div>
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
      <div className="space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="font-mono text-xs text-muted-foreground">Incident {incidentId}</div>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        <div className="flex flex-wrap items-end gap-2 rounded-xl border border-border bg-card p-4 shadow-sm">
          <label className="text-sm">
            <span className="mb-1 block font-medium">Record mission status</span>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as (typeof STATUSES)[number])}
              className="rounded-lg border border-border bg-background px-3 py-2"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, " ")}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            onClick={() => void record()}
            disabled={busy}
            className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-sm font-semibold text-white hover:bg-teal-700 disabled:opacity-60"
          >
            {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Radio className="h-4 w-4" />}
            Record
          </button>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-10 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading timeline…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load timeline
            </div>
            <p>{error}</p>
          </div>
        ) : !events || events.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            No mission events yet.
          </div>
        ) : (
          <ol className="space-y-3 rounded-xl border border-border bg-card p-5 shadow-sm">
            {events.map((m) => (
              <li key={m.id} className="flex gap-3">
                <span className="mt-1 h-2 w-2 shrink-0 rounded-full bg-teal-500" />
                <div>
                  <div className="text-sm font-medium">{m.status}</div>
                  {m.nhumeMissionRef ? (
                    <div className="text-xs text-muted-foreground">Mission {m.nhumeMissionRef}</div>
                  ) : null}
                  {m.occurredAt ? (
                    <div className="text-xs text-muted-foreground">
                      {new Date(m.occurredAt).toLocaleString()}
                    </div>
                  ) : null}
                </div>
              </li>
            ))}
          </ol>
        )}
      </div>

      <NompiloContextualGuidance routePath="/work/daidzai" />
    </div>
  );
}

export default function DaidzaiMissionsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Mission tracking"
        subtitle="Mission status timeline — Daidzai tracks, Nhume executes"
        serviceSlug="daidzai"
      >
        <Suspense
          fallback={
            <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading…
            </div>
          }
        >
          <MissionsInner />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
