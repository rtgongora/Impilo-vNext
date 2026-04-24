"use client";

import { useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { usePublicHealthAlerts } from "@/hooks/queries/usePublicHealth";
import { countHighOrCriticalSurveillanceAlerts, countOpenSurveillanceAlerts } from "./publicHealthAlertMetrics";

export function ComplaintsTab() {
  const [panel, setPanel] = useState<"environmental" | "health_alerts">("health_alerts");
  const { data: alerts = [], isLoading, isError } = usePublicHealthAlerts();

  const openCount = countOpenSurveillanceAlerts(alerts);
  const criticalish = countHighOrCriticalSurveillanceAlerts(alerts);

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-impilo-200 bg-impilo-50/90 p-3 text-xs text-impilo-800">
        <strong>Live:</strong> health and surveillance alerts load from{" "}
        <code className="text-[10px]">/internal/v1/public-health/alerts</code>.
        <strong className="ml-1">Pending:</strong> environmental and nuisance complaints stay disabled until a governed
        complaints API exists on the Experience BFF.
      </div>

      <div className="flex flex-wrap gap-1 border-b border-gray-200">
        {[
          { key: "environmental" as const, label: "Environmental & nuisance (pending)", disabled: true },
          { key: "health_alerts" as const, label: "Health & surveillance alerts (live)", disabled: false },
        ].map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => !t.disabled && setPanel(t.key)}
            disabled={t.disabled}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              panel === t.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            } ${t.disabled ? "cursor-not-allowed opacity-50" : ""}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {panel === "environmental" && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-6 text-sm text-amber-950">
          <p className="font-medium">Environmental complaints are not yet wired to a governed backend flow.</p>
          <p className="mt-2 text-xs leading-relaxed text-amber-900">
            Demo complaint registers were removed from this screen so operators do not mistake placeholder records for
            production data. Re-enable this tab when the Experience BFF exposes a real complaints API.
          </p>
        </div>
      )}

      {panel === "health_alerts" && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {[
              { label: "Open surveillance alerts", value: String(openCount), color: "text-amber-700" },
              { label: "High / critical alerts", value: String(criticalish), color: "text-red-700" },
              { label: "Environmental API", value: "—", color: "text-gray-500", sub: "Pending BFF complaints API" },
              { label: "Resolution metrics", value: "—", color: "text-gray-500", sub: "Pending governed workflow" },
            ].map((kpi) => (
              <div key={kpi.label} className="rounded-lg border border-gray-200 bg-white p-3 text-center">
                <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
                {"sub" in kpi && kpi.sub && <p className="text-[10px] text-gray-400">{kpi.sub}</p>}
              </div>
            ))}
          </div>

          <div className="rounded-lg border border-gray-200 bg-white">
            <div className="border-b px-4 py-3">
              <h4 className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                <AlertTriangle className="h-4 w-4" /> Surveillance alerts
              </h4>
              <p className="text-xs text-gray-500">Syndrome and facility alerts from surveillance-service</p>
            </div>
            <div className="p-4">
              {isLoading && (
                <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
                  <Loader2 className="h-5 w-5 animate-spin" /> Loading alerts…
                </div>
              )}
              {isError && <p className="py-4 text-center text-sm text-red-600">Failed to load alerts.</p>}
              {!isLoading && !isError && alerts.length === 0 && (
                <p className="py-6 text-center text-sm text-gray-500">No alerts returned.</p>
              )}
              {!isLoading && !isError && alerts.length > 0 && (
                <ul className="max-h-[420px] divide-y divide-gray-100 overflow-y-auto">
                  {alerts.map((a) => (
                    <li key={a.id} className="flex flex-wrap justify-between gap-2 py-3">
                      <div>
                        <p className="text-sm font-medium text-gray-900">{a.title}</p>
                        <p className="text-xs text-gray-500">
                          {a.location} · {a.severity} · {a.detectedAt || "—"}
                        </p>
                      </div>
                      <span className="h-fit rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-700">
                        {a.status}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
