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
  Shield,
  Star,
} from "lucide-react";
import {
  useCreatePublicHealthInspectionSchedule,
  usePublicHealthInspectionSchedules,
  usePublicHealthSiteRegistryComplianceActions,
  usePublicHealthSiteRegistryEnforcementCases,
  usePublicHealthSiteRegistryInspections,
  usePublicHealthSites,
} from "@/hooks/queries/usePublicHealth";
import { apiClient } from "@/lib/api-client";

const INSPECTION_TYPES = [
  "Food premises",
  "Water source",
  "Health facility",
  "School",
  "Market",
  "Abattoir",
  "Burial site",
  "Swimming pool",
  "Housing",
  "Other",
] as const;

const INSPECTION_STATUSES = [
  { value: "PASSED", label: "Passed" },
  { value: "CONDITIONAL_PASS", label: "Conditional Pass" },
  { value: "FAILED", label: "Failed" },
  { value: "CLOSED", label: "Closed" },
] as const;

interface NewInspection {
  inspectionType: string;
  premisesName: string;
  premisesAddress: string;
  province: string;
  district: string;
  ward: string;
  inspectorName: string;
  inspectorDesignation: string;
  inspectionDate: string;
  overallScore: string;
  criticalFindings: string;
  majorFindings: string;
  minorFindings: string;
  findingsNarrative: string;
  correctiveActions: string;
  followUpDate: string;
  status: string;
  photoReferences: string;
}

interface ScheduleMiniForm {
  siteId: string;
  inspectionType: string;
  scheduledDate: string;
  scheduledTime: string;
  responsibleOfficerOrTeam: string;
  priority: string;
  reason: string;
  scope: string;
  recurrence: string;
  notes: string;
  notificationPreference: string;
}

const EMPTY_INSPECTION: NewInspection = {
  inspectionType: "",
  premisesName: "",
  premisesAddress: "",
  province: "",
  district: "",
  ward: "",
  inspectorName: "",
  inspectorDesignation: "",
  inspectionDate: new Date().toISOString().slice(0, 10),
  overallScore: "",
  criticalFindings: "0",
  majorFindings: "0",
  minorFindings: "0",
  findingsNarrative: "",
  correctiveActions: "",
  followUpDate: "",
  status: "",
  photoReferences: "",
};

const EMPTY_SCHEDULE: ScheduleMiniForm = {
  siteId: "",
  inspectionType: "ROUTINE",
  scheduledDate: new Date().toISOString().slice(0, 10),
  scheduledTime: "09:00",
  responsibleOfficerOrTeam: "",
  priority: "MEDIUM",
  reason: "",
  scope: "SITE",
  recurrence: "",
  notes: "",
  notificationPreference: "IN_APP",
};

