"use client";

/**
 * Insurance-plan terms configuration.
 * Route: /finance/insurance-plans
 *
 * COSTA saves a PENDING_TERMS placeholder the first time it sees a coverage plan
 * — it never invents the billing split. Finance enters the real terms here
 * (coverage %, co-pay, deductible, annual cap), which activates the plan so the
 * coverage engine can start splitting bills against it. Terms are finance-entered
 * and governed, never derived.
 */

import Link from "next/link";
import { Fragment, useMemo, useState } from "react";
import { ArrowLeft, ShieldCheck, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useInsurancePlans,
  useConfigureInsurancePlan,
  type InsurancePlan,
  type PlanTerms,
} from "@/hooks/queries/useInsurancePlans";

function asList(v: unknown): InsurancePlan[] {
  if (!v) return [];
  const r = v as Record<string, unknown>;
  if (Array.isArray(r.data)) return r.data as InsurancePlan[];
  if (Array.isArray(v)) return v as InsurancePlan[];
  return [];
}

function statusBadge(status: string) {
  const pending = status === "PENDING_TERMS";
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
        pending ? "bg-amber-100 text-amber-700" : "bg-success-soft text-primary-hover"
      }`}
    >
      {status.replace(/_/g, " ")}
    </span>
  );
}

type Draft = { coveragePct: string; copayPct: string; copayFixed: string; deductible: string; annualCap: string };

const EMPTY_DRAFT: Draft = { coveragePct: "", copayPct: "", copayFixed: "", deductible: "", annualCap: "" };

export default function InsurancePlansPage() {
  const q = useInsurancePlans();
  const configure = useConfigureInsurancePlan();
  const [editing, setEditing] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const [formError, setFormError] = useState<string | null>(null);

  const plans = useMemo(() => asList(q.data), [q.data]);
  const pendingCount = plans.filter((p) => p.status === "PENDING_TERMS").length;

  const startEdit = (p: InsurancePlan) => {
    setEditing(p.planCode);
    setFormError(null);
    // A PENDING_TERMS placeholder carries only default figures that mean nothing
    // yet, so finance enters the real terms on a blank form. An already-ACTIVE
    // plan is pre-filled so its terms can be adjusted.
    if (p.status === "PENDING_TERMS") {
      setDraft(EMPTY_DRAFT);
      return;
    }
    setDraft({
      coveragePct: p.coveragePct != null ? String(p.coveragePct) : "",
      copayPct: p.copayPct != null ? String(p.copayPct) : "",
      copayFixed: p.copayFixed != null ? String(p.copayFixed) : "",
      deductible: p.deductible != null ? String(p.deductible) : "",
      annualCap: p.annualCap != null ? String(p.annualCap) : "",
    });
  };

  const submit = (planCode: string) => {
    const coveragePct = Number(draft.coveragePct);
    const copayPct = Number(draft.copayPct);
    if (!draft.coveragePct || !draft.copayPct || Number.isNaN(coveragePct) || Number.isNaN(copayPct)) {
      setFormError("Coverage % and co-pay % are required.");
      return;
    }
    if (coveragePct < 0 || coveragePct > 100 || copayPct < 0 || copayPct > 100) {
      setFormError("Coverage % and co-pay % must be between 0 and 100.");
      return;
    }
    const terms: PlanTerms = {
      coveragePct,
      copayPct,
      copayFixed: draft.copayFixed ? Number(draft.copayFixed) : null,
      deductible: draft.deductible ? Number(draft.deductible) : undefined,
      annualCap: draft.annualCap ? Number(draft.annualCap) : null,
    };
    configure.mutate(
      { planCode, terms },
      { onSuccess: () => { setEditing(null); setDraft(EMPTY_DRAFT); } },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Insurance plan terms"
        subtitle="Configure coverage split terms so the engine can bill against each plan"
        icon={<ShieldCheck className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/finance"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to finance
          </Link>
        </div>

        <div className="mb-4 flex items-start gap-2 rounded-lg border border-border bg-amber-50/50 px-4 py-3 text-sm">
          <AlertCircle className="mt-0.5 h-4 w-4 text-amber-600" />
          <p className="text-muted-foreground">
            When a coverage plan is first seen, it lands as <strong>PENDING_TERMS</strong> — the platform never
            invents a billing split. Enter the governed terms below to activate a plan.
            {pendingCount > 0 && (
              <> <span className="font-medium text-amber-700">{pendingCount} plan{pendingCount === 1 ? "" : "s"} awaiting terms.</span></>
            )}
          </p>
        </div>

        <section className="rounded-xl border border-border bg-card shadow-sm">
          <div className="p-4">
            {q.isLoading && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading plans…
              </p>
            )}
            {q.isError && <p className="text-sm text-danger">Unable to load insurance plans.</p>}
            {!q.isLoading && !q.isError && plans.length === 0 && (
              <p className="text-sm text-muted-foreground">No insurance plans yet.</p>
            )}
            {plans.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-xs text-muted-foreground">
                      <th className="pb-2 pr-3 font-medium">Plan</th>
                      <th className="pb-2 pr-3 font-medium">Insurer</th>
                      <th className="pb-2 pr-3 font-medium">Coverage %</th>
                      <th className="pb-2 pr-3 font-medium">Co-pay %</th>
                      <th className="pb-2 pr-3 font-medium">Status</th>
                      <th className="pb-2 font-medium">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {plans.map((p) => (
                      <Fragment key={p.planCode}>
                        <tr className="border-t border-border align-top">
                          <td className="py-2 pr-3">
                            <div className="font-medium text-foreground">{p.planName || p.planCode}</div>
                            <div className="font-mono text-xs text-muted-foreground">{p.planCode}</div>
                          </td>
                          <td className="py-2 pr-3 text-muted-foreground">{p.insurerName}</td>
                          <td className="py-2 pr-3 font-mono">{p.status === "PENDING_TERMS" ? "—" : `${p.coveragePct}`}</td>
                          <td className="py-2 pr-3 font-mono">{p.status === "PENDING_TERMS" ? "—" : `${p.copayPct}`}</td>
                          <td className="py-2 pr-3">{statusBadge(p.status)}</td>
                          <td className="py-2">
                            <button
                              type="button"
                              onClick={() => (editing === p.planCode ? setEditing(null) : startEdit(p))}
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-primary hover:bg-primary-soft"
                            >
                              {editing === p.planCode ? "Close" : p.status === "PENDING_TERMS" ? "Configure & activate" : "Edit terms"}
                            </button>
                          </td>
                        </tr>
                        {editing === p.planCode && (
                          <tr className="border-t border-border bg-background/60">
                            <td colSpan={6} className="p-4">
                              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
                                {([
                                  ["coveragePct", "Coverage % *"],
                                  ["copayPct", "Co-pay % *"],
                                  ["copayFixed", "Co-pay fixed"],
                                  ["deductible", "Deductible"],
                                  ["annualCap", "Annual cap"],
                                ] as const).map(([field, label]) => (
                                  <label key={field} className="text-xs text-muted-foreground">
                                    {label}
                                    <input
                                      type="number"
                                      step="0.01"
                                      min="0"
                                      value={draft[field]}
                                      onChange={(e) => setDraft((d) => ({ ...d, [field]: e.target.value }))}
                                      className="mt-1 w-full rounded-lg border border-border px-2 py-1.5 text-sm text-foreground"
                                    />
                                  </label>
                                ))}
                              </div>
                              {formError && <p className="mt-2 text-xs text-danger">{formError}</p>}
                              {configure.isError && (
                                <p className="mt-2 text-xs text-danger">
                                  The plan could not be configured. Check the terms and try again.
                                </p>
                              )}
                              <div className="mt-3 flex items-center gap-2">
                                <button
                                  type="button"
                                  onClick={() => submit(p.planCode)}
                                  disabled={configure.isPending}
                                  className="inline-flex items-center gap-1 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                                >
                                  {configure.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                                  {p.status === "PENDING_TERMS" ? "Save terms & activate" : "Update terms"}
                                </button>
                                <span className="text-xs text-muted-foreground">
                                  Coverage % + co-pay % should reflect the real plan schedule.
                                </span>
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      </PageShell>
    </AppLayout>
  );
}
