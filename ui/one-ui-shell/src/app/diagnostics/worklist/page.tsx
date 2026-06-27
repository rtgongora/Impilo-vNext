"use client";

/**
 * Imaging Worklist — fulfilment-team view of active imaging orders with workflow actions (W4).
 * Route: /diagnostics/worklist | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS; actions drive the guarded
 * imaging workflow (accept/start/complete). Illegal transitions are rejected server-side.
 */

import { Loader2, Stethoscope } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useImagingWorklist,
  useImagingTransition,
  type DiagnosticOrder,
} from "@/hooks/queries/useDiagnosticsOrders";

/** The next actionable transitions offered for a given imaging state. */
function actionsFor(state: string | null): { label: string; target: string }[] {
  switch (state) {
    case "RECEIVED":
      return [{ label: "Accept", target: "ACCEPTED" }];
    case "ACCEPTED":
      return [{ label: "Start", target: "IN_PROGRESS" }];
    case "SCHEDULED":
    case "ARRIVED":
      return [{ label: "Start", target: "IN_PROGRESS" }];
    case "IN_PROGRESS":
      return [{ label: "Complete", target: "PERFORMED" }];
    default:
      return [];
  }
}

export default function ImagingWorklistPage() {
  const worklistQ = useImagingWorklist();
  const transitionMut = useImagingTransition();
  const orders: DiagnosticOrder[] = worklistQ.data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Imaging Worklist" subtitle="Active imaging orders awaiting fulfilment">
        {worklistQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading worklist…
          </div>
        ) : worklistQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load the imaging worklist from OROS.</div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-12 text-center">
            <Stethoscope className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No active imaging orders.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Patient</th>
                  <th className="px-4 py-2">Accession</th>
                  <th className="px-4 py-2">State</th>
                  <th className="px-4 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => {
                  const busy = transitionMut.isPending && transitionMut.variables?.orderId === o.orderId;
                  return (
                    <tr key={o.orderId} data-testid="worklist-row">
                      <td className="px-4 py-3 font-mono text-xs">{o.orderId}</td>
                      <td className="px-4 py-3">{o.patientCpid}</td>
                      <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                      <td className="px-4 py-3">{o.imagingState ?? "—"}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          {actionsFor(o.imagingState).map((a) => (
                            <button key={a.target} type="button" disabled={busy}
                              onClick={() => transitionMut.mutate({ orderId: o.orderId, target: a.target })}
                              className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50">
                              {busy ? "…" : a.label}
                            </button>
                          ))}
                          {actionsFor(o.imagingState).length === 0 && (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        {transitionMut.isError && (
          <p className="mt-3 text-sm text-danger" role="alert">Transition rejected. Refresh and retry.</p>
        )}
      </PageShell>
    </AppLayout>
  );
}