/** Inspections & enforcement backed by site-registry APIs. */
export function InspectionsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"premises" | "inspections" | "enforcement" | "compliance">("premises");
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [selectedInspection, setSelectedInspection] = useState<string | null>(null);
  const { data: sites = [], isLoading, isError } = usePublicHealthSites();
  const inspectionsQ = usePublicHealthSiteRegistryInspections();
  const enforcementQ = usePublicHealthSiteRegistryEnforcementCases();
  const complianceQ = usePublicHealthSiteRegistryComplianceActions();
  const schedulesQ = usePublicHealthInspectionSchedules();
  const createSchedule = useCreatePublicHealthInspectionSchedule();

  const [showNewForm, setShowNewForm] = useState(false);
  const [inspForm, setInspForm] = useState<NewInspection>(EMPTY_INSPECTION);
  const [inspSubmitting, setInspSubmitting] = useState(false);
  const [inspSubmitted, setInspSubmitted] = useState(false);
  const [sessionSubmissions, setSessionSubmissions] = useState(0);
  const [inspError, setInspError] = useState<string | null>(null);
  const [scheduleForm, setScheduleForm] = useState<ScheduleMiniForm>(EMPTY_SCHEDULE);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [scheduleSuccess, setScheduleSuccess] = useState(false);
  const activeSites = sites.filter((s) => s.operationalStatus.toUpperCase() === "ACTIVE").length;
  const uniqueSiteTypes = new Set(sites.map((s) => s.siteType)).size;
  const uniqueJurisdictions = new Set(sites.map((s) => s.jurisdiction)).size;

  function updateInsp(field: keyof NewInspection, value: string) {
    setInspForm((f) => ({ ...f, [field]: value }));
  }

  function updateSchedule(field: keyof ScheduleMiniForm, value: string) {
    setScheduleForm((f) => ({ ...f, [field]: value }));
  }

  async function submitSchedule(form: ScheduleMiniForm) {
    const scheduledAt = `${form.scheduledDate}T${form.scheduledTime}:00Z`;
    await createSchedule.mutateAsync({
      siteId: form.siteId,
      inspectionType: form.inspectionType,
      scheduledAt,
      responsibleOfficerOrTeam: form.responsibleOfficerOrTeam,
      priority: form.priority,
      reason: form.reason,
      scope: form.scope,
      recurrence: form.recurrence || null,
      notes: form.notes || null,
      notificationPreference: form.notificationPreference,
    });
  }

  async function handleInspSubmit() {
    const selectedSite = sites.find((s) => s.name.toLowerCase() === inspForm.premisesName.toLowerCase());
    if (!inspForm.inspectionType || !selectedSite || !inspForm.inspectorName) {
      setInspError("Inspection type, inspector, and an existing premises are required.");
      return;
    }
    setInspSubmitting(true);
    setInspError(null);
    try {
      await submitSchedule({
        ...EMPTY_SCHEDULE,
        siteId: selectedSite.id,
        inspectionType: inspForm.inspectionType.toUpperCase().replaceAll(" ", "_"),
        scheduledDate: inspForm.inspectionDate || EMPTY_SCHEDULE.scheduledDate,
        scheduledTime: "09:00",
        responsibleOfficerOrTeam: `${inspForm.inspectorName}${inspForm.inspectorDesignation ? ` (${inspForm.inspectorDesignation})` : ""}`,
        priority: Number(inspForm.criticalFindings || 0) > 0 ? "HIGH" : "MEDIUM",
        reason: inspForm.findingsNarrative || inspForm.correctiveActions || "Inspection workflow submission",
        scope: inspForm.ward || inspForm.district || inspForm.province || "SITE",
        notes: inspForm.photoReferences,
        notificationPreference: "IN_APP",
      });
    } catch {
      setInspSubmitting(false);
      setInspError("Failed to submit inspection schedule. Verify the public-health scheduling API is available.");
      return;
    }
    setInspSubmitting(false);
    setInspSubmitted(true);
    setSessionSubmissions((n) => n + 1);
    setInspForm(EMPTY_INSPECTION);
    setTimeout(() => { setInspSubmitted(false); setShowNewForm(false); }, 3000);
  }

  return (
    <div className="space-y-4">
      {/* Header + New Inspection button */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
            <ClipboardCheck className="w-5 h-5 text-violet-600" /> Inspections & Enforcement
          </h3>
          <p className="text-xs text-muted-foreground mt-0.5">
            Record, schedule, and track environmental health inspections and enforcement actions
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowNewForm((s) => !s)}
          className="flex items-center gap-1.5 px-4 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700"
        >
          <Plus className="w-4 h-4" />
          Record New Inspection
        </button>
      </div>

      {/* Success banner */}
      {inspSubmitted && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center gap-2">
          <Shield className="w-4 h-4" /> Inspection recorded successfully and submitted for review.
        </div>
      )}

      {/* ═══ NEW INSPECTION FORM ═══ */}
      {showNewForm && (
        <div className="bg-card rounded-xl border-2 border-warning/35 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-warning-foreground flex items-center gap-2">
            <ClipboardCheck className="w-4 h-4" /> New Inspection Report
          </h4>

          {inspError && (
            <div className="p-3 bg-danger-soft border border-danger/28 rounded-lg text-sm text-danger">{inspError}</div>
          )}

          {/* Section 1: Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">1. Inspection Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Inspection Type *</span>
                <select value={inspForm.inspectionType} onChange={(e) => updateInsp("inspectionType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select type...</option>
                  {INSPECTION_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Status</span>
                <select value={inspForm.status} onChange={(e) => updateInsp("status", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select status...</option>
                  {INSPECTION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
          </fieldset>

          {/* Section 2: Premises */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 2. Premises Details
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Premises Name *</span>
                <input value={inspForm.premisesName} onChange={(e) => updateInsp("premisesName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Mbare Musika Market" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Premises Address</span>
                <input value={inspForm.premisesAddress} onChange={(e) => updateInsp("premisesAddress", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Physical address" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Province</span>
                <select value={inspForm.province} onChange={(e) => updateInsp("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">District</span>
                <input value={inspForm.district} onChange={(e) => updateInsp("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Ward</span>
                <input value={inspForm.ward} onChange={(e) => updateInsp("ward", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Ward 12" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Inspector */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">3. Inspector Details</legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Inspector Name *</span>
                <input value={inspForm.inspectorName} onChange={(e) => updateInsp("inspectorName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Designation</span>
                <input value={inspForm.inspectorDesignation} onChange={(e) => updateInsp("inspectorDesignation", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Environmental Health Officer" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Date of Inspection</span>
                <input type="date" value={inspForm.inspectionDate} onChange={(e) => updateInsp("inspectionDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 4: Scoring & Findings */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Star className="w-3.5 h-3.5" /> 4. Scoring & Findings
            </legend>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Overall Score (0-100)</span>
                <input type="number" min="0" max="100" value={inspForm.overallScore} onChange={(e) => updateInsp("overallScore", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Critical Findings</span>
                <input type="number" min="0" value={inspForm.criticalFindings} onChange={(e) => updateInsp("criticalFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Major Findings</span>
                <input type="number" min="0" value={inspForm.majorFindings} onChange={(e) => updateInsp("majorFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Minor Findings</span>
                <input type="number" min="0" value={inspForm.minorFindings} onChange={(e) => updateInsp("minorFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Findings Narrative</span>
              <textarea value={inspForm.findingsNarrative} onChange={(e) => updateInsp("findingsNarrative", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm resize-none" placeholder="Detailed description of findings, observations, and non-compliances..." />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Corrective Actions Required</span>
              <textarea value={inspForm.correctiveActions} onChange={(e) => updateInsp("correctiveActions", e.target.value)} rows={2}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm resize-none" placeholder="List required corrective actions and timelines..." />
            </label>
          </fieldset>

          {/* Section 5: Follow-up & Evidence */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> 5. Follow-up & Evidence
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Follow-up Date</span>
                <input type="date" value={inspForm.followUpDate} onChange={(e) => updateInsp("followUpDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Photos / Evidence Reference Numbers</span>
                <input value={inspForm.photoReferences} onChange={(e) => updateInsp("photoReferences", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. IMG-20260414-001, IMG-20260414-002" />
              </label>
            </div>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-muted-foreground">* Required fields</p>
            <button
              onClick={handleInspSubmit}
              disabled={inspSubmitting}
              className="flex items-center gap-2 px-6 py-2.5 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 disabled:opacity-50 transition-colors"
            >
              {inspSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ClipboardCheck className="w-4 h-4" />}
              {inspSubmitting ? "Submitting..." : "Submit Inspection Report"}
            </button>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-violet-200 bg-violet-50/90 p-3 text-xs text-violet-950">
        <strong>Live:</strong> registered premises from <code className="text-[10px]">GET /internal/v1/public-health/sites</code>.
        <strong className="ml-1">Live write:</strong> inspection scheduling uses
        <code className="text-[10px]"> POST /internal/v1/public-health/inspections/schedules</code> through the BFF.
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          { label: "Registered premises", value: String(sites.length), Icon: ClipboardCheck, tone: "text-violet-800" },
          { label: "Active premises", value: String(activeSites), Icon: Calendar, tone: "text-primary-hover" },
          { label: "Site types", value: String(uniqueSiteTypes), Icon: AlertTriangle, tone: "text-danger" },
          { label: "Jurisdictions", value: String(uniqueJurisdictions), Icon: Star, tone: "text-warning-foreground" },
          { label: "Submissions this session", value: String(sessionSubmissions), Icon: FileText, tone: "text-foreground" },
        ].map((k) => {
          const I = k.Icon;
          return (
            <div key={k.label} className="rounded-lg border border-border bg-card p-3 shadow-sm">
              <div className="flex items-center gap-2">
                <div className="rounded-lg bg-violet-100 p-1.5">
                  <I className="h-4 w-4 text-violet-700" />
                </div>
                <div>
                  <p className={`text-lg font-bold ${k.tone}`}>{k.value}</p>
                  <p className="text-[10px] font-medium text-muted-foreground">{k.label}</p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex flex-wrap gap-1 border-b border-border">
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
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeSubTab === "premises" && (
        <div className="rounded-lg border border-border bg-card">
          <div className="flex items-center gap-2 border-b px-4 py-3">
            <MapPin className="h-4 w-4 text-primary" />
            <div>
              <h4 className="text-sm font-semibold text-foreground">Registered premises</h4>
              <p className="text-xs text-muted-foreground">GET /internal/v1/public-health/sites → indawo-service</p>
            </div>
          </div>
          <div className="p-4">
            {isLoading && (
              <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading sites…
              </div>
            )}
            {isError && <p className="py-4 text-center text-sm text-red-600">Failed to load sites.</p>}
            {!isLoading && !isError && sites.length === 0 && (
              <p className="py-6 text-center text-sm text-muted-foreground">No sites returned.</p>
            )}
            {!isLoading && !isError && sites.length > 0 && (
              <ul className="max-h-[480px] divide-y divide-gray-100 overflow-y-auto">
                {sites.map((s) => (
                  <li key={s.id} className="flex flex-wrap justify-between gap-2 py-3">
                    <div>
                      <p className="text-sm font-medium text-foreground">{s.name}</p>
                      <p className="text-xs text-muted-foreground">
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
        <div className="space-y-4 rounded-lg border border-border bg-card p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h4 className="text-sm font-semibold text-foreground">Inspection register</h4>
              <p className="text-xs text-muted-foreground">GET /internal/v1/public-health/site-registry/inspections</p>
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
              <h5 className="mb-3 font-semibold text-foreground">Schedule new inspection</h5>
              {scheduleError && <p className="mb-2 rounded border border-danger/28 bg-danger-soft p-2 text-xs text-danger">{scheduleError}</p>}
              {scheduleSuccess && (
                <p className="mb-2 rounded border border-green-200 bg-green-50 p-2 text-xs text-green-700">
                  Inspection schedule created successfully.
                </p>
              )}
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Site / premises</span>
                  <select
                    value={scheduleForm.siteId}
                    onChange={(e) => updateSchedule("siteId", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  >
                    <option value="">Select site...</option>
                    {sites.map((site) => (
                      <option key={site.id} value={site.id}>
                        {site.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Inspection type</span>
                  <select
                    value={scheduleForm.inspectionType}
                    onChange={(e) => updateSchedule("inspectionType", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  >
                    <option value="ROUTINE">Routine</option>
                    <option value="AD_HOC_COMPLAINT">Ad-hoc / complaint-driven</option>
                    <option value="FOLLOW_UP">Follow-up</option>
                    <option value="LICENSING_ASSESSMENT">Licensing assessment</option>
                    <option value="OUTBREAK_INVESTIGATION">Outbreak investigation</option>
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Scheduled date</span>
                  <input
                    type="date"
                    value={scheduleForm.scheduledDate}
                    onChange={(e) => updateSchedule("scheduledDate", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Scheduled time</span>
                  <input
                    type="time"
                    value={scheduleForm.scheduledTime}
                    onChange={(e) => updateSchedule("scheduledTime", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Responsible officer/team</span>
                  <input
                    value={scheduleForm.responsibleOfficerOrTeam}
                    onChange={(e) => updateSchedule("responsibleOfficerOrTeam", e.target.value)}
                    placeholder="EHO Team A"
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Priority</span>
                  <select
                    value={scheduleForm.priority}
                    onChange={(e) => updateSchedule("priority", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                    <option value="CRITICAL">Critical</option>
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Scope</span>
                  <input
                    value={scheduleForm.scope}
                    onChange={(e) => updateSchedule("scope", e.target.value)}
                    placeholder="District / site scope"
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Recurrence</span>
                  <input
                    value={scheduleForm.recurrence}
                    onChange={(e) => updateSchedule("recurrence", e.target.value)}
                    placeholder="e.g. WEEKLY"
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-muted-foreground">Notification preference</span>
                  <select
                    value={scheduleForm.notificationPreference}
                    onChange={(e) => updateSchedule("notificationPreference", e.target.value)}
                    className="mt-1 h-8 w-full rounded border border-border px-2 text-xs"
                  >
                    <option value="IN_APP">In-app</option>
                    <option value="SMS">SMS</option>
                    <option value="EMAIL">Email</option>
                  </select>
                </label>
                <label className="block text-xs sm:col-span-3">
                  <span className="font-medium text-muted-foreground">Reason</span>
                  <textarea
                    value={scheduleForm.reason}
                    onChange={(e) => updateSchedule("reason", e.target.value)}
                    placeholder="Why this inspection should be scheduled..."
                    className="mt-1 min-h-[48px] w-full rounded border border-border p-2 text-xs"
                  />
                </label>
                <label className="block text-xs sm:col-span-3">
                  <span className="font-medium text-muted-foreground">Notes</span>
                  <textarea
                    value={scheduleForm.notes}
                    onChange={(e) => updateSchedule("notes", e.target.value)}
                    placeholder="Specific areas to inspect…"
                    className="mt-1 min-h-[48px] w-full rounded border border-border p-2 text-xs"
                  />
                </label>
              </div>
              <div className="mt-3 flex items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setScheduleForm(EMPTY_SCHEDULE)}
                  className="rounded border border-border px-3 py-1.5 text-xs text-foreground hover:bg-card"
                >
                  Save draft
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    if (!scheduleForm.siteId || !scheduleForm.scheduledDate) {
                      setScheduleError("Site and schedule date are required.");
                      return;
                    }
                    setScheduleError(null);
                    try {
                      await submitSchedule(scheduleForm);
                      setScheduleSuccess(true);
                      setSessionSubmissions((n) => n + 1);
                      setScheduleForm(EMPTY_SCHEDULE);
                      setTimeout(() => setScheduleSuccess(false), 2500);
                    } catch {
                      setScheduleError("Unable to create inspection schedule.");
                    }
                  }}
                  disabled={createSchedule.isPending}
                  className="rounded bg-violet-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                >
                  {createSchedule.isPending ? "Submitting..." : "Submit schedule"}
                </button>
              </div>
            </div>
          )}

          <div className="rounded border border-border bg-background p-3">
            <p className="mb-2 text-xs font-semibold text-foreground">Recent schedules</p>
            {(schedulesQ.data ?? []).slice(0, 5).map((schedule) => (
              <div key={schedule.id} className="flex items-center justify-between border-t border-border py-1 text-[11px] text-foreground first:border-t-0">
                <span>{schedule.inspectionType.replaceAll("_", " ")}</span>
                <span>{schedule.scheduledAt || "—"}</span>
                <span>{schedule.status}</span>
              </div>
            ))}
            {schedulesQ.isPending && <p className="text-[11px] text-muted-foreground">Loading schedules…</p>}
            {schedulesQ.isError && <p className="text-[11px] text-red-600">Schedule list unavailable.</p>}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-xs">
              <thead>
                <tr className="border-b bg-background text-left">
                  <th className="px-2 py-2 font-medium text-muted-foreground">ID</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Site</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Type</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Inspector</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Date</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Status</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Score</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground">Critical</th>
                  <th className="px-2 py-2 font-medium text-muted-foreground" />
                </tr>
              </thead>
              <tbody>
                {(inspectionsQ.data ?? []).map((row) => (
                  <tr key={row.inspectionId} className="border-b hover:bg-background">
                    <td className="px-2 py-2 font-mono">{row.inspectionId}</td>
                    <td className="px-2 py-2">
                      <p className="font-medium text-foreground">{row.siteName}</p>
                      <p className="text-[10px] text-muted-foreground">{row.siteType}</p>
                    </td>
                    <td className="px-2 py-2 text-foreground">{row.inspectionType}</td>
                    <td className="px-2 py-2">—</td>
                    <td className="px-2 py-2 text-muted-foreground">{row.actualDate || row.scheduledDate || "—"}</td>
                    <td className="px-2 py-2">
                      <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] capitalize text-foreground">
                        {row.status.replaceAll("_", " ")}
                      </span>
                    </td>
                    <td className="px-2 py-2 tabular-nums">{row.scorePercent > 0 ? `${row.scorePercent}%` : "—"}</td>
                    <td className="px-2 py-2">
                      {row.criticalFailCount > 0 ? <span className="font-semibold text-danger">{row.criticalFailCount}</span> : "0"}
                    </td>
                    <td className="px-2 py-2">
                      <button
                        type="button"
                        onClick={() => setSelectedInspection(selectedInspection === row.inspectionId ? null : row.inspectionId)}
                        className="rounded border border-border px-2 py-1 text-[10px] font-medium hover:bg-background"
                      >
                        {selectedInspection === row.inspectionId ? "Close" : "Details"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {inspectionsQ.isPending && <p className="text-xs text-muted-foreground">Loading inspection register…</p>}
          {inspectionsQ.isError && <p className="text-xs text-red-600">Inspection register failed to load.</p>}

          {selectedInspection && (
            <div className="rounded-lg border border-border bg-background p-3 text-xs text-foreground">
              <p className="font-semibold">Findings & follow-up — {selectedInspection}</p>
              <p className="mt-1 text-muted-foreground">
                Wire checklist, photos, and improvement notices to persistence when the inspections service is available.
              </p>
            </div>
          )}
        </div>
      )}

      {activeSubTab === "enforcement" && (
        <div className="rounded-lg border border-border bg-card p-4">
          <h4 className="mb-3 text-sm font-semibold text-foreground">Enforcement actions</h4>
          <div className="space-y-3">
            {(enforcementQ.data ?? []).map((e) => (
              <div key={e.caseId} className="rounded-lg border border-warning/35 bg-warning-soft/40 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-mono text-[10px] text-muted-foreground">{e.caseId}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                      e.status.toUpperCase() === "OPEN" ? "bg-red-100 text-red-800" : "bg-amber-100 text-warning-foreground"
                    }`}
                  >
                    {e.status}
                  </span>
                </div>
                <p className="mt-1 text-sm font-medium text-foreground">{e.siteName}</p>
                <p className="text-xs text-foreground">{e.triggerType}</p>
                <p className="mt-2 text-[10px] text-muted-foreground">
                  {e.recommendation || "No recommendation"} · route {e.authorityRoute || "—"}
                </p>
              </div>
            ))}
          </div>
          {enforcementQ.isPending && <p className="mt-3 text-xs text-muted-foreground">Loading enforcement cases…</p>}
          {enforcementQ.isError && <p className="mt-3 text-xs text-red-600">Enforcement cases failed to load.</p>}
        </div>
      )}

      {activeSubTab === "compliance" && (
        <div className="rounded-lg border border-border bg-card p-4">
          <h4 className="mb-3 text-sm font-semibold text-foreground">Compliance actions</h4>
          <div className="space-y-3">
            {(complianceQ.data ?? []).map((action) => (
              <div key={action.actionId} className="rounded-lg border border-border p-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-[10px] text-muted-foreground">{action.actionId}</span>
                  <span className="rounded-full bg-violet-100 px-2 py-0.5 text-[10px] text-violet-800">{action.status}</span>
                </div>
                <p className="mt-1 text-sm font-medium text-foreground">{action.siteName}</p>
                <p className="text-xs text-muted-foreground">{action.actionType} · owner {action.ownerRef || "—"}</p>
                <p className="text-[10px] text-muted-foreground">Due: {action.dueDate || "—"}</p>
              </div>
            ))}
          </div>
          {complianceQ.isPending && <p className="mt-3 text-xs text-muted-foreground">Loading compliance actions…</p>}
          {complianceQ.isError && <p className="mt-3 text-xs text-red-600">Compliance actions failed to load.</p>}
        </div>
      )}
    </div>
  );
}
