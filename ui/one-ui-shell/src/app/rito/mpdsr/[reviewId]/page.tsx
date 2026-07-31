"use client";

/**
 * MPDSR review detail — committee, three-delay findings, CAPA actions, firewalled readiness task.
 * Route: /rito/mpdsr/[reviewId]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, Loader2, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface ReviewDetail {
  review?: {
    id: string;
    status?: string;
    reviewLevel?: string;
    triggerFlags?: string;
    facilityId?: string;
    deathCaseId?: string;
  };
  meetings?: unknown[];
  findings?: { id: string; delayType?: string; modifiableFactor?: string }[];
  actions?: { id: string; title?: string; status?: string; dueAt?: string }[];
  readinessTasks?: { id: string; taskReference?: string; status?: string }[];
}

export default function MpdsrReviewDetailPage() {
  const params = useParams();
  const reviewId = String(params.reviewId ?? "");
  const [detail, setDetail] = useState<ReviewDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!reviewId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<ReviewDetail>(`/internal/v1/rito/mpdsr/reviews/${reviewId}`);
      setDetail(res as ReviewDetail);
    } catch {
      setError("Could not load review detail.");
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [reviewId]);

  useEffect(() => {
    void load();
  }, [load]);

  const raiseReadinessTask = async () => {
    setActionMsg(null);
    try {
      await apiClient.post(`/internal/v1/rito/mpdsr/reviews/${reviewId}/readiness-task`, {});
      setActionMsg("Facility readiness reassessment task raised (firewalled payload).");
      await load();
    } catch {
      setActionMsg("Could not raise readiness task.");
    }
  };

  const addSampleFinding = async () => {
    setActionMsg(null);
    try {
      await apiClient.post(`/internal/v1/rito/mpdsr/reviews/${reviewId}/findings`, {
        delayType: "TYPE3_RECEIVING",
        modifiableFactor: "Delayed escalation protocol",
        narrative: "Committee finding — confidential to this review.",
      });
      await load();
    } catch {
      setActionMsg("Could not record finding.");
    }
  };

  if (loading && !detail) {
    return (
      <AppLayout>
        <PageShell title="MPDSR Review" serviceSlug="rito">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </PageShell>
      </AppLayout>
    );
  }

  const review = detail?.review;

  return (
    <AppLayout>
      <PageShell
        title={`MPDSR Review ${reviewId.slice(0, 8)}…`}
        subtitle="Confidential committee instrument — not surfaced on citizen or EHR maternity routes"
        serviceSlug="rito"
      >
        <Link href="/rito/mpdsr" className="mb-4 inline-flex items-center gap-1 text-sm text-teal-700 hover:underline">
          <ArrowLeft className="h-4 w-4" /> Back to worklist
        </Link>

        {error && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
        )}

        {actionMsg && (
          <div className="mb-4 rounded-lg border border-teal-200 bg-teal-50 p-3 text-sm text-teal-900">{actionMsg}</div>
        )}

        {review && (
          <div className="mb-6 grid gap-4 md:grid-cols-3">
            <div className="rounded-xl border border-border p-4">
              <div className="text-xs uppercase text-muted-foreground">Status</div>
              <div className="font-semibold">{review.status}</div>
            </div>
            <div className="rounded-xl border border-border p-4">
              <div className="text-xs uppercase text-muted-foreground">Level</div>
              <div className="font-semibold">{review.reviewLevel}</div>
            </div>
            <div className="rounded-xl border border-border p-4">
              <div className="text-xs uppercase text-muted-foreground">Trigger flags</div>
              <div className="font-semibold">{review.triggerFlags ?? "—"}</div>
            </div>
          </div>
        )}

        <section className="mb-8">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Three-delay findings</h2>
            <button
              type="button"
              onClick={() => void addSampleFinding()}
              className="rounded-lg bg-teal-600 px-3 py-1.5 text-sm text-white hover:bg-teal-700"
            >
              Record finding
            </button>
          </div>
          {(detail?.findings?.length ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">No findings recorded yet.</p>
          ) : (
            <ul className="space-y-2">
              {detail?.findings?.map((f) => (
                <li key={f.id} className="rounded-lg border border-border p-3 text-sm">
                  <span className="font-medium">{f.delayType}</span>: {f.modifiableFactor}
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="mb-8">
          <h2 className="mb-3 text-lg font-semibold">Recommendations / CAPA actions</h2>
          {(detail?.actions?.length ?? 0) === 0 ? (
            <p className="text-sm text-muted-foreground">No actions linked yet.</p>
          ) : (
            <ul className="space-y-2">
              {detail?.actions?.map((a) => (
                <li key={a.id} className="rounded-lg border border-border p-3 text-sm">
                  {a.title} — <span className="text-muted-foreground">{a.status}</span>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
          <div className="mb-2 flex items-center gap-2 font-semibold text-amber-900">
            <ShieldAlert className="h-5 w-5" /> Facility readiness firewall
          </div>
          <p className="mb-3 text-sm text-amber-900/90">
            A review may prompt a readiness reassessment. Review narrative, findings, and death
            identifiers must never appear on the readiness task payload.
          </p>
          <button
            type="button"
            onClick={() => void raiseReadinessTask()}
            className="rounded-lg border border-amber-300 bg-white px-3 py-1.5 text-sm font-medium hover:bg-amber-100"
          >
            Raise facility readiness task
          </button>
          {(detail?.readinessTasks?.length ?? 0) > 0 && (
            <ul className="mt-3 space-y-1 text-sm text-amber-900">
              {detail?.readinessTasks?.map((t) => (
                <li key={t.id}>
                  {t.taskReference} — {t.status}
                </li>
              ))}
            </ul>
          )}
        </section>
      </PageShell>
    </AppLayout>
  );
}
