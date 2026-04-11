"use client";

import { Loader2, AlertTriangle } from "lucide-react";
import { usePublicHealthAlerts } from "@/hooks/queries/usePublicHealth";
import { countHighOrCriticalSurveillanceAlerts, countOpenSurveillanceAlerts } from "./publicHealthAlertMetrics";

/**
 * Environmental / nuisance complaint register: **unsupported** (no BFF route).
 * **Surveillance alerts** are real (same source as Surveillance tab).
 */
export function ComplaintsTab() {
  const { data: alerts = [], isLoading, isError } = usePublicHealthAlerts();

  const openCount = countOpenSurveillanceAlerts(alerts);
  const criticalish = countHighOrCriticalSurveillanceAlerts(alerts);

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-200 bg-blue-50/90 p-3 text-xs text-blue-950">
        <strong>Environmental complaints:</strong> no{" "}
        <code className="text-[10px]">/internal/v1/public-health/complaints</code> (or equivalent) in Experience BFF. Intake,
        assignment, and enforcement workflows are backlog until a service is wired. The panel below shows{" "}
        <strong>surveillance alerts</strong> from{" "}
        <code className="text-[10px]">GET /internal/v1/public-health/alerts</code>.
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: "Open surveillance alerts", value: String(openCount), color: "text-amber-700" },
          { label: "High / critical alerts", value: String(criticalish), color: "text-red-700" },
          {
            label: "Environmental complaints API",
            value: "—",
            color: "text-gray-500",
            sub: "Unsupported",
          },
          {
            label: "Resolution metrics",
            value: "—",
            color: "text-gray-500",
            sub: "Unsupported",
          },
        ].map((kpi) => (
          <div key={kpi.label} className="bg-white rounded-lg border border-gray-200 p-3 text-center">
            <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
            <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
            {"sub" in kpi && kpi.sub && <p className="text-[10px] text-gray-400">{kpi.sub}</p>}
          </div>
        ))}
      </div>

      <div className="bg-white rounded-lg border border-gray-200">
        <div className="px-4 py-3 border-b">
          <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
            <AlertTriangle className="h-4 w-4" /> Surveillance alerts
          </h4>
          <p className="text-xs text-gray-500">Syndrome / facility alerts from surveillance-service (not environmental tickets)</p>
        </div>
        <div className="p-4">
          {isLoading && (
            <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
              <Loader2 className="h-5 w-5 animate-spin" /> Loading alerts…
            </div>
          )}
          {isError && <p className="text-sm text-red-600 text-center py-4">Failed to load alerts.</p>}
          {!isLoading && !isError && alerts.length === 0 && (
            <p className="text-sm text-gray-500 text-center py-6">No alerts returned.</p>
          )}
          {!isLoading && !isError && alerts.length > 0 && (
            <ul className="divide-y divide-gray-100 max-h-[420px] overflow-y-auto">
              {alerts.map((a) => (
                <li key={a.id} className="py-3 flex flex-wrap justify-between gap-2">
                  <div>
                    <p className="font-medium text-sm text-gray-900">{a.title}</p>
                    <p className="text-xs text-gray-500">
                      {a.location} · {a.severity} · {a.detectedAt || "—"}
                    </p>
                  </div>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 h-fit">{a.status}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-4">
        <h4 className="text-sm font-semibold text-gray-800 mb-1">Environmental complaint intake</h4>
        <p className="text-xs text-gray-600">
          Log-complaint forms and officer assignment tables were removed (demo data). Blocker: define persistence and expose
          list/create/transition endpoints via Experience BFF before rebuilding this UI.
        </p>
      </div>
    </div>
  );
}
