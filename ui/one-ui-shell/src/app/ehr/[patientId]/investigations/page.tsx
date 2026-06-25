"use client";

/**
 * Patient-File Investigations — diagnostic/imaging orders + results for the chart patient (criterion F).
 * Route: /ehr/[patientId]/investigations | Zone: ehr | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS, scoped to the chart patient.
 */

import Link from "next/link";
import { useParams } from "next/navigation";
import { Activity, Loader2 } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useDiagnosticsOrders } from "@/hooks/queries/useDiagnosticsOrders";

export default function InvestigationsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const ordersQ = useDiagnosticsOrders({ client: patientId });
  const orders = ordersQ.data?.data ?? [];

  return (
    <EHRLayout>
      <PageShell title="Investigations" subtitle="Diagnostic & imaging orders for this patient">
        {ordersQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading investigations…
          </div>
        ) : ordersQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load investigations from OROS.</div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-16 text-center">
            <Activity className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No diagnostic orders for this patient.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-2xl border border-border">
            <table className="w-full text-sm">
              <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Type</th>
                  <th className="px-4 py-2">Accession</th>
                  <th className="px-4 py-2">Lifecycle</th>
                  <th className="px-4 py-2">Placed</th>
                  <th className="px-4 py-2">Links</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {orders.map((o) => (
                  <tr key={o.orderId} data-testid="investigation-row">
                    <td className="px-4 py-3 font-mono text-xs">{o.orderId}</td>
                    <td className="px-4 py-3">{o.orderType}</td>
                    <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-primary-soft px-2 py-0.5 text-xs font-medium">
                        {o.imagingState ?? o.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-xs text-muted-foreground">
                      {o.placedAt ? new Date(o.placedAt).toLocaleString() : "—"}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3 text-xs">
                        <Link href={`/diagnostics/reporting?orderId=${encodeURIComponent(o.orderId)}`}
                          className="text-primary hover:underline">Report</Link>
                        {o.studyViewerUrl ? (
                          <a href={o.studyViewerUrl} target="_blank" rel="noopener noreferrer"
                            className="text-primary hover:underline">View study</a>
                        ) : o.studyUid ? (
                          <span className="text-muted-foreground" title={o.studyUid}>Imaged</span>
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
    </EHRLayout>
  );
}
