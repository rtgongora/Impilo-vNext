"use client";

/**
 * Public fundraiser / bill-contribution share target — /share/fund/[token].
 *
 * Loads the tokenized money view from GET /internal/v1/wallet/bill-contributions/{shareToken}.
 * Signed-out visitors get a Donate CTA that routes to login. Signed-in visitors:
 *   - Prefer the governed Daidzai fundraiser donate flow when a public case matches the token
 *   - Otherwise chip in directly via POST .../bill-contributions/{token}/contribute (card "help
 *     pay my bill" links are mushe-only and have no fundraiser case)
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { HandHeart, Loader2, LogIn, RefreshCw, SearchX } from "lucide-react";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  asArray,
  errMessage,
  money,
  num,
  progressPct,
  type FundraiserCase,
} from "@/app/my-life/fundraisers/fundraisers";

interface BillContributionRequest {
  id?: string;
  title?: string;
  targetAmount?: number | string;
  raisedAmount?: number | string;
  currency?: string;
  status?: string;
}

interface ShareView {
  request?: BillContributionRequest;
  contributions?: { id?: string }[];
}

function unwrapData<T>(payload: unknown): T | null {
  if (payload && typeof payload === "object" && "data" in payload) {
    const inner = (payload as { data?: unknown }).data;
    if (inner && typeof inner === "object") return inner as T;
  }
  return null;
}

export default function ShareFundPage() {
  const params = useParams();
  const token = String(params?.token ?? "");
  const isAuthenticated = useAuthStore((s) => !!s.token && !!s.user);

  const [view, setView] = useState<ShareView | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [needsSignIn, setNeedsSignIn] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [caseId, setCaseId] = useState<string | null>(null);
  const [caseLookupDone, setCaseLookupDone] = useState(false);

  const [chipAmount, setChipAmount] = useState("");
  const [chipMessage, setChipMessage] = useState("");
  const [chipBusy, setChipBusy] = useState(false);
  const [chipError, setChipError] = useState<string | null>(null);
  const [chipDone, setChipDone] = useState(false);

  const load = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError(null);
    setNotFound(false);
    setNeedsSignIn(false);
    try {
      const res = await apiClient.get<unknown>(
        `/internal/v1/wallet/bill-contributions/${encodeURIComponent(token)}`,
      );
      setView(unwrapData<ShareView>(res));
    } catch (e) {
      const status = (e as { status?: number })?.status;
      if (status === 404) {
        setNotFound(true);
      } else if (status === 401 || status === 403) {
        setNeedsSignIn(true);
      } else {
        setError(errMessage(e, "We couldn't load this fundraiser right now."));
      }
      setView(null);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!isAuthenticated || !token) {
      setCaseId(null);
      setCaseLookupDone(!isAuthenticated);
      return;
    }
    let cancelled = false;
    setCaseLookupDone(false);
    void (async () => {
      try {
        const res = await apiClient.get<unknown>("/internal/v1/citizen/fundraisers");
        const match = asArray<FundraiserCase>(res).find((f) => f.shareToken === token);
        if (!cancelled && match?.id) setCaseId(match.id);
      } catch {
        // Non-fatal — fall through to direct chip-in.
      } finally {
        if (!cancelled) setCaseLookupDone(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, token]);

  const req = view?.request;
  const returnTo = encodeURIComponent(`/share/fund/${token}`);
  const loginHref = `/auth/login?returnTo=${returnTo}`;
  const openForChipIn = !req?.status || req.status === "OPEN";

  const chipIn = async () => {
    const amount = chipAmount.trim();
    if (!amount || Number(amount) <= 0) {
      setChipError("Enter an amount greater than 0.");
      return;
    }
    setChipBusy(true);
    setChipError(null);
    try {
      await apiClient.post(
        `/internal/v1/wallet/bill-contributions/${encodeURIComponent(token)}/contribute`,
        {
          amount,
          message: chipMessage.trim() || undefined,
        },
      );
      setChipDone(true);
      setChipAmount("");
      setChipMessage("");
      await load();
    } catch (e) {
      setChipError(errMessage(e, "Contribution could not be recorded. Nothing was charged if the wallet debit failed."));
    } finally {
      setChipBusy(false);
    }
  };

  return (
    <div className="space-y-4">
      {loading ? (
        <div className="flex items-center justify-center gap-2 rounded-xl border border-border bg-card p-10 text-sm text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading fundraiser…
        </div>
      ) : notFound ? (
        <div
          className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground"
          data-testid="share-not-found"
        >
          <SearchX className="mx-auto mb-2 h-6 w-6 opacity-50" />
          This fundraiser link is unknown or has expired. Please check the link with the person
          who shared it.
        </div>
      ) : needsSignIn ? (
        <div
          className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground"
          data-testid="share-signin-required"
        >
          <LogIn className="mx-auto mb-2 h-6 w-6 opacity-50" />
          <p className="mb-4">Sign in to view and support this fundraiser.</p>
          <Link
            href={loginHref}
            className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
          >
            <LogIn className="h-4 w-4" /> Sign in to continue
          </Link>
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
      ) : !req ? (
        <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
          This fundraiser has no details to show yet.
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-card p-6" data-testid="share-fund-card">
          <h2 className="text-lg font-semibold text-foreground">{req.title ?? "Fundraiser"}</h2>

          <div className="mt-4">
            <div className="h-2.5 rounded-full bg-slate-200">
              <div
                className="h-2.5 rounded-full bg-emerald-500 transition-all"
                style={{ width: `${progressPct(req.raisedAmount, req.targetAmount)}%` }}
                data-testid="share-progress-bar"
              />
            </div>
            <div className="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1 text-sm">
              <span className="font-semibold text-emerald-700">
                {money(req.raisedAmount, req.currency)}
              </span>
              {num(req.targetAmount) > 0 && (
                <span className="text-muted-foreground">
                  of {money(req.targetAmount, req.currency)}
                </span>
              )}
              <span className="text-xs text-muted-foreground">
                {view?.contributions?.length ?? 0} contributions
              </span>
              {req.status && req.status !== "OPEN" && (
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                  {req.status.replace(/_/g, " ")}
                </span>
              )}
            </div>
          </div>

          <div className="mt-5 space-y-3">
            {!isAuthenticated ? (
              <Link
                href={loginHref}
                data-testid="share-donate-cta"
                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
              >
                <HandHeart className="h-4 w-4" /> Sign in to donate
              </Link>
            ) : caseId ? (
              <Link
                href={`/my-life/fundraisers/${encodeURIComponent(caseId)}?donate=1`}
                data-testid="share-donate-cta"
                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
              >
                <HandHeart className="h-4 w-4" /> Donate
              </Link>
            ) : !caseLookupDone ? (
              <p className="text-sm text-muted-foreground" data-testid="share-lookup">
                Checking donation options…
              </p>
            ) : !openForChipIn ? (
              <p className="text-sm text-muted-foreground" data-testid="share-not-open">
                This contribution link is not open for further donations ({req.status}).
              </p>
            ) : chipDone ? (
              <p className="text-sm text-emerald-700" data-testid="share-chip-done" role="status">
                Thank you — your contribution was recorded.
              </p>
            ) : (
              <div className="space-y-2" data-testid="share-chip-in">
                <p className="text-sm text-muted-foreground">
                  Chip in from your Impilo wallet toward this bill.
                </p>
                <div className="flex flex-wrap gap-2">
                  <input
                    type="number"
                    min="0.01"
                    step="0.01"
                    inputMode="decimal"
                    placeholder={`Amount (${req.currency ?? "USD"})`}
                    value={chipAmount}
                    onChange={(e) => setChipAmount(e.target.value)}
                    aria-label="Contribution amount"
                    className="w-36 rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  />
                  <input
                    type="text"
                    placeholder="Message (optional)"
                    value={chipMessage}
                    onChange={(e) => setChipMessage(e.target.value)}
                    aria-label="Contribution message"
                    className="min-w-[12rem] flex-1 rounded-lg border border-border bg-background px-3 py-2 text-sm"
                  />
                  <button
                    type="button"
                    onClick={() => void chipIn()}
                    disabled={chipBusy}
                    data-testid="share-chip-in-submit"
                    className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-60"
                  >
                    {chipBusy ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <HandHeart className="h-4 w-4" />
                    )}
                    Chip in
                  </button>
                </div>
                {chipError && (
                  <p className="text-sm text-red-700" role="alert">
                    {chipError}
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
