"use client";

/**
 * Public fundraiser share target — /share/fund/[token].
 *
 * Shows the tokenized money view (title + progress) from the BFF bill-contribution
 * endpoint GET /internal/v1/wallet/bill-contributions/{shareToken}. Signed-out visitors
 * get a Donate CTA that routes to login with returnTo back here; signed-in visitors are
 * routed to the fundraiser detail (resolved from the public list by share token) where
 * the governed donate flow lives. Honest empty/error states — no fabricated progress.
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
        // Honest state: this deployment requires sign-in to view the tokenized money view.
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

  // Signed-in visitors: resolve the fundraiser case id from the public list so the
  // Donate CTA can land on the governed donate flow (wallet-debiting, idempotent).
  useEffect(() => {
    if (!isAuthenticated || !token) return;
    let cancelled = false;
    void (async () => {
      try {
        const res = await apiClient.get<unknown>("/internal/v1/citizen/fundraisers");
        const match = asArray<FundraiserCase>(res).find((f) => f.shareToken === token);
        if (!cancelled && match?.id) setCaseId(match.id);
      } catch {
        // Non-fatal — the share view still renders; donation entry just stays generic.
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, token]);

  const req = view?.request;
  const returnTo = encodeURIComponent(`/share/fund/${token}`);
  const loginHref = `/auth/login?returnTo=${returnTo}`;

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

          <div className="mt-5">
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
            ) : (
              <p className="text-sm text-muted-foreground" data-testid="share-not-open">
                This fundraiser is not currently open for donations through the public list.
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
