"use client";

/**
 * Substitutions — absorbs msika-flow-portal sidecar
 * Manage product substitution requests and approvals.
 * Route: /marketplace/substitutions | Zone: marketplace | Guard: auth
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, ArrowLeftRight, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useApproveCommerceSubstitution,
  useCommerceSubstitutions,
  useRejectCommerceSubstitution,
} from "@/hooks/queries/useCommerceSubstitutions";

function coerceRows(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  if (payload && typeof payload === "object") {
    const record = payload as Record<string, unknown>;
    for (const key of ["items", "data", "content", "substitutions"]) {
      const value = record[key];
      if (Array.isArray(value)) return value as Array<Record<string, unknown>>;
    }
  }
  return [];
}

function pickOrderId(row: Record<string, unknown>) {
  const value = row.orderId ?? row.order_id ?? row.id;
  return typeof value === "string" ? value : typeof value === "number" ? String(value) : "";
}

const DEFAULT_DECISION_JSON = "{\n  \"reason\": \"Clinically acceptable substitution\"\n}\n";

export default function SubstitutionsPage() {
  const [page, setPage] = useState("0");
  const [size, setSize] = useState("20");
  const [armed, setArmed] = useState(true);
  const substitutionsQ = useCommerceSubstitutions(
    {
      page: Number.isFinite(Number(page)) ? Number(page) : 0,
      size: Number.isFinite(Number(size)) ? Number(size) : 20,
    },
    armed,
  );
  const approveM = useApproveCommerceSubstitution();
  const rejectM = useRejectCommerceSubstitution();
  const rows = useMemo(() => coerceRows(substitutionsQ.data), [substitutionsQ.data]);

  const [orderId, setOrderId] = useState("");
  const [decisionJson, setDecisionJson] = useState(DEFAULT_DECISION_JSON);
  const [decisionError, setDecisionError] = useState<string | null>(null);

  function parseDecisionBody() {
    try {
      setDecisionError(null);
      return JSON.parse(decisionJson) as unknown;
    } catch {
      setDecisionError("Invalid JSON body.");
      return null;
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Substitutions"
        subtitle="Review substitution requests and approve or reject them through the Experience BFF commerce family."
        icon={<ArrowLeftRight className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link href="/marketplace" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="space-y-6">
          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Substitution inbox</h2>
            <p className="mt-1 text-xs text-slate-500">
              GET <code className="text-[11px]">/internal/v1/commerce/substitutions</code> and POST decisions to{" "}
              <code className="text-[11px]">/internal/v1/commerce/rx/{"{orderId}"}/substitution/approve|reject</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-slate-600">
                page
                <input
                  value={page}
                  onChange={(event) => setPage(event.target.value)}
                  className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                />
              </label>
              <label className="text-xs text-slate-600">
                size
                <input
                  value={size}
                  onChange={(event) => setSize(event.target.value)}
                  className="mt-1 block w-24 rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-slate-200 px-4 py-2 text-sm hover:bg-slate-50"
                onClick={() => {
                  setArmed(true);
                  void substitutionsQ.refetch();
                }}
              >
                Fetch substitutions
              </button>
            </div>

            {substitutionsQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading substitution inbox...
              </p>
            ) : substitutionsQ.isError ? (
              <p className="mt-3 text-sm text-red-700">Could not load substitutions.</p>
            ) : rows.length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">No substitution rows were returned for the current query.</p>
            ) : (
              <div className="mt-4 overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full min-w-[520px] text-sm">
                  <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                    <tr>
                      <th className="px-3 py-2">Order</th>
                      <th className="px-3 py-2">Status</th>
                      <th className="px-3 py-2">Summary</th>
                      <th className="px-3 py-2 text-right">Select</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row, index) => {
                      const rowOrderId = pickOrderId(row);
                      const status = String(row.status ?? row.state ?? row.decision ?? "PENDING");
                      const summary = String(row.reason ?? row.notes ?? row.productName ?? row.title ?? "Substitution request");
                      return (
                        <tr key={rowOrderId || `sub-${index}`} className="bg-white">
                          <td className="px-3 py-2 font-mono text-xs text-slate-900">{rowOrderId || "—"}</td>
                          <td className="px-3 py-2 text-slate-700">{status}</td>
                          <td className="px-3 py-2 text-slate-600">{summary}</td>
                          <td className="px-3 py-2 text-right">
                            <button
                              type="button"
                              disabled={!rowOrderId}
                              onClick={() => setOrderId(rowOrderId)}
                              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium hover:bg-slate-50 disabled:opacity-50"
                            >
                              Use
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            {substitutionsQ.data ? (
              <details className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-3">
                <summary className="cursor-pointer text-xs font-medium text-slate-700">Raw substitution payload</summary>
                <pre className="mt-3 max-h-56 overflow-auto rounded-lg border border-slate-200 bg-white p-3 text-[11px] text-slate-800">
                  {JSON.stringify(substitutionsQ.data, null, 2)}
                </pre>
              </details>
            ) : null}
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">Approve or reject</h2>
            <p className="mt-1 text-xs text-slate-500">
              Use the selected order id or paste one directly if you received it from another workflow.
            </p>
            <label className="mt-3 block text-xs text-slate-600">
              Order id
              <input
                value={orderId}
                onChange={(event) => setOrderId(event.target.value)}
                className="mt-1 block w-full max-w-md rounded-lg border border-slate-200 px-3 py-2 text-sm font-mono"
                placeholder="order-123"
                aria-label="Substitution order id"
              />
            </label>
            <label className="mt-3 block text-xs text-slate-600">
              Decision body JSON
              <textarea
                value={decisionJson}
                onChange={(event) => setDecisionJson(event.target.value)}
                rows={6}
                className="mt-1 block w-full rounded-lg border border-slate-200 p-3 font-mono text-xs"
                aria-label="Substitution decision JSON"
              />
            </label>
            {decisionError ? <p className="mt-2 text-xs text-red-700">{decisionError}</p> : null}
            <div className="mt-3 flex flex-wrap gap-2">
              <button
                type="button"
                disabled={approveM.isPending || !orderId.trim()}
                className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50"
                onClick={() => {
                  const body = parseDecisionBody();
                  if (body == null) return;
                  approveM.mutate({ orderId: orderId.trim(), body });
                }}
              >
                Approve substitution
              </button>
              <button
                type="button"
                disabled={rejectM.isPending || !orderId.trim()}
                className="rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50"
                onClick={() => {
                  const body = parseDecisionBody();
                  if (body == null) return;
                  rejectM.mutate({ orderId: orderId.trim(), body });
                }}
              >
                Reject substitution
              </button>
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
