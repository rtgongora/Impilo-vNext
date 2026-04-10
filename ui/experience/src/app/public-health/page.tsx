"use client";

/**
 * Public Health & Local Authority Operations
 * Route: /public-health
 * Full jurisdiction-pack system with 8 sub-modules: dashboard, surveillance/eIDSR,
 * outbreaks, inspections, complaints, campaigns, field ops, emergency coordination.
 */

import { useState } from "react";
import {
  Activity, AlertTriangle, Bug, ClipboardCheck, Megaphone,
  MapPin, Shield, Siren, Settings, Building, TreePine,
  Ship, School, Globe, Radio, Target, Users,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SurveillanceTab } from "@/components/public-health/SurveillanceTab";
import { OutbreaksTab } from "@/components/public-health/OutbreaksTab";
import { InspectionsTab } from "@/components/public-health/InspectionsTab";
import { ComplaintsTab } from "@/components/public-health/ComplaintsTab";
import { CampaignsTab } from "@/components/public-health/CampaignsTab";
import { FieldOperationsTab } from "@/components/public-health/FieldOperationsTab";
import { EmergencyCoordinationTab } from "@/components/public-health/EmergencyCoordinationTab";

type ActiveTab = "dashboard" | "surveillance" | "outbreaks" | "inspections" | "complaints" | "campaigns" | "field" | "emergency";

const JURISDICTION_PACKS = [
  { id: "city_health", label: "City Health Pack", description: "Urban municipal public health operations", Icon: Building, color: "bg-blue-500", activeIn: "Harare, Bulawayo, Mutare, Gweru, Kwekwe, Masvingo" },
  { id: "rdc_health", label: "Rural District Council Health Pack", description: "Rural public health operations and community health", Icon: TreePine, color: "bg-green-500", activeIn: "62 Rural District Councils" },
  { id: "provincial", label: "Provincial Public Health Oversight Pack", description: "Provincial surveillance, coordination, and oversight", Icon: Globe, color: "bg-purple-500", activeIn: "All 10 Provinces" },
  { id: "national", label: "National Public Health Oversight Pack", description: "National surveillance, policy, and coordination", Icon: Shield, color: "bg-red-500", activeIn: "National" },
  { id: "port_health", label: "Port Health Pack", description: "Border and port of entry health operations", Icon: Ship, color: "bg-indigo-500", activeIn: "14 Ports of Entry" },
  { id: "school_health", label: "School Health Pack", description: "School-based health services and inspections", Icon: School, color: "bg-amber-500", activeIn: "1,284 Schools" },
];

const TABS: { key: ActiveTab; label: string; Icon: typeof Activity }[] = [
  { key: "dashboard", label: "Dashboard", Icon: Activity },
  { key: "surveillance", label: "Surveillance / eIDSR", Icon: Target },
  { key: "outbreaks", label: "Outbreaks & Incidents", Icon: AlertTriangle },
  { key: "inspections", label: "Inspections", Icon: ClipboardCheck },
  { key: "complaints", label: "Complaints & Alerts", Icon: AlertTriangle },
  { key: "campaigns", label: "Campaigns & Outreach", Icon: Megaphone },
  { key: "field", label: "Field Operations", Icon: MapPin },
  { key: "emergency", label: "Emergency Coordination", Icon: Siren },
];

