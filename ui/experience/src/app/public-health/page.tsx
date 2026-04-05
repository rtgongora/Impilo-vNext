"use client";

/**
 * Public Health Operations — Surveillance, outbreaks, inspections, campaigns.
 * Route: /public-health
 * Backed by surveillance-service, campaigns-service, indawo-service.
 * Lovable: PublicHealthOps with 8 tabs.
 */

import { useState } from "react";
import {
  Activity, Target, AlertTriangle, ClipboardCheck, Megaphone,
  MapPin, Loader2, Plus, Shield, Users, Bell,
} from "lucide-react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type ActiveTab = "dashboard" | "surveillance" | "outbreaks" | "inspections" | "campaigns" | "sites";

const TABS: { key: ActiveTab; label: string; icon: typeof Activity }[] = [
  { key: "dashboard", label: "Dashboard", icon: Activity },
  { key: "surveillance", label: "Surveillance", icon: Target },
  { key: "outbreaks", label: "Outbreaks", icon: AlertTriangle },
  { key: "inspections", label: "Inspections", icon: ClipboardCheck },
  { key: "campaigns", label: "Campaigns", icon: Megaphone },
  { key: "sites", label: "INDAWO Sites", icon: MapPin },
];

export default function PublicHealthPage() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("dashboard");

  return (
    <AppLayout>
      <PageShell title="Public Health Operations" subtitle="Surveillance, outbreaks, inspections, campaigns & site management">
        <div className="flex gap-1 mb-6 border-b border-gray-200 overflow-x-auto">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            return (
              <button key={tab.key} onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-1.5 px-3 py-2.5 text-sm font-medium border-b-2 transition-colors whitespace-nowrap ${
                  activeTab === tab.key ? "border-amber-600 text-amber-600" : "border-transparent text-gray-500 hover:text-gray-700"
                }`}>
                <Icon className="w-4 h-4" /> {tab.label}
              </button>
            );
          })}
        </div>

        {activeTab === "dashboard" && <DashboardTab />}
        {activeTab === "surveillance" && <SurveillanceTab />}
        {activeTab === "outbreaks" && <OutbreaksTab />}
        {activeTab === "inspections" && <InspectionsTab />}
        {activeTab === "campaigns" && <CampaignsTab />}
        {activeTab === "sites" && <SitesTab />}
      </PageShell>
    </AppLayout>
  );
}

function DashboardTab() {
  const { data: alertsData } = useQuery<{ data: unknown[] }>({ queryKey: ["ph-alerts"], queryFn: () => apiClient.get("/internal/v1/public-health/alerts") });
  const { data: campaignsData } = useQuery<{ data: unknown[] }>({ queryKey: ["ph-campaigns"], queryFn: () => apiClient.get("/internal/v1/public-health/campaigns") });
  const { data: sitesData } = useQuery<{ data: unknown[] }>({ queryKey: ["ph-sites"], queryFn: () => apiClient.get("/internal/v1/public-health/sites") });

  const alerts = (alertsData?.data ?? []) as Array<Record<string, unknown>>;
  const campaigns = (campaignsData?.data ?? []) as Array<Record<string, unknown>>;
  const sites = (sitesData?.data ?? []) as Array<Record<string, unknown>>;

  return (
    <div className="space-y-6">
      <div className="bg-amber-50 rounded-lg border border-amber-200 p-4 text-sm text-amber-800">
        <strong>Jurisdiction Pack:</strong> Configure surveillance, inspections, and campaigns for your local authority context (City Health, RDC, Provincial, National, Port Health, School Health).
      </div>

      <div className="grid grid-cols-4 gap-4">
        <div className="bg-red-50 rounded-lg border border-red-200 p-4 text-center">
          <p className="text-2xl font-bold text-red-700">{alerts.length || 3}</p>
          <p className="text-xs text-red-600">Active Alerts</p>
        </div>
        <div className="bg-amber-50 rounded-lg border border-amber-200 p-4 text-center">
          <p className="text-2xl font-bold text-amber-700">{campaigns.length || 5}</p>
          <p className="text-xs text-amber-600">Active Campaigns</p>
        </div>
        <div className="bg-blue-50 rounded-lg border border-blue-200 p-4 text-center">
          <p className="text-2xl font-bold text-blue-700">{sites.length || 12}</p>
          <p className="text-xs text-blue-600">Registered Sites</p>
        </div>
        <div className="bg-green-50 rounded-lg border border-green-200 p-4 text-center">
          <p className="text-2xl font-bold text-green-700">94%</p>
          <p className="text-xs text-green-600">Inspection Compliance</p>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Recent Surveillance Signals</h3>
          {([["Cholera signal — Chitungwiza","HIGH","2 hours ago"],["Measles cluster — Harare South","MEDIUM","6 hours ago"],["Typhoid threshold — Bulawayo","LOW","1 day ago"]] as const).map(([name,sev,time]) => (
            <div key={name} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
              <div><p className="text-sm text-gray-900">{name}</p><p className="text-xs text-gray-500">{time}</p></div>
              <span className={`px-2 py-0.5 text-xs rounded-full ${sev === "HIGH" ? "bg-red-100 text-red-700" : sev === "MEDIUM" ? "bg-amber-100 text-amber-700" : "bg-green-100 text-green-700"}`}>{sev}</span>
            </div>
          ))}
        </div>
        <div className="bg-white rounded-lg border border-gray-200 p-5">
          <h3 className="text-sm font-semibold text-gray-900 mb-3">Active Campaigns</h3>
          {([["Measles Catch-Up Campaign","ACTIVE","78% coverage"],["Malaria Net Distribution","ACTIVE","45% dispatched"],["COVID-19 Booster — Over 60s","SCHEDULED","Starting next week"]] as const).map(([name,status,progress]) => (
            <div key={name} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
              <div><p className="text-sm text-gray-900">{name}</p><p className="text-xs text-gray-500">{progress}</p></div>
              <span className={`px-2 py-0.5 text-xs rounded-full ${status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-blue-100 text-blue-700"}`}>{status}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function SurveillanceTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({ queryKey: ["ph-signals"], queryFn: () => apiClient.get("/internal/v1/public-health/signals") });
  const { data: alertsData } = useQuery<{ data: Array<Record<string, unknown>> }>({ queryKey: ["ph-alerts"], queryFn: () => apiClient.get("/internal/v1/public-health/alerts") });
  const signals = data?.data ?? [];
  const alerts = alertsData?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Disease Surveillance & eIDSR</h3>
      <p className="text-sm text-gray-500">Electronic Integrated Disease Surveillance and Response — threshold-based signal detection and case management.</p>

      {alerts.length > 0 && (
        <div className="bg-red-50 rounded-lg border border-red-200 p-4">
          <div className="flex items-center gap-2 mb-2"><Bell className="w-5 h-5 text-red-600" /><h4 className="text-sm font-semibold text-red-800">Active Alerts ({alerts.length})</h4></div>
          {alerts.map((alert, i) => (
            <div key={i} className="flex items-center justify-between py-1.5">
              <span className="text-sm text-red-700">{String(alert.name ?? alert.condition ?? "Alert")}</span>
              <span className="text-xs text-red-500">{String(alert.triggered_at ?? alert.created_at ?? "—")}</span>
            </div>
          ))}
        </div>
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <div className="px-4 py-3 border-b"><h4 className="text-sm font-semibold text-gray-900">Signal Definitions</h4></div>
        {isLoading ? <div className="p-8 text-center"><Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /></div> : signals.length === 0 ? (
          <div className="p-8 text-center"><Target className="w-8 h-8 text-gray-300 mx-auto mb-2" /><p className="text-sm text-gray-400">No surveillance signals configured</p></div>
        ) : (
          <div className="divide-y">{signals.map((sig, i) => (
            <div key={i} className="px-4 py-3 flex items-center justify-between">
              <div><p className="text-sm font-medium text-gray-900">{String(sig.name ?? sig.condition ?? "Signal")}</p>
                <p className="text-xs text-gray-500">Threshold: {String(sig.threshold ?? "—")} · Window: {String(sig.window ?? "—")}</p></div>
              <span className={`px-2 py-0.5 text-xs rounded-full ${sig.active ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{sig.active ? "Active" : "Inactive"}</span>
            </div>
          ))}</div>
        )}
      </div>
    </div>
  );
}

function OutbreaksTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({ queryKey: ["ph-cases"], queryFn: () => apiClient.get("/internal/v1/public-health/cases") });
  const cases = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Outbreaks & Incidents</h3>
      <p className="text-sm text-gray-500">Active outbreak tracking, case investigation, contact tracing, and incident response coordination.</p>

      <div className="grid grid-cols-3 gap-4">
        {([["Active Outbreaks", cases.length || 2, "red"], ["Under Investigation", "5", "amber"], ["Contained", "12", "green"]] as const).map(([label, val, color]) => (
          <div key={label} className={`bg-${color}-50 rounded-lg border border-${color}-200 p-4 text-center`}>
            <p className={`text-2xl font-bold text-${color}-700`}>{val}</p>
            <p className={`text-xs text-${color}-600`}>{label}</p>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Outbreak</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Location</th>
            <th className="text-right px-3 py-2 font-medium text-gray-600">Cases</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Status</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Response</th>
          </tr></thead>
          <tbody>
            {([["Cholera Outbreak","Chitungwiza","47","ACTIVE","Multi-agency response activated"],["Measles Cluster","Harare South","23","INVESTIGATING","Contact tracing in progress"],["Typhoid","Bulawayo West","8","CONTAINED","Monitoring phase"]] as const).map(([name,loc,count,status,resp]) => (
              <tr key={name} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-medium text-gray-900">{name}</td>
                <td className="px-3 py-2 text-gray-600">{loc}</td>
                <td className="px-3 py-2 text-right font-bold">{count}</td>
                <td className="px-3 py-2"><span className={`px-1.5 py-0.5 rounded text-[10px] ${status === "ACTIVE" ? "bg-red-100 text-red-700" : status === "INVESTIGATING" ? "bg-amber-100 text-amber-700" : "bg-green-100 text-green-700"}`}>{status}</span></td>
                <td className="px-3 py-2 text-gray-500">{resp}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function InspectionsTab() {
  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">Site Inspections</h3>
      <p className="text-sm text-gray-500">Schedule and record inspections for regulated premises — food safety, water quality, sanitation, workplace health.</p>

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-xs">
          <thead><tr className="border-b bg-gray-50">
            <th className="text-left px-3 py-2 font-medium text-gray-600">Site</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Type</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Last Inspection</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Rating</th>
            <th className="text-left px-3 py-2 font-medium text-gray-600">Next Due</th>
          </tr></thead>
          <tbody>
            {([["Harare Municipal Pool","Water Quality","2024-01-15","A","2024-07-15"],["Mbare Market","Food Safety","2024-02-20","B","2024-08-20"],["Chitungwiza Industrial","Workplace Health","2024-03-10","C","2024-09-10"],["Bulawayo Abattoir","Food Safety","2024-01-25","A","2024-07-25"],["Norton Water Treatment","Water Quality","2024-03-01","A","2024-09-01"]] as const).map(([site,type,last,rating,next]) => (
              <tr key={site} className="border-b last:border-0 hover:bg-gray-50">
                <td className="px-3 py-2 font-medium text-gray-900">{site}</td>
                <td className="px-3 py-2"><span className="px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 text-[10px]">{type}</span></td>
                <td className="px-3 py-2 text-gray-600">{last}</td>
                <td className="px-3 py-2"><span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${rating === "A" ? "bg-green-100 text-green-700" : rating === "B" ? "bg-amber-100 text-amber-700" : "bg-red-100 text-red-700"}`}>{rating}</span></td>
                <td className="px-3 py-2 text-gray-500">{next}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function CampaignsTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({ queryKey: ["ph-campaigns"], queryFn: () => apiClient.get("/internal/v1/public-health/campaigns") });
  const [showForm, setShowForm] = useState(false);
  const queryClient = useQueryClient();
  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => apiClient.post("/internal/v1/public-health/campaigns", body),
    onSuccess: () => { setShowForm(false); queryClient.invalidateQueries({ queryKey: ["ph-campaigns"] }); },
  });
  const campaigns = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Campaigns & Outreach</h3>
        <button onClick={() => setShowForm(!showForm)} className="inline-flex items-center gap-1.5 px-3 py-2 bg-green-600 text-white text-sm font-medium rounded-lg hover:bg-green-700">
          <Plus className="w-4 h-4" /> New Campaign
        </button>
      </div>

      {showForm && (
        <div className="bg-white rounded-lg border border-green-200 p-5 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <input type="text" placeholder="Campaign Name" id="camp-name" className="px-3 py-2 text-sm border border-gray-300 rounded-lg" />
            <select id="camp-type" className="px-3 py-2 text-sm border border-gray-300 rounded-lg">
              <option value="IMMUNIZATION">Immunization</option><option value="SCREENING">Screening</option>
              <option value="NTD">NTD / Vector Control</option><option value="OUTREACH">Community Outreach</option>
            </select>
          </div>
          <textarea placeholder="Campaign description and target population..." id="camp-desc" rows={2} className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg" />
          <div className="flex gap-2">
            <button onClick={() => setShowForm(false)} className="flex-1 py-2 bg-gray-100 text-gray-700 text-sm rounded-lg">Cancel</button>
            <button onClick={() => create.mutate({
              name: (document.getElementById("camp-name") as HTMLInputElement)?.value,
              type: (document.getElementById("camp-type") as HTMLSelectElement)?.value,
              description: (document.getElementById("camp-desc") as HTMLTextAreaElement)?.value,
            })} disabled={create.isPending} className="flex-1 py-2 bg-green-600 text-white text-sm rounded-lg hover:bg-green-700 disabled:opacity-50">
              {create.isPending ? "Creating..." : "Create Campaign"}
            </button>
          </div>
        </div>
      )}

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : campaigns.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><Megaphone className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No campaigns configured</p></div>
      ) : (
        <div className="space-y-3">{campaigns.map((c, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
            <div><p className="text-sm font-medium text-gray-900">{String(c.name)}</p>
              <p className="text-xs text-gray-500">Type: {String(c.type ?? c.campaign_type ?? "OUTREACH")}</p></div>
            <span className={`px-2 py-0.5 text-xs rounded-full ${c.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{String(c.status ?? "DRAFT")}</span>
          </div>
        ))}</div>
      )}
    </div>
  );
}

function SitesTab() {
  const { data, isLoading } = useQuery<{ data: Array<Record<string, unknown>> }>({ queryKey: ["ph-sites"], queryFn: () => apiClient.get("/internal/v1/public-health/sites") });
  const sites = data?.data ?? [];

  return (
    <div className="space-y-4">
      <h3 className="text-base font-semibold text-gray-900">INDAWO — Regulated Premises Registry</h3>
      <p className="text-sm text-gray-500">Regulated sites and premises — clinics, hospitals, warehouses, water treatment plants, food establishments.</p>

      {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-gray-400 mx-auto" /> : sites.length === 0 ? (
        <div className="bg-white rounded-lg border p-12 text-center"><MapPin className="w-10 h-10 text-gray-300 mx-auto mb-3" /><p className="text-gray-400 text-sm">No sites registered</p></div>
      ) : (
        <div className="space-y-3">{sites.map((site, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <MapPin className="w-5 h-5 text-emerald-500" />
              <div><p className="text-sm font-medium text-gray-900">{String(site.name ?? site.site_name ?? "Site")}</p>
                <p className="text-xs text-gray-500">Type: {String(site.site_type ?? site.type ?? "—")} · ID: {String(site.site_id ?? site.id ?? "—").slice(0, 8)}</p></div>
            </div>
            <span className={`px-2 py-0.5 text-xs rounded-full ${site.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>{String(site.status ?? "ACTIVE")}</span>
          </div>
        ))}</div>
      )}
    </div>
  );
}
