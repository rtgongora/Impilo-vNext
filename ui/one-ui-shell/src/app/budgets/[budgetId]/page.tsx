"use client";

/**
 * Intelligent Budgeting — budget detail.
 * Route: /budgets/[budgetId]
 *
 * Budget-vs-actual, variance (with explanation), transparent forecast (with
 * drivers), rule-based recommendations, commitment + actual registers, and
 * lifecycle actions. Every value comes from COSTA via the BFF — no fake numbers,
 * no dead buttons, no misleading "AI" claims (recommendations show their drivers).
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { Loader2, ArrowLeft, TrendingUp, AlertTriangle, Info } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface BudgetLine {
  lineId: string; budgetCategory: string; departmentId: string | null;
  allocatedAmount: number; glAccountRef: string | null;
}
interface BudgetDetail {
  budgetId: string; title: string; status: string; scopeLevel: string;
  periodYear: number; currency: string; lines: BudgetLine[];
}
interface Variance {
  budgeted: number; committed: number; actual: number; available: number;
  varianceAmount: number; variancePct: number | null; classification: string; driverNote: string;
  asOfDate: string;
}
interface Forecast {
  method: string; projectedYearEnd: number; projectedOverrun: number;
  elapsedFraction: number | null; confidenceNote: string; asOfDate: string;
}
interface Recommendation {
  recommendationId: string; ruleCode: string; severity: string; title: string;
  rationale: string; suggestedAction: string; status: string;
}
interface Commitment {
  commitmentId: string; ownerSystem: string; ownerReference: string;
  committedAmount: number; liquidatedAmount: number; status: string;
}
interface Actual {
  actualRefId: string; source: string; sourceReference: string; actualAmount: number; postedAt: string;
}

const CLASS_STYLE: Record<string, string> = {
  ON_TRACK: "bg-green-100 text-green-700 border-green-300",
  FAVOURABLE: "bg-green-100 text-green-700 border-green-300",
  UNFAVOURABLE_MINOR: "bg-amber-100 text-warning-foreground border-amber-300",
  UNFAVOURABLE_MAJOR: "bg-orange-100 text-orange-700 border-orange-300",
  OVERSPENT: "bg-red-100 text-red-700 border-red-300",
  UNDERSPENT_STALE: "bg-blue-100 text-blue-700 border-blue-300",
};
const SEV_ICON: Record<string, typeof Info> = { INFO: Info, WARN: AlertTriangle, CRITICAL: AlertTriangle };

const NEXT_ACTION: Record<string, string | null> = {
  DRAFT: "submit", SUBMITTED: "review", UNDER_REVIEW: "approve", APPROVED: "activate", ACTIVE: null,
};

function money(v: number | null | undefined, currency: string) {
  if (v == null) return "—";
  return `${currency} ${Number(v).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export default function BudgetDetailPage() {
  const params = useParams();
  const budgetId = params.budgetId as string;
  const queryClient = useQueryClient();
  const base = `/internal/v1/finance/budgets/managed/${budgetId}`;

  const budgetQ = useQuery<ApiResponse<BudgetDetail>>({
    queryKey: ["budget", budgetId],
    queryFn: () => apiClient.get(base),
  });
  const varianceQ = useQuery<ApiResponse<Variance>>({
    queryKey: ["budget-variance", budgetId],
    queryFn: () => apiClient.get(`${base}/variance`),
  });
  const forecastQ = useQuery<ApiResponse<Forecast>>({
    queryKey: ["budget-forecast", budgetId],
    queryFn: () => apiClient.get(`${base}/forecast`),
  });
  const recsQ = useQuery<ApiResponse<Recommendation[]>>({
    queryKey: ["budget-recs", budgetId],
    queryFn: () => apiClient.get(`${base}/recommendations`),
  });
  const commitQ = useQuery<ApiResponse<Commitment[]>>({
    queryKey: ["budget-commitments", budgetId],
    queryFn: () => apiClient.get(`${base}/commitments`),
  });
  const actualQ = useQuery<ApiResponse<Actual[]>>({
    queryKey: ["budget-actuals", budgetId],
    queryFn: () => apiClient.get(`${base}/actuals`),
  });

  const invalidateAll = () =>
    ["budget", "budget-variance", "budget-forecast", "budget-recs", "budget-commitments", "budget-actuals"].forEach(
      (k) => queryClient.invalidateQueries({ queryKey: [k, budgetId] }),
    );

  const transition = useMutation({
    mutationFn: (action: string) => apiClient.post(`${base}/${action}`, {}),
    onSuccess: invalidateAll,
  });
  const evaluate = useMutation({
    mutationFn: () => apiClient.post(`${base}/recommendations/evaluate`, {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["budget-recs", budgetId] }),
  });

  const budget = budgetQ.data?.data;
  const variance = varianceQ.data?.data;
  const forecast = forecastQ.data?.data;
  const recs = recsQ.data?.data ?? [];
  const commitments = commitQ.data?.data ?? [];
  const actuals = actualQ.data?.data ?? [];
  const currency = budget?.currency ?? "USD";

  if (budgetQ.isLoading) {
    return (
      <AppLayout>
        <PageShell title="Budget">
          <div className="flex items-center gap-2 text-muted-foreground py-12 justify-center">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading budget…
          </div>
        </PageShell>
      </AppLayout>
    );
  }
  if (budgetQ.isError || !budget) {
    return (
      <AppLayout>
        <PageShell title="Budget">
          <div className="py-12 text-center text-destructive">Could not load this budget.</div>
        </PageShell>
      </AppLayout>
    );
  }

  const nextAction = NEXT_ACTION[budget.status];
  const utilPct =
    variance && variance.budgeted > 0
      ? Math.min(100, ((variance.committed + variance.actual) / variance.budgeted) * 100)
      : 0;

  return (
    <AppLayout>
      <PageShell title={budget.title} subtitle={`${budget.scopeLevel} · FY${budget.periodYear} · ${currency}`}>
        <Link href="/budgets" className="inline-flex items-center gap-1 text-sm text-muted-foreground mb-4">
          <ArrowLeft className="h-4 w-4" /> All budgets
        </Link>

        <div className="mb-6 flex items-center gap-3 flex-wrap">
          <span className="rounded-full border px-3 py-1 text-xs bg-muted/40">{budget.status}</span>
          {nextAction && (
            <button
              disabled={transition.isPending}
              onClick={() => transition.mutate(nextAction)}
              className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50 capitalize"
            >
              {transition.isPending ? "Working…" : nextAction}
            </button>
          )}
          {transition.isError && (
            <span className="text-sm text-destructive">Action blocked (check role / segregation of duties)</span>
          )}
        </div>

        {/* KPI row — budget vs actual */}
        <div className="grid gap-3 sm:grid-cols-4 mb-6">
          {[
            ["Budgeted", variance?.budgeted],
            ["Committed", variance?.committed],
            ["Actual", variance?.actual],
            ["Available", variance?.available],
          ].map(([label, val]) => (
            <div key={label as string} className="rounded-lg border border-border p-4 bg-card">
              <div className="text-xs text-muted-foreground">{label as string}</div>
              <div className="text-lg font-semibold">{money(val as number, currency)}</div>
            </div>
          ))}
        </div>

        {/* utilisation bar */}
        {variance && (
          <div className="mb-6">
            <div className="flex justify-between text-xs text-muted-foreground mb-1">
              <span>Utilisation (committed + actual)</span>
              <span>{utilPct.toFixed(1)}%</span>
            </div>
            <div className="h-2 w-full rounded-full bg-muted overflow-hidden">
              <div className="h-full bg-primary" style={{ width: `${utilPct}%` }} />
            </div>
            <div className="mt-1 text-xs text-muted-foreground">As of {variance.asOfDate}</div>
          </div>
        )}

        <div className="grid gap-4 lg:grid-cols-2 mb-6">
          {/* variance */}
          <div className="rounded-lg border border-border p-4 bg-card">
            <h3 className="font-medium mb-2">Variance</h3>
            {variance ? (
              <>
                <span className={`inline-block rounded-full border px-2 py-0.5 text-xs ${CLASS_STYLE[variance.classification] ?? ""}`}>
                  {variance.classification.replace(/_/g, " ")}
                </span>
                <p className="mt-2 text-sm">
                  Variance {money(variance.varianceAmount, currency)}
                  {variance.variancePct != null && ` (${variance.variancePct}%)`}
                </p>
                <p className="mt-2 text-xs text-muted-foreground">{variance.driverNote}</p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">No variance data.</p>
            )}
          </div>

          {/* forecast */}
          <div className="rounded-lg border border-border p-4 bg-card">
            <h3 className="font-medium mb-2 flex items-center gap-1">
              <TrendingUp className="h-4 w-4" /> Forecast
            </h3>
            {forecast ? (
              <>
                <p className="text-sm">
                  Projected year-end <strong>{money(forecast.projectedYearEnd, currency)}</strong> ({forecast.method})
                </p>
                <p className={`text-sm ${forecast.projectedOverrun > 0 ? "text-destructive" : "text-green-700"}`}>
                  {forecast.projectedOverrun > 0 ? "Overrun" : "Headroom"} {money(Math.abs(forecast.projectedOverrun), currency)}
                </p>
                <p className="mt-2 text-xs text-muted-foreground">{forecast.confidenceNote}</p>
              </>
            ) : (
              <p className="text-sm text-muted-foreground">No forecast data.</p>
            )}
          </div>
        </div>

        {/* recommendations */}
        <div className="rounded-lg border border-border p-4 bg-card mb-6">
          <div className="flex items-center justify-between mb-3">
            <h3 className="font-medium">Recommendations</h3>
            <button
              onClick={() => evaluate.mutate()}
              disabled={evaluate.isPending}
              className="text-sm text-primary hover:underline disabled:opacity-50"
            >
              {evaluate.isPending ? "Evaluating…" : "Re-evaluate"}
            </button>
          </div>
          {recs.length === 0 ? (
            <p className="text-sm text-muted-foreground">No recommendations. Re-evaluate to check current risk.</p>
          ) : (
            <ul className="space-y-2">
              {recs.map((r) => {
                const Icon = SEV_ICON[r.severity] ?? Info;
                return (
                  <li key={r.recommendationId} className="flex gap-2 text-sm border-t border-border pt-2 first:border-0 first:pt-0">
                    <Icon className={`h-4 w-4 mt-0.5 ${r.severity === "CRITICAL" ? "text-destructive" : "text-warning-foreground"}`} />
                    <div>
                      <div className="font-medium">{r.title} <span className="text-xs text-muted-foreground">({r.ruleCode})</span></div>
                      <div className="text-muted-foreground">{r.rationale}</div>
                      {r.suggestedAction && <div className="text-xs mt-0.5">→ {r.suggestedAction}</div>}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>

        {/* lines */}
        <Register title="Budget lines" cols={["Category", "Department", "GL ref", "Allocated"]}>
          {budget.lines?.map((l) => (
            <tr key={l.lineId} className="border-t border-border">
              <td className="px-4 py-2">{l.budgetCategory}</td>
              <td className="px-4 py-2">{l.departmentId ?? "—"}</td>
              <td className="px-4 py-2">{l.glAccountRef ?? "—"}</td>
              <td className="px-4 py-2">{money(l.allocatedAmount, currency)}</td>
            </tr>
          ))}
        </Register>

        {/* commitments */}
        <Register title="Commitments" cols={["Owner", "Reference", "Committed", "Liquidated", "Status"]}>
          {commitments.map((c) => (
            <tr key={c.commitmentId} className="border-t border-border">
              <td className="px-4 py-2">{c.ownerSystem}</td>
              <td className="px-4 py-2">{c.ownerReference}</td>
              <td className="px-4 py-2">{money(c.committedAmount, currency)}</td>
              <td className="px-4 py-2">{money(c.liquidatedAmount, currency)}</td>
              <td className="px-4 py-2">{c.status}</td>
            </tr>
          ))}
        </Register>

        {/* actuals */}
        <Register title="Actual expenditure" cols={["Source", "Reference", "Amount", "Posted"]}>
          {actuals.map((a) => (
            <tr key={a.actualRefId} className="border-t border-border">
              <td className="px-4 py-2">{a.source}</td>
              <td className="px-4 py-2">{a.sourceReference}</td>
              <td className="px-4 py-2">{money(a.actualAmount, currency)}</td>
              <td className="px-4 py-2">{a.postedAt?.slice(0, 10)}</td>
            </tr>
          ))}
        </Register>
      </PageShell>
    </AppLayout>
  );
}

function Register({ title, cols, children }: { title: string; cols: string[]; children: React.ReactNode }) {
  const rows = Array.isArray(children) ? children : [children];
  return (
    <div className="mb-6">
      <h3 className="font-medium mb-2">{title}</h3>
      <div className="overflow-x-auto rounded-lg border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted/40 text-left text-muted-foreground">
            <tr>{cols.map((c) => <th key={c} className="px-4 py-2 font-medium">{c}</th>)}</tr>
          </thead>
          <tbody>
            {rows.length > 0 ? rows : (
              <tr><td colSpan={cols.length} className="px-4 py-6 text-center text-muted-foreground">None yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
