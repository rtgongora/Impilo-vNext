"use client";

import { useMemo, useState } from "react";
import { AlertTriangle, Loader2, Plus } from "lucide-react";
import { usePublicHealthAlerts } from "@/hooks/queries/usePublicHealth";
import { countHighOrCriticalSurveillanceAlerts, countOpenSurveillanceAlerts } from "./publicHealthAlertMetrics";
import { DEMO_COMPLAINTS } from "./publicHealthDemoFixtures";

/**
 * Complaints & alerts — **environmental / nuisance** demo register (Lovable parity) plus **live** surveillance alerts.
 */
export function ComplaintsTab() {
  const [panel, setPanel] = useState<"environmental" | "health_alerts">("environmental");
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [showLogForm, setShowLogForm] = useState(false);
  const { data: alerts = [], isLoading, isError } = usePublicHealthAlerts();

  const openCount = countOpenSurveillanceAlerts(alerts);
  const criticalish = countHighOrCriticalSurveillanceAlerts(alerts);

  const filteredComplaints = useMemo(() => {
    if (statusFilter === "all") return DEMO_COMPLAINTS;
    return DEMO_COMPLAINTS.filter((c) => c.status === statusFilter);
  }, [statusFilter]);

  const demoOpen = DEMO_COMPLAINTS.filter((c) => c.status !== "resolved").length;
  const demoCritical = DEMO_COMPLAINTS.filter((c) => c.priority === "critical" && c.status !== "resolved").length;

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-200 bg-blue-50/90 p-3 text-xs text-blue-950">
        <strong>Environmental complaints:</strong> table below is <strong>demonstration data</strong> (same shape as Lovable /
        impilo-structure) until <code className="text-[10px]">GET/POST …/complaints</code> exists on the BFF.
        <strong className="ml-1">Syndrome alerts:</strong> switch to &quot;Health &amp; surveillance alerts&quot; for live{" "}
        <code className="text-[10px]">/internal/v1/public-health/alerts</code>.
      </div>

      <div className="flex flex-wrap gap-1 border-b border-gray-200">
        {[
          { key: "environmental" as const, label: "Environmental & nuisance" },
          { key: "health_alerts" as const, label: "Health & surveillance alerts (live)" },
        ].map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setPanel(t.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              panel === t.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {panel === "environmental" && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
            {[
              { label: "Open (demo)", value: String(demoOpen), color: "text-amber-800" },
              { label: "Critical (demo)", value: String(demoCritical), color: "text-red-700" },
              { label: "Avg resolution (demo)", value: "4.2d", color: "text-blue-800" },
              { label: "Resolved month (demo)", value: "34", color: "text-emerald-800" },
              { label: "Satisfaction (demo)", value: "4.1/5", color: "text-violet-800" },
            ].map((kpi) => (
              <div key={kpi.label} className="rounded-lg border border-gray-200 bg-white p-3 text-center shadow-sm">
                <p className={`text-2xl font-bold ${kpi.color}`}>{kpi.value}</p>
                <p className="text-xs font-medium text-gray-900">{kpi.label}</p>
              </div>
            ))}
          </div>

          <div className="rounded-lg border border-gray-200 bg-white">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-3">
              <div>
                <h4 className="flex items-center gap-2 text-sm font-semibold text-gray-900">
                  <AlertTriangle className="h-4 w-4" /> Complaints, alerts &amp; nuisance (demo register)
                </h4>
                <p className="text-xs text-gray-500">Intake, assignment, priority — persistence backlog</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="h-8 rounded border border-gray-300 px-2 text-xs"
                >
                  <option value="all">All status</option>
                  <option value="assigned">Assigned</option>
                  <option value="investigating">Investigating</option>
                  <option value="enforcement_pending">Enforcement pending</option>
                  <option value="resolved">Resolved</option>
                </select>
                <button
                  type="button"
                  onClick={() => setShowLogForm((s) => !s)}
                  className="inline-flex items-center gap-1 rounded-lg bg-amber-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-amber-700"
                >
                  <Plus className="h-3.5 w-3.5" /> Log complaint
                </button>
              </div>
            </div>

            <div className="p-4">
              {showLogForm && (
                <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50/50 p-4 text-xs">
                  <h5 className="mb-3 font-semibold text-gray-900">Log new complaint (demo)</h5>
                  <div className="grid gap-3 sm:grid-cols-3">
                    <label className="block">
                      <span className="text-gray-600">Category</span>
                      <select className="mt-1 h-8 w-full rounded border border-gray-300 px-2">
                        <option>Environmental</option>
                        <option>Food safety</option>
                        <option>Water</option>
                        <option>Sanitation</option>
                        <option>Waste</option>
                      </select>
                    </label>
                    <label className="block">
                      <span className="text-gray-600">Type</span>
                      <input placeholder="e.g. Sewage overflow" className="mt-1 h-8 w-full rounded border border-gray-300 px-2" />
                    </label>
                    <label className="block">
                      <span className="text-gray-600">Location</span>
                      <input placeholder="Ward / address" className="mt-1 h-8 w-full rounded border border-gray-300 px-2" />
                    </label>
                  </div>
                  <p className="mt-2 text-[10px] text-amber-900">Submit disabled — no POST endpoint.</p>
                </div>
              )}

              <div className="overflow-x-auto">
                <table className="w-full min-w-[640px] text-xs">
                  <thead>
                    <tr className="border-b bg-gray-50 text-left">
                      <th className="px-2 py-2 font-medium text-gray-600">ID</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Type</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Category</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Location</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Priority</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Status</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Assigned</th>
                      <th className="px-2 py-2 font-medium text-gray-600">Days open</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredComplaints.map((c) => (
                      <tr key={c.id} className="border-b hover:bg-gray-50">
                        <td className="px-2 py-2 font-mono">{c.id}</td>
                        <td className="px-2 py-2 font-medium capitalize text-gray-900">{c.type}</td>
                        <td className="px-2 py-2 text-gray-700">{c.category}</td>
                        <td className="px-2 py-2 capitalize text-gray-700">{c.location}</td>
                        <td className="px-2 py-2">
                          <span
                            className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                              c.priority === "critical"
                                ? "bg-red-100 text-red-800"
                                : c.priority === "high"
                                  ? "bg-amber-100 text-amber-900"
                                  : "bg-slate-100 text-slate-700"
                            }`}
                          >
                            {c.priority}
                          </span>
                        </td>
                        <td className="px-2 py-2 capitalize text-gray-700">{c.status.replace(/_/g, " ")}</td>
                        <td className="px-2 py-2">{c.assignedTo}</td>
                        <td className="px-2 py-2 tabular-nums">{c.daysOpen}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </>
      )}

      {panel === "health_alerts" && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            {[
              { label: "Open surveillance alerts", value: String(openCount), color: "text-amber-700" },
              { label: "High / critical alerts", value: String(criticalish), color: "text-red-700" },
              { label: "Environmental API", value: "—", color: "text-gray-500", sub: "Demo only (other tab)" },
              { label: "Resolution metrics", value: "—", color: "text-gray-500", sub: "Backlog" },
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
              <p className="text-xs text-gray-500">Syndrome / facility alerts from surveillance-service</p>
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
                      <span className="h-fit rounded-full bg-slate-100 px-2 py-0.5 text-[10px] text-slate-700">{a.status}</span>
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
