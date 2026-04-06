"use client";

import { useState } from "react";
import { Siren, Plus, FileText, Navigation } from "lucide-react";

const EOC_STATUS = { level: "Level 2 - Enhanced Response", active: true, activatedAt: "2026-04-02 14:30", incident: "Cholera Outbreak - Harare South", commander: "Dr. M. Nyathi (Provincial PEHO)" };

const SITUATION_REPORTS = [
  { id: "SITREP-014", date: "2026-04-06 06:00", author: "Dr. M. Nyathi", period: "24hr", newCases: 8, totalCases: 47, deaths: 0, status: "published" },
  { id: "SITREP-013", date: "2026-04-05 06:00", author: "Dr. M. Nyathi", period: "24hr", newCases: 12, totalCases: 39, deaths: 1, status: "published" },
  { id: "SITREP-012", date: "2026-04-04 06:00", author: "Sr. T. Moyo", period: "24hr", newCases: 6, totalCases: 27, deaths: 0, status: "published" },
  { id: "SITREP-011", date: "2026-04-03 06:00", author: "Dr. M. Nyathi", period: "24hr", newCases: 9, totalCases: 21, deaths: 1, status: "published" },
];

const RESOURCES = [
  { type: "ORS Sachets", requested: 5000, mobilized: 3200, deployed: 2800, source: "WHO / UNICEF" },
  { type: "Cholera Kits", requested: 200, mobilized: 200, deployed: 150, source: "Central Medical Stores" },
  { type: "Water Purification Tablets", requested: 10000, mobilized: 8000, deployed: 6500, source: "ZINWA" },
  { type: "Body Bags", requested: 50, mobilized: 50, deployed: 10, source: "Red Cross" },
  { type: "PPE Sets", requested: 300, mobilized: 300, deployed: 250, source: "MOHCC" },
  { type: "IV Fluids (Ringer's Lactate)", requested: 1000, mobilized: 600, deployed: 400, source: "NatPharm" },
];

const CONTACTS = [
  { name: "Dr. M. Nyathi", role: "Incident Commander / PEHO", org: "MoHCC", phone: "+263-77-XXX-1234", available: true },
  { name: "Mr. J. Chigumba", role: "Logistics Lead", org: "City of Harare", phone: "+263-77-XXX-2345", available: true },
  { name: "Sr. R. Maposa", role: "Epidemiologist", org: "WHO Zimbabwe", phone: "+263-77-XXX-3456", available: true },
  { name: "Mr. T. Matamba", role: "WASH Coordinator", org: "UNICEF", phone: "+263-77-XXX-4567", available: false },
  { name: "Dr. S. Hwende", role: "Clinical Lead - CTC", org: "Parirenyatwa Hospital", phone: "+263-77-XXX-5678", available: true },
];

