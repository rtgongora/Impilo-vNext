"use client";

/**
 * Diagnostics Orders Tracking — OROS diagnostic/imaging order lifecycle tracking.
 * Route: /diagnostics/orders | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS. No mock data / dead buttons.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { Activity, Loader2, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useDiagnosticsOrders, type DiagnosticOrder } from "@/hooks/queries/useDiagnosticsOrders";

const TYPE_OPTIONS = ["", "IMAGING", "LAB", "PHARMACY", "PROCEDURE"] as const;

function lifecycleLabel(order: DiagnosticOrder): string {
  // Imaging orders track on the fine-grained imaging state; others on coarse status.
  return order.imagingState ?? order.status;
}

export default function DiagnosticsOrdersPage() {
  const [search, setSearch] = useState("");
  const [type, setType] = useState<string>("IMAGING");

  const ordersQ = useDiagnosticsOrders({ type: type || undefined });

  const orders = useMemo(() => {
    const raw = ordersQ.data?.data ?? [];
    if (!search.trim()) return raw;
    const needle = search.trim().toLowerCase();
    return raw.filter((o) =>
      [o.orderId, o.patientCpid, o.accessionNumber, o.referringProviderName, o.clinicalNotes]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(needle),
    );
  }, [ordersQ.data?.data, search]);

  return (
    <AppLayout>
      <PageShell
        title="Diagnostics Orders"
        subtitle="Track diagnostic and imaging orders through their lifecycle"
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[220px]">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by order, patient, accession, provider…"
              className="w-full rounded-lg border border-gray-200 py-2 pl-9 pr-3 text-sm"
              aria-label="Search orders"
            />
          </div>
          <select
            value={type}
            onChange={(e) => setType(e.target.value)}
            className="rounded-lg border border-gray-200 px-3 py-2 text-sm"
            aria-label="Filter by order type"
          >
            {TYPE_OPTIONS.map((t) => (
              <option key={t || "ALL"} value={t}>
                {t || "All types"}
              </option>
            ))}
          </select>
        </div>

        {ordersQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
            Loading diagnostic orders…
          </div>
        ) : ordersQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">
            Unable to load diagnostic orders from OROS.
          </div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-12 text-center">
            <Activity className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No diagnostic orders match the current filter.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Patient</th>
                  <th className="px-4 py-2">Accession</th>
                  <th className="px-4 py-2">Lifecycle</th>
                  <th className="px-4 py-2">Requester</th>
                  <th className="px-4 py-2">Placed</th>
                  <th className="px-4 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => (
                  <tr key={o.orderId} data-testid="diagnostic-order-row">
                    <td className="px-4 py-3 font-mono text-xs">{o.orderId}</td>
                    <td className="px-4 py-3">{o.patientCpid}</td>
                    <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-primary-soft px-2 py-0.5 text-xs font-medium">
                        {lifecycleLabel(o)}
                      </span>
                    </td>
                    <td className="px-4 py-3">{o.referringProviderName ?? o.placedBy ?? "—"}</td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {o.placedAt ? new Date(o.placedAt).toLocaleString() : "—"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3 text-xs">
                        <Link href={`/diagnostics/orders/route?orderId=${encodeURIComponent(o.orderId)}`}
                          className="text-primary hover:underline">Route</Link>
                        {o.studyViewerUrl ? (
                          <a href={o.studyViewerUrl} target="_blank" rel="noopener noreferrer"
                            className="text-primary hover:underline">View study</a>
                        ) : o.studyUid ? (
                          <span className="text-muted-foreground" title={o.studyUid}>Linked</span>
                        ) : null}
                      </div>
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