export default function PublicHealthPage() {
  const [activePack, setActivePack] = useState("city_health");
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");

  return (
    <AppLayout>
      <PageShell
        title="Public Health & Local Authority Operations"
        subtitle="Shared reusable capability configured for different jurisdictions - not cloned apps"
      >
        {/* Jurisdiction Pack Selector */}
        <div className="mb-6 rounded-lg border border-blue-200 bg-blue-50 p-4">
          <div className="flex items-center gap-2 mb-1">
            <Settings className="h-4 w-4 text-blue-600" />
            <h3 className="text-sm font-semibold text-blue-900">Active Jurisdiction Pack</h3>
          </div>
          <p className="text-xs text-blue-700 mb-3">Same platform capabilities, configured per jurisdiction</p>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
            {JURISDICTION_PACKS.map(pack => (
              <button
                key={pack.id}
                onClick={() => setActivePack(pack.id)}
                className={`p-3 rounded-lg border text-left transition-all ${
                  activePack === pack.id
                    ? "border-blue-500 bg-white ring-2 ring-blue-200"
                    : "border-gray-200 bg-white hover:border-blue-300"
                }`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <div className={`w-6 h-6 rounded ${pack.color} flex items-center justify-center`}>
                    <pack.Icon className="h-3.5 w-3.5 text-white" />
                  </div>
                  <span className="text-xs font-medium text-gray-900">{pack.label}</span>
                </div>
                <p className="text-[10px] text-gray-500 truncate">{pack.activeIn}</p>
              </button>
            ))}
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
          {TABS.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                activeTab === tab.key
                  ? "border-amber-600 text-amber-600"
                  : "border-transparent text-gray-500 hover:text-gray-700"
              }`}
            >
              <tab.Icon className="w-4 h-4" /> {tab.label}
            </button>
          ))}
        </div>

        {activeTab === "dashboard" && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
              {[
                { Icon: Bug, value: "3", label: "Active Outbreaks", color: "text-red-700", bg: "bg-red-50", border: "border-red-200" },
                { Icon: ClipboardCheck, value: "142", label: "Inspections This Month", color: "text-blue-700", bg: "bg-blue-50", border: "border-blue-200" },
                { Icon: AlertTriangle, value: "18", label: "Open Complaints", color: "text-amber-700", bg: "bg-amber-50", border: "border-amber-200" },
                { Icon: Megaphone, value: "3", label: "Active Campaigns", color: "text-green-700", bg: "bg-green-50", border: "border-green-200" },
                { Icon: Users, value: "377K", label: "People Reached", color: "text-sky-700", bg: "bg-sky-50", border: "border-sky-200" },
                { Icon: Siren, value: "Level 2", label: "EOC Status", color: "text-red-700", bg: "bg-red-50", border: "border-red-200" },
              ].map((kpi, i) => (
                <div key={i} className={`${kpi.bg} rounded-lg border ${kpi.border} p-3 text-center`}>
                  <kpi.Icon className={`h-5 w-5 mx-auto mb-1.5 ${kpi.color}`} />
                  <p className={`text-xl font-bold ${kpi.color}`}>{kpi.value}</p>
                  <p className="text-[10px] text-gray-600">{kpi.label}</p>
                </div>
              ))}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                  <Siren className="h-4 w-4" /> Active Outbreaks
                </h3>
                <div className="space-y-2">
                  {[
                    { disease: "Cholera - Budiriro, Harare", cases: 47, deaths: 2, severity: "high" },
                    { disease: "Typhoid - Chitungwiza", cases: 23, deaths: 0, severity: "medium" },
                  ].map((ob, i) => (
                    <div key={i} className="flex items-center justify-between p-2.5 border border-gray-200 rounded-lg">
                      <div>
                        <p className="font-medium text-sm text-gray-900">{ob.disease}</p>
                        <p className="text-xs text-gray-500">{ob.cases} cases, {ob.deaths} deaths</p>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        ob.severity === "high" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
                      }`}>{ob.severity}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                  <AlertTriangle className="h-4 w-4" /> Critical Complaints
                </h3>
                <div className="space-y-2">
                  {[
                    { type: "Water Contamination", location: "Glen Norah Borehole", priority: "critical", days: 1 },
                    { type: "Sewage Overflow", location: "Chitungwiza Unit L", priority: "critical", days: 3 },
                    { type: "Illegal Dumping", location: "Highfield", priority: "high", days: 5 },
                  ].map((c, i) => (
                    <div key={i} className="flex items-center justify-between p-2.5 border border-gray-200 rounded-lg">
                      <div>
                        <p className="font-medium text-sm text-gray-900">{c.type}</p>
                        <p className="text-xs text-gray-500">{c.location} -- {c.days}d open</p>
                      </div>
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${
                        c.priority === "critical" ? "bg-red-100 text-red-700" : "bg-amber-100 text-amber-700"
                      }`}>{c.priority}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                  <Megaphone className="h-4 w-4" /> Campaign Progress
                </h3>
                <div className="space-y-3">
                  {[
                    { name: "COVID-19 Booster", target: 500000, reached: 234567 },
                    { name: "School Deworming", target: 120000, reached: 98000 },
                    { name: "Cholera Vaccination - Harare", target: 200000, reached: 45000 },
                  ].map((c, i) => {
                    const pct = Math.round((c.reached / c.target) * 100);
                    return (
                      <div key={i}>
                        <div className="flex justify-between text-xs mb-1">
                          <span className="font-medium text-gray-900">{c.name}</span>
                          <span className="text-gray-600">{pct}%</span>
                        </div>
                        <div className="w-full bg-gray-200 rounded-full h-2">
                          <div className="h-2 rounded-full bg-green-500" style={{ width: `${pct}%` }} />
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2 mb-3">
                  <Radio className="h-4 w-4" /> Surveillance Reporting
                </h3>
                <div className="space-y-3">
                  {[
                    { label: "Weekly IDSR Completeness", value: 89, target: 80 },
                    { label: "Timeliness of Reporting", value: 76, target: 80 },
                    { label: "Case Investigation Rate", value: 92, target: 90 },
                    { label: "Lab Confirmation Rate", value: 68, target: 75 },
                  ].map((m, i) => (
                    <div key={i}>
                      <div className="flex justify-between text-xs mb-1">
                        <span className="font-medium text-gray-900">{m.label}</span>
                        <span className={m.value >= m.target ? "text-green-700" : "text-amber-700"}>
                          {m.value}% (target: {m.target}%)
                        </span>
                      </div>
                      <div className="w-full bg-gray-200 rounded-full h-2">
                        <div className={`h-2 rounded-full ${m.value >= m.target ? "bg-green-500" : "bg-amber-500"}`} style={{ width: `${m.value}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}

        {activeTab === "surveillance" && <SurveillanceTab />}
        {activeTab === "outbreaks" && <OutbreaksTab />}
        {activeTab === "inspections" && <InspectionsTab />}
        {activeTab === "complaints" && <ComplaintsTab />}
        {activeTab === "campaigns" && <CampaignsTab />}
        {activeTab === "field" && <FieldOperationsTab />}
        {activeTab === "emergency" && <EmergencyCoordinationTab />}
      </PageShell>
    </AppLayout>
  );
}
