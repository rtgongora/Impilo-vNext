"use client";

/**
 * Public Health & Local Authority Operations
 * Route: /public-health
 * Full jurisdiction-pack system with 8 sub-modules: dashboard, surveillance/eIDSR,
 * outbreaks, inspections, complaints, campaigns, field ops, emergency coordination.
 *
 * Deep links: ?tab=surveillance|outbreaks|…|field|sites|emergency (sites → Field Operations / Indawo).
 */

import { useEffect, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import {
  Activity, AlertTriangle, ClipboardCheck, Megaphone,
  MapPin, Shield, Siren, Settings, Building, TreePine,
  Ship, School, Globe, Target,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { PublicHealthDashboard } from "@/components/public-health/PublicHealthDashboard";
import { SurveillanceTab } from "@/components/public-health/SurveillanceTab";
import { OutbreaksTab } from "@/components/public-health/OutbreaksTab";
import { InspectionsTab } from "@/components/public-health/InspectionsTab";
import { ComplaintsTab } from "@/components/public-health/ComplaintsTab";
import { CampaignsTab } from "@/components/public-health/CampaignsTab";
import { FieldOperationsTab } from "@/components/public-health/FieldOperationsTab";
import { EmergencyCoordinationTab } from "@/components/public-health/EmergencyCoordinationTab";
import {
  type PublicHealthActiveTab,
  publicHealthTabFromSearchParam,
} from "./publicHealthTabParams";

const JURISDICTION_PACKS = [
  { id: "city_health", label: "City Health Pack", description: "Urban municipal public health operations", Icon: Building, color: "bg-impilo-500", activeIn: "Harare, Bulawayo, Mutare, Gweru, Kwekwe, Masvingo" },
  { id: "rdc_health", label: "Rural District Council Health Pack", description: "Rural public health operations and community health", Icon: TreePine, color: "bg-green-500", activeIn: "62 Rural District Councils" },
  { id: "provincial", label: "Provincial Public Health Oversight Pack", description: "Provincial surveillance, coordination, and oversight", Icon: Globe, color: "bg-purple-500", activeIn: "All 10 Provinces" },
  { id: "national", label: "National Public Health Oversight Pack", description: "National surveillance, policy, and coordination", Icon: Shield, color: "bg-red-500", activeIn: "National" },
  { id: "port_health", label: "Port Health Pack", description: "Border and port of entry health operations", Icon: Ship, color: "bg-indigo-500", activeIn: "14 Ports of Entry" },
  { id: "school_health", label: "School Health Pack", description: "School-based health services and inspections", Icon: School, color: "bg-amber-500", activeIn: "1,284 Schools" },
];

const TABS: { key: PublicHealthActiveTab; label: string; Icon: typeof Activity }[] = [
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
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const [activePack, setActivePack] = useState("city_health");
  const [activeTab, setActiveTab] = useState<PublicHealthActiveTab>("dashboard");

  useEffect(() => {
    setActiveTab(publicHealthTabFromSearchParam(searchParams.get("tab")));
  }, [searchParams]);

  function goToTab(next: PublicHealthActiveTab) {
    setActiveTab(next);
    if (next === "dashboard") {
      router.replace(pathname, { scroll: false });
    } else {
      const q = next === "field" ? "field" : next;
      router.replace(`${pathname}?tab=${encodeURIComponent(q)}`, { scroll: false });
    }
  }

  return (
    <AppLayout>
      <PageShell
        title="Public Health & Local Authority Operations"
        subtitle="Shared reusable capability configured for different jurisdictions - not cloned apps"
      >
        {/* Jurisdiction Pack Selector */}
        <div className="mb-6 rounded-lg border border-impilo-200 bg-impilo-50 p-4">
          <div className="flex items-center gap-2 mb-1">
            <Settings className="h-4 w-4 text-impilo-500" />
            <h3 className="text-sm font-semibold text-impilo-800">Active Jurisdiction Pack</h3>
          </div>
          <p className="text-xs text-impilo-600 mb-3">Same platform capabilities, configured per jurisdiction</p>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
            {JURISDICTION_PACKS.map(pack => (
              <button
                key={pack.id}
                onClick={() => setActivePack(pack.id)}
                className={`p-3 rounded-lg border text-left transition-all ${
                  activePack === pack.id
                    ? "border-impilo-400 bg-white ring-2 ring-impilo-200"
                    : "border-gray-200 bg-white hover:border-impilo-200"
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
              onClick={() => goToTab(tab.key)}
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

        {activeTab === "dashboard" && <PublicHealthDashboard />}

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
