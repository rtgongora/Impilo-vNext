"use client";

/**
 * Provider "My reputation" + response surface (RW13). A provider sees how they have been
 * experienced — the authorised management view (all four domains + moderation counts) plus their
 * own ratings — and may post a professional response to a rating. Reputation is read-only truth
 * owned by Rito; a response never alters the rating or any authority record (regulation firewall).
 *
 * Route: /work/rito/my-reputation  (defaults to the signed-in provider; ?provider=<id> to override)
 */

import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Loader2, MessageSquare, ShieldCheck, Star } from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { apiClient } from "@/lib/api-client";
import {
  DOMAIN_LABELS,
  type ManagementView,
  type RatingSummary,
  unwrapList,
  unwrapObject,
} from "@/lib/rito/reputation";

export default function MyReputationPage() {
  const { user } = useAuthStore();
  const searchParams = useSearchParams();
  const providerPublicId = searchParams.get("provider") ?? user?.linkedIds?.providerId ?? "";

  const [view, setView] = useState<ManagementView | null>(null);
  const [ratings, setRatings] = useState<RatingSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!providerPublicId) return;
    setLoading(true);
    setError(null);
    try {
      const q = `?provider_public_id=${encodeURIComponent(providerPublicId)}`;
      const [v, r] = await Promise.all([
        apiClient.get<unknown>(`/internal/v1/work/rito/my-reputation${q}`),
        apiClient.get<unknown>(`/internal/v1/work/rito/my-ratings${q}`),
      ]);
      setView(unwrapObject<ManagementView>(v));
      setRatings(unwrapList<RatingSummary>(r));
    } catch {
      setError("Could not load your reputation summary. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [providerPublicId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="My reputation"
        subtitle="How you have been experienced — verified feedback across the four quality domains."
        serviceSlug="rito"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          {!providerPublicId ? (
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
              Activate your Provider ID to view your reputation.
            </div>
          ) : loading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading…
            </div>
          ) : error ? (
            <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
              {error}
            </div>
          ) : (
            <>
              <section className="grid gap-3 sm:grid-cols-2">
                {(view?.domains ?? []).map((d) => (
                  <div key={d.domain} className="rounded-2xl border border-border bg-card p-4">
                    <div className="mb-1 text-sm font-semibold text-foreground">
                      {DOMAIN_LABELS[d.domain] ?? d.domain}
                    </div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-2xl font-bold text-foreground">
                        {d.verifiedMeanScore != null ? Number(d.verifiedMeanScore).toFixed(1) : "—"}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        verified mean · {d.verifiedCount} rating{d.verifiedCount === 1 ? "" : "s"}
                      </span>
                    </div>
                    {d.meanScore != null && d.ratingCount !== d.verifiedCount && (
                      <div className="mt-0.5 text-xs text-muted-foreground">
                        {Number(d.meanScore).toFixed(1)} across all {d.ratingCount} (incl. unverified)
                      </div>
                    )}
                  </div>
                ))}
                {(!view || view.domains.length === 0) && (
                  <div className="text-sm text-muted-foreground">
                    No reputation summary yet — it appears once verified feedback is recorded.
                  </div>
                )}
              </section>

              {view?.moderationStateCounts && Object.keys(view.moderationStateCounts).length > 0 && (
                <div className="flex flex-wrap gap-2 text-xs">
                  {Object.entries(view.moderationStateCounts).map(([state, count]) => (
                    <span key={state} className="rounded-full border border-border px-2.5 py-1 text-muted-foreground">
                      {state.replace(/_/g, " ").toLowerCase()}: <b className="text-foreground">{count}</b>
                    </span>
                  ))}
                </div>
              )}

              <section className="space-y-3">
                <h2 className="text-sm font-semibold text-foreground">Your ratings</h2>
                {ratings.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No ratings yet.</p>
                ) : (
                  ratings.map((r) => <RatingRow key={r.id} rating={r} providerPublicId={providerPublicId} onResponded={load} />)
                )}
              </section>

              <div className="flex items-start gap-2 rounded-lg border border-border bg-muted/40 p-3 text-xs text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-600" />
                <span>
                  Ratings reflect experience, not a verdict on your registration. Feedback never
                  changes your licence, scope or access — concerns are only ever referred to the
                  responsible authority for a decision.
                </span>
              </div>
            </>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}

function RatingRow({
  rating,
  providerPublicId,
  onResponded,
}: {
  rating: RatingSummary;
  providerPublicId: string;
  onResponded: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [saving, setSaving] = useState(false);

  const respond = async () => {
    if (!text.trim()) return;
    setSaving(true);
    try {
      await apiClient.post<unknown>(`/internal/v1/work/rito/ratings/${encodeURIComponent(rating.id)}/response`, {
        providerPublicId,
        responseText: text.trim(),
        respondedBy: providerPublicId,
      });
      setOpen(false);
      setText("");
      onResponded();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="rounded-xl border border-border bg-card p-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
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
          {rating.verifiedInteraction && (
            <span className="rounded-full bg-teal-50 px-2 py-0.5 text-[11px] font-medium text-teal-700">
              Verified visit
            </span>
          )}
          <span className="text-xs text-muted-foreground">{rating.moderationState.toLowerCase()}</span>
        </div>
        {rating.hasProviderResponse ? (
          <span className="text-xs text-muted-foreground">Responded</span>
        ) : (
          <button
            type="button"
            onClick={() => setOpen((v) => !v)}
            className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
          >
            <MessageSquare className="h-3.5 w-3.5" /> Respond
          </button>
        )}
      </div>
      {rating.reportingPeriod && (
        <div className="mt-1 text-[11px] text-muted-foreground">
          {rating.reportingPeriod}
          {rating.providerRole ? ` · ${rating.providerRole}` : ""}
          {rating.modality ? ` · ${rating.modality}` : ""}
        </div>
      )}
      {open && (
        <div className="mt-2 space-y-2">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={3}
            placeholder="A professional, respectful response — visible alongside the rating."
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
          <button
            type="button"
            onClick={respond}
            disabled={saving || !text.trim()}
            className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-4 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {saving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {saving ? "Saving…" : "Submit response"}
          </button>
        </div>
      )}
    </div>
  );
}
