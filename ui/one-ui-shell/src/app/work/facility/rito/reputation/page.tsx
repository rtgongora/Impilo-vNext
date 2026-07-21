"use client";

/**
 * Reputation management console + moderation queue (RW14). For facility leadership, quality
 * teams and regulators (TSHEPO-gated: V044 REGULATOR + FACILITY_SAFETY_FOCAL, plus V021 broad
 * /rito/ for FACILITY_ADMIN + DISTRICT_PHO). Two panels:
 *   1. Moderation queue — ratings pending review; publish or withhold (with review-bombing flags).
 *   2. Provider lookup — the authorised management view (all four domains + moderation counts).
 *
 * Moderating a rating changes only its publication state. It never touches the provider's
 * licence, scope, employment or access — a pattern of concern is referred to the responsible
 * authority through a case, never actioned here (regulation firewall).
 *
 * Route: /work/facility/rito/reputation
 */

import { useCallback, useEffect, useState } from "react";
import { Loader2, ShieldAlert, ShieldCheck, Star } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import {
  DOMAIN_LABELS,
  type ManagementView,
  type RatingSummary,
  unwrapList,
  unwrapObject,
} from "@/lib/rito/reputation";

export default function ReputationManagementPage() {
  const [queue, setQueue] = useState<RatingSummary[]>([]);
  const [loadingQueue, setLoadingQueue] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadQueue = useCallback(async () => {
    setLoadingQueue(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/facility/rito/moderation-queue");
      setQueue(unwrapList<RatingSummary>(res));
    } catch {
      setError("Could not load the moderation queue.");
    } finally {
      setLoadingQueue(false);
    }
  }, []);

  useEffect(() => {
    void loadQueue();
  }, [loadQueue]);

  return (
    <AppLayout>
      <PageShell
        title="Reputation & moderation"
        subtitle="Review pending experience feedback and view provider reputation across all quality domains."
        serviceSlug="rito"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <section className="space-y-3">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">
                Moderation queue{queue.length > 0 ? ` (${queue.length})` : ""}
              </h2>
              <button
                type="button"
                onClick={() => void loadQueue()}
                className="text-xs font-semibold text-teal-700 hover:underline"
              >
                Refresh
              </button>
            </div>
            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
            )}
            {loadingQueue ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </div>
            ) : queue.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nothing pending review.</p>
            ) : (
              queue.map((r) => <QueueRow key={r.id} rating={r} onModerated={loadQueue} />)
            )}
          </section>

          <ProviderLookup />

          <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
            <span>
              Moderation controls publication only. Ratings never change a provider&apos;s
              registration, scope or access — a pattern of concern is referred to the responsible
              authority through a case.
            </span>
          </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function QueueRow({ rating, onModerated }: { rating: RatingSummary; onModerated: () => void }) {
  const [busy, setBusy] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [bombing, setBombing] = useState(false);

  const moderate = async (decision: "PUBLISHED" | "WITHHELD") => {
    setBusy(decision);
    try {
      await apiClient.post<unknown>(`/internal/v1/facility/rito/ratings/${encodeURIComponent(rating.id)}/moderate`, {
        decision,
        reason: reason.trim() || null,
        manipulationFlags: bombing ? ["REVIEW_BOMBING_SUSPECTED"] : [],
      });
      onModerated();
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex items-center gap-0.5">
          {[1, 2, 3, 4, 5].map((n) => (
            <Star
              key={n}
              className={`h-4 w-4 ${
                rating.overallScore != null && n <= Math.round(Number(rating.overallScore))
                  ? "fill-amber-400 text-amber-400"
                  : "text-muted-foreground/30"
              }`}
            />
          ))}
        </div>
        {rating.verifiedInteraction ? (
          <span className="rounded-full bg-teal-50 px-2 py-0.5 text-[11px] font-medium text-teal-700">Verified</span>
        ) : (
          <span className="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">Unverified</span>
        )}
        <span className="text-xs text-muted-foreground">{rating.moderationState.toLowerCase()}</span>
        <span className="ml-auto font-mono text-[11px] text-muted-foreground">{rating.providerPublicId}</span>
      </div>
      {(rating.domainScores?.length ?? 0) > 0 && (
        <div className="mt-1.5 flex flex-wrap gap-1.5">
          {rating.domainScores!.map((d, i) => (
            <span key={i} className="rounded border border-border px-1.5 py-0.5 text-[11px] text-muted-foreground">
              {DOMAIN_LABELS[d.domain] ?? d.domain}: {d.score ?? "—"}
            </span>
          ))}
        </div>
      )}
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <input
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="Reason (optional)"
          className="min-w-[12rem] flex-1 rounded-lg border border-border px-2.5 py-1.5 text-xs"
        />
        <label className="flex items-center gap-1 text-[11px] text-muted-foreground">
          <input type="checkbox" checked={bombing} onChange={(e) => setBombing(e.target.checked)} />
          <ShieldAlert className="h-3.5 w-3.5 text-amber-600" /> Review-bombing
        </label>
        <button
          type="button"
          onClick={() => moderate("PUBLISHED")}
          disabled={busy != null}
          className="rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {busy === "PUBLISHED" ? "…" : "Publish"}
        </button>
        <button
          type="button"
          onClick={() => moderate("WITHHELD")}
          disabled={busy != null}
          className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50"
        >
          {busy === "WITHHELD" ? "…" : "Withhold"}
        </button>
      </div>
    </div>
  );
}

function ProviderLookup() {
  const [providerId, setProviderId] = useState("");
  const [view, setView] = useState<ManagementView | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  const lookup = async () => {
    if (!providerId.trim()) return;
    setLoading(true);
    setSearched(true);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/facility/rito/reputation/${encodeURIComponent(providerId.trim())}`,
      );
      setView(unwrapObject<ManagementView>(res));
    } catch {
      setView(null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="space-y-3 border-t border-border pt-5">
      <h2 className="text-sm font-semibold text-foreground">Provider reputation lookup</h2>
      <div className="flex flex-wrap items-center gap-2">
        <input
          value={providerId}
          onChange={(e) => setProviderId(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && lookup()}
          placeholder="Provider public ID"
          className="min-w-[16rem] flex-1 rounded-lg border border-border px-3 py-2 text-sm"
        />
        <button
          type="button"
          onClick={lookup}
          disabled={loading || !providerId.trim()}
          className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
        >
          {loading ? "…" : "View"}
        </button>
      </div>
      {searched && !loading && (view?.domains?.length ? (
        <div className="grid gap-3 sm:grid-cols-2">
          {view.domains.map((d) => (
            <div key={d.domain} className="rounded-2xl border border-border bg-card p-4">
              <div className="mb-1 text-sm font-semibold text-foreground">
                {DOMAIN_LABELS[d.domain] ?? d.domain}
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-2xl font-bold text-foreground">
                  {d.verifiedMeanScore != null ? Number(d.verifiedMeanScore).toFixed(1) : "—"}
                </span>
                <span className="text-xs text-muted-foreground">
                  verified · {d.verifiedCount} of {d.ratingCount}
                </span>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No reputation summary for that provider.</p>
      ))}
    </section>
  );
}
