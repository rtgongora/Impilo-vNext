"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { MARKETPLACE_PRODUCT_COLUMNS } from "@/lib/json-api/generic-table-columns";
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
          <Link href="/marketplace" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to marketplace
          </Link>
        </div>

        <div className="space-y-6">
          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Substitution inbox</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/commerce/substitutions</code> and POST decisions to{" "}
              <code className="text-[11px]">/internal/v1/commerce/rx/{"{orderId}"}/substitution/approve|reject</code>.
            </p>
            <div className="mt-3 flex flex-wrap items-end gap-3">
              <label className="text-xs text-muted-foreground">
                page
                <input
                  value={page}
                  onChange={(event) => setPage(event.target.value)}
                  className="mt-1 block w-24 rounded-lg border border-border px-2 py-1.5 text-sm"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                size
                <input
                  value={size}
                  onChange={(event) => setSize(event.target.value)}
                  className="mt-1 block w-24 rounded-lg border border-border px-2 py-1.5 text-sm"
                />
              </label>
              <button
                type="button"
                className="rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
                onClick={() => {
                  setArmed(true);
                  void substitutionsQ.refetch();
                }}
              >
                Fetch substitutions
              </button>
            </div>

            {substitutionsQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading substitution inbox...
              </p>
            ) : substitutionsQ.isError ? (
              <p className="mt-3 text-sm text-danger">Could not load substitutions.</p>
            ) : rows.length === 0 ? (
              <p className="mt-3 text-sm text-muted-foreground">No substitution rows were returned for the current query.</p>
            ) : (
              <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                <table className="w-full min-w-[520px] text-sm">
                  <thead className="bg-background text-left text-xs uppercase tracking-wide text-muted-foreground">
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
                        <tr key={rowOrderId || `sub-${index}`} className="bg-card">
                          <td className="px-3 py-2 font-mono text-xs text-foreground">{rowOrderId || "—"}</td>
                          <td className="px-3 py-2 text-foreground">{status}</td>
                          <td className="px-3 py-2 text-muted-foreground">{summary}</td>
                          <td className="px-3 py-2 text-right">
                            <button
                              type="button"
                              disabled={!rowOrderId}
                              onClick={() => setOrderId(rowOrderId)}
                              className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium hover:bg-background disabled:opacity-50"
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
              <details className="mt-4 rounded-lg border border-border bg-background p-3">
                <summary className="cursor-pointer text-xs font-medium text-foreground">Raw substitution payload</summary>
                <JsonApiDataTable data={substitutionsQ.data} columns={MARKETPLACE_PRODUCT_COLUMNS} isLoading={substitutionsQ.isPending} error={substitutionsQ.error as Error | null} emptyTitle="No substitutions" />
              </details>
            ) : null}
          </div>

          <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Approve or reject</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Use the selected order id or paste one directly if you received it from another workflow.
            </p>
            <label className="mt-3 block text-xs text-muted-foreground">
              Order id
              <input
                value={orderId}
                onChange={(event) => setOrderId(event.target.value)}
                className="mt-1 block w-full max-w-md rounded-lg border border-border px-3 py-2 text-sm font-mono"
                placeholder="order-123"
                aria-label="Substitution order id"
              />
            </label>
            <label className="mt-3 block text-xs text-muted-foreground">
              Decision body JSON
              <textarea
                value={decisionJson}
                onChange={(event) => setDecisionJson(event.target.value)}
                rows={6}
                className="mt-1 block w-full rounded-lg border border-border p-3 font-mono text-xs"
                aria-label="Substitution decision JSON"
              />
            </label>
            {decisionError ? <p className="mt-2 text-xs text-danger">{decisionError}</p> : null}
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
