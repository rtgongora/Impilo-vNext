"use client";

import { useState } from "react";
import { Loader2, Megaphone, MapPin, Package, Plus, Send, Users, TrendingUp } from "lucide-react";
import {
  useCreatePublicHealthCampaign,
  useDispatchPublicHealthCampaign,
  usePublicHealthCampaigns,
  usePublicHealthSites,
} from "@/hooks/queries/usePublicHealth";
import { formatPublicHealthCompact } from "./publicHealthDashboardUtils";
import {
  countActivePublicHealthCampaigns,
  sumCampaignsReachedPopulation,
  weightedCampaignCoveragePercent,
} from "./publicHealthCampaignKpis";

export function CampaignsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"campaigns" | "coverage" | "supply">("campaigns");
  const [showPlanForm, setShowPlanForm] = useState(false);
  const [form, setForm] = useState({
    name: "",
    campaign_type: "Immunization",
    jurisdiction: "",
    status: "planning",
    target_population: "",
  });

  const { data: campaigns = [], isLoading: campLoading, isError: campError } = usePublicHealthCampaigns();
  const { data: sites = [], isLoading: sitesLoading, isError: sitesError } = usePublicHealthSites();
  const createCampaign = useCreatePublicHealthCampaign();
  const dispatchCampaign = useDispatchPublicHealthCampaign();

  const activeCount = countActivePublicHealthCampaigns(campaigns);
  const reachedSum = sumCampaignsReachedPopulation(campaigns);
  const coveragePct = weightedCampaignCoveragePercent(campaigns);
  const siteCount = sites.length;

  const kpis = [
    { label: "Active campaigns", value: String(activeCount), Icon: Megaphone, hint: "From campaigns-service via BFF" },
    {
      label: "People reached",
      value: formatPublicHealthCompact(reachedSum),
      Icon: Users,
      hint: "Sum of reached_population across returned campaigns",
    },
    {
      label: "Indawo sites",
      value: sitesLoading ? "…" : String(siteCount),
      Icon: MapPin,
      hint: "Premises registry (not per-campaign site coverage)",
    },
    { label: "Field teams", value: "—", Icon: Users, hint: "No workforce/team endpoint in public-health BFF yet" },
    {
      label: "Overall coverage",
      value: coveragePct != null ? `${coveragePct}%` : "—",
      Icon: TrendingUp,
      hint: coveragePct != null ? "Weighted reached ÷ target (campaign list)" : "No target_population on campaigns",
    },
  ];

  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-200 bg-blue-50/80 p-3 text-xs text-blue-900">
        <strong>Live data:</strong> Campaign registry, plan, and dispatch use{" "}
        <code className="text-[10px]">GET/POST /internal/v1/public-health/campaigns</code> (Experience BFF →
        campaigns-service). <strong>Site Coverage</strong> and <strong>Supply &amp; Logistics</strong> sub-tabs are not
        backed by repository endpoints yet — demo tables were removed to avoid fake operational data.
      </div>

      {(campError || sitesError) && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-950">
          {campError && <p>Could not load campaigns.</p>}
          {sitesError && <p>Could not load Indawo sites for the sites KPI.</p>}
        </div>
      )}

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
        {kpis.map((kpi) => (
          <div key={kpi.label} className="bg-white rounded-lg border border-gray-200 p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-blue-50">
              <kpi.Icon className="h-4 w-4 text-blue-600" />
            </div>
            <div className="min-w-0">
              <p className="text-lg font-bold text-gray-900 truncate">{kpi.value}</p>
              <p className="text-[10px] text-gray-500">{kpi.label}</p>
              <p className="text-[9px] text-gray-400 mt-0.5 leading-tight">{kpi.hint}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "campaigns" as const, label: "Campaign Registry" },
          { key: "coverage" as const, label: "Site Coverage" },
          { key: "supply" as const, label: "Supply & Logistics" },
        ].map((tab) => (
          <button
            key={tab.key}
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

      {activeSubTab === "campaigns" && (
        <div className="bg-white rounded-lg border border-gray-200">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-gray-900">Campaign Registry</h4>
            <button
              type="button"
              onClick={() => setShowPlanForm((v) => !v)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-lg hover:bg-blue-700"
            >
              <Plus className="h-3.5 w-3.5" /> Plan campaign
            </button>
          </div>

          {showPlanForm && (
            <div className="p-4 border-b border-gray-100 bg-slate-50 space-y-3">
              <p className="text-xs text-gray-600">
                Submits to <code className="text-[10px]">POST /internal/v1/public-health/campaigns</code>. Fields must
                match campaigns-service expectations; adjust if the upstream contract differs.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <label className="text-xs font-medium text-gray-700 block">
                  Name
                  <input
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
                    placeholder="Campaign name"
                  />
                </label>
                <label className="text-xs font-medium text-gray-700 block">
                  Type
                  <input
                    value={form.campaign_type}
                    onChange={(e) => setForm({ ...form, campaign_type: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-gray-700 block">
                  Jurisdiction
                  <input
                    value={form.jurisdiction}
                    onChange={(e) => setForm({ ...form, jurisdiction: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
                    placeholder="e.g. Harare"
                  />
                </label>
                <label className="text-xs font-medium text-gray-700 block">
                  Status
                  <select
                    value={form.status}
                    onChange={(e) => setForm({ ...form, status: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
                  >
                    <option value="planning">planning</option>
                    <option value="active">active</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-gray-700 block md:col-span-2">
                  Target population
                  <input
                    value={form.target_population}
                    onChange={(e) => setForm({ ...form, target_population: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-gray-300 rounded text-sm"
                    placeholder="e.g. 50000"
                  />
                </label>
              </div>
              <button
                type="button"
                disabled={!form.name.trim() || createCampaign.isPending}
                onClick={() => {
                  const body: Record<string, string> = {
                    name: form.name.trim(),
                    campaign_type: form.campaign_type.trim(),
                    jurisdiction: form.jurisdiction.trim(),
                    status: form.status,
                  };
                  if (form.target_population.trim()) body.target_population = form.target_population.trim();
                  createCampaign.mutate(body, {
                    onSuccess: () => {
                      setShowPlanForm(false);
                      setForm({
                        name: "",
                        campaign_type: "Immunization",
                        jurisdiction: "",
                        status: "planning",
                        target_population: "",
                      });
                    },
                  });
                }}
                className="inline-flex items-center gap-1 px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-lg disabled:opacity-50"
              >
                {createCampaign.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
                Create campaign
              </button>
              {createCampaign.isError && (
                <p className="text-xs text-red-600">Create failed — check BFF logs or upstream validation.</p>
              )}
            </div>
          )}

          <div className="p-4 space-y-3">
            {campLoading ? (
              <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading campaigns…
              </div>
            ) : campaigns.length === 0 ? (
              <p className="text-sm text-gray-500 py-6 text-center">No campaigns returned (empty or service unavailable).</p>
            ) : (
              campaigns.map((cam) => {
                const pct =
                  cam.targetPopulation > 0
                    ? Math.min(100, Math.round((cam.reachedPopulation / cam.targetPopulation) * 100))
                    : 0;
                return (
                  <div key={cam.id} className="p-4 border border-gray-200 rounded-lg">
                    <div className="flex items-center justify-between mb-2 gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="font-semibold text-sm text-gray-900 truncate">{cam.name}</p>
                          <span className="px-2 py-0.5 border border-gray-300 rounded text-[10px] shrink-0">
                            {cam.campaignType}
                          </span>
                        </div>
                        <p className="text-xs text-gray-500 mt-0.5">
                          {cam.jurisdiction}
                          {cam.startDate ? ` · ${cam.startDate}` : ""}
                          {cam.endDate ? ` → ${cam.endDate}` : ""}
                        </p>
                      </div>
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-medium shrink-0 ${
                          cam.status.toLowerCase() === "active"
                            ? "bg-green-100 text-green-700"
                            : cam.status.toLowerCase() === "completed"
                              ? "bg-blue-100 text-blue-700"
                              : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {cam.status}
                      </span>
                    </div>
                    <div className="flex flex-col sm:flex-row sm:items-center gap-3">
                      <div className="flex-1 bg-gray-200 rounded-full h-2.5 min-w-0">
                        <div className="h-2.5 rounded-full bg-green-500" style={{ width: `${pct}%` }} />
                      </div>
                      <span className="text-sm font-bold tabular-nums sm:w-36 text-right">
                        {formatPublicHealthCompact(cam.reachedPopulation)} /{" "}
                        {formatPublicHealthCompact(cam.targetPopulation)}
                      </span>
                      <span className="text-sm font-bold w-12 text-right">{cam.targetPopulation > 0 ? `${pct}%` : "—"}</span>
                      <button
                        type="button"
                        disabled={!cam.id || dispatchCampaign.isPending}
                        onClick={() => dispatchCampaign.mutate(cam.id)}
                        className="inline-flex items-center gap-1 px-2 py-1 border border-gray-300 rounded text-[10px] font-medium hover:bg-gray-50 disabled:opacity-50"
                      >
                        <Send className="h-3 w-3" /> Dispatch
                      </button>
                    </div>
                  </div>
                );
              })
            )}
            {dispatchCampaign.isError && (
              <p className="text-xs text-red-600 px-4 pb-4">Dispatch request failed — verify campaign id and campaigns-service.</p>
            )}
          </div>
        </div>
      )}

      {activeSubTab === "coverage" && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <MapPin className="h-10 w-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Site-level campaign coverage</h4>
          <p className="text-xs text-gray-600 mt-2 max-w-lg mx-auto">
            Per-campaign site vaccination rows and ward breakdowns are <strong>not</strong> exposed on{" "}
            <code className="text-[10px]">/internal/v1/public-health/*</code> today. Use the live{" "}
            <strong>Campaign Registry</strong> for aggregate targets and the <strong>Field Operations</strong> tab for
            Indawo premises. This sub-tab stays empty until a real list endpoint exists.
          </p>
        </div>
      )}

      {activeSubTab === "supply" && (
        <div className="bg-white rounded-lg border border-gray-200 p-8 text-center">
          <Package className="h-10 w-10 text-gray-300 mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-gray-900">Supply &amp; logistics</h4>
          <p className="text-xs text-gray-600 mt-2 max-w-lg mx-auto">
            Cold chain and stock balances are <strong>unsupported</strong> on the current public-health BFF contract. No
            placeholder inventory table is shown.
          </p>
        </div>
      )}
    </div>
  );
}
