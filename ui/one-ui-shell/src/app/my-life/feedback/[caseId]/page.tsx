"use client";

/**
 * Citizen case tracking — friendly status + progress timeline.
 * Route: /my-life/feedback/[caseId]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Clock, Loader2, RefreshCw, SearchX } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface RitoCase {
  id?: string;
  caseReference?: string;
  caseType?: string;
  status?: string;
  title?: string;
}

interface TimelineEvent {
  id?: string;
  eventType?: string;
  toStatus?: string;
  note?: string;
  occurredAt?: string;
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

export default function TrackFeedbackPage() {
  const params = useParams();
  const caseId = String(params?.caseId ?? "");

  const [caseData, setCaseData] = useState<RitoCase | null>(null);
  const [timeline, setTimeline] = useState<TimelineEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!caseId) return;
    setLoading(true);
    setError(null);
    setNotFound(false);
    try {
      const c = await apiClient.get<unknown>(
        `/internal/v1/nompilo/rito/cases/${encodeURIComponent(caseId)}`,
      );
      setCaseData(unwrap<RitoCase>(c));
      const t = await apiClient
        .get<unknown>(`/internal/v1/nompilo/rito/cases/${encodeURIComponent(caseId)}/timeline`)
        .catch(() => []);
      setTimeline(asArray<TimelineEvent>(t));
    } catch (e) {
      const status = (e as { status?: number })?.status;
      if (status === 404) {
        setNotFound(true);
      } else {
        const msg = (e as { error?: { message?: string } })?.error?.message;
        setError(msg ?? "We couldn't load that case right now. Please try again.");
      }
      setCaseData(null);
    } finally {
      setLoading(false);
    }
  }, [caseId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell title="Track feedback" subtitle="Follow the progress of your case" serviceSlug="rito">
        <div className="mb-4">
          <Link
            href="/my-life/feedback"
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" /> Back to feedback
          </Link>
        </div>

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading your case…
          </div>
        ) : notFound ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            <SearchX className="mx-auto mb-2 h-6 w-6 opacity-50" />
            We couldn&apos;t find that reference. Please check it and try again.
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
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
            We couldn&apos;t find that reference.
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                <span className="font-mono font-semibold text-teal-700">
                  {caseData.caseReference ?? caseData.id}
                </span>
                {caseData.caseType && (
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                    {caseData.caseType.replace(/_/g, " ")}
                  </span>
                )}
                {caseData.status && (
                  <span className="rounded-full bg-teal-50 px-2 py-0.5 text-teal-700">
                    {caseData.status.replace(/_/g, " ")}
                  </span>
                )}
              </div>
              <h2 className="text-lg font-semibold text-foreground">
                {caseData.title ?? "Your feedback"}
              </h2>
            </div>

            <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
              <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
                <Clock className="h-4 w-4 text-teal-600" /> Progress
              </h3>
              {timeline.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  We&apos;ve received your feedback. Updates will appear here.
                </p>
              ) : (
                <ol className="space-y-3">
                  {timeline.map((ev, i) => (
                    <li key={ev.id ?? i} className="border-l-2 border-teal-200 pl-3">
                      <div className="text-sm font-medium text-foreground">
                        {ev.toStatus
                          ? ev.toStatus.replace(/_/g, " ")
                          : ev.eventType ?? "Update"}
                      </div>
                      {ev.note && <p className="text-sm text-muted-foreground">{ev.note}</p>}
                      <div className="text-xs text-muted-foreground">
                        {ev.occurredAt ? new Date(ev.occurredAt).toLocaleString() : ""}
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
