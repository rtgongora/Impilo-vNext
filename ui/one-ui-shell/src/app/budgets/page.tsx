"use client";

/**
 * Intelligent Budgeting — dashboard.
 * Route: /budgets | pageTitle: "Budgets"
 *
 * Lists managed budgets (COSTA is the system of record via the BFF) and lets a
 * budget holder create a new budget. All values are real; no placeholder numbers.
 */

import { useState } from "react";
import Link from "next/link";
import { Loader2, Wallet, Plus } from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface BudgetSummary {
  budgetId: string;
  title: string;
  status: string;
  scopeLevel: string;
  periodYear: number;
  currency: string;
  facilityId: string | null;
}

const STATUS_STYLE: Record<string, string> = {
  DRAFT: "bg-neutral-100 text-muted-foreground border-border",
  SUBMITTED: "bg-amber-100 text-warning-foreground border-amber-300",
  UNDER_REVIEW: "bg-amber-100 text-warning-foreground border-amber-300",
  APPROVED: "bg-blue-100 text-blue-700 border-blue-300",
  ACTIVE: "bg-green-100 text-green-700 border-green-300",
  REVISED: "bg-blue-100 text-blue-700 border-blue-300",
  FROZEN: "bg-purple-100 text-purple-700 border-purple-300",
  CLOSED: "bg-neutral-200 text-muted-foreground border-border",
  ARCHIVED: "bg-neutral-200 text-muted-foreground border-border",
};

export default function BudgetsDashboardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const queryClient = useQueryClient();
  const currentYear = new Date().getFullYear();
  const [year, setYear] = useState<number>(currentYear);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ title: "", scopeLevel: "FACILITY", currency: "USD" });

  const { data, isLoading, isError } = useQuery<ApiResponse<BudgetSummary[]>>({
    queryKey: ["budgets", facility?.id, year],
    queryFn: () => {
      let url = `/internal/v1/finance/budgets/managed?year=${year}`;
      if (facility?.id) url += `&facility_id=${facility.id}`;
      return apiClient.get(url);
    },
  });

  const createBudget = useMutation({
    mutationFn: () =>
      apiClient.post<ApiResponse<BudgetSummary>>("/internal/v1/finance/budgets/managed", {
        facilityId: form.scopeLevel === "FACILITY" ? facility?.id : null,
        scopeLevel: form.scopeLevel,
        periodYear: year,
        title: form.title,
        currency: form.currency,
      }),
    onSuccess: () => {
      setShowCreate(false);
      setForm({ title: "", scopeLevel: "FACILITY", currency: "USD" });
      queryClient.invalidateQueries({ queryKey: ["budgets"] });
    },
  });

  const budgets = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Budgets" subtitle="Plan, approve, execute and track budgets" icon={<Wallet />}>
        <div className="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <label className="text-sm text-muted-foreground flex items-center gap-2">
            Financial year
            <input
              type="number"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="w-24 rounded-md border border-border px-2 py-1 text-sm"
            />
          </label>
          <button
            onClick={() => setShowCreate((v) => !v)}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground"
          >
            <Plus className="h-4 w-4" /> New budget
          </button>
        </div>

        {showCreate && (
          <div className="mb-6 rounded-lg border border-border p-4 bg-card">
            <h3 className="font-medium mb-3">Create budget</h3>
            <div className="grid gap-3 sm:grid-cols-3">
              <input
                placeholder="Title (e.g. HIV Programme 2026)"
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                className="rounded-md border border-border px-2 py-1.5 text-sm sm:col-span-2"
              />
              <select
                value={form.scopeLevel}
                onChange={(e) => setForm({ ...form, scopeLevel: e.target.value })}
                className="rounded-md border border-border px-2 py-1.5 text-sm"
              >
                {["FACILITY", "DISTRICT", "PROVINCE", "NATIONAL"].map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
            <div className="mt-3 flex gap-2">
              <button
                disabled={!form.title || createBudget.isPending}
                onClick={() => createBudget.mutate()}
                className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              >
                {createBudget.isPending ? "Creating…" : "Create draft"}
              </button>
              {createBudget.isError && (
                <span className="text-sm text-destructive self-center">Could not create budget</span>
              )}
            </div>
          </div>
        )}

        {isLoading ? (
          <div className="flex items-center gap-2 text-muted-foreground py-12 justify-center">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading budgets…
          </div>
        ) : isError ? (
          <div className="py-12 text-center text-destructive">Could not load budgets. Please retry.</div>
        ) : budgets.length === 0 ? (
          <div className="py-12 text-center text-muted-foreground">
            No budgets for {year}. Create one to begin planning.
          </div>
        ) : (
          <div className="overflow-x-auto rounded-lg border border-border">
            <table className="w-full text-sm">
              <thead className="bg-muted/40 text-left text-muted-foreground">
                <tr>
                  <th className="px-4 py-2 font-medium">Title</th>
                  <th className="px-4 py-2 font-medium">Scope</th>
                  <th className="px-4 py-2 font-medium">Year</th>
                  <th className="px-4 py-2 font-medium">Currency</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {budgets.map((b) => (
                  <tr key={b.budgetId} className="border-t border-border hover:bg-muted/20">
                    <td className="px-4 py-2">
                      <Link href={`/budgets/${b.budgetId}`} className="text-primary hover:underline">
                        {b.title}
                      </Link>
                    </td>
                    <td className="px-4 py-2">{b.scopeLevel}</td>
                    <td className="px-4 py-2">{b.periodYear}</td>
                    <td className="px-4 py-2">{b.currency}</td>
                    <td className="px-4 py-2">
                      <span className={`inline-block rounded-full border px-2 py-0.5 text-xs ${STATUS_STYLE[b.status] ?? ""}`}>
                        {b.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
