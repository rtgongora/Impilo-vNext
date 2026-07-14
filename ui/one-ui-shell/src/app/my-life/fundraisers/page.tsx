"use client";

/**
 * Citizen fundraisers — browse verified-ACTIVE public fundraisers and track my own
 * cases (any status, honest chips). Route: /my-life/fundraisers
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { BadgeCheck, HandHeart, Loader2, Plus, RefreshCw, Users } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import {
  asArray,
  errMessage,
  isVerified,
  money,
  progressPct,
  statusMeta,
  type FundraiserCase,
} from "./fundraisers";

type Tab = "public" | "mine";

function ProgressBar({ raised, target }: { raised?: number | string; target?: number | string }) {
  const pct = progressPct(raised, target);
  return (
    <div>
      <div className="h-2 rounded-full bg-slate-200">
        <div
          className="h-2 rounded-full bg-emerald-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="mt-1 text-xs text-muted-foreground">{pct}% of target</div>
    </div>
  );
}

export default function FundraisersPage() {
  const [tab, setTab] = useState<Tab>("public");
  const [publicCases, setPublicCases] = useState<FundraiserCase[]>([]);
  const [mine, setMine] = useState<FundraiserCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (which: Tab) => {
    setLoading(true);
    setError(null);
    try {
      if (which === "public") {
        const res = await apiClient.get<unknown>("/internal/v1/citizen/fundraisers");
        setPublicCases(asArray<FundraiserCase>(res));
      } else {
        const res = await apiClient.get<unknown>("/internal/v1/citizen/fundraisers/mine");
        setMine(asArray<FundraiserCase>(res));
      }
    } catch (e) {
      setError(errMessage(e, "We couldn't load fundraisers right now."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(tab);
  }, [tab, load]);

  const cases = tab === "public" ? publicCases : mine;

  return (
    <AppLayout>
      <PageShell
        title="Fundraisers"
        subtitle="Help pay for care — every public fundraiser is verified by a treating facility"
        serviceSlug="daidzai"
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex gap-2" role="tablist">
              {(
                [
                  { key: "public" as Tab, label: "Public fundraisers" },
                  { key: "mine" as Tab, label: "My fundraisers" },
                ] as const
              ).map((t) => (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === t.key}
                  data-testid={`fundraisers-tab-${t.key}`}
                  onClick={() => setTab(t.key)}
                  className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
                    tab === t.key
                      ? "bg-teal-600 text-white"
                      : "bg-slate-100 text-slate-700 hover:bg-slate-200"
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </div>
            <Link
              href="/my-life/fundraisers/new"
              data-testid="start-fundraiser"
              className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
            >
              <Plus className="h-4 w-4" /> Start a fundraiser
            </Link>
          </div>

          {loading ? (
            <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading fundraisers…
            </div>
          ) : error ? (
            <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
              <p className="mb-3">{error}</p>
              <button
                type="button"
                onClick={() => void load(tab)}
                className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 py-1.5 font-medium text-red-700 hover:bg-red-100"
              >
                <RefreshCw className="h-3.5 w-3.5" /> Retry
              </button>
            </div>
          ) : cases.length === 0 ? (
            <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
              <HandHeart className="mx-auto mb-2 h-6 w-6 opacity-50" />
              {tab === "public"
                ? "No verified fundraisers are open right now."
                : "You haven't started a fundraiser yet."}
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {cases.map((c, i) => {
                const meta = statusMeta(c.verificationStatus);
                return (
                  <Link
                    key={c.id ?? i}
                    href={`/my-life/fundraisers/${encodeURIComponent(c.id ?? "")}`}
                    data-testid="fundraiser-card"
                    className="block"
                  >
                    <GlassSurface className="h-full p-4 transition-shadow hover:shadow-md">
                      <div className="mb-2 flex items-start justify-between gap-2">
                        <h3 className="font-semibold text-foreground">{c.title ?? "Fundraiser"}</h3>
                        {isVerified(c.verificationStatus) && (
                          <span
                            className="inline-flex shrink-0 items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800"
                            data-testid="verified-badge"
                          >
                            <BadgeCheck className="h-3.5 w-3.5" /> Verified
                          </span>
                        )}
                      </div>
                      {tab === "mine" && (
                        <span
                          className={`mb-2 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${meta.className}`}
                          data-testid="status-chip"
                        >
                          {meta.label}
                        </span>
                      )}
                      {c.story && (
                        <p className="mb-3 line-clamp-2 text-sm text-muted-foreground">{c.story}</p>
                      )}
                      <ProgressBar raised={c.raisedAmount} target={c.targetAmount} />
                      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
                        <span className="font-semibold text-emerald-700">
                          {money(c.raisedAmount, c.currency)}
                        </span>
                        <span>of {money(c.targetAmount, c.currency)}</span>
                        <span className="inline-flex items-center gap-1">
                          <Users className="h-3.5 w-3.5" /> {c.donorCount ?? 0} donors
                        </span>
                        {c.category && (
                          <span className="rounded-full bg-slate-100 px-2 py-0.5 text-slate-600">
                            {c.category.replace(/_/g, " ")}
                          </span>
                        )}
                      </div>
                    </GlassSurface>
                  </Link>
                );
              })}
            </div>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
