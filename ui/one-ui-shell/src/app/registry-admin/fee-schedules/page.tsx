"use client";

/**
 * Regulatory fee schedule (SI 78 of 2017) — governance surface.
 * Route: /registry-admin/fee-schedules | guard: REGISTRY_ADMIN
 *
 * Fee AMOUNTS are defined by Statutory Instrument 78 of 2017, never by this
 * platform. This page shows the seeded structure rows (amount pending), lets a
 * registrar author an amount for a fee code and class, and approve it ACTIVE.
 * A row with no amount is shown honestly as "amount pending SI 78 of 2017" — the
 * fee gate only enforces once an amount is configured and approved.
 */

import { useState } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useFeeSchedules,
  useCreateFeeSchedule,
  useApproveFeeSchedule,
  type FeeScheduleView,
} from "@/hooks/queries/useHpaRegulatory";

function money(row: FeeScheduleView): string {
  if (row.amount == null) return "amount pending SI 78 of 2017";
  return `${row.currency ?? ""} ${Number(row.amount).toFixed(2)}`.trim();
}

export default function FeeSchedulesPage() {
  const { data, isLoading, isError } = useFeeSchedules();
  const create = useCreateFeeSchedule();
  const approve = useApproveFeeSchedule();
  const rows: FeeScheduleView[] = data?.data ?? [];

  const [form, setForm] = useState({ feeCode: "", appliesToCatalogueCode: "", appliesToClassCode: "", amount: "", currency: "USD" });

  return (
    <AppLayout>
      <PageShell
        title="Regulatory fee schedule"
        subtitle="SI 78 of 2017 — amounts are configured through governed approval, never invented"
      >
        <div className="mb-4">
          <FeatureMaturityBadge status="connected" detail="Backed by tuso.regulatory_fee_schedule via the admin proxy" />
        </div>

        <div className="mb-6 overflow-x-auto rounded border border-slate-200 dark:border-slate-700">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-muted-foreground dark:bg-slate-800">
              <tr>
                <th className="px-3 py-2">Fee code</th>
                <th className="px-3 py-2">Application type</th>
                <th className="px-3 py-2">Class</th>
                <th className="px-3 py-2">Amount</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Instrument</th>
                <th className="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              {isLoading && <tr><td colSpan={7} className="px-3 py-6 text-center text-muted-foreground">Loading…</td></tr>}
              {isError && <tr><td colSpan={7} className="px-3 py-6 text-center text-red-600">Could not load the fee schedule.</td></tr>}
              {!isLoading && rows.length === 0 && (
                <tr><td colSpan={7} className="px-3 py-6 text-center text-muted-foreground">No fee schedule rows.</td></tr>
              )}
              {rows.map((r) => (
                <tr key={r.feeId} className="border-t border-slate-100 dark:border-slate-800">
                  <td className="px-3 py-2 font-mono">{r.feeCode}</td>
                  <td className="px-3 py-2 font-mono">{r.appliesToCatalogueCode}</td>
                  <td className="px-3 py-2">{r.appliesToClassCode ?? "—"}</td>
                  <td className="px-3 py-2">{money(r)}</td>
                  <td className="px-3 py-2">
                    <span className={`rounded px-2 py-0.5 text-xs ${r.status === "ACTIVE" ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200" : "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300"}`}>
                      {r.status}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">{r.sourceInstrument}</td>
                  <td className="px-3 py-2">
                    {r.status !== "ACTIVE" && r.amount != null && (
                      <button
                        onClick={() => approve.mutate({ feeId: r.feeId })}
                        disabled={approve.isPending}
                        className="rounded bg-emerald-600 px-2 py-1 text-xs text-white disabled:opacity-50"
                      >
                        Approve ACTIVE
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="rounded border border-slate-200 p-4 dark:border-slate-700">
          <div className="mb-2 text-sm font-semibold">Configure a fee amount (from SI 78 of 2017)</div>
          <div className="flex flex-wrap items-end gap-2 text-xs">
            <label>Fee code
              <input value={form.feeCode} onChange={(e) => setForm({ ...form, feeCode: e.target.value })} placeholder="FEE_INITIAL_REGISTRATION_PRIVATE" className="mt-0.5 block w-64 rounded border px-2 py-1 text-sm" />
            </label>
            <label>Application type
              <input value={form.appliesToCatalogueCode} onChange={(e) => setForm({ ...form, appliesToCatalogueCode: e.target.value })} placeholder="INITIAL_REGISTRATION_PRIVATE" className="mt-0.5 block w-56 rounded border px-2 py-1 text-sm" />
            </label>
            <label>Class
              <input value={form.appliesToClassCode} onChange={(e) => setForm({ ...form, appliesToClassCode: e.target.value })} placeholder="A/B/C (blank=any)" className="mt-0.5 block w-28 rounded border px-2 py-1 text-sm" />
            </label>
            <label>Amount
              <input type="number" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} className="mt-0.5 block w-28 rounded border px-2 py-1 text-sm" />
            </label>
            <label>Currency
              <input value={form.currency} onChange={(e) => setForm({ ...form, currency: e.target.value })} className="mt-0.5 block w-20 rounded border px-2 py-1 text-sm" />
            </label>
            <button
              onClick={() =>
                create.mutate({
                  feeCode: form.feeCode,
                  appliesToCatalogueCode: form.appliesToCatalogueCode,
                  appliesToClassCode: form.appliesToClassCode || null,
                  amount: form.amount ? Number(form.amount) : null,
                  currency: form.currency,
                  sourceInstrument: "SI 78 of 2017",
                })
              }
              disabled={create.isPending || !form.feeCode || !form.appliesToCatalogueCode}
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
            >
              Author (PENDING)
            </button>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">
            Authoring records a PENDING row; it takes effect only after Approve ACTIVE. The amount must come from the
            statutory instrument — the platform never generates it.
          </p>
        </div>

        <div className="mt-4 text-xs text-muted-foreground">
          <Link href="/registry-admin" className="hover:text-primary">← Registry administration</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
