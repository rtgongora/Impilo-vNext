"use client";

/**
 * Rito audit detail + findings + lifecycle actions. Real data via experience-BFF.
 * Route: /rito/audits/[auditId]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertTriangle, ArrowLeft, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface Audit {
  id: string;
  auditReference?: string;
  auditType?: string;
  status?: string;
  scorePercent?: number | null;
}

interface Finding {
  id?: string;
  findingType?: string;
  severity?: string;
  description?: string;
  recommendedAction?: string;
  sectionKey?: string;
}

function asArray<T>(payload: unknown): T[] {
  if (Array.isArray(payload)) return payload as T[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as T[];
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

export default function RitoAuditDetailPage() {
  const params = useParams();
  const auditId = String(params?.auditId ?? "");

  const [audit, setAudit] = useState<Audit | null>(null);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!auditId) return;
    setLoading(true);
    setError(null);
    try {
      const [a, f] = await Promise.all([
        apiClient.get<unknown>(`/internal/v1/rito/audits/${encodeURIComponent(auditId)}`),
        apiClient
          .get<unknown>(`/internal/v1/rito/audits/${encodeURIComponent(auditId)}/findings`)
          .catch(() => []),
      ]);
      setAudit(unwrap<Audit>(a));
      setFindings(asArray<Finding>(f));
    } catch (e) {
      setError(errMessage(e, "Could not load this audit."));
      setAudit(null);
    } finally {
      setLoading(false);
    }
  }, [auditId]);

  useEffect(() => {
    void load();
  }, [load]);

  const act = useCallback(
    async (action: "start" | "submit" | "close") => {
      setBusy(action);
      setActionError(null);
      try {
        await apiClient.post<unknown>(
          `/internal/v1/rito/audits/${encodeURIComponent(auditId)}/${action}`,
          {},
        );
        await load();
      } catch (e) {
        setActionError(errMessage(e, `Could not ${action} this audit.`));
      } finally {
        setBusy(null);
      }
    },
    [auditId, load],
  );

  return (
    <AppLayout>
      <PageShell title="Audit detail" subtitle="Quality audit findings and lifecycle" serviceSlug="rito">
        <div className="mb-4">
          <Link
            href="/rito/audits"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to audits
          </Link>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading audit…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load audit
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
        ) : !audit ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            We couldn&apos;t find that audit.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                <span className="font-mono font-semibold text-teal-700">
                  {audit.auditReference ?? audit.id}
                </span>
                {audit.auditType && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                    {audit.auditType}
                  </span>
                )}
                {audit.status && (
                  <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                    {audit.status}
                  </span>
                )}
                <span className="text-muted-foreground">
                  Score: {audit.scorePercent != null ? `${audit.scorePercent}%` : "—"}
                </span>
              </div>

              {actionError && (
                <div className="my-3 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800">
                  {actionError}
                </div>
              )}

              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  type="button"
                  disabled={!!busy}
                  onClick={() => void act("start")}
                  className="rounded-lg border border-teal-600 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                >
                  {busy === "start" ? "Starting…" : "Start"}
                </button>
                <button
                  type="button"
                  disabled={!!busy}
                  onClick={() => void act("submit")}
                  className="rounded-lg border border-teal-600 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                >
                  {busy === "submit" ? "Submitting…" : "Submit"}
                </button>
                <button
                  type="button"
                  disabled={!!busy}
                  onClick={() => void act("close")}
                  className="rounded-lg border border-slate-400 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                >
                  {busy === "close" ? "Closing…" : "Close"}
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h3 className="mb-3 text-sm font-semibold text-foreground">Findings</h3>
              {findings.length === 0 ? (
                <p className="text-sm text-muted-foreground">No findings recorded for this audit yet.</p>
              ) : (
                <ul className="space-y-3">
                  {findings.map((f, i) => (
                    <li key={f.id ?? i} className="rounded-lg border border-border p-3">
                      <div className="mb-1 flex flex-wrap items-center gap-2 text-xs">
                        <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                          {f.findingType ?? "Finding"}
                        </span>
                        {f.severity && (
                          <span className="rounded-full bg-amber-100 px-2 py-0.5 text-amber-700">
                            {f.severity}
                          </span>
                        )}
                        {f.sectionKey && (
                          <span className="text-muted-foreground">§ {f.sectionKey}</span>
                        )}
                      </div>
                      {f.description && <p className="text-sm text-foreground">{f.description}</p>}
                      {f.recommendedAction && (
                        <p className="mt-1 text-sm text-muted-foreground">
                          <span className="font-medium">Recommended:</span> {f.recommendedAction}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
