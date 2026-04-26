"use client";

/**
 * Aggregate-first workspace for supervisory and oversight visibility tiers.
 * Route: /workspace/aggregate
 */

import Link from "next/link";
import { BarChart3, FileSpreadsheet, LayoutDashboard, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useVisibilityProfile } from "@/hooks/queries/useVisibilityProfile";

export default function AggregateWorkspacePage() {
  const { data, isLoading, isError } = useVisibilityProfile();

  const tier = data?.visibilityTier ?? "—";
  const aggregate = data?.aggregateOnly === true || tier === "AGGREGATE_ONLY";

  return (
    <AppLayout>
      <PageShell
        title="Aggregate oversight workspace"
        subtitle="KPIs, counts, and regional summaries aligned to your current data visibility. Person-level charts stay hidden when your session is aggregate-only or de-identified."
      >
        <div className="mb-6 flex flex-wrap items-center gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700 shadow-sm dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200">
          <Shield className="h-4 w-4 text-impilo-500" aria-hidden />
          {isLoading && <span>Loading visibility profile…</span>}
          {isError && <span>Could not load visibility profile from the BFF.</span>}
          {!isLoading && !isError && data && (
            <span>
              Active view: <strong>{tier}</strong>
              {data.exportPolicy ? (
                <>
                  {" "}
                  · export <strong>{data.exportPolicy}</strong>
                </>
              ) : null}
              {data.obligationsHeaderPresent ? " · obligations header present" : null}
            </span>
          )}
        </div>

        <div className="grid gap-4 md:grid-cols-3">
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <LayoutDashboard className="mb-2 h-8 w-8 text-purple-500" />
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">Coverage KPIs</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Facility and programme coverage as percentages — no named patient lists in this view.
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <BarChart3 className="mb-2 h-8 w-8 text-impilo-500" />
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">Trends</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Weekly encounter volumes and stock-out signals at district or provincial grain.
            </p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-700 dark:bg-slate-900">
            <FileSpreadsheet className="mb-2 h-8 w-8 text-emerald-600" />
            <h2 className="text-base font-semibold text-slate-900 dark:text-slate-100">Redacted exports</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              When export policy is <code className="rounded bg-slate-100 px-1 dark:bg-slate-800">REDACTED</code>, the
              reporting service masks sensitive columns while still delivering analytics extracts.
            </p>
          </div>
        </div>

        <div className="mt-8 flex flex-wrap gap-3 text-sm">
          <Link href="/reports" className="text-impilo-600 underline-offset-2 hover:underline dark:text-impilo-400">
            Go to Reports hub
          </Link>
          {!aggregate ? (
            <span className="text-slate-500 dark:text-slate-400">
              Your session is not aggregate-only; clinical and operational apps remain available from the main menu.
            </span>
          ) : null}
        </div>
      </PageShell>
    </AppLayout>
  );
}
