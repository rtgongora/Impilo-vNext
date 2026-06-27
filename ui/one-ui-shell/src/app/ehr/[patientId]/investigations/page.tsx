"use client";

/**
 * Patient-File Investigations — consolidated diagnostic/imaging/lab/procedure orders & results for
 * the chart patient (spec §6).
 * Route: /ehr/[patientId]/investigations | Zone: ehr | Guard: facility
 *
 * Real data via the experience-bff diagnostics proxy → OROS, scoped to the chart patient. Lab
 * orders expand to show structured observations (value/unit/reference-range/abnormal+critical
 * flags); every category surfaces its generalised fine-grained workflow state.
 */

import { Fragment, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Activity, ChevronDown, ChevronRight, Loader2 } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import {
  useDiagnosticsOrders,
  useOrderResults,
  useResultObservations,
  type DiagnosticOrder,
} from "@/hooks/queries/useDiagnosticsOrders";

function ObservationTable({ resultId }: { resultId: string }) {
  const obsQ = useResultObservations(resultId);
  const observations = obsQ.data?.data ?? [];
  if (obsQ.isLoading) {
    return <p className="px-4 py-2 text-xs text-muted-foreground">Loading observations…</p>;
  }
  if (observations.length === 0) {
    return <p className="px-4 py-2 text-xs text-muted-foreground">No structured observations.</p>;
  }
  return (
    <table className="w-full text-xs" data-testid="observation-table">
      <thead className="text-left text-muted-foreground">
        <tr>
          <th className="px-4 py-1">Analyte</th>
          <th className="px-4 py-1">Value</th>
          <th className="px-4 py-1">Unit</th>
          <th className="px-4 py-1">Reference</th>
          <th className="px-4 py-1">Flag</th>
        </tr>
      </thead>
      <tbody>
        {observations.map((o) => (
          <tr key={o.observationId} className={o.criticalFlag ? "text-danger font-medium" : ""}>
            <td className="px-4 py-1">{o.analyteName}</td>
            <td className="px-4 py-1 font-mono">{o.valueText ?? o.valueNumeric ?? "—"}</td>
            <td className="px-4 py-1">{o.unit ?? "—"}</td>
            <td className="px-4 py-1">{o.refRangeText ?? "—"}</td>
            <td className="px-4 py-1">
              {o.criticalFlag ? "CRITICAL" : o.abnormalFlag && o.abnormalFlag !== "N" ? o.abnormalFlag : "—"}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function OrderResultsPanel({ orderId }: { orderId: string }) {
  const resultsQ = useOrderResults(orderId);
  const results = resultsQ.data?.data ?? [];
  if (resultsQ.isLoading) {
    return <p className="px-4 py-2 text-xs text-muted-foreground">Loading results…</p>;
  }
  if (results.length === 0) {
    return <p className="px-4 py-2 text-xs text-muted-foreground">No results recorded yet.</p>;
  }
  return (
    <div className="space-y-2 bg-muted/30 py-2">
      {results.map((r) => (
        <div key={r.resultId}>
          <div className="px-4 py-1 text-xs font-medium">
            {r.reportStatus ?? r.kind} (v{r.version}){r.isCritical ? " · CRITICAL" : ""}
          </div>
          <ObservationTable resultId={r.resultId} />
        </div>
      ))}
    </div>
  );
}

export default function InvestigationsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const ordersQ = useDiagnosticsOrders({ client: patientId });
  const orders = ordersQ.data?.data ?? [];
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const lifecycle = (o: DiagnosticOrder) => o.workflowState ?? o.imagingState ?? o.status;

  return (
    <EHRLayout>
      <PageShell title="Investigations" subtitle="Orders, results & observations for this patient">
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
                  <Fragment key={o.orderId}>
                    <tr data-testid="investigation-row">
                      <td className="px-4 py-3 font-mono text-xs">
                        <button
                          type="button"
                          onClick={() => toggle(o.orderId)}
                          className="inline-flex items-center gap-1 hover:underline"
                          aria-label="Toggle results"
                        >
                          {expanded.has(o.orderId) ? (
                            <ChevronDown className="h-3 w-3" />
                          ) : (
                            <ChevronRight className="h-3 w-3" />
                          )}
                          {o.orderId}
                        </button>
                      </td>
                      <td className="px-4 py-3">{o.orderType}</td>
                      <td className="px-4 py-3 font-mono text-xs">{o.accessionNumber ?? "—"}</td>
                      <td className="px-4 py-3">
                        <span className="rounded-full bg-primary-soft px-2 py-0.5 text-xs font-medium">
                          {lifecycle(o)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {o.placedAt ? new Date(o.placedAt).toLocaleString() : "—"}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3 text-xs">
                          <Link
                            href={`/diagnostics/reporting?orderId=${encodeURIComponent(o.orderId)}`}
                            className="text-primary hover:underline"
                          >
                            Report
                          </Link>
                          {o.studyViewerUrl ? (
                            <a
                              href={o.studyViewerUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-primary hover:underline"
                            >
                              View study
                            </a>
                          ) : o.studyUid ? (
                            <span className="text-muted-foreground" title={o.studyUid}>
                              Imaged
                            </span>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                    {expanded.has(o.orderId) ? (
                      <tr>
                        <td colSpan={6} className="p-0">
                          <OrderResultsPanel orderId={o.orderId} />
                        </td>
                      </tr>
                    ) : null}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
