"use client";

import {
  Activity,
  AlertTriangle,
  ClipboardCheck,
  Loader2,
  MapPin,
  Megaphone,
  Radio,
  Siren,
  Users,
} from "lucide-react";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import {
  useNompiloPublicHealthSummary,
  usePublicHealthOperationsHome,
} from "@/hooks/queries/usePublicHealth";
import { formatPublicHealthCompact } from "./publicHealthDashboardUtils";
import { PublicHealthMapPanel } from "./PublicHealthMapPanel";
import { PublicHealthReportsPanel } from "./PublicHealthReportsPanel";

export function PublicHealthDashboard() {
  const homeQ = usePublicHealthOperationsHome();
  const nompiloQ = useNompiloPublicHealthSummary();

  const home = homeQ.data;
  const kpis = home?.kpis ?? {};
  const worklist = home?.priorityWorklist ?? [];

  const kpiCards = [
    { Icon: Activity, value: String(kpis.active_signals ?? 0), label: "Active signals", color: "text-red-700", bg: "bg-red-50", border: "border-red-200" },
    { Icon: ClipboardCheck, value: String(kpis.open_cases ?? 0), label: "Open cases", color: "text-impilo-600", bg: "bg-impilo-50", border: "border-impilo-200" },
    { Icon: AlertTriangle, value: String(kpis.open_alerts ?? 0), label: "Open alerts", color: "text-amber-700", bg: "bg-amber-50", border: "border-amber-200" },
    { Icon: Siren, value: String(kpis.active_outbreaks ?? 0), label: "Active outbreaks", color: "text-red-800", bg: "bg-red-100", border: "border-red-300" },
    { Icon: MapPin, value: String(kpis.field_tasks_open ?? 0), label: "Open field tasks", color: "text-emerald-700", bg: "bg-emerald-50", border: "border-emerald-200" },
    { Icon: Users, value: String(kpis.open_investigations ?? 0), label: "Open investigations", color: "text-sky-700", bg: "bg-sky-50", border: "border-sky-200" },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-emerald-200 bg-emerald-50/90 p-3 text-xs text-emerald-950">
        <strong>Operations command centre:</strong> single aggregated read model from{" "}
        <code className="rounded bg-white/70 px-1">GET /internal/v1/public-health/operations-home</code> — outbreaks,
        field tasks, investigations, map markers, and intelligence briefs are now governed lifecycle APIs.
      </div>

      {homeQ.isError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-900">
          Operations home failed to load. Downstream counts may be unavailable.
        </div>
      )}

      {homeQ.isPending ? (
        <div className="flex items-center justify-center gap-2 py-12 text-sm text-gray-500">
          <Loader2 className="h-5 w-5 animate-spin" /> Loading operations home…
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
            {kpiCards.map((kpi) => (
              <div key={kpi.label} className={`${kpi.bg} rounded-lg border ${kpi.border} p-3 text-center`}>
                <kpi.Icon className={`h-5 w-5 mx-auto mb-1.5 ${kpi.color}`} />
                <p className={`text-xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-[10px] text-gray-600">{kpi.label}</p>
              </div>
            ))}
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <Megaphone className="h-4 w-4" /> Priority worklist
              </h3>
              {worklist.length === 0 ? (
                <p className="text-xs text-gray-500">No priority items in the worklist.</p>
              ) : (
                <div className="space-y-2">
                  {worklist.map((item) => (
                    <div key={`${item.type}-${item.id}`} className="flex justify-between p-2.5 border border-gray-200 rounded-lg text-xs">
                      <span className="font-medium text-gray-900">{item.label}</span>
                      <span className="text-gray-500">{item.type} · {item.priority}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-white rounded-lg border border-gray-200 p-5">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                <Radio className="h-4 w-4" /> Map coverage
              </h3>
              <p className="text-sm text-gray-700">
                <span className="font-bold text-indigo-700">{home?.mapMarkerCount ?? 0}</span> geo-tagged markers on the operations map.
              </p>
              <p className="text-[10px] text-gray-500 mt-2">
                Draft briefs pending: {formatPublicHealthCompact(kpis.draft_briefs ?? 0)}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <PublicHealthMapPanel />
            <PublicHealthReportsPanel />
          </div>
        </>
      )}

      {nompiloQ.data?.message && (
        <NompiloHint message={nompiloQ.data.message} suggestions={nompiloQ.data.suggestions} />
      )}
    </div>
  );
}
