"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { Loader2, Receipt, Search } from "lucide-react";
import { ConsumablesCostPanel } from "@/components/enterprise/ConsumablesCostPanel";
import { EnterpriseWorkspaceShell } from "@/components/enterprise/EnterpriseWorkspaceShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { useCostaIntelTariffLists } from "@/hooks/queries/useCostaIntel";
import { useFinanceBillingCharges } from "@/hooks/queries/useFinanceBillingWorkspace";

function extractRows(payload: unknown): Record<string, unknown>[] {
  if (payload == null) return [];
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    if (Array.isArray(obj.data)) return obj.data as Record<string, unknown>[];
    if (Array.isArray(obj.items)) return obj.items as Record<string, unknown>[];
    if (Array.isArray(obj.content)) return obj.content as Record<string, unknown>[];
  }
  return [];
}

export default function EnterpriseChargeSheetPage() {
  const tariffsQ = useCostaIntelTariffLists();
  const [tariffQuery, setTariffQuery] = useState("");
  const [billId, setBillId] = useState("");
  const [billLookupId, setBillLookupId] = useState("");

  const chargesQ = useFinanceBillingCharges(billLookupId, Boolean(billLookupId.trim()));

  const tariffs = tariffsQ.data ?? [];
  const filteredTariffs = useMemo(() => {
    const q = tariffQuery.trim().toLowerCase();
    if (!q) return tariffs.slice(0, 20);
    return tariffs
      .filter((t) => {
        const name = (t.name ?? "").toLowerCase();
        const code = (t.externalCode ?? "").toLowerCase();
        return name.includes(q) || code.includes(q);
      })
      .slice(0, 20);
  }, [tariffQuery, tariffs]);

  const chargeRows = extractRows(chargesQ.data);

  return (
    <EnterpriseWorkspaceShell
      title="Charge sheet"
      subtitle="Tariff lookup (COSTA) and billing charge rows (billing-workspace) — clinical capture stays in encounters"
    >
      <div className="space-y-6">
        <div className="flex flex-wrap items-center gap-3">
          <FeatureMaturityBadge
            status="partial"
            detail="Tariff lists from /internal/v1/finance/costa-intel/tariff-lists; charge rows from /internal/v1/finance/billing-workspace/lifecycle/charges?billId=…"
          />
        </div>

        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5 text-sm text-slate-700">
          <p>
            <strong className="text-slate-900">COSTA (costing-engine-service)</strong> computes tariffs,
            exemptions, and bill lifecycle. <strong className="text-slate-900">MusheX (mushex-service)</strong>{" "}
            captures payments and settlement. Use the lookups below for reference tariffs and posted charge
            lines — not synthetic patient totals.
          </p>
        </div>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-start gap-3">
            <Receipt className="mt-1 h-5 w-5 text-indigo-600" />
            <div className="flex-1">
              <h2 className="text-sm font-semibold text-slate-900">Tariff list lookup</h2>
              <p className="mt-1 text-xs text-slate-500">
                Live rows from{" "}
                <code className="text-[10px]">GET /internal/v1/finance/costa-intel/tariff-lists</code>
              </p>
              <div className="mt-3 flex items-center gap-2">
                <Search className="h-4 w-4 text-slate-400" />
                <input
                  type="search"
                  placeholder="Filter by list name or code"
                  value={tariffQuery}
                  onChange={(e) => setTariffQuery(e.target.value)}
                  className="flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm"
                  aria-label="Filter tariff lists"
                />
              </div>

              {tariffsQ.isLoading ? (
                <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading tariff lists…
                </p>
              ) : tariffsQ.isError ? (
                <p className="mt-3 text-sm text-red-700">Could not load COSTA tariff lists from the BFF.</p>
              ) : filteredTariffs.length === 0 ? (
                <p className="mt-3 text-sm text-slate-500">No tariff lists matched your filter.</p>
              ) : (
                <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                  <table className="w-full text-xs">
                    <thead className="bg-slate-50 text-slate-600">
                      <tr>
                        <th className="px-3 py-2 text-left font-medium">List</th>
                        <th className="px-3 py-2 text-left font-medium">Code</th>
                        <th className="px-3 py-2 text-left font-medium">Billing</th>
                        <th className="px-3 py-2 text-left font-medium">Reference only</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {filteredTariffs.map((row) => (
                        <tr key={row.id ?? row.externalCode ?? row.name}>
                          <td className="px-3 py-2 text-slate-900">{row.name ?? "—"}</td>
                          <td className="px-3 py-2 font-mono text-[10px] text-slate-600">
                            {row.externalCode ?? "—"}
                          </td>
                          <td className="px-3 py-2 text-slate-600">
                            {row.approvedForBilling ? "Approved" : "Not approved"}
                          </td>
                          <td className="px-3 py-2 text-slate-600">
                            {row.referenceOnly ? "Yes" : "No"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Billing charge lookup</h2>
          <p className="mt-1 text-xs text-slate-500">
            Enter a bill id to load charge lines from{" "}
            <code className="text-[10px]">
              GET /internal/v1/finance/billing-workspace/lifecycle/charges?billId=…
            </code>
          </p>
          <form
            className="mt-3 flex flex-wrap items-end gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              setBillLookupId(billId.trim());
            }}
          >
            <label className="flex flex-1 min-w-[200px] flex-col gap-1 text-xs text-slate-600">
              Bill ID
              <input
                type="text"
                value={billId}
                onChange={(e) => setBillId(e.target.value)}
                placeholder="bill-uuid or bill reference"
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
              />
            </label>
            <button
              type="submit"
              disabled={!billId.trim()}
              className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
            >
              Load charges
            </button>
          </form>

          {billLookupId ? (
            chargesQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading charges for {billLookupId}…
              </p>
            ) : chargesQ.isError ? (
              <p className="mt-3 text-sm text-red-700">
                Could not load charges for bill <span className="font-mono">{billLookupId}</span>.
              </p>
            ) : chargeRows.length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">
                No charge rows returned for bill <span className="font-mono">{billLookupId}</span>.
              </p>
            ) : (
              <div className="mt-3 overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-xs">
                  <thead className="bg-slate-50 text-slate-600">
                    <tr>
                      <th className="px-3 py-2 text-left font-medium">Charge</th>
                      <th className="px-3 py-2 text-right font-medium">Amount</th>
                      <th className="px-3 py-2 text-left font-medium">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {chargeRows.map((row, idx) => (
                      <tr key={String(row.id ?? row.charge_id ?? idx)}>
                        <td className="px-3 py-2 text-slate-900">
                          {String(row.description ?? row.service_name ?? row.charge_code ?? "Charge line")}
                        </td>
                        <td className="px-3 py-2 text-right text-slate-900">
                          {String(row.amount ?? row.total_amount ?? "—")}
                        </td>
                        <td className="px-3 py-2 text-slate-600">{String(row.status ?? "—")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          ) : null}
        </section>

        <ConsumablesCostPanel />

        <div className="flex flex-wrap gap-3 text-sm">
          <Link href="/queue" className="font-semibold text-impilo-600 underline">
            Clinical queue
          </Link>
          <Link href="/finance/billing" className="font-semibold text-impilo-600 underline">
            Billing workspace
          </Link>
          <Link href="/finance/costa" className="font-semibold text-impilo-600 underline">
            COSTA hub
          </Link>
          <Link href="/inventory/movements" className="font-semibold text-impilo-600 underline">
            Inventory ledger
          </Link>
        </div>
      </div>
    </EnterpriseWorkspaceShell>
  );
}
