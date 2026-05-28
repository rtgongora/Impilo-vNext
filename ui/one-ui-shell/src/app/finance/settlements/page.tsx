"use client";
import { QueryResultPanel } from "@/components/common/QueryResultPanel";
import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFinanceReleasePayouts, useFinanceRunSettlement, useFinanceSettlement } from "@/hooks/queries/useFinanceSettlements";

function pickSettlementId(body: unknown): string | null {
  if (!body || typeof body !== "object") return null;
  const o = body as Record<string, unknown>;
  const id = o.settlementId ?? o.id;
  return typeof id === "string" && id.trim() ? id.trim() : null;
}

export default function FinanceSettlementsPage() {
  const today = new Date();
  const defaultEnd = today.toISOString().slice(0, 10);
  const startDate = new Date(today);
  startDate.setDate(startDate.getDate() - 7);
  const defaultStart = startDate.toISOString().slice(0, 10);

  const [periodStart, setPeriodStart] = useState(defaultStart);
  const [periodEnd, setPeriodEnd] = useState(defaultEnd);
  const [settlementIdInput, setSettlementIdInput] = useState("");
  const [activeSettlementId, setActiveSettlementId] = useState<string | undefined>(undefined);
  const [lastRunResponse, setLastRunResponse] = useState<unknown>(null);

  const settlementQ = useFinanceSettlement(activeSettlementId);
  const runM = useFinanceRunSettlement();
  const releaseM = useFinanceReleasePayouts();

  const derivedIdFromRun = useMemo(() => pickSettlementId(lastRunResponse), [lastRunResponse]);

  return (
    <AppLayout>
      <PageShell
        title="Settlements"
        subtitle="Run settlement periods, inspect a settlement, and release payouts through the Experience BFF (MusheX upstream). Reconciliation and refunds stay on other finance routes."
      >
        <div className="mb-4">
          <Link href="/finance" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Finance dashboard
          </Link>
        </div>

        <div className="max-w-3xl space-y-6">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Run settlement</h2>
            <p className="mt-1 text-xs text-slate-500">
              POST <code className="text-[11px]">/internal/v1/finance/settlements/run</code> with{" "}
              <code className="text-[11px]">periodStart</code> and <code className="text-[11px]">periodEnd</code> (ISO
              dates).
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="flex flex-col text-xs text-slate-600">
                Period start
                <input
                  type="date"
                  className="mt-1 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                  value={periodStart}
                  onChange={(e) => setPeriodStart(e.target.value)}
                />
              </label>
              <label className="flex flex-col text-xs text-slate-600">
                Period end
                <input
                  type="date"
                  className="mt-1 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                  value={periodEnd}
                  onChange={(e) => setPeriodEnd(e.target.value)}
                />
              </label>
              <button
                type="button"
                disabled={runM.isPending || !periodStart || !periodEnd}
                onClick={() => {
                  setLastRunResponse(null);
                  runM.mutate(
                    { periodStart, periodEnd },
                    {
                      onSuccess: (res) => {
                        setLastRunResponse(res);
                        const sid = pickSettlementId(res);
                        if (sid) {
                          setSettlementIdInput(sid);
                          setActiveSettlementId(sid);
                        }
                      },
                    },
                  );
                }}
                className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
              >
                {runM.isPending ? (
                  <span className="inline-flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Running…
                  </span>
                ) : (
                  "Run settlement"
                )}
              </button>
            </div>
            {lastRunResponse ? (
              <QueryResultPanel title="Last run" data={lastRunResponse} />
            ) : null}
            {derivedIdFromRun ? (
              <p className="mt-2 text-xs text-emerald-800">Detected settlement id: {derivedIdFromRun}</p>
            ) : null}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Load settlement</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/finance/settlements/&#123;settlementId&#125;</code>
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <input
                type="text"
                value={settlementIdInput}
                onChange={(e) => setSettlementIdInput(e.target.value)}
                placeholder="Settlement id"
                className="min-w-[200px] flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                aria-label="Settlement id"
              />
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm hover:bg-slate-50"
                onClick={() => setActiveSettlementId(settlementIdInput.trim() || undefined)}
              >
                Load
              </button>
            </div>
            {settlementQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading…
              </p>
            ) : settlementQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load that settlement.</p>
            ) : settlementQ.data ? (
              <QueryResultPanel title="Settlement Q" isPending={settlementQ.isPending} isLoading={settlementQ.isPending} isError={settlementQ.isError} error={settlementQ.error} data={settlementQ.data} />
            ) : (
              <p className="mt-3 text-xs text-slate-500">Enter an id and choose Load.</p>
            )}

            <div className="mt-4 border-t border-slate-100 pt-4">
              <button
                type="button"
                disabled={releaseM.isPending || !activeSettlementId}
                onClick={() => {
                  if (activeSettlementId) releaseM.mutate(activeSettlementId);
                }}
                className="rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
              >
                {releaseM.isPending ? (
                  <span className="inline-flex items-center gap-2">
                    <Loader2 className="h-4 w-4 animate-spin" /> Releasing…
                  </span>
                ) : (
                  "Release payouts"
                )}
              </button>
              <p className="mt-2 text-xs text-slate-500">
                POST{" "}
                <code className="text-[11px]">/internal/v1/finance/settlements/&#123;settlementId&#125;/release-payouts</code>
                . Uses the settlement id currently loaded above.
              </p>
            </div>
          </div>

          <p className="text-xs text-slate-500">
            <Link href="/finance/commerce-integrations" className="text-indigo-700 hover:underline">
              Integration map
            </Link>{" "}
            — recon, refunds, and payer remittance are still outside this settlements slice.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
