"use client";

import { useState } from "react";
import { Megaphone, Plus, MapPin, Users, TrendingUp, Package } from "lucide-react";

const CAMPAIGNS = [
  { id: "CAM-001", name: "COVID-19 Booster Rollout", type: "Immunization", status: "active", target: 500000, reached: 234567, startDate: "2026-01-15", endDate: "2026-06-30", jurisdiction: "National", sites: 450, teams: 120 },
  { id: "CAM-002", name: "School Deworming Programme", type: "NTD", status: "active", target: 120000, reached: 98000, startDate: "2026-02-01", endDate: "2026-04-30", jurisdiction: "Mashonaland East", sites: 284, teams: 45 },
  { id: "CAM-003", name: "Malaria IRS - Round 2", type: "Vector Control", status: "planning", target: 300000, reached: 0, startDate: "2026-04-01", endDate: "2026-06-30", jurisdiction: "Manicaland", sites: 156, teams: 60 },
  { id: "CAM-004", name: "HIV Testing Week", type: "Screening", status: "completed", target: 50000, reached: 52340, startDate: "2026-03-01", endDate: "2026-03-07", jurisdiction: "National", sites: 320, teams: 80 },
  { id: "CAM-005", name: "Cholera Vaccination - Harare", type: "Immunization", status: "active", target: 200000, reached: 45000, startDate: "2026-04-01", endDate: "2026-05-15", jurisdiction: "Harare", sites: 85, teams: 30 },
];

const SITE_COVERAGE = [
  { site: "Budiriro Clinic", ward: "Ward 22", target: 5000, vaccinated: 4200, wastage: "3.2%", stockRemaining: 850 },
  { site: "Glen Norah Polyclinic", ward: "Ward 29", target: 8000, vaccinated: 6100, wastage: "2.8%", stockRemaining: 2100 },
  { site: "Mbare Musika Outreach", ward: "Ward 1", target: 3000, vaccinated: 1200, wastage: "5.1%", stockRemaining: 1900 },
  { site: "Highfield Community Hall", ward: "Ward 14", target: 6000, vaccinated: 5800, wastage: "1.9%", stockRemaining: 250 },
  { site: "Epworth Clinic", ward: "Ward 7", target: 4000, vaccinated: 2800, wastage: "4.0%", stockRemaining: 1300 },
];

const SUPPLY_TRACKING = [
  { item: "OCV Vaccine (doses)", allocated: 200000, distributed: 165000, used: 142000, wastage: 4600, balance: 53400 },
  { item: "Syringes (0.5ml)", allocated: 210000, distributed: 175000, used: 142000, wastage: 1200, balance: 66800 },
  { item: "Safety Boxes", allocated: 4200, distributed: 3500, used: 2840, wastage: 0, balance: 1360 },
  { item: "Vaccination Cards", allocated: 200000, distributed: 170000, used: 142000, wastage: 500, balance: 57500 },
  { item: "Cold Boxes", allocated: 180, distributed: 160, used: 160, wastage: 0, balance: 20 },
];

