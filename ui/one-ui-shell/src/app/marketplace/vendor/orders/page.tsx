"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { MARKETPLACE_ORDER_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, ArrowLeft, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useVendorAcceptOrder,
  useVendorMarkDelivered,
  useVendorMarkReady,
  useVendorOrders,
  useVendorProposeRxSubstitution,
} from "@/hooks/queries/useCommerceVendor";
import { readCommerceVendorId, writeCommerceVendorId } from "@/lib/commerce-vendor-session";

function coerceOrderRows(data: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(data)) return data as Array<Record<string, unknown>>;
  if (data && typeof data === "object") {
    const o = data as Record<string, unknown>;
    for (const k of ["orders", "items", "data", "content"]) {
      const v = o[k];
      if (Array.isArray(v)) return v as Array<Record<string, unknown>>;
    }
  }
  return [];
}

function pickOrderId(row: Record<string, unknown>): string {
  const id = row.orderId ?? row.order_id ?? row.id;
  if (typeof id === "string" && id) return id;
  if (typeof id === "number") return String(id);
  return "";
}

const DEFAULT_SUB_JSON = "{\n  \"reason\": \"Therapeutic substitution\"\n}\n";

export default function MarketplaceVendorOrdersPage() {
  const searchParams = useSearchParams();
  const paramVendor = searchParams.get("vendorId");
  const [sessionVendor, setSessionVendor] = useState<string | null>(null);

  useEffect(() => {
    setSessionVendor(readCommerceVendorId());
  }, []);

  const vendorId = useMemo(() => {
    const fromParam = paramVendor?.trim();
    if (fromParam) return fromParam;
    return sessionVendor?.trim() || "";
  }, [paramVendor, sessionVendor]);

  useEffect(() => {
    const fromParam = paramVendor?.trim();
    if (fromParam) writeCommerceVendorId(fromParam);
  }, [paramVendor]);

  const ordersQ = useVendorOrders(vendorId || undefined);
  const acceptM = useVendorAcceptOrder();
  const readyM = useVendorMarkReady();
  const deliveredM = useVendorMarkDelivered();
  const subM = useVendorProposeRxSubstitution();

  const rows = useMemo(() => coerceOrderRows(ordersQ.data), [ordersQ.data]);

  const [subOrderId, setSubOrderId] = useState<string | null>(null);
  const [subJson, setSubJson] = useState(DEFAULT_SUB_JSON);
  const [subError, setSubError] = useState<string | null>(null);

  const busy = acceptM.isPending || readyM.isPending || deliveredM.isPending || subM.isPending;

  return (
    <AppLayout>
      <PageShell
        title="Vendor orders"
        subtitle="Trusted Experience-operator queue: list, accept, ready, delivered, and propose Rx substitution (BFF to MSIKA Flow)."
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link
            href="/marketplace/vendor"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Vendor workspace
          </Link>
          <button
            type="button"
            disabled={!vendorId || ordersQ.isFetching}
            onClick={() => void ordersQ.refetch()}
            className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh list
          </button>
        </div>

        {!vendorId ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50 p-5">
            <div className="flex items-start gap-3">
              <AlertTriangle className="h-5 w-5 text-amber-700 mt-0.5 shrink-0" />
              <div className="text-sm text-amber-950">
                <p className="font-medium">Set a vendor ID first</p>
                <p className="mt-1 text-amber-900/90">
                  Use <code className="text-xs">?vendorId=…</code> on this URL or save a vendor ID on the vendor workspace
                  page. This keeps the scope explicit for Experience operators.
                </p>
                <Link href="/marketplace/vendor" className="mt-2 inline-block font-medium text-amber-950 hover:underline">
                  Open vendor workspace
                </Link>
              </div>
            </div>
          </div>
        ) : ordersQ.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading vendor orders…
          </div>
        ) : ordersQ.isError ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900">
            Could not load orders for this vendor. Check the vendor ID and BFF connectivity.
          </div>
        ) : (
          <div className="space-y-4">
            <p className="text-xs text-slate-500 break-all">
              Vendor: <span className="font-mono">{vendorId}</span> —{" "}
              <code className="text-[11px]">GET /internal/v1/commerce/vendor/&#123;vendorId&#125;/orders</code>
            </p>

            {rows.length === 0 ? (
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700">
                No order rows parsed from the response (upstream shape may differ). Raw payload below.
              </div>
            ) : (
              <div className="overflow-x-auto rounded-xl border border-slate-200">
                <table className="w-full min-w-[640px] text-sm">
                  <thead className="bg-slate-50 text-left text-xs font-medium uppercase tracking-wide text-slate-600">
                    <tr>
                      <th className="px-3 py-2">Order</th>
                      <th className="px-3 py-2">Status</th>
                      <th className="px-3 py-2 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {rows.map((row, idx) => {
                      const oid = pickOrderId(row);
                      const status =
                        typeof row.status === "string"
                          ? row.status
                          : typeof row.state === "string"
                            ? row.state
                            : "—";
                      const key = oid || `row-${idx}`;
                      return (
                        <tr key={key} className="bg-white">
                          <td className="px-3 py-2 font-mono text-xs">
                            {oid ? (
                              <Link href={`/marketplace/orders/${encodeURIComponent(oid)}`} className="text-impilo-600 hover:underline">
                                {oid}
                              </Link>
                            ) : (
                              <span className="text-amber-700">(no id)</span>
                            )}
                          </td>
                          <td className="px-3 py-2 text-slate-700">{status}</td>
                          <td className="px-3 py-2 text-right">
                            <div className="flex flex-wrap justify-end gap-1">
                              <button
                                type="button"
                                disabled={busy || !oid}
                                className="rounded border border-slate-200 px-2 py-1 text-[11px] font-medium hover:bg-slate-50 disabled:opacity-40"
                                onClick={() => acceptM.mutate({ vendorId, orderId: oid })}
                              >
                                Accept
                              </button>
                              <button
                                type="button"
                                disabled={busy || !oid}
                                className="rounded border border-slate-200 px-2 py-1 text-[11px] font-medium hover:bg-slate-50 disabled:opacity-40"
                                onClick={() => readyM.mutate({ orderId: oid })}
                              >
                                Mark ready
                              </button>
                              <button
                                type="button"
                                disabled={busy || !oid}
                                className="rounded border border-slate-200 px-2 py-1 text-[11px] font-medium hover:bg-slate-50 disabled:opacity-40"
                                onClick={() => deliveredM.mutate({ orderId: oid })}
                              >
                                Mark delivered
                              </button>
                              <button
                                type="button"
                                disabled={busy || !oid}
                                className="rounded border border-indigo-200 px-2 py-1 text-[11px] font-medium text-indigo-800 hover:bg-indigo-50 disabled:opacity-40"
                                onClick={() => {
                                  setSubError(null);
                                  setSubOrderId(oid);
                                  setSubJson(DEFAULT_SUB_JSON);
                                }}
                              >
                                Propose substitution
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}

            <JsonApiDataTable data={ordersQ.data} columns={MARKETPLACE_ORDER_COLUMNS} isLoading={ordersQ.isPending} error={ordersQ.error as Error | null} emptyTitle="No vendor orders" />
          </div>
        )}

        {subOrderId ? (
          <div className="fixed inset-0 z-40 flex items-end justify-center bg-black/30 p-4 sm:items-center">
            <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl border border-slate-200 bg-white p-5 shadow-lg">
              <h3 className="text-sm font-semibold text-slate-900">Propose Rx substitution</h3>
              <p className="mt-1 text-xs text-slate-500 break-all">Order: {subOrderId}</p>
              <p className="mt-1 text-xs text-slate-500">
                POST JSON body to{" "}
                <code className="text-[10px]">/internal/v1/commerce/vendor/rx/&#123;orderId&#125;/substitution/propose</code>.
                Shape is defined by MSIKA Flow; edit the JSON to match your environment.
              </p>
              <textarea
                className="mt-3 w-full min-h-[140px] rounded-lg border border-slate-200 p-2 font-mono text-xs"
                value={subJson}
                onChange={(e) => setSubJson(e.target.value)}
                aria-label="Substitution JSON body"
              />
              {subError ? <p className="mt-2 text-xs text-red-700">{subError}</p> : null}
              <div className="mt-3 flex justify-end gap-2">
                <button
                  type="button"
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm"
                  onClick={() => setSubOrderId(null)}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={subM.isPending}
                  className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-indigo-700 disabled:opacity-50"
                  onClick={() => {
                    let parsed: unknown;
                    try {
                      parsed = JSON.parse(subJson) as unknown;
                    } catch {
                      setSubError("Invalid JSON.");
                      return;
                    }
                    setSubError(null);
                    subM.mutate(
                      { orderId: subOrderId, body: parsed },
                      { onSuccess: () => setSubOrderId(null) },
                    );
                  }}
                >
                  Send proposal
                </button>
              </div>
            </div>
          </div>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
