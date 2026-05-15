"use client";

import { useState } from "react";
import {
  ClipboardList,
  Loader2,
  MapPin,
  Navigation,
  Plus,
  Radio,
  Shield,
  Target,
  Users,
  X,
} from "lucide-react";
import { usePublicHealthSites } from "@/hooks/queries/usePublicHealth";
import { apiClient } from "@/lib/api-client";

const FIELD_ACTIVITY_TYPES = [
  "Home visit",
  "Community screening",
  "Mobile clinic",
  "School health visit",
  "Contact tracing",
  "Water quality testing",
  "Vector surveillance",
  "Community education",
  "Defaulter tracing",
  "Growth monitoring",
  "Other",
] as const;

const RISK_LEVELS = [
  { value: "LOW", label: "Low" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HIGH", label: "High" },
] as const;

const FIELD_STATUSES = [
  { value: "PLANNED", label: "Planned" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
] as const;

interface NewFieldActivity {
  activityType: string;
  activityTitle: string;
  description: string;
  province: string;
  district: string;
  wardVillage: string;
  gpsLat: string;
  gpsLng: string;
  teamLeaderName: string;
  teamLeaderPhone: string;
  teamSize: string;
  activityDate: string;
  plannedDuration: string;
  householdsTargeted: string;
  equipmentSupplies: string;
  riskAssessment: string;
  status: string;
}

const EMPTY_FIELD_ACTIVITY: NewFieldActivity = {
  activityType: "",
  activityTitle: "",
  description: "",
  province: "",
  district: "",
  wardVillage: "",
  gpsLat: "",
  gpsLng: "",
  teamLeaderName: "",
  teamLeaderPhone: "",
  teamSize: "",
  activityDate: new Date().toISOString().slice(0, 10),
  plannedDuration: "",
  householdsTargeted: "",
  equipmentSupplies: "",
  riskAssessment: "",
  status: "",
};

function EmptyOpsTable(title: string, detail: string) {
  return (
    <div className="bg-white rounded-lg border border-gray-200">
      <div className="px-4 py-3 border-b flex items-center justify-between">
        <h4 className="text-sm font-semibold text-gray-900">{title}</h4>
        <button
          type="button"
          disabled
          className="inline-flex items-center gap-1 px-3 py-1.5 bg-gray-200 text-gray-500 text-xs font-medium rounded-lg cursor-not-allowed"
        >
          <Plus className="h-3.5 w-3.5" /> Not available
        </button>
      </div>
      <div className="px-4 py-12 text-center text-sm text-gray-500">{detail}</div>
    </div>
  );
}

export function FieldOperationsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"teams" | "tasks" | "tracking">("teams");
  const { data: indawoSites = [], isLoading: sitesLoading, isError: sitesError } = usePublicHealthSites();
  const siteCount = indawoSites.length;

  const [showNewForm, setShowNewForm] = useState(false);
  const [fieldForm, setFieldForm] = useState<NewFieldActivity>(EMPTY_FIELD_ACTIVITY);
  const [fieldSubmitting, setFieldSubmitting] = useState(false);
  const [fieldSubmitted, setFieldSubmitted] = useState(false);
  const [fieldError, setFieldError] = useState<string | null>(null);

  function updateField(field: keyof NewFieldActivity, value: string) {
    setFieldForm((f) => ({ ...f, [field]: value }));
  }

  async function handleFieldSubmit() {
    if (!fieldForm.activityType || !fieldForm.activityTitle || !fieldForm.teamLeaderName) {
      setFieldError("Activity type, title, and team leader name are required.");
      return;
    }
    setFieldSubmitting(true);
    setFieldError(null);
    try {
      await apiClient.post("/internal/v1/public-health/field-operations", {
        ...fieldForm,
        teamSize: Number(fieldForm.teamSize) || 0,
        householdsTargeted: Number(fieldForm.householdsTargeted) || 0,
        coordinates: fieldForm.gpsLat && fieldForm.gpsLng
          ? { latitude: parseFloat(fieldForm.gpsLat), longitude: parseFloat(fieldForm.gpsLng) }
          : null,
      });
    } catch {
      setFieldSubmitting(false);
      setFieldError("Field operations service is unavailable. Activity was not saved.");
      return;
    }
    setFieldSubmitting(false);
    setFieldSubmitted(true);
    setFieldForm(EMPTY_FIELD_ACTIVITY);
    setTimeout(() => { setFieldSubmitted(false); setShowNewForm(false); }, 3000);
  }

  return (
    <div className="space-y-4">
      {/* Header + New Field Activity button */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
            <Navigation className="w-5 h-5 text-emerald-600" /> Field Operations
          </h3>
          <p className="text-xs text-gray-500 mt-0.5">
            Record, coordinate, and track field activities, community visits, and mobile health operations
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowNewForm((s) => !s)}
          className="flex items-center gap-1.5 px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700"
        >
          <Plus className="w-4 h-4" />
          Record New Field Activity
        </button>
      </div>

      {/* Success banner */}
      {fieldSubmitted && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center gap-2">
          <Shield className="w-4 h-4" /> Field activity recorded successfully and submitted for coordination.
        </div>
      )}

      {/* ═══ NEW FIELD ACTIVITY FORM ═══ */}
      {showNewForm && (
        <div className="bg-white rounded-xl border-2 border-red-200 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-red-800 flex items-center gap-2">
            <Navigation className="w-4 h-4" /> New Field Activity Report
          </h4>

          {fieldError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{fieldError}</div>
          )}

          {/* Section 1: Activity Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider">1. Activity Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Activity Type *</span>
                <select value={fieldForm.activityType} onChange={(e) => updateField("activityType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select type...</option>
                  {FIELD_ACTIVITY_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Status</span>
                <select value={fieldForm.status} onChange={(e) => updateField("status", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select status...</option>
                  {FIELD_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Activity Title *</span>
              <input value={fieldForm.activityTitle} onChange={(e) => updateField("activityTitle", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Mbare Ward 12 home visit — TB defaulter tracing" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Description</span>
              <textarea value={fieldForm.description} onChange={(e) => updateField("description", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="Activity objectives, scope, and methodology..." />
            </label>
          </fieldset>

          {/* Section 2: Location */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 2. Location
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Province</span>
                <select value={fieldForm.province} onChange={(e) => updateField("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">District</span>
                <input value={fieldForm.district} onChange={(e) => updateField("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Ward / Village</span>
                <input value={fieldForm.wardVillage} onChange={(e) => updateField("wardVillage", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Mbare Ward 12" />
              </label>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">GPS Latitude</span>
                <input value={fieldForm.gpsLat} onChange={(e) => updateField("gpsLat", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="-17.83" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">GPS Longitude</span>
                <input value={fieldForm.gpsLng} onChange={(e) => updateField("gpsLng", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="31.05" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Risk Assessment</span>
                <select value={fieldForm.riskAssessment} onChange={(e) => updateField("riskAssessment", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
                  <option value="">Select risk...</option>
                  {RISK_LEVELS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Households Targeted</span>
                <input type="number" min="0" value={fieldForm.householdsTargeted} onChange={(e) => updateField("householdsTargeted", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Team & Schedule */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <Users className="w-3.5 h-3.5" /> 3. Team & Schedule
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Team Leader Name *</span>
                <input value={fieldForm.teamLeaderName} onChange={(e) => updateField("teamLeaderName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Team Leader Phone</span>
                <input value={fieldForm.teamLeaderPhone} onChange={(e) => updateField("teamLeaderPhone", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="+263 7..." />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Team Size</span>
                <input type="number" min="1" value={fieldForm.teamSize} onChange={(e) => updateField("teamSize", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. 5" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Planned Duration</span>
                <input value={fieldForm.plannedDuration} onChange={(e) => updateField("plannedDuration", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. 4 hours, 2 days" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Activity Date</span>
                <input type="date" value={fieldForm.activityDate} onChange={(e) => updateField("activityDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 4: Equipment & Supplies */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <ClipboardList className="w-3.5 h-3.5" /> 4. Equipment & Supplies
            </legend>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Equipment / Supplies Needed</span>
              <textarea value={fieldForm.equipmentSupplies} onChange={(e) => updateField("equipmentSupplies", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="e.g. Rapid test kits (50), BP monitors (3), health education pamphlets (200), PPE sets (10)..." />
            </label>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-gray-400">* Required fields</p>
            <button
              onClick={handleFieldSubmit}
              disabled={fieldSubmitting}
              className="flex items-center gap-2 px-6 py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors"
            >
              {fieldSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Navigation className="w-4 h-4" />}
              {fieldSubmitting ? "Submitting..." : "Submit Field Activity Report"}
            </button>
          </div>
        </div>
      )}

      <div className="rounded-lg border border-emerald-200 bg-emerald-50/80 p-4">
        <h4 className="text-sm font-semibold text-emerald-900 flex items-center gap-2">
          <MapPin className="h-4 w-4" /> Registered sites (Indawo, via BFF)
        </h4>
        <p className="mt-1 text-xs text-emerald-800">
          Live list from <code className="rounded bg-white/70 px-1">GET /internal/v1/public-health/sites</code> → indawo-service.
          Field team rosters, task boards, and GPS logs are not fabricated — they require workforce / ops APIs on the BFF.
        </p>
        {sitesLoading && (
          <div className="mt-3 flex items-center gap-2 text-sm text-gray-600">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading sites…
          </div>
        )}
        {sitesError && (
          <p className="mt-3 text-sm text-red-700">Could not load Indawo sites (service unreachable or empty).</p>
        )}
        {!sitesLoading && !sitesError && siteCount === 0 && (
          <p className="mt-3 text-sm text-gray-600">No sites returned for this tenant.</p>
        )}
        {!sitesLoading && !sitesError && siteCount > 0 && (
          <div className="mt-3 overflow-x-auto rounded-md border border-emerald-100 bg-white">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b bg-gray-50 text-left">
                  <th className="px-3 py-2 font-medium text-gray-600">Site ID</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Name</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Type</th>
                  <th className="px-3 py-2 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {indawoSites.slice(0, 25).map((s) => (
                  <tr key={s.id} className="border-b border-gray-100">
                    <td className="px-3 py-2 font-mono text-gray-700">{s.id}</td>
                    <td className="px-3 py-2 font-medium text-gray-900">{s.name}</td>
                    <td className="px-3 py-2 text-gray-600">{s.siteType}</td>
                    <td className="px-3 py-2 text-gray-600">{s.operationalStatus}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="grid grid-cols-5 gap-3">
        {[
          {
            label: "Registered sites (Indawo)",
            value: sitesLoading ? "…" : String(siteCount),
            Icon: MapPin,
            sub: sitesError ? "Could not load" : "Live from BFF",
          },
          { label: "Field teams deployed", value: "—", Icon: Users, sub: "No workforce API" },
          { label: "Active field tasks", value: "—", Icon: ClipboardList, sub: "No task board API" },
          { label: "Contacts traced (24h)", value: "—", Icon: Target, sub: "No aggregate API" },
          { label: "GPS check-ins (24h)", value: "—", Icon: Navigation, sub: "No telemetry API" },
        ].map((kpi, i) => (
          <div key={i} className="bg-white rounded-lg border border-gray-200 p-3 flex items-center gap-3">
            <div className="p-2 rounded-lg bg-impilo-50">
              <kpi.Icon className="h-4 w-4 text-impilo-500" />
            </div>
            <div>
              <p className="text-lg font-bold text-gray-900">{kpi.value}</p>
              <p className="text-[10px] text-gray-500">{kpi.label}</p>
              <p className="text-[9px] text-gray-400 mt-0.5">{kpi.sub}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 flex items-center gap-2 text-xs text-gray-600">
        <Radio className="h-3.5 w-3.5 shrink-0" />
        Field operation submissions are live; roster/task/GPS dashboards still require dedicated workforce and telemetry APIs.
      </div>

      <div className="flex gap-1 border-b border-gray-200">
        {[
          { key: "teams" as const, label: "Field Teams" },
          { key: "tasks" as const, label: "Task Board" },
          { key: "tracking" as const, label: "GPS Tracking" },
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

      {activeSubTab === "teams" &&
        EmptyOpsTable(
          "Field Team Roster & Deployment",
          "No field team roster endpoint under /internal/v1/public-health/*. Use Indawo sites above for premises context.",
        )}

      {activeSubTab === "tasks" &&
        EmptyOpsTable(
          "Field Task Assignment Board",
          "No task board API on the Experience BFF. Tasks will list here when a governed workforce or case-task service is wired.",
        )}

      {activeSubTab === "tracking" &&
        EmptyOpsTable(
          "GPS Check-in Log",
          "No GPS / check-in stream on the Experience BFF. This tab stays empty until telemetry ingestion is productised.",
        )}
    </div>
  );
}