export function CampaignsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"campaigns" | "coverage" | "supply">("campaigns");

  return (
    <div className="space-y-4">
      {/* KPIs */}
      <div className="grid grid-cols-5 gap-3">
        {[
          { label: "Active Campaigns", value: "3", Icon: Megaphone },
          { label: "People Reached", value: "377K", Icon: Users },
          { label: "Active Sites", value: "819", Icon: MapPin },
          { label: "Field Teams", value: "210", Icon: Users },
          { label: "Overall Coverage", value: "68%", Icon: TrendingUp },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-50">
              <kpi.Icon className="h-4 w-4 text-blue-600" />
            </div>
            <div>
              <p className="text-lg font-bold text-gray-900">{kpi.value}</p>
              <p className="text-[10px] text-gray-500">{kpi.label}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "campaigns" as const, label: "Campaign Registry" },
          { key: "coverage" as const, label: "Site Coverage" },
          { key: "supply" as const, label: "Supply & Logistics" },
        ].map((tab) => (
          <button key={tab.key} onClick={() => setActiveSubTab(tab.key)}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
            }`}>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Campaign Registry */}
      {activeSubTab === "campaigns" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Campaign Registry</h4>
            <button className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700">
              <Plus className="h-3.5 w-3.5" /> Plan Campaign
            </button>
          </div>
          <div className="p-4 space-y-3">
            {CAMPAIGNS.map(cam => {
              const pct = Math.round((cam.reached / cam.target) * 100);
              return (
                <div key={cam.id} className="p-4 border border-gray-200 rounded-lg">
                  <div className="flex items-center justify-between mb-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="font-semibold text-sm text-gray-900">{cam.name}</p>
                        <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px]">{cam.type}</span>
                      </div>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {cam.jurisdiction} -- {cam.startDate} to {cam.endDate} -- {cam.sites} sites -- {cam.teams} teams
                      </p>
                    </div>
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                      cam.status === "active" ? "bg-green-100 text-green-700" :
                      cam.status === "completed" ? "bg-blue-100 text-blue-700" : "bg-gray-100 text-gray-600"
                    }`}>{cam.status}</span>
                  </div>
                  <div className="flex items-center gap-4">
                    <div className="flex-1 bg-gray-200 rounded-full h-2.5">
                      <div className="h-2.5 rounded-full bg-green-500" style={{ width: `${pct}%` }} />
                    </div>
                    <span className="text-sm font-bold tabular-nums w-28 text-right">
                      {cam.reached.toLocaleString()} / {cam.target.toLocaleString()}
                    </span>
                    <span className="text-sm font-bold w-12 text-right">{pct}%</span>
                    <button className="px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50">Manage</button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Site Coverage */}
      {activeSubTab === "coverage" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b">
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <MapPin className="h-4 w-4" /> Site-Level Coverage Monitoring
            </h4>
            <p className="text-xs text-gray-500">Cholera Vaccination - Harare (CAM-005)</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Site</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Ward</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Target</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Vaccinated</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Coverage</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Wastage</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Stock</th>
                </tr>
              </thead>
              <tbody>
                {SITE_COVERAGE.map((s, i) => {
                  const coverage = Math.round((s.vaccinated / s.target) * 100);
                  return (
                    <tr key={i} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-medium text-gray-900">{s.site}</td>
                      <td className="px-3 py-2">{s.ward}</td>
                      <td className="px-3 py-2">{s.target.toLocaleString()}</td>
                      <td className="px-3 py-2">{s.vaccinated.toLocaleString()}</td>
                      <td className="px-3 py-2">
                        <div className="flex items-center gap-2">
                          <div className="w-16 bg-gray-200 rounded-full h-2">
                            <div className={`h-2 rounded-full ${coverage >= 80 ? "bg-green-500" : coverage >= 50 ? "bg-amber-500" : "bg-red-500"}`} style={{ width: `${coverage}%` }} />
                          </div>
                          <span className={`text-xs font-bold ${coverage >= 80 ? "text-green-700" : coverage >= 50 ? "text-amber-700" : "text-red-700"}`}>{coverage}%</span>
                        </div>
                      </td>
                      <td className={`px-3 py-2 ${parseFloat(s.wastage) > 4 ? "text-amber-700 font-bold" : ""}`}>{s.wastage}</td>
                      <td className={`px-3 py-2 ${s.stockRemaining < 500 ? "text-red-700 font-bold" : ""}`}>{s.stockRemaining.toLocaleString()}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Supply & Logistics */}
      {activeSubTab === "supply" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b">
            <h4 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Package className="h-4 w-4" /> Supply & Logistics Tracking
            </h4>
            <p className="text-xs text-gray-500">Cholera Vaccination - Harare (CAM-005)</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Item</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Allocated</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Distributed</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Used</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Wastage</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Balance</th>
                  <th className="text-left px-3 py-2 font-medium text-gray-600">Pipeline</th>
                </tr>
              </thead>
              <tbody>
                {SUPPLY_TRACKING.map((s, i) => {
                  const pct = Math.round((s.used / s.allocated) * 100);
                  return (
                    <tr key={i} className="border-b hover:bg-gray-50">
                      <td className="px-3 py-2 font-medium text-gray-900">{s.item}</td>
                      <td className="px-3 py-2">{s.allocated.toLocaleString()}</td>
                      <td className="px-3 py-2">{s.distributed.toLocaleString()}</td>
                      <td className="px-3 py-2">{s.used.toLocaleString()}</td>
                      <td className={`px-3 py-2 ${s.wastage > 1000 ? "text-amber-700 font-bold" : ""}`}>{s.wastage.toLocaleString()}</td>
                      <td className="px-3 py-2">{s.balance.toLocaleString()}</td>
                      <td className="px-3 py-2">
                        <div className="w-20 bg-gray-200 rounded-full h-2">
                          <div className="h-2 rounded-full bg-blue-500" style={{ width: `${pct}%` }} />
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
    </div>
  );
}
