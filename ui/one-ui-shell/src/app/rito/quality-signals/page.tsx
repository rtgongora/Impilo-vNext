"use client";

/**
 * Rito quality signals — review / convert to case. Real data via experience-BFF.
 * Route: /rito/quality-signals
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, Radar, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Signal {
  id: string;
  signalType?: string;
  sourceSystem?: string;
  severity?: string;
  summary?: string;
  status?: string;
  receivedAt?: string;
}

function asArray(payload: unknown): Signal[] {
  if (Array.isArray(payload)) return payload as Signal[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as Signal[];
  }
  return [];
}

function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}

export default function RitoQualitySignalsPage() {
  const [data, setData] = useState<Signal[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/quality-signals");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e, "Could not load quality signals."));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const act = useCallback(
    async (id: string, action: "convert" | "review", body: Record<string, unknown>) => {
      setBusy(`${id}:${action}`);
      setActionError(null);
      try {
        await apiClient.post<unknown>(
          `/internal/v1/rito/quality-signals/${encodeURIComponent(id)}/${action}`,
          body,
        );
        await load();
      } catch (e) {
        setActionError(errMessage(e, `Could not ${action} this signal.`));
      } finally {
        setBusy(null);
      }
    },
    [load],
  );

  return (
    <AppLayout>
      <PageShell
        title="Quality signals"
        subtitle="Inbound signals from connected systems — review and convert to cases"
        serviceSlug="rito"
      >
        <div className="mb-4 flex items-center gap-3">
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>

        {actionError && (
          <div className="mb-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
            {actionError}
          </div>
        )}

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading signals…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load signals
            </div>
            <p className="mb-3">{error}</p>
            <button
              type="button"
              onClick={() => void load()}
              className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
            >
              <RefreshCw className="h-3.5 w-3.5" /> Retry
            </button>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <Radar className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No quality signals to review right now.
          </div>
        ) : (
          <div className="space-y-3">
            {data.map((s) => {
              const isNew = (s.status ?? "").toUpperCase() === "NEW";
              return (
                <div
                  key={s.id}
                  className="rounded-xl border border-border bg-card p-4 shadow-sm"
                >
                  <div className="mb-1 flex flex-wrap items-center gap-2 text-xs">
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {s.signalType ?? "Signal"}
                    </span>
                    {s.sourceSystem && (
                      <span className="text-muted-foreground">from {s.sourceSystem}</span>
                    )}
                    {s.severity && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-amber-700">
                        {s.severity}
                      </span>
                    )}
                    <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                      {s.status ?? "—"}
                    </span>
                  </div>
                  <p className="text-sm text-foreground">{s.summary ?? "—"}</p>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {s.receivedAt ? new Date(s.receivedAt).toLocaleString() : ""}
                  </div>
                  {isNew && (
                    <div className="mt-3 flex gap-2">
                      <button
                        type="button"
                        disabled={!!busy}
                        onClick={() => void act(s.id, "convert", { case_type: "QUALITY_FINDING" })}
                        className="rounded-lg bg-teal-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                      >
                        {busy === `${s.id}:convert` ? "Converting…" : "Convert to case"}
                      </button>
                      <button
                        type="button"
                        disabled={!!busy}
                        onClick={() => void act(s.id, "review", { dismiss: true })}
                        className="rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-muted-foreground hover:text-foreground disabled:opacity-50"
                      >
                        {busy === `${s.id}:review` ? "Dismissing…" : "Dismiss"}
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