export function EmergencyCoordinationTab() {
  const [activeSubTab, setActiveSubTab] = useState<"sitreps" | "resources" | "contacts" | "iap">("sitreps");

  return (
    <div className="space-y-4">
      {/* EOC Status Banner */}
      <div className={`rounded-lg border p-4 ${EOC_STATUS.active ? "border-red-200 bg-red-50" : "border-gray-200 bg-gray-50"}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2 rounded-lg bg-red-100">
              <Siren className="h-5 w-5 text-red-600" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h3 className="font-bold text-gray-900">{EOC_STATUS.level}</h3>
                <span className="px-2 py-0.5 bg-red-600 text-white rounded-full text-[10px] font-medium">ACTIVE</span>
              </div>
              <p className="text-sm text-gray-600">{EOC_STATUS.incident}</p>
              <p className="text-xs text-gray-500">Commander: {EOC_STATUS.commander} -- Activated: {EOC_STATUS.activatedAt}</p>
            </div>
          </div>
          <div className="flex gap-2">
            <button className="px-3 py-1.5 border border-gray-300 rounded-lg text-xs font-medium hover:bg-gray-50">Escalate to Level 3</button>
            <button className="px-3 py-1.5 bg-red-600 text-white text-xs font-medium rounded-lg hover:bg-red-700">Deactivate EOC</button>
          </div>
        </div>
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "sitreps" as const, label: "Situation Reports" },
          { key: "resources" as const, label: "Resource Mobilization" },
          { key: "contacts" as const, label: "Agency Directory" },
          { key: "iap" as const, label: "Incident Action Plan" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Situation Reports */}
      {activeSubTab === "sitreps" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Situation Reports (SitRep)</h4>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> New SitRep
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">SitRep ID</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Date/Time</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Author</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Period</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">New Cases</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Cumulative</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Deaths</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600"></th>
                </tr>
              </thead>
              <tbody>
                {SITUATION_REPORTS.map(s => (
                  <tr key={s.id} className="border-b hover:bg-gray-50">
                    <td className="px-3 py-2 font-mono">{s.id}</td>
                    <td className="px-3 py-2 text-gray-500">{s.date}</td>
                    <td className="px-3 py-2">{s.author}</td>
                    <td className="px-3 py-2">{s.period}</td>
                    <td className="px-3 py-2 font-bold">{s.newCases}</td>
                    <td className="px-3 py-2">{s.totalCases}</td>
                    <td className="px-3 py-2">{s.deaths}</td>
                    <td className="px-3 py-2">
                      <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded-full text-[10px] font-medium">{s.status}</span>
                    </td>
                    <td className="px-3 py-2">
                      <button className="p-1 text-gray-400 hover:text-gray-600">
                        <FileText className="h-3.5 w-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Resource Mobilization */}
      {activeSubTab === "resources" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Resource Mobilization Tracker</h4>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Request Resource
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Resource</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Source</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Requested</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Mobilized</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Deployed</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Pipeline</th>
                </tr>
              </thead>
              <tbody>
                {RESOURCES.map((r, i) => {
                  const pct = Math.round((r.deployed / r.requested) * 100);
                  return (
                    <tr key={i} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-medium text-gray-900">{r.type}</td>
                      <td className="px-3 py-2 text-gray-500">{r.source}</td>
                      <td className="px-3 py-2">{r.requested.toLocaleString()}</td>
                      <td className="px-3 py-2">{r.mobilized.toLocaleString()}</td>
                      <td className="px-3 py-2">{r.deployed.toLocaleString()}</td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2">
                          <div className="w-20 bg-gray-200 rounded-full h-2">
                            <div className="h-2 rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
                          </div>
                          <span className="text-xs text-gray-500">{pct}%</span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Agency Directory */}
      {activeSubTab === "contacts" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b">
            <h4 className="text-sm font-semibold text-gray-900">Inter-Agency Contact Directory</h4>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Name</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Role</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Organization</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Phone</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Availability</th>
                </tr>
              </thead>
              <tbody>
                {CONTACTS.map((c, i) => (
                  <tr key={i} className="border-b hover:bg-gray-50">
                    <td className="px-3 py-2 font-medium text-gray-900">{c.name}</td>
                    <td className="px-3 py-2">{c.role}</td>
                    <td className="px-3 py-2">{c.org}</td>
                    <td className="px-3 py-2 font-mono">{c.phone}</td>
                    <td className="px-3 py-2">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        c.available ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"
                      }`}>{c.available ? "Available" : "Unavailable"}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Incident Action Plan */}
      {activeSubTab === "iap" && (
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h4 className="text-sm font-semibold text-gray-900">Incident Action Plan (IAP)</h4>
          <p className="text-xs text-gray-500 mb-4">Operational objectives and response strategy for current activation</p>

          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-3">
              <h4 className="text-sm font-semibold text-gray-900">Response Objectives</h4>
              {[
                { obj: "Reduce case fatality rate below 1%", status: "on_track", progress: 75 },
                { obj: "Achieve 100% contact tracing within 48hrs", status: "at_risk", progress: 60 },
                { obj: "Ensure safe water access in affected wards", status: "on_track", progress: 85 },
                { obj: "Establish cholera treatment centres x3", status: "completed", progress: 100 },
                { obj: "Community health education - 50,000 households", status: "in_progress", progress: 45 },
              ].map((o, i) => (
                <div key={i} className="p-3 border border-gray-200 rounded-lg">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-medium text-gray-900">{o.obj}</span>
                    <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px] capitalize">{o.status.replace(/_/g, " ")}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className={`h-2 rounded-full ${o.progress >= 80 ? "bg-green-500" : o.progress >= 50 ? "bg-amber-500" : "bg-red-500"}`} style={{ width: `${o.progress}%` }} />
                  </div>
                </div>
              ))}
            </div>
            <div className="space-y-3">
              <h4 className="text-sm font-semibold text-gray-900">Command Notes</h4>
              <textarea placeholder="Enter incident commander notes..." className="w-full border border-gray-300 rounded-lg p-3 min-h-[200px] text-sm" />
              <button className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">Save Notes</button>
              <h4 className="text-sm font-semibold text-gray-900 mt-4">Response Timeline</h4>
              <div className="space-y-2 text-sm">
                {[
                  { time: "Apr 2, 14:30", event: "EOC activated - Level 2" },
                  { time: "Apr 2, 16:00", event: "First response teams deployed to Budiriro" },
                  { time: "Apr 3, 08:00", event: "CTC established at Budiriro Clinic" },
                  { time: "Apr 4, 10:00", event: "Water sampling initiated - 12 sites" },
                  { time: "Apr 5, 06:00", event: "WHO technical support team arrives" },
                ].map((e, i) => (
                  <div key={i} className="flex gap-3 items-start">
                    <span className="text-xs text-gray-500 w-28 shrink-0">{e.time}</span>
                    <span className="text-gray-900">{e.event}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
