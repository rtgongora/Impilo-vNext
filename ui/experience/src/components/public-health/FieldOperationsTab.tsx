"use client";

import { useState } from "react";
import { Users, ClipboardList, Target, Navigation, Radio, MapPin, Plus, Loader2 } from "lucide-react";
import { usePublicHealthSites } from "@/hooks/queries/usePublicHealth";

function EmptyOpsTable(title: string, detail: string) {
  return (
    <div className="bg-white rounded-lg border border-gray-200">
      <div className="px-4 py-3 border-b flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-900">{title}</h4>
        <button
          type="button"
          disabled
          className="inline-flex items-center gap-1 px-3 py-1.5 bg-gray-200 text-gray-500 text-xs font-medium rounded-lg cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Not available
        </button>
      </div>
      <div className="px-4 py-12 text-center text-sm text-gray-500">{detail}</div>
    </div>
  );
}

export function FieldOperationsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"teams" | "tasks" | "tracking">("teams");
  const { data: indawoSites = [], isLoading: sitesLoading, isError: sitesError } = usePublicHealthSites();
  const siteCount = indawoSites.length;

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-emerald-200 bg-emerald-50/80 p-4">
        <h4 className="text-sm font-semibold text-emerald-900 flex items-center gap-2">
          <MapPin className="h-4 w-4" /> Registered sites (Indawo, via BFF)
        </h4>
        <p className="mt-1 text-xs text-emerald-800">
          Live list from <code className="rounded bg-white/70 px-1">GET /internal/v1/public-health/sites</code> → indawo-service.
          Field team rosters, task boards, and GPS logs are not fabricated — they require workforce / ops APIs on the BFF.
        </p>
        {sitesLoading && (
          <div className="mt-3 flex items-center gap-2 text-sm text-gray-600">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading sites…
          </div>
        )}
        {sitesError && (
          <p className="mt-3 text-sm text-red-700">Could not load Indawo sites (service unreachable or empty).</p>
        )}
        {!sitesLoading && !sitesError && siteCount === 0 && (
          <p className="mt-3 text-sm text-gray-600">No sites returned for this tenant.</p>
        )}
        {!sitesLoading && !sitesError && siteCount > 0 && (
          <div className="mt-3 overflow-x-auto rounded-md border border-emerald-100 bg-white">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50 text-left">
                  <th className="px-3 py-2 font-medium text-gray-600">Site ID</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Name</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Type</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {indawoSites.slice(0, 25).map((s) => (
                  <tr key={s.id} className="border-b border-gray-100">
                    <td className="px-3 py-2 font-mono text-gray-700">{s.id}</td>
                    <td className="px-3 py-2 font-medium text-gray-900">{s.name}</td>
                    <td className="px-3 py-2 text-gray-600">{s.siteType}</td>
                    <td className="px-3 py-2 text-gray-600">{s.operationalStatus}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="grid grid-cols-5 gap-3">
        {[
          {
            label: "Registered sites (Indawo)",
            value: sitesLoading ? "…" : String(siteCount),
            Icon: MapPin,
            sub: sitesError ? "Could not load" : "Live from BFF",
          },
          { label: "Field teams deployed", value: "—", Icon: Users, sub: "No workforce API" },
          { label: "Active field tasks", value: "—", Icon: ClipboardList, sub: "No task board API" },
          { label: "Contacts traced (24h)", value: "—", Icon: Target, sub: "No aggregate API" },
          { label: "GPS check-ins (24h)", value: "—", Icon: Navigation, sub: "No telemetry API" },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-impilo-50">
              <kpi.Icon className="h-4 w-4 text-impilo-500" />
            </div>
            <div>
              <p className="text-lg font-bold text-gray-900">{kpi.value}</p>
              <p className="text-[10px] text-gray-500">{kpi.label}</p>
              <p className="text-[9px] text-gray-400 mt-0.5">{kpi.sub}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 flex items-center gap-2 text-xs text-gray-600">
        <Radio className="h-3.5 w-3.5 shrink-0" />
        Data forms / comms KPIs will appear when public-health BFF exposes field operations endpoints.
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "teams" as const, label: "Field Teams" },
          { key: "tasks" as const, label: "Task Board" },
          { key: "tracking" as const, label: "GPS Tracking" },
        ].map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key
                ? "border-amber-600 text-amber-600"
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeSubTab === "teams" &&
        EmptyOpsTable(
          "Field Team Roster & Deployment",
          "No field team roster endpoint under /internal/v1/public-health/*. Use Indawo sites above for premises context.",
        )}

      {activeSubTab === "tasks" &&
        EmptyOpsTable(
          "Field Task Assignment Board",
          "No task board API on the Experience BFF. Tasks will list here when a governed workforce or case-task service is wired.",
        )}

      {activeSubTab === "tracking" &&
        EmptyOpsTable(
          "GPS Check-in Log",
          "No GPS / check-in stream on the Experience BFF. This tab stays empty until telemetry ingestion is productised.",
        )}
    </div>
  );
}
