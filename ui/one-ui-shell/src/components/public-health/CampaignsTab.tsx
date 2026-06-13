"use client";

import { useState } from "react";
import {
  Calendar,
  Loader2,
  MapPin,
  Megaphone,
  Package,
  Plus,
  Send,
  Shield,
  TrendingUp,
  Users,
  X,
} from "lucide-react";
import {
  useCreatePublicHealthCampaign,
  useDispatchPublicHealthCampaign,
  usePublicHealthCampaigns,
  usePublicHealthSites,
} from "@/hooks/queries/usePublicHealth";
import { apiClient } from "@/lib/api-client";
import { formatPublicHealthCompact } from "./publicHealthDashboardUtils";
import {
  countActivePublicHealthCampaigns,
  sumCampaignsReachedPopulation,
  weightedCampaignCoveragePercent,
} from "./publicHealthCampaignKpis";

const CAMPAIGN_TYPES = [
  "Immunisation",
  "Health education",
  "Screening",
  "Vector control",
  "Water & sanitation",
  "Nutrition",
  "Family planning",
  "HIV/TB",
  "Malaria",
  "NCD",
  "Other",
] as const;

const CAMPAIGN_STATUSES = [
  { value: "PLANNING", label: "Planning" },
  { value: "ACTIVE", label: "Active" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
] as const;

interface NewCampaign {
  campaignType: string;
  campaignName: string;
  description: string;
  targetPopulation: string;
  estimatedReach: string;
  province: string;
  district: string;
  wardCommunity: string;
  startDate: string;
  endDate: string;
  responsibleOfficer: string;
  responsiblePhone: string;
  partnerOrganizations: string;
  budgetAllocated: string;
  status: string;
}

const EMPTY_CAMPAIGN: NewCampaign = {
  campaignType: "",
  campaignName: "",
  description: "",
  targetPopulation: "",
  estimatedReach: "",
  province: "",
  district: "",
  wardCommunity: "",
  startDate: "",
  endDate: "",
  responsibleOfficer: "",
  responsiblePhone: "",
  partnerOrganizations: "",
  budgetAllocated: "",
  status: "",
};

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

  const [showNewCampaignForm, setShowNewCampaignForm] = useState(false);
  const [campForm, setCampForm] = useState<NewCampaign>(EMPTY_CAMPAIGN);
  const [campSubmitting, setCampSubmitting] = useState(false);
  const [campSubmitted, setCampSubmitted] = useState(false);
  const [campFormError, setCampFormError] = useState<string | null>(null);

  function updateCamp(field: keyof NewCampaign, value: string) {
    setCampForm((f) => ({ ...f, [field]: value }));
  }

  async function handleCampSubmit() {
    if (!campForm.campaignType || !campForm.campaignName) {
      setCampFormError("Campaign type and campaign name are required.");
      return;
    }
    setCampSubmitting(true);
    setCampFormError(null);
    try {
      await apiClient.post("/internal/v1/public-health/campaigns", {
        name: campForm.campaignName,
        description: campForm.description,
        campaignType: campForm.campaignType,
        targetGroup: campForm.targetPopulation || "GENERAL",
        messageTemplate: JSON.stringify({
          province: campForm.province,
          district: campForm.district,
          wardCommunity: campForm.wardCommunity,
          startDate: campForm.startDate,
          endDate: campForm.endDate,
          responsibleOfficer: campForm.responsibleOfficer,
          estimatedReach: Number(campForm.estimatedReach) || 0,
          budgetAllocated: Number(campForm.budgetAllocated) || 0,
        }),
        channel: "SMS",
        status: campForm.status || "PLANNING",
      });
      setCampSubmitted(true);
    } catch (err) {
      setCampFormError(err instanceof Error ? err.message : "Failed to create campaign.");
      setCampSubmitting(false);
      return;
    }
    setCampSubmitting(false);
    setCampForm(EMPTY_CAMPAIGN);
    setTimeout(() => { setCampSubmitted(false); setShowNewCampaignForm(false); }, 3000);
  }

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
      {/* Header + New Campaign button */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
            <Megaphone className="w-5 h-5 text-amber-600" /> Campaigns & Outreach
          </h3>
          <p className="text-xs text-muted-foreground mt-0.5">
            Plan, record, and manage public health campaigns and community outreach programmes
          </p>
        </div>
        <button
          onClick={() => { setShowNewCampaignForm(!showNewCampaignForm); setCampSubmitted(false); setCampFormError(null); }}
          className="flex items-center gap-1.5 px-4 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 transition-colors"
        >
          {showNewCampaignForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          {showNewCampaignForm ? "Cancel" : "Record New Campaign"}
        </button>
      </div>

      {/* Success banner */}
      {campSubmitted && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center gap-2">
          <Shield className="w-4 h-4" /> Campaign recorded successfully and submitted for approval.
        </div>
      )}

      {/* ═══ NEW CAMPAIGN FORM ═══ */}
      {showNewCampaignForm && (
        <div className="bg-card rounded-xl border-2 border-warning/35 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-warning-foreground flex items-center gap-2">
            <Megaphone className="w-4 h-4" /> New Campaign Registration
          </h4>

          {campFormError && (
            <div className="p-3 bg-danger-soft border border-danger/28 rounded-lg text-sm text-danger">{campFormError}</div>
          )}

          {/* Section 1: Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">1. Campaign Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Campaign Type *</span>
                <select value={campForm.campaignType} onChange={(e) => updateCamp("campaignType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select type...</option>
                  {CAMPAIGN_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Status</span>
                <select value={campForm.status} onChange={(e) => updateCamp("status", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select status...</option>
                  {CAMPAIGN_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Campaign Name / Title *</span>
              <input value={campForm.campaignName} onChange={(e) => updateCamp("campaignName", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. National Measles-Rubella Catch-up Campaign 2026" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Description / Objectives</span>
              <textarea value={campForm.description} onChange={(e) => updateCamp("description", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm resize-none" placeholder="Campaign objectives, target outcomes, and key strategies..." />
            </label>
          </fieldset>

          {/* Section 2: Target & Reach */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Users className="w-3.5 h-3.5" /> 2. Target Population & Reach
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Target Population</span>
                <input value={campForm.targetPopulation} onChange={(e) => updateCamp("targetPopulation", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Children 6 months - 15 years" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Estimated Reach</span>
                <input type="number" min="0" value={campForm.estimatedReach} onChange={(e) => updateCamp("estimatedReach", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. 500000" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Location */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 3. Geographic Coverage
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Province</span>
                <select value={campForm.province} onChange={(e) => updateCamp("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">District</span>
                <input value={campForm.district} onChange={(e) => updateCamp("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Ward / Community</span>
                <input value={campForm.wardCommunity} onChange={(e) => updateCamp("wardCommunity", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Mbare, Epworth" />
              </label>
            </div>
          </fieldset>

          {/* Section 4: Dates */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> 4. Campaign Period
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Start Date</span>
                <input type="date" value={campForm.startDate} onChange={(e) => updateCamp("startDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">End Date</span>
                <input type="date" value={campForm.endDate} onChange={(e) => updateCamp("endDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 5: Responsible Officer & Partners */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">5. Responsible Officer & Partners</legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Responsible Officer</span>
                <input value={campForm.responsibleOfficer} onChange={(e) => updateCamp("responsibleOfficer", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Phone</span>
                <input value={campForm.responsiblePhone} onChange={(e) => updateCamp("responsiblePhone", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="+263 7..." />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Budget Allocated (USD)</span>
                <input type="number" min="0" value={campForm.budgetAllocated} onChange={(e) => updateCamp("budgetAllocated", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. 150000" />
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Partner Organizations</span>
              <input value={campForm.partnerOrganizations} onChange={(e) => updateCamp("partnerOrganizations", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. WHO, UNICEF, Clinton Health Access Initiative" />
            </label>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-muted-foreground">* Required fields</p>
            <button
              onClick={handleCampSubmit}
              disabled={campSubmitting}
              className="flex items-center gap-2 px-6 py-2.5 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors"
            >
              {campSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Megaphone className="w-4 h-4" />}
              {campSubmitting ? "Submitting..." : "Submit Campaign Registration"}
            </button>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-primary/25 bg-primary-soft/80 p-3 text-xs text-impilo-800">
        <strong>Live data:</strong> Campaign registry, plan, and dispatch use{" "}
        <code className="text-[10px]">GET/POST /internal/v1/public-health/campaigns</code> (Experience BFF →
        campaigns-service). <strong>Site Coverage</strong> and <strong>Supply &amp; Logistics</strong> sub-tabs are not
        backed by repository endpoints yet — demo tables were removed to avoid fake operational data.
      </div>

      {(campError || sitesError) && (
        <div className="rounded-lg border border-warning/35 bg-warning-soft p-3 text-xs text-warning-foreground">
          {campError && <p>Could not load campaigns.</p>}
          {sitesError && <p>Could not load Indawo sites for the sites KPI.</p>}
        </div>
      )}

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-3">
        {kpis.map((kpi) => (
          <div key={kpi.label} className="bg-card rounded-lg border border-border p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-primary-soft">
              <kpi.Icon className="h-4 w-4 text-primary" />
            </div>
            <div className="min-w-0">
              <p className="text-lg font-bold text-foreground truncate">{kpi.value}</p>
              <p className="text-[10px] text-muted-foreground">{kpi.label}</p>
              <p className="text-[9px] text-muted-foreground mt-0.5 leading-tight">{kpi.hint}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-1 border-b border-border">
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
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeSubTab === "campaigns" && (
        <div className="bg-card rounded-lg border border-border">
          <div className="px-4 py-3 border-b flex items-center justify-between">
            <h4 className="text-sm font-semibold text-foreground">Campaign Registry</h4>
            <button
              type="button"
              onClick={() => setShowPlanForm((v) => !v)}
              className="inline-flex items-center gap-1 px-3 py-1.5 bg-primary text-white text-xs font-medium rounded-lg hover:bg-primary-hover"
            >
              <Plus className="h-3.5 w-3.5" /> Plan campaign
            </button>
          </div>

          {showPlanForm && (
            <div className="p-4 border-b border-border bg-background space-y-3">
              <p className="text-xs text-muted-foreground">
                Submits to <code className="text-[10px]">POST /internal/v1/public-health/campaigns</code>. Fields must
                match campaigns-service expectations; adjust if the upstream contract differs.
              </p>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <label className="text-xs font-medium text-foreground block">
                  Name
                  <input
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-border rounded text-sm"
                    placeholder="Campaign name"
                  />
                </label>
                <label className="text-xs font-medium text-foreground block">
                  Type
                  <input
                    value={form.campaign_type}
                    onChange={(e) => setForm({ ...form, campaign_type: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-border rounded text-sm"
                  />
                </label>
                <label className="text-xs font-medium text-foreground block">
                  Jurisdiction
                  <input
                    value={form.jurisdiction}
                    onChange={(e) => setForm({ ...form, jurisdiction: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-border rounded text-sm"
                    placeholder="e.g. Harare"
                  />
                </label>
                <label className="text-xs font-medium text-foreground block">
                  Status
                  <select
                    value={form.status}
                    onChange={(e) => setForm({ ...form, status: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-border rounded text-sm"
                  >
                    <option value="planning">planning</option>
                    <option value="active">active</option>
                  </select>
                </label>
                <label className="text-xs font-medium text-foreground block md:col-span-2">
                  Target population
                  <input
                    value={form.target_population}
                    onChange={(e) => setForm({ ...form, target_population: e.target.value })}
                    className="mt-1 w-full px-2 py-1.5 border border-border rounded text-sm"
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
              <div className="flex items-center gap-2 text-sm text-muted-foreground py-8 justify-center">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading campaigns…
              </div>
            ) : campaigns.length === 0 ? (
              <p className="text-sm text-muted-foreground py-6 text-center">No campaigns returned (empty or service unavailable).</p>
            ) : (
              campaigns.map((cam) => {
                const pct =
                  cam.targetPopulation > 0
                    ? Math.min(100, Math.round((cam.reachedPopulation / cam.targetPopulation) * 100))
                    : 0;
                return (
                  <div key={cam.id} className="p-4 border border-border rounded-lg">
                    <div className="flex items-center justify-between mb-2 gap-2">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="font-semibold text-sm text-foreground truncate">{cam.name}</p>
                          <span className="px-2 py-0.5 border border-border rounded text-[10px] shrink-0">
                            {cam.campaignType}
                          </span>
                        </div>
                        <p className="text-xs text-muted-foreground mt-0.5">
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
                              ? "bg-primary-soft text-primary"
                              : "bg-neutral-100 text-muted-foreground"
                        }`}
                      >
                        {cam.status}
                      </span>
                    </div>
                    <div className="flex flex-col sm:flex-row sm:items-center gap-3">
                      <div className="flex-1 bg-neutral-100 rounded-full h-2.5 min-w-0">
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
                        className="inline-flex items-center gap-1 px-2 py-1 border border-border rounded text-[10px] font-medium hover:bg-background disabled:opacity-50"
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
        <div className="bg-card rounded-lg border border-border p-8 text-center">
          <MapPin className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-foreground">Site-level campaign coverage</h4>
          <p className="text-xs text-muted-foreground mt-2 max-w-lg mx-auto">
            Per-campaign site vaccination rows and ward breakdowns are <strong>not</strong> exposed on{" "}
            <code className="text-[10px]">/internal/v1/public-health/*</code> today. Use the live{" "}
            <strong>Campaign Registry</strong> for aggregate targets and the <strong>Field Operations</strong> tab for
            Indawo premises. This sub-tab stays empty until a real list endpoint exists.
          </p>
        </div>
      )}

      {activeSubTab === "supply" && (
        <div className="bg-card rounded-lg border border-border p-8 text-center">
          <Package className="h-10 w-10 text-muted-foreground mx-auto mb-3" />
          <h4 className="text-sm font-semibold text-foreground">Supply &amp; logistics</h4>
          <p className="text-xs text-muted-foreground mt-2 max-w-lg mx-auto">
            Cold chain and stock balances are <strong>unsupported</strong> on the current public-health BFF contract. No
            placeholder inventory table is shown.
          </p>
        </div>
      )}
    </div>
  );
}
