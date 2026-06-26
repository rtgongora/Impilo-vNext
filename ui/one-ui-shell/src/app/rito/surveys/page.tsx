"use client";

/**
 * Rito experience surveys — list + per-survey aggregate. Real data via experience-BFF.
 * Route: /rito/surveys
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, ChevronDown, ChevronRight, Loader2, MessageSquareHeart, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Survey {
  id: string;
  surveyKey?: string;
  name?: string;
  surveyType?: string;
  active?: boolean;
}

interface Aggregate {
  surveyType?: string;
  totalResponses?: number;
  averageScore?: number | null;
  npsScore?: number | null;
  promoters?: number;
  passives?: number;
  detractors?: number;
}

function asArray(payload: unknown): Survey[] {
  if (Array.isArray(payload)) return payload as Survey[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as Survey[];
  }
  return [];
}

function unwrap<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return (payload as T) ?? null;
}

function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
}

function AggregateView({ surveyId }: { surveyId: string }) {
  const [agg, setAgg] = useState<Aggregate | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    apiClient
      .get<unknown>(`/internal/v1/rito/surveys/${encodeURIComponent(surveyId)}/aggregate`)
      .then((res) => {
        if (active) setAgg(unwrap<Aggregate>(res));
      })
      .catch((e) => {
        if (active) setError(errMessage(e, "Could not load results."));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [surveyId]);

  if (loading) {
    return (
      <div className="flex items-center gap-2 py-3 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin text-teal-600" /> Loading results…
      </div>
    );
  }
  if (error) {
    return <div className="py-3 text-sm text-red-700">{error}</div>;
  }
  if (!agg) {
    return <div className="py-3 text-sm text-muted-foreground">No results available.</div>;
  }
  const total = agg.totalResponses ?? 0;
  if (total === 0) {
    return <div className="py-3 text-sm text-muted-foreground">No responses yet.</div>;
  }
  const isNps = agg.npsScore != null || (agg.surveyType ?? "").toUpperCase() === "NPS";
  return (
    <div className="grid grid-cols-2 gap-3 py-3 sm:grid-cols-3">
      <div className="rounded-lg border border-border p-3">
        <div className="text-xs text-muted-foreground">Responses</div>
        <div className="text-lg font-semibold text-foreground">{total}</div>
      </div>
      <div className="rounded-lg border border-border p-3">
        <div className="text-xs text-muted-foreground">Average score</div>
        <div className="text-lg font-semibold text-foreground">
          {agg.averageScore != null ? agg.averageScore.toFixed(2) : "—"}
        </div>
      </div>
      {isNps && (
        <>
          <div className="rounded-lg border border-border p-3">
            <div className="text-xs text-muted-foreground">NPS</div>
            <div className="text-lg font-semibold text-foreground">
              {agg.npsScore != null ? agg.npsScore : "—"}
            </div>
          </div>
          <div className="rounded-lg border border-border p-3">
            <div className="text-xs text-muted-foreground">Promoters</div>
            <div className="text-lg font-semibold text-teal-700">{agg.promoters ?? 0}</div>
          </div>
          <div className="rounded-lg border border-border p-3">
            <div className="text-xs text-muted-foreground">Passives</div>
            <div className="text-lg font-semibold text-slate-600">{agg.passives ?? 0}</div>
          </div>
          <div className="rounded-lg border border-border p-3">
            <div className="text-xs text-muted-foreground">Detractors</div>
            <div className="text-lg font-semibold text-red-600">{agg.detractors ?? 0}</div>
          </div>
        </>
      )}
    </div>
  );
}

export default function RitoSurveysPage() {
  const [data, setData] = useState<Survey[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/surveys");
      setData(asArray(res));
    } catch (e) {
      setError(errMessage(e, "Could not load surveys."));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="Experience surveys"
        subtitle="Client-experience instruments and their results"
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

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading surveys…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load surveys
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
            <MessageSquareHeart className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No surveys have been configured yet.
          </div>
        ) : (
          <div className="space-y-3">
            {data.map((s) => {
              const open = expanded === s.id;
              return (
                <div key={s.id} className="rounded-xl border border-border bg-card shadow-sm">
                  <button
                    type="button"
                    onClick={() => setExpanded(open ? null : s.id)}
                    className="flex w-full items-center justify-between gap-3 p-4 text-left"
                  >
                    <div>
                      <div className="font-medium text-foreground">{s.name ?? s.surveyKey ?? s.id}</div>
                      <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                        {s.surveyType && (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                            {s.surveyType}
                          </span>
                        )}
                        <span
                          className={`rounded-full px-2 py-0.5 ${
                            s.active ? "bg-teal-50 text-teal-700" : "bg-slate-100 text-slate-500"
                          }`}
                        >
                          {s.active ? "Active" : "Inactive"}
                        </span>
                      </div>
                    </div>
                    <span className="inline-flex items-center gap-1 text-sm text-teal-700">
                      {open ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                      View results
                    </span>
                  </button>
                  {open && (
                    <div className="border-t border-border px-4 pb-2">
                      <AggregateView surveyId={s.id} />
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
