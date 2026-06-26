"use client";

/**
 * Rito case detail + lifecycle actions — real data via experience-BFF.
 * Route: /rito/cases/[caseId]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertTriangle, ArrowLeft, Clock, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface RitoCase {
  id: string;
  caseReference?: string;
  caseType?: string;
  pillar?: string;
  severity?: string;
  status?: string;
  title?: string;
  description?: string;
  category?: string;
  createdAt?: string;
}

interface TimelineEvent {
  id?: string;
  eventType?: string;
  fromStatus?: string;
  toStatus?: string;
  note?: string;
  actorId?: string;
  occurredAt?: string;
}

function errMessage(e: unknown, fallback: string): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `${fallback} (HTTP ${obj.status}).`;
  }
  return fallback;
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

export default function RitoCaseDetailPage() {
  const params = useParams();
  const caseId = String(params?.caseId ?? "");

  const [caseData, setCaseData] = useState<RitoCase | null>(null);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // action form state
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [triageSeverity, setTriageSeverity] = useState("MEDIUM");
  const [triageCategory, setTriageCategory] = useState("");
  const [assignee, setAssignee] = useState("");
  const [assigneeRole, setAssigneeRole] = useState("");
  const [escalateLevel, setEscalateLevel] = useState("");
  const [resolutionSummary, setResolutionSummary] = useState("");
  const [satisfaction, setSatisfaction] = useState("5");

  const load = useCallback(async () => {
    if (!caseId) return;
    setLoading(true);
    setError(null);
    try {
      const [c, t] = await Promise.all([
        apiClient.get<unknown>(`/internal/v1/rito/cases/${encodeURIComponent(caseId)}`),
        apiClient
          .get<unknown>(`/internal/v1/rito/cases/${encodeURIComponent(caseId)}/timeline`)
          .catch(() => []),
      ]);
      setCaseData(unwrap<RitoCase>(c));
      setTimeline(asArray<TimelineEvent>(t));
    } catch (e) {
      setError(errMessage(e, "Could not load this case."));
      setCaseData(null);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  const runAction = useCallback(
    async (action: string, body: Record<string, unknown>) => {
      setBusy(action);
      setActionError(null);
      try {
        await apiClient.post<unknown>(
          `/internal/v1/rito/cases/${encodeURIComponent(caseId)}/${action}`,
          body,
        );
        await load();
      } catch (e) {
        setActionError(errMessage(e, `Could not ${action} this case.`));
      } finally {
        setBusy(null);
      }
    },
    [caseId, load],
  );

  return (
    <AppLayout>
      <PageShell title="Case detail" subtitle="Quality & safety case lifecycle" serviceSlug="rito">
        <div className="mb-4">
          <Link
            href="/rito/cases"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to worklist
          </Link>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading case…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> Could not load case
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
        ) : !caseData ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            We couldn&apos;t find that case.
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* Header + timeline */}
            <div className="space-y-6 lg:col-span-2">
              <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                  <span className="font-mono font-semibold text-teal-700">
                    {caseData.caseReference ?? caseData.id}
                  </span>
                  {caseData.caseType && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {caseData.caseType}
                    </span>
                  )}
                  {caseData.severity && (
                    <span className="rounded-full bg-amber-100 px-2 py-0.5 text-amber-700">
                      {caseData.severity}
                    </span>
                  )}
                  {caseData.status && (
                    <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                      {caseData.status}
                    </span>
                  )}
                </div>
                <h2 className="text-lg font-semibold text-foreground">
                  {caseData.title ?? "Untitled case"}
                </h2>
                {caseData.description && (
                  <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">
                    {caseData.description}
                  </p>
                )}
              </div>

              <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Clock className="h-4 w-4 text-teal-600" /> Timeline
                </h3>
                {timeline.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No timeline events recorded yet.</p>
                ) : (
                  <ol className="space-y-3">
                    {timeline.map((ev, i) => (
                      <li key={ev.id ?? i} className="border-l-2 border-teal-200 pl-3">
                        <div className="text-sm font-medium text-foreground">
                          {ev.eventType ?? "Event"}
                          {ev.fromStatus || ev.toStatus ? (
                            <span className="ml-1 text-xs font-normal text-muted-foreground">
                              {ev.fromStatus ?? "—"} → {ev.toStatus ?? "—"}
                            </span>
                          ) : null}
                        </div>
                        {ev.note && <p className="text-sm text-muted-foreground">{ev.note}</p>}
                        <div className="text-xs text-muted-foreground">
                          {ev.actorId ? `${ev.actorId} · ` : ""}
                          {ev.occurredAt ? new Date(ev.occurredAt).toLocaleString() : ""}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </div>

            {/* Actions */}
            <div className="space-y-4">
              <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
                <h3 className="mb-3 text-sm font-semibold text-foreground">Actions</h3>
                {actionError && (
                  <div className="mb-3 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-800">
                    {actionError}
                  </div>
                )}

                <button
                  type="button"
                  disabled={!!busy}
                  onClick={() => void runAction("acknowledge", {})}
                  className="mb-3 w-full rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                >
                  {busy === "acknowledge" ? "Acknowledging…" : "Acknowledge"}
                </button>

                <div className="mb-3 space-y-2 border-t border-border pt-3">
                  <label className="text-xs font-semibold text-muted-foreground">Triage</label>
                  <select
                    value={triageSeverity}
                    onChange={(e) => setTriageSeverity(e.target.value)}
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  >
                    {["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((s) => (
                      <option key={s} value={s}>
                        {s}
                      </option>
                    ))}
                  </select>
                  <input
                    value={triageCategory}
                    onChange={(e) => setTriageCategory(e.target.value)}
                    placeholder="Category"
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    disabled={!!busy}
                    onClick={() =>
                      void runAction("triage", {
                        severity: triageSeverity,
                        category: triageCategory || undefined,
                      })
                    }
                    className="w-full rounded-lg border border-teal-600 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                  >
                    {busy === "triage" ? "Triaging…" : "Triage"}
                  </button>
                </div>

                <div className="mb-3 space-y-2 border-t border-border pt-3">
                  <label className="text-xs font-semibold text-muted-foreground">Assign</label>
                  <input
                    value={assignee}
                    onChange={(e) => setAssignee(e.target.value)}
                    placeholder="Assignee actor ID"
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <input
                    value={assigneeRole}
                    onChange={(e) => setAssigneeRole(e.target.value)}
                    placeholder="Assignee role"
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    disabled={!!busy || !assignee}
                    onClick={() =>
                      void runAction("assign", {
                        assignee_actor_id: assignee,
                        assignee_role: assigneeRole || undefined,
                      })
                    }
                    className="w-full rounded-lg border border-teal-600 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                  >
                    {busy === "assign" ? "Assigning…" : "Assign"}
                  </button>
                </div>

                <div className="mb-3 space-y-2 border-t border-border pt-3">
                  <label className="text-xs font-semibold text-muted-foreground">Escalate</label>
                  <input
                    value={escalateLevel}
                    onChange={(e) => setEscalateLevel(e.target.value)}
                    placeholder="Escalation level"
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    disabled={!!busy || !escalateLevel}
                    onClick={() => void runAction("escalate", { level: escalateLevel })}
                    className="w-full rounded-lg border border-orange-500 px-3 py-1.5 text-sm font-medium text-orange-600 hover:bg-orange-50 disabled:opacity-50"
                  >
                    {busy === "escalate" ? "Escalating…" : "Escalate"}
                  </button>
                </div>

                <div className="mb-3 space-y-2 border-t border-border pt-3">
                  <label className="text-xs font-semibold text-muted-foreground">Resolve</label>
                  <textarea
                    value={resolutionSummary}
                    onChange={(e) => setResolutionSummary(e.target.value)}
                    placeholder="Resolution summary"
                    rows={3}
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  />
                  <button
                    type="button"
                    disabled={!!busy || !resolutionSummary}
                    onClick={() =>
                      void runAction("resolve", {
                        resolution_summary: resolutionSummary,
                        outcome: "RESOLVED",
                      })
                    }
                    className="w-full rounded-lg border border-teal-600 px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                  >
                    {busy === "resolve" ? "Resolving…" : "Resolve"}
                  </button>
                </div>

                <div className="space-y-2 border-t border-border pt-3">
                  <label className="text-xs font-semibold text-muted-foreground">
                    Close (satisfaction 1–5)
                  </label>
                  <select
                    value={satisfaction}
                    onChange={(e) => setSatisfaction(e.target.value)}
                    className="w-full rounded-lg border border-border px-2 py-1.5 text-sm"
                  >
                    {[1, 2, 3, 4, 5].map((n) => (
                      <option key={n} value={n}>
                        {n}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={!!busy}
                    onClick={() =>
                      void runAction("close", { satisfaction_score: Number(satisfaction) })
                    }
                    className="w-full rounded-lg border border-slate-400 px-3 py-1.5 text-sm font-medium text-slate-600 hover:bg-slate-50 disabled:opacity-50"
                  >
                    {busy === "close" ? "Closing…" : "Close"}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
