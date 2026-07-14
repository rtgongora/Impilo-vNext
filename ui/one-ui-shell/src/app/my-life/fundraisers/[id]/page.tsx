"use client";

/**
 * Fundraiser detail — status timeline, live progress, contributions (anonymous donors
 * stay anonymous), share link, independent verification link, donate drawer, and
 * owner actions (cancel / release) with confirm dialogs. Errors from the money rail
 * (409 not-open, insufficient funds) surface verbatim-ish.
 * Route: /my-life/fundraisers/[id]
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  BadgeCheck,
  Clock,
  Copy,
  ExternalLink,
  HandHeart,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Users,
  XCircle,
} from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { randomUUID } from "@/lib/uuid";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  asArray,
  errMessage,
  isVerified,
  money,
  progressPct,
  statusMeta,
  type FundraiserCase,
  type FundraiserContribution,
  type FundraiserDetail,
  type FundraiserTimelineEvent,
} from "../fundraisers";

export default function FundraiserDetailPage() {
  const params = useParams();
  const searchParams = useSearchParams();
  const id = String(params?.id ?? "");
  const user = useAuthStore((s) => s.user);

  const [detail, setDetail] = useState<FundraiserDetail | null>(null);
  const [timeline, setTimeline] = useState<FundraiserTimelineEvent[]>([]);
  const [contributions, setContributions] = useState<FundraiserContribution[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [donateOpen, setDonateOpen] = useState(searchParams.get("donate") === "1");
  const [amount, setAmount] = useState("");
  const [message, setMessage] = useState("");
  const [anonymous, setAnonymous] = useState(false);
  const [donating, setDonating] = useState(false);
  const [donateError, setDonateError] = useState<string | null>(null);
  const [donated, setDonated] = useState(false);

  const [actionError, setActionError] = useState<string | null>(null);
  const [actionBusy, setActionBusy] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const d = await apiClient.get<FundraiserDetail>(
        `/internal/v1/citizen/fundraisers/${encodeURIComponent(id)}`,
      );
      setDetail(d);
      const [t, c] = await Promise.all([
        apiClient
          .get<unknown>(`/internal/v1/citizen/fundraisers/${encodeURIComponent(id)}/timeline`)
          .catch(() => []),
        apiClient
          .get<unknown>(`/internal/v1/citizen/fundraisers/${encodeURIComponent(id)}/contributions`)
          .catch(() => []),
      ]);
      setTimeline(asArray<FundraiserTimelineEvent>(t));
      setContributions(asArray<FundraiserContribution>(c));
    } catch (e) {
      setError(errMessage(e, "We couldn't load that fundraiser."));
      setDetail(null);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const c: FundraiserCase = detail?.case ?? {};
  const status = c.verificationStatus;
  const isOwner =
    !!c.requesterActorId &&
    !!user &&
    (user.healthId === c.requesterActorId || user.id === c.requesterActorId);
  const verifyUrl = detail?.independentVerificationUrl ?? c.attestationPublicToken;

  const donate = async (e: React.FormEvent) => {
    e.preventDefault();
    setDonating(true);
    setDonateError(null);
    try {
      // Fresh idempotency key per donation attempt — retries of the SAME attempt reuse it.
      const idempotencyKey = randomUUID();
      await apiClient.post<unknown>(
        `/internal/v1/citizen/fundraisers/${encodeURIComponent(id)}/donate`,
        { amount, message: message.trim() || undefined, isAnonymous: anonymous },
        { extraHeaders: { "Idempotency-Key": idempotencyKey } },
      );
      setDonated(true);
      setAmount("");
      setMessage("");
      await load();
    } catch (e2) {
      // 409 FUNDRAISER_NOT_OPEN and mushe rejections (e.g. insufficient funds) verbatim-ish.
      setDonateError(errMessage(e2, "Your donation could not be captured — nothing was charged."));
    } finally {
      setDonating(false);
    }
  };

  const runAction = async (action: "cancel" | "release", confirmText: string) => {
    if (typeof window !== "undefined" && !window.confirm(confirmText)) return;
    setActionBusy(action);
    setActionError(null);
    try {
      await apiClient.post<unknown>(
        `/internal/v1/citizen/fundraisers/${encodeURIComponent(id)}/${action}`,
        action === "release" ? {} : undefined,
      );
      await load();
    } catch (e2) {
      setActionError(errMessage(e2, `Could not ${action} this fundraiser.`));
    } finally {
      setActionBusy(null);
    }
  };

  const copyShareLink = async () => {
    if (!detail?.shareUrl) return;
    const url = `${window.location.origin}${detail.shareUrl}`;
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard unavailable (insecure context) — show the raw URL instead.
      window.prompt("Copy this share link:", url);
    }
  };

  return (
    <AppLayout>
      <PageShell
        title="Fundraiser"
        subtitle="Progress, verification and contributions"
        serviceSlug="daidzai"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <div className="mb-4">
            <Link
              href="/my-life/fundraisers"
              className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to fundraisers
            </Link>
          </div>

          {loading ? (
            <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading fundraiser…
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
          ) : !detail ? (
            <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
              We couldn&apos;t find that fundraiser.
            </div>
          ) : (
            <div className="space-y-6">
              <GlassSurface className="p-5">
                <div className="mb-2 flex flex-wrap items-center gap-2 text-xs">
                  {c.assistanceReference && (
                    <span className="font-mono font-semibold text-teal-700">
                      {c.assistanceReference}
                    </span>
                  )}
                  <span
                    className={`rounded-full px-2 py-0.5 font-medium ${statusMeta(status).className}`}
                    data-testid="status-chip"
                  >
                    {statusMeta(status).label}
                  </span>
                  {isVerified(status) && (
                    <span
                      className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 font-medium text-emerald-800"
                      data-testid="verified-badge"
                    >
                      <BadgeCheck className="h-3.5 w-3.5" /> Verified
                    </span>
                  )}
                  {c.category && (
                    <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                      {c.category.replace(/_/g, " ")}
                    </span>
                  )}
                </div>
                <h2 className="text-lg font-semibold text-foreground">{c.title ?? "Fundraiser"}</h2>
                {c.story && <p className="mt-2 text-sm text-muted-foreground">{c.story}</p>}

                <div className="mt-4">
                  <div className="h-2.5 rounded-full bg-slate-200">
                    <div
                      className="h-2.5 rounded-full bg-emerald-500 transition-all"
                      style={{ width: `${progressPct(c.raisedAmount, c.targetAmount)}%` }}
                      data-testid="progress-bar"
                    />
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                    <span className="font-semibold text-emerald-700">
                      {money(c.raisedAmount, c.currency)}
                    </span>
                    <span className="text-muted-foreground">
                      of {money(c.targetAmount, c.currency)}
                    </span>
                    <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                      <Users className="h-3.5 w-3.5" /> {c.donorCount ?? 0} donors
                    </span>
                    {detail.moneyUnavailable && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-800">
                        Live money view unavailable — amounts shown are the latest recorded
                        snapshot
                      </span>
                    )}
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
                  {detail.shareUrl && (
                    <button
                      type="button"
                      onClick={() => void copyShareLink()}
                      data-testid="share-copy"
                      className="inline-flex items-center gap-1.5 rounded-lg border border-teal-300 bg-white px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50"
                    >
                      <Copy className="h-3.5 w-3.5" /> {copied ? "Copied!" : "Copy share link"}
                    </button>
                  )}
                  {verifyUrl && (
                    <a
                      href={verifyUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      data-testid="verify-link"
                      className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 bg-white px-3 py-1.5 text-sm font-medium text-emerald-700 hover:bg-emerald-50"
                    >
                      <ShieldCheck className="h-3.5 w-3.5" /> Verify independently
                      <ExternalLink className="h-3 w-3" />
                    </a>
                  )}
                  {status === "ACTIVE" && (
                    <button
                      type="button"
                      onClick={() => {
                        setDonateOpen(true);
                        setDonated(false);
                      }}
                      data-testid="donate-open"
                      className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-teal-700"
                    >
                      <HandHeart className="h-4 w-4" /> Donate
                    </button>
                  )}
                </div>
              </GlassSurface>

              {donateOpen && status === "ACTIVE" && (
                <GlassSurface className="p-5" data-testid="donate-drawer">
                  <h3 className="mb-3 text-sm font-semibold text-foreground">Donate</h3>
                  {donated && (
                    <div className="mb-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
                      Thank you — your donation was recorded.
                    </div>
                  )}
                  {donateError && (
                    <div
                      className="mb-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
                      data-testid="donate-error"
                    >
                      {donateError}
                    </div>
                  )}
                  <form onSubmit={donate} className="space-y-3">
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <label className="mb-1 block text-sm font-medium text-foreground">
                          Amount ({c.currency ?? "USD"})
                        </label>
                        <input
                          value={amount}
                          onChange={(e) => setAmount(e.target.value)}
                          required
                          type="number"
                          min="0.01"
                          step="0.01"
                          inputMode="decimal"
                          data-testid="donate-amount"
                          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        />
                      </div>
                      <div>
                        <label className="mb-1 block text-sm font-medium text-foreground">
                          Message (optional)
                        </label>
                        <input
                          value={message}
                          onChange={(e) => setMessage(e.target.value)}
                          data-testid="donate-message"
                          className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                        />
                      </div>
                    </div>
                    <label className="flex items-center gap-2 text-sm text-foreground">
                      <input
                        type="checkbox"
                        checked={anonymous}
                        onChange={(e) => setAnonymous(e.target.checked)}
                        data-testid="donate-anonymous"
                      />
                      Donate anonymously
                    </label>
                    <div className="flex gap-2">
                      <button
                        type="submit"
                        disabled={donating || !amount || Number(amount) <= 0}
                        data-testid="donate-submit"
                        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-5 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                      >
                        {donating && <Loader2 className="h-4 w-4 animate-spin" />}
                        {donating ? "Donating…" : "Donate from my wallet"}
                      </button>
                      <button
                        type="button"
                        onClick={() => setDonateOpen(false)}
                        className="rounded-lg border border-border px-4 py-2 text-sm text-muted-foreground hover:bg-slate-50"
                      >
                        Close
                      </button>
                    </div>
                  </form>
                </GlassSurface>
              )}

              {isOwner && (
                <GlassSurface className="p-5">
                  <h3 className="mb-2 text-sm font-semibold text-foreground">Owner actions</h3>
                  {actionError && (
                    <div className="mb-3 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">
                      {actionError}
                    </div>
                  )}
                  <div className="flex flex-wrap gap-2">
                    <button
                      type="button"
                      disabled={actionBusy !== null}
                      onClick={() =>
                        void runAction(
                          "cancel",
                          "Cancel this fundraiser? It will stop accepting donations and cannot be reopened.",
                        )
                      }
                      data-testid="cancel-action"
                      className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-50 disabled:opacity-50"
                    >
                      <XCircle className="h-3.5 w-3.5" />
                      {actionBusy === "cancel" ? "Cancelling…" : "Cancel fundraiser"}
                    </button>
                    <button
                      type="button"
                      disabled={actionBusy !== null}
                      onClick={() =>
                        void runAction(
                          "release",
                          "Release the raised funds to the beneficiary wallet now?",
                        )
                      }
                      data-testid="release-action"
                      className="inline-flex items-center gap-1.5 rounded-lg border border-teal-300 bg-white px-3 py-1.5 text-sm font-medium text-teal-700 hover:bg-teal-50 disabled:opacity-50"
                    >
                      <HandHeart className="h-3.5 w-3.5" />
                      {actionBusy === "release" ? "Releasing…" : "Release funds"}
                    </button>
                  </div>
                </GlassSurface>
              )}

              <GlassSurface className="p-5">
                <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Users className="h-4 w-4 text-teal-600" /> Contributions
                </h3>
                {contributions.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No contributions yet.</p>
                ) : (
                  <ul className="space-y-2">
                    {contributions.map((ct, i) => (
                      <li
                        key={ct.contributionId ?? i}
                        className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-sm"
                        data-testid="contribution-row"
                      >
                        <div>
                          <span className="font-medium text-foreground">
                            {ct.isAnonymous ? "Anonymous" : (ct.contributorLabel ?? "Anonymous")}
                          </span>
                          {ct.message && (
                            <span className="ml-2 text-muted-foreground">“{ct.message}”</span>
                          )}
                        </div>
                        <div className="flex items-baseline gap-2">
                          <span className="font-semibold text-emerald-700">
                            {money(ct.amount, c.currency)}
                          </span>
                          <span className="text-xs text-muted-foreground">
                            {ct.recordedAt ? new Date(ct.recordedAt).toLocaleString() : ""}
                          </span>
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </GlassSurface>

              <GlassSurface className="p-5">
                <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Clock className="h-4 w-4 text-teal-600" /> Status timeline
                </h3>
                {timeline.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No events recorded yet.</p>
                ) : (
                  <ol className="space-y-3">
                    {timeline.map((ev, i) => (
                      <li key={i} className="border-l-2 border-teal-200 pl-3">
                        <div className="text-sm font-medium text-foreground">
                          {ev.toStatus
                            ? statusMeta(ev.toStatus).label
                            : (ev.eventType ?? "Update").replace(/_/g, " ")}
                        </div>
                        {ev.note && <p className="text-sm text-muted-foreground">{ev.note}</p>}
                        <div className="text-xs text-muted-foreground">
                          {ev.occurredAt ? new Date(ev.occurredAt).toLocaleString() : ""}
                        </div>
                      </li>
                    ))}
                  </ol>
                )}
              </GlassSurface>
            </div>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
