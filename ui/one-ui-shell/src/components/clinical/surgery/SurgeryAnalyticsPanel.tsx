"use client";

/**
 * Surgical-pack analytics indicator catalogue (§23). Indicator DEFINITIONS and their honest
 * computation status, not computed numbers — same empty-versus-unavailable contract as the
 * procedures pipeline panel.
 */

import { AlertTriangle, Loader2 } from "lucide-react";
import { useSurgeryAnalyticsIndicators } from "@/hooks/queries/useSurgeryEpisodes";

const COMPUTATION_STATUS_STYLE: Record<string, string> = {
  COMPUTED: "bg-emerald-50 text-emerald-800",
  PARTIAL: "bg-amber-50 text-amber-800",
  NOT_YET_INSTRUMENTED: "bg-gray-100 text-gray-600",
};

export function SurgeryAnalyticsPanel() {
  const indicatorsQ = useSurgeryAnalyticsIndicators();

  return (
    <div className="mt-6 rounded-lg border border-gray-100 p-4" data-testid="surgery-analytics-panel">
      <h3 className="text-sm font-semibold">Surgical analytics indicators (§23)</h3>
      {indicatorsQ.isLoading ? (
        <div className="flex items-center gap-2 p-6 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading indicator catalogue…
        </div>
      ) : indicatorsQ.isError ? (
        <div
          className="flex items-center gap-2 p-4 text-sm text-danger"
          role="alert"
          data-testid="surgery-analytics-unavailable"
        >
          <AlertTriangle className="h-5 w-5" />
          Could not reach the surgical indicator catalogue. This is not the same as there being
          no indicators — try again, or report if it persists.
        </div>
      ) : indicatorsQ.data && !Array.isArray(indicatorsQ.data.indicators) ? (
        <div
          className="flex items-center gap-2 p-4 text-sm text-danger"
          role="alert"
          data-testid="surgery-analytics-malformed"
        >
          <AlertTriangle className="h-5 w-5" />
          The indicator catalogue answered in an unexpected shape — treating it as unavailable,
          not as empty.
        </div>
      ) : indicatorsQ.data ? (
        <div className="mt-2">
          <div className="flex flex-wrap gap-4 text-sm" data-testid="surgery-analytics-summary">
            <span>
              Total: <strong>{indicatorsQ.data.total}</strong>
            </span>
            <span className="text-emerald-800">
              Computed: <strong>{indicatorsQ.data.computed}</strong>
            </span>
            <span className="text-amber-800">
              Partial: <strong>{indicatorsQ.data.partial}</strong>
            </span>
            <span className="text-gray-600">
              Not yet instrumented: <strong>{indicatorsQ.data.notYetInstrumented}</strong>
            </span>
          </div>
          {indicatorsQ.data.indicators.length === 0 ? (
            <p className="mt-3 text-sm text-muted-foreground" data-testid="surgery-analytics-empty">
              The indicator catalogue is genuinely empty.
            </p>
          ) : (
            <div className="mt-3 overflow-x-auto">
              <table className="w-full text-left text-xs" data-testid="surgery-analytics-table">
                <thead>
                  <tr className="border-b border-gray-100 uppercase text-muted-foreground">
                    <th className="py-1.5 pr-3">Indicator</th>
                    <th className="py-1.5 pr-3">Status</th>
                    <th className="py-1.5 pr-3">Executable via</th>
                    <th className="py-1.5">Gap reason</th>
                  </tr>
                </thead>
                <tbody>
                  {indicatorsQ.data.indicators.map((ind) => (
                    <tr key={ind.indicatorCode} className="border-b border-gray-50 align-top">
                      <td className="py-1.5 pr-3">
                        <div className="font-medium">{ind.indicatorName}</div>
                        <div className="text-muted-foreground">{ind.indicatorCode}</div>
                      </td>
                      <td className="py-1.5 pr-3">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                            COMPUTATION_STATUS_STYLE[ind.computationStatus] ??
                            "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {ind.computationStatus}
                        </span>
                        {ind.delegatedOutOfScope && (
                          <span className="ml-1 text-[10px] uppercase text-muted-foreground">
                            delegated
                          </span>
                        )}
                      </td>
                      <td className="py-1.5 pr-3">{ind.executableVia ?? "—"}</td>
                      <td className="py-1.5 text-muted-foreground">{ind.gapReason ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      ) : null}
    </div>
  );
}
