"use client";

/**
 * Lab Worklist — fulfilment-team view of active laboratory orders with specimen + workflow actions (O11).
 * Route: /diagnostics/lab-worklist | Zone: lab | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS. Order actions drive the guarded LAB
 * workflow; specimen actions drive the specimen lifecycle (collect/dispatch/receive/reject).
 * Illegal transitions are rejected server-side.
 */

import { Fragment, useState } from "react";
import { FlaskConical, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useFulfilmentWorklist,
  useWorkflowTransition,
  useCollectSpecimen,
  useOrderSpecimens,
  useSpecimenAction,
  type DiagnosticOrder,
  type Specimen,
} from "@/hooks/queries/useDiagnosticsOrders";

/** Next order-workflow action offered for a given LAB workflow state. */
function orderActionsFor(state: string | null): { label: string; target: string }[] {
  switch (state) {
    case "RECEIVED":
      return [{ label: "Accept", target: "ACCEPTED" }];
    case "RECEIVED_IN_LAB":
      return [{ label: "Start analysis", target: "IN_PROGRESS" }];
    default:
      return [];
  }
}

/** Specimen actions offered for a given specimen status. */
function specimenActionsFor(status: string): { label: string; action: string }[] {
  switch (status) {
    case "COLLECTED":
    case "LABELLED":
      return [{ label: "Dispatch", action: "dispatch" }];
    case "DISPATCHED":
    case "IN_TRANSIT":
      return [{ label: "Receive", action: "receive" }, { label: "Reject", action: "reject" }];
    default:
      return [];
  }
}

function SpecimenPanel({ orderId }: { orderId: string }) {
  const specimensQ = useOrderSpecimens(orderId);
  const collectMut = useCollectSpecimen();
  const actionMut = useSpecimenAction();
  const specimens: Specimen[] = specimensQ.data?.data ?? [];

  return (
    <div className="bg-gray-50 px-4 py-3" data-testid="specimen-panel">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-medium uppercase text-muted-foreground">Specimens</span>
        <button
          type="button"
          disabled={collectMut.isPending}
          onClick={() => collectMut.mutate({ orderId, specimenType: "blood" })}
          className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
        >
          {collectMut.isPending ? "…" : "Collect specimen"}
        </button>
      </div>
      {specimens.length === 0 ? (
        <p className="text-xs text-muted-foreground">No specimens collected yet.</p>
      ) : (
        <table className="w-full text-xs">
          <tbody>
            {specimens.map((s) => (
              <tr key={s.specimenId} data-testid="specimen-row">
                <td className="py-1 font-mono">{s.sampleId ?? s.specimenId}</td>
                <td className="py-1">{s.specimenType ?? "—"}</td>
                <td className="py-1">{s.status}</td>
                <td className="py-1">
                  <div className="flex gap-2">
                    {specimenActionsFor(s.status).map((a) => (
                      <button
                        key={a.action}
                        type="button"
                        disabled={actionMut.isPending}
                        onClick={() =>
                          actionMut.mutate({
                            specimenId: s.specimenId,
                            action: a.action,
                            labNumber: a.action === "receive" ? `LAB-${s.specimenId.slice(0, 6)}` : undefined,
                            reason: a.action === "reject" ? "Rejected at receipt" : undefined,
                          })
                        }
                        className="rounded bg-muted px-2 py-0.5 hover:bg-muted/70 disabled:opacity-50"
                      >
                        {a.label}
                      </button>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default function LabWorklistPage() {
  const worklistQ = useFulfilmentWorklist("LAB");
  const transitionMut = useWorkflowTransition();
  const orders: DiagnosticOrder[] = worklistQ.data?.data ?? [];
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  return (
    <AppLayout>
      <PageShell title="Lab Worklist" subtitle="Active laboratory orders awaiting fulfilment">
        {worklistQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading worklist…
          </div>
        ) : worklistQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load the lab worklist from OROS.</div>
        ) : orders.length === 0 ? (
          <div className="flex flex-col items-center gap-2 p-12 text-center">
            <FlaskConical className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No active laboratory orders.</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border border-gray-100">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-2">Order</th>
                  <th className="px-4 py-2">Patient</th>
                  <th className="px-4 py-2">Lab no.</th>
                  <th className="px-4 py-2">State</th>
                  <th className="px-4 py-2">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {orders.map((o) => {
                  const busy = transitionMut.isPending && transitionMut.variables?.orderId === o.orderId;
                  return (
                    <Fragment key={o.orderId}>
                      <tr data-testid="lab-worklist-row">
                        <td className="px-4 py-3 font-mono text-xs">
                          <button type="button" onClick={() => toggle(o.orderId)} className="hover:underline">
                            {o.orderId}
                          </button>
                        </td>
                        <td className="px-4 py-3">{o.patientCpid}</td>
                        <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                        <td className="px-4 py-3">{o.workflowState ?? o.status}</td>
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            {orderActionsFor(o.workflowState).map((a) => (
                              <button
                                key={a.target}
                                type="button"
                                disabled={busy}
                                onClick={() => transitionMut.mutate({ orderId: o.orderId, target: a.target })}
                                className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-primary-foreground disabled:opacity-50"
                              >
                                {busy ? "…" : a.label}
                              </button>
                            ))}
                            <button
                              type="button"
                              onClick={() => toggle(o.orderId)}
                              className="rounded-md bg-muted px-3 py-1 text-xs font-medium hover:bg-muted/70"
                            >
                              Specimens
                            </button>
                          </div>
                        </td>
                      </tr>
                      {expanded.has(o.orderId) ? (
                        <tr>
                          <td colSpan={5} className="p-0">
                            <SpecimenPanel orderId={o.orderId} />
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
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
