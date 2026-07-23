"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, ShieldCheck } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public coverage compare (PF-W4). Compare medical-aid/insurance plans and their benefits
 * WITHOUT sign-in — plan/benefit definitions only. Membership, personal eligibility,
 * authorisations and claims require sign-in.
 */

interface Plan {
  planCode?: string;
  planName?: string;
  payerId?: string;
  planType?: string;
  status?: string;
}

export function PublicCoverageCompare() {
  const [plans, setPlans] = useState<Plan[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<Plan[]>("/internal/v1/public/gateway/coverage/plans")
      .then((r) => {
        if (!cancelled) setPlans(Array.isArray(r) ? r : []);
      })
      .catch(() => {
        if (!cancelled) setError("Coverage information is temporarily unavailable. Please try again shortly.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) return <p className="text-sm text-red-700">{error}</p>;
  if (plans === null) {
    return (
      <p className="flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading plans…
      </p>
    );
  }

  return (
    <div className="space-y-6">
      {plans.length === 0 ? (
        <p className="text-sm text-slate-600" data-testid="coverage-empty">
          No plans are published for comparison yet.
        </p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" data-testid="coverage-plans">
          {plans.map((p) => (
            <div key={p.planCode} className="rounded-xl border border-slate-200 bg-white p-4">
              <div className="flex items-start justify-between gap-2">
                <p className="font-semibold text-slate-900">{p.planName}</p>
                <ShieldCheck className="h-4 w-4 shrink-0 text-emerald-600" aria-hidden />
              </div>
              {p.planType && <p className="text-xs text-slate-500">{p.planType}</p>}
              <p className="mt-2 text-[12px] text-slate-500">Plan code: {p.planCode}</p>
            </div>
          ))}
        </div>
      )}

      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <p className="text-sm font-medium text-emerald-900">Check your own cover?</p>
        <p className="mt-1 text-sm text-emerald-800">
          Compare freely. Sign in to view your membership, check personal eligibility, request
          authorisations and submit claims.
        </p>
        <Link
          href="/auth/login?returnTo=%2Fcoverage"
          className="mt-2 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Sign in for your cover
        </Link>
      </div>
    </div>
  );
}
