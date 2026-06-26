"use client";

/**
 * Rito improvement — CAPA (corrective actions) + QI plans. Real data via experience-BFF.
 * Route: /rito/improvement
 */

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw, TrendingUp } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface CorrectiveAction {
  id?: string;
  caReference?: string;
  title?: string;
  actionType?: string;
  status?: string;
  priority?: string;
  dueAt?: string;
  ownerActorId?: string;
}

interface QiPlan {
  id?: string;
  qiReference?: string;
  title?: string;
  methodology?: string;
  status?: string;
}

function asArray<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as T[];
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

export default function RitoImprovementPage() {
  const [tab, setTab] = useState<"capa" | "qi">("capa");

  const [capa, setCapa] = useState<CorrectiveAction[] | null>(null);
  const [capaLoading, setCapaLoading] = useState(true);
  const [capaError, setCapaError] = useState<string | null>(null);

  const [qi, setQi] = useState<QiPlan[] | null>(null);
  const [qiLoading, setQiLoading] = useState(true);
  const [qiError, setQiError] = useState<string | null>(null);

  const loadCapa = useCallback(async () => {
    setCapaLoading(true);
    setCapaError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/corrective-actions");
      setCapa(asArray<CorrectiveAction>(res));
    } catch (e) {
      setCapaError(errMessage(e, "Could not load corrective actions."));
      setCapa(null);
    } finally {
      setCapaLoading(false);
    }
  }, []);

  const loadQi = useCallback(async () => {
    setQiLoading(true);
    setQiError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/qi-plans");
      setQi(asArray<QiPlan>(res));
    } catch (e) {
      setQiError(errMessage(e, "Could not load QI plans."));
      setQi(null);
    } finally {
      setQiLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCapa();
    void loadQi();
  }, [loadCapa, loadQi]);

  const ErrorBlock = ({ message, retry }: { message: string; retry: () => void }) => (
    <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
      <div className="mb-2 flex items-center gap-2 font-semibold">
        <AlertTriangle className="h-4 w-4" /> Could not load
      </div>
      <p className="mb-3">{message}</p>
      <button
        type="button"
        onClick={retry}
        className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
      >
        <RefreshCw className="h-3.5 w-3.5" /> Retry
      </button>
    </div>
  );

  return (
    <AppLayout>
      <PageShell
        title="Improvement — CAPA & QI"
        subtitle="Corrective and preventive actions, and quality-improvement plans"
        serviceSlug="rito"
      >
        <div className="mb-4 inline-flex rounded-lg border border-border bg-card p-1">
          <button
            type="button"
            onClick={() => setTab("capa")}
            className={`rounded-md px-4 py-1.5 text-sm font-medium ${
              tab === "capa" ? "bg-teal-600 text-white" : "text-muted-foreground"
            }`}
          >
            CAPA
          </button>
          <button
            type="button"
            onClick={() => setTab("qi")}
            className={`rounded-md px-4 py-1.5 text-sm font-medium ${
              tab === "qi" ? "bg-teal-600 text-white" : "text-muted-foreground"
            }`}
          >
            QI Plans
          </button>
        </div>

        {tab === "capa" ? (
          capaLoading ? (
            <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading corrective actions…
            </div>
          ) : capaError ? (
            <ErrorBlock message={capaError} retry={() => void loadCapa()} />
          ) : !capa || capa.length === 0 ? (
            <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
              <TrendingUp className="mx-auto mb-2 h-6 w-6 opacity-50" />
              No corrective or preventive actions have been raised yet.
            </div>
          ) : (
            <div className="space-y-3">
              {capa.map((c, i) => (
                <div key={c.id ?? i} className="rounded-xl border border-border bg-card p-4 shadow-sm">
                  <div className="mb-1 flex flex-wrap items-center gap-2 text-xs">
                    <span className="font-mono font-semibold text-teal-700">
                      {c.caReference ?? c.id ?? "—"}
                    </span>
                    {c.actionType && (
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                        {c.actionType}
                      </span>
                    )}
                    {c.priority && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-amber-700">
                        {c.priority}
                      </span>
                    )}
                    {c.status && (
                      <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                        {c.status}
                      </span>
                    )}
                  </div>
                  <p className="text-sm font-medium text-foreground">{c.title ?? "—"}</p>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {c.ownerActorId ? `Owner: ${c.ownerActorId}` : ""}
                    {c.dueAt ? ` · Due ${new Date(c.dueAt).toLocaleDateString()}` : ""}
                  </div>
                </div>
              ))}
            </div>
          )
        ) : qiLoading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading QI plans…
          </div>
        ) : qiError ? (
          <ErrorBlock message={qiError} retry={() => void loadQi()} />
        ) : !qi || qi.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <TrendingUp className="mx-auto mb-2 h-6 w-6 opacity-50" />
            No quality-improvement plans have been created yet.
          </div>
        ) : (
          <div className="space-y-3">
            {qi.map((p, i) => (
              <div key={p.id ?? i} className="rounded-xl border border-border bg-card p-4 shadow-sm">
                <div className="mb-1 flex flex-wrap items-center gap-2 text-xs">
                  <span className="font-mono font-semibold text-teal-700">
                    {p.qiReference ?? p.id ?? "—"}
                  </span>
                  {p.methodology && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {p.methodology}
                    </span>
                  )}
                  {p.status && (
                    <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                      {p.status}
                    </span>
                  )}
                </div>
                <p className="text-sm font-medium text-foreground">{p.title ?? "—"}</p>
              </div>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
