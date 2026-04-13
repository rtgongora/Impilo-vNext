"use client";

import { useState } from "react";
import {
  AlertTriangle,
  Calendar,
  ClipboardCheck,
  FileText,
  Loader2,
  MapPin,
  Plus,
  Star,
} from "lucide-react";
import { usePublicHealthSites } from "@/hooks/queries/usePublicHealth";
import {
  DEMO_COMPLIANCE_BY_CATEGORY,
  DEMO_ENFORCEMENT,
  DEMO_INSPECTIONS,
} from "./publicHealthDemoFixtures";

/**
 * Inspections & enforcement — live **premises (Indawo)** plus **demonstration** register / enforcement / compliance
 * (Lovable-style depth until BFF exposes persisted inspections).
 */
export function InspectionsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"premises" | "inspections" | "enforcement" | "compliance">("premises");
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [selectedInspection, setSelectedInspection] = useState<string | null>(null);
  const { data: sites = [], isLoading, isError } = usePublicHealthSites();

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-violet-200 bg-violet-50/90 p-3 text-xs text-violet-950">
        <strong>Live:</strong> registered premises from <code className="text-[10px]">GET /internal/v1/public-health/sites</code>.
        <strong className="ml-1">Demo:</strong> inspection register, enforcement, and compliance tiles match Lovable /
        impilo-structure workflows — not saved until inspections APIs are wired on the BFF.
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          { label: "Inspections (demo month)", value: "142", Icon: ClipboardCheck, tone: "text-violet-800" },
          { label: "Scheduled (demo)", value: "23", Icon: Calendar, tone: "text-impilo-700" },
          { label: "Critical findings (demo)", value: "4", Icon: AlertTriangle, tone: "text-red-700" },
          { label: "Avg score (demo)", value: "78%", Icon: Star, tone: "text-amber-800" },
          { label: "Active enforcement (demo)", value: "6", Icon: FileText, tone: "text-gray-800" },
        ].map((k) => {
          const I = k.Icon;
          return (
            <div key={k.label} className="rounded-lg border border-gray-200 bg-white p-3 shadow-sm">
              <div className="flex items-center gap-2">
                <div className="rounded-lg bg-violet-100 p-1.5">
                  <I className="h-4 w-4 text-violet-700" />
                </div>
                <div>
                  <p className={`text-lg font-bold ${k.tone}`}>{k.value}</p>
                  <p className="text-[10px] font-medium text-gray-600">{k.label}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex flex-wrap gap-1 border-b border-gray-200">
        {[
          { key: "premises" as const, label: "Premises (Indawo)" },
          { key: "inspections" as const, label: "Inspection register" },
          { key: "enforcement" as const, label: "Enforcement" },
          { key: "compliance" as const, label: "Compliance overview" },
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

      {activeSubTab === "premises" && (
        <div className="rounded-lg border border-gray-200 bg-white">
          <div className="flex items-center gap-2 border-b px-4 py-3">
            <MapPin className="h-4 w-4 text-emerald-600" />
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Registered premises</h4>
              <p className="text-xs text-gray-500">GET /internal/v1/public-health/sites → indawo-service</p>
            </div>
          </div>
          <div className="p-4">
            {isLoading && (
              <div className="flex items-center justify-center gap-2 py-8 text-sm text-gray-500">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading sites…
              </div>
            )}
            {isError && <p className="py-4 text-center text-sm text-red-600">Failed to load sites.</p>}
            {!isLoading && !isError && sites.length === 0 && (
              <p className="py-6 text-center text-sm text-gray-500">No sites returned.</p>
            )}
            {!isLoading && !isError && sites.length > 0 && (
              <ul className="max-h-[480px] divide-y divide-gray-100 overflow-y-auto">
                {sites.map((s) => (
                  <li key={s.id} className="flex flex-wrap justify-between gap-2 py-3">
                    <div>
                      <p className="text-sm font-medium text-gray-900">{s.name}</p>
                      <p className="text-xs text-gray-500">
                        {s.siteType} · {s.jurisdiction} · {s.operationalStatus}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {activeSubTab === "inspections" && (
        <div className="space-y-4 rounded-lg border border-gray-200 bg-white p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Inspection register (demo)</h4>
              <p className="text-xs text-gray-500">Schedule, score, and follow-up — linked to INDAWO-style sites when APIs land</p>
            </div>
            <button
              type="button"
              onClick={() => setShowScheduleForm((s) => !s)}
              className="inline-flex items-center gap-1 rounded-lg bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700"
            >
              <Plus className="h-3.5 w-3.5" /> Schedule inspection
            </button>
          </div>

          {showScheduleForm && (
            <div className="rounded-lg border border-violet-200 bg-violet-50/50 p-4 text-sm">
              <h5 className="mb-3 font-semibold text-gray-900">Schedule new inspection (demo form)</h5>
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Site / premises</span>
                  <input placeholder="Search site (INDAWO)…" className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs" />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Inspection type</span>
                  <select className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs">
                    <option>Routine</option>
                    <option>Ad-hoc / complaint-driven</option>
                    <option>Follow-up</option>
                    <option>Licensing assessment</option>
                    <option>Outbreak investigation</option>
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Scheduled date</span>
                  <input type="date" className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs" />
                </label>
                <label className="block text-xs sm:col-span-3">
                  <span className="font-medium text-gray-600">Notes</span>
                  <textarea
                    placeholder="Specific areas to inspect…"
                    className="mt-1 min-h-[48px] w-full rounded border border-gray-300 p-2 text-xs"
                  />
                </label>
              </div>
              <p className="mt-2 text-[10px] text-violet-800">Submit is disabled — no POST endpoint yet.</p>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-xs">
              <thead>
                <tr className="border-b bg-gray-50 text-left">
                  <th className="px-2 py-2 font-medium text-gray-600">ID</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Site</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Type</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Inspector</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Date</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Status</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Score</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Critical</th>
                  <th className="px-2 py-2 font-medium text-gray-600" />
                </tr>
              </thead>
              <tbody>
                {DEMO_INSPECTIONS.map((row) => (
                  <tr key={row.id} className="border-b hover:bg-gray-50">
                    <td className="px-2 py-2 font-mono">{row.id}</td>
                    <td className="px-2 py-2">
                      <p className="font-medium text-gray-900">{row.site}</p>
                      <p className="text-[10px] text-gray-500">{row.siteType}</p>
                    </td>
                    <td className="px-2 py-2 text-gray-700">{row.type}</td>
                    <td className="px-2 py-2">{row.inspector}</td>
                    <td className="px-2 py-2 text-gray-600">{row.date}</td>
                    <td className="px-2 py-2">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] capitalize text-slate-800">
                        {row.status.replace("_", " ")}
                      </span>
                    </td>
                    <td className="px-2 py-2 tabular-nums">{row.score ?? "—"}</td>
                    <td className="px-2 py-2">{row.critical > 0 ? <span className="font-semibold text-red-700">{row.critical}</span> : "0"}</td>
                    <td className="px-2 py-2">
                      <button
                        type="button"
                        onClick={() => setSelectedInspection(selectedInspection === row.id ? null : row.id)}
                        className="rounded border border-gray-300 px-2 py-1 text-[10px] font-medium hover:bg-gray-50"
                      >
                        {selectedInspection === row.id ? "Close" : "Details"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {selectedInspection && (
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs text-gray-800">
              <p className="font-semibold">Findings & follow-up — {selectedInspection}</p>
              <p className="mt-1 text-gray-600">
                Wire checklist, photos, and improvement notices to persistence when the inspections service is available.
              </p>
            </div>
          )}
        </div>
      )}

      {activeSubTab === "enforcement" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-3 text-sm font-semibold text-gray-900">Enforcement actions (demo)</h4>
          <div className="space-y-3">
            {DEMO_ENFORCEMENT.map((e) => (
              <div key={e.id} className="rounded-lg border border-amber-200 bg-amber-50/40 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-mono text-[10px] text-gray-600">{e.id}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                      e.severity === "critical" ? "bg-red-100 text-red-800" : "bg-amber-100 text-amber-900"
                    }`}
                  >
                    {e.severity}
                  </span>
                </div>
                <p className="mt-1 text-sm font-medium text-gray-900">{e.site}</p>
                <p className="text-xs text-gray-700">{e.violation}</p>
                <p className="mt-2 text-[10px] text-gray-600">
                  {e.action} · issued {e.issued} · deadline {e.deadline} · <span className="font-medium">{e.status}</span>
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeSubTab === "compliance" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-3 text-sm font-semibold text-gray-900">Compliance overview by category (demo)</h4>
          <div className="space-y-4">
            {DEMO_COMPLIANCE_BY_CATEGORY.map((c) => {
              const pct = Math.min(100, Math.round((c.rate / c.target) * 100));
              return (
                <div key={c.category}>
                  <div className="mb-1 flex justify-between text-xs">
                    <span className="font-medium text-gray-800">{c.category}</span>
                    <span className={c.rate >= c.target ? "text-emerald-700" : "text-amber-800"}>
                      {c.rate}% / target {c.target}%
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                    <div className="h-full rounded-full bg-violet-500 transition-all" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
