"use client";

/**
 * Critical diagnostic results — unacknowledged safety panel (WU6). Surfaces the ED's
 * unacknowledged critical results and closes the loop with an acknowledgement, reusing
 * the diagnostics safety hooks (GET /internal/v1/diagnostics/critical-unacknowledged,
 * POST /internal/v1/diagnostics/results/{id}/critical/ack). Linked into the ED trauma
 * flow so a trauma team never misses a critical lab/imaging result. No mocks.
 */

import { AlertTriangle, CheckCircle2, Loader2 } from "lucide-react";
import { useAcknowledgeCritical, useCriticalUnacknowledged } from "@/hooks/queries/useDiagnosticsOrders";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

export function CriticalResultsPanel() {
  const critical = useCriticalUnacknowledged();
  const ack = useAcknowledgeCritical();
  const results = critical.data?.data ?? [];

  if (critical.isLoading) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Checking critical results…
      </div>
    );
  }
  if (critical.isError) return null;
  if (results.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-xl border border-green-200 bg-success-soft px-4 py-2.5 text-sm text-primary-hover" data-testid="critical-results-panel">
        <CheckCircle2 className="h-4 w-4" /> No unacknowledged critical results.
      </div>
    );
  }

  return (
    <section className="rounded-xl border border-red-300 bg-red-50 p-4 shadow-sm" data-testid="critical-results-panel">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-red-900">
        <AlertTriangle className="h-4 w-4" /> Critical results — action required
        <span className="text-xs font-normal text-red-800/70">{results.length} unacknowledged</span>
      </h2>
      <ul className="mt-3 space-y-2">
        {results.map((r) => (
          <li key={r.resultId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-red-200 bg-white px-3 py-2 text-sm">
            <div className="min-w-0">
              <div className="font-medium text-red-900">{str(r.criticalReason) || "Critical result"}</div>
              <div className="font-mono text-[11px] text-muted-foreground">order {str(r.orderId).slice(0, 10)}{r.impression ? ` · ${str(r.impression)}` : ""}</div>
            </div>
            <button
              type="button"
              disabled={ack.isPending}
              onClick={() => ack.mutate({ resultId: r.resultId })}
              className="rounded-lg bg-red-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-red-700 disabled:opacity-50"
            >
              {ack.isPending ? "Acknowledging…" : "Acknowledge"}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
