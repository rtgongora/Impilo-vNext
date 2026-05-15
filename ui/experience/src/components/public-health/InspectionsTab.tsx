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
  X,
} from "lucide-react";
import { usePublicHealthSites } from "@/hooks/queries/usePublicHealth";
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

/**
 * Inspections & enforcement — live **premises (Indawo)** plus **demonstration** register / enforcement / compliance
 * (Lovable-style depth until BFF exposes persisted inspections).
 */
export function InspectionsTab() {
  const [activeSubTab, setActiveSubTab] = useState<"premises" | "inspections" | "enforcement" | "compliance">("premises");
  const { data: sites = [], isLoading, isError } = usePublicHealthSites();

  const [showNewForm, setShowNewForm] = useState(false);
  const [inspForm, setInspForm] = useState<NewInspection>(EMPTY_INSPECTION);
  const [inspSubmitting, setInspSubmitting] = useState(false);
  const [inspSubmitted, setInspSubmitted] = useState(false);
  const [inspError, setInspError] = useState<string | null>(null);

  function updateInsp(field: keyof NewInspection, value: string) {
    setInspForm((f) => ({ ...f, [field]: value }));
  }

  async function handleInspSubmit() {
    if (!inspForm.inspectionType || !inspForm.premisesName || !inspForm.inspectorName) {
      setInspError("Inspection type, premises name, and inspector name are required.");
      return;
    }
    setInspSubmitting(true);
    setInspError(null);
    try {
      await apiClient.post("/internal/v1/public-health/site-registry/inspections", {
        ...inspForm,
        overallScore: Number(inspForm.overallScore) || 0,
        criticalFindings: Number(inspForm.criticalFindings) || 0,
        majorFindings: Number(inspForm.majorFindings) || 0,
        minorFindings: Number(inspForm.minorFindings) || 0,
      });
    } catch {
      setInspSubmitting(false);
      setInspError("Inspection service is unavailable. The report was not saved.");
      return;
    }
    setInspSubmitting(false);
    setInspSubmitted(true);
    setInspForm(EMPTY_INSPECTION);
    setTimeout(() => { setInspSubmitted(false); setShowNewForm(false); }, 3000);
  }

  return (
    <div className="space-y-4">
      {/* Header + New Inspection button */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
            <ClipboardCheck className="w-5 h-5 text-violet-600" /> Inspections & Enforcement
          </h3>
          <p className="text-xs text-gray-500 mt-0.5">
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
        <div className="bg-white rounded-xl border-2 border-amber-200 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-amber-800 flex items-center gap-2">
            <ClipboardCheck className="w-4 h-4" /> New Inspection Report
          </h4>

          {inspError && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{inspError}</div>
          )}

          {/* Section 1: Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider">1. Inspection Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Inspection Type *</span>
                <select value={inspForm.inspectionType} onChange={(e) => updateInsp("inspectionType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select type...</option>
                  {INSPECTION_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Status</span>
                <select value={inspForm.status} onChange={(e) => updateInsp("status", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-amber-400 focus:border-amber-400">
                  <option value="">Select status...</option>
                  {INSPECTION_STATUSES.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
          </fieldset>

          {/* Section 2: Premises */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 2. Premises Details
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Premises Name *</span>
                <input value={inspForm.premisesName} onChange={(e) => updateInsp("premisesName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Mbare Musika Market" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Premises Address</span>
                <input value={inspForm.premisesAddress} onChange={(e) => updateInsp("premisesAddress", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Physical address" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Province</span>
                <select value={inspForm.province} onChange={(e) => updateInsp("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">District</span>
                <input value={inspForm.district} onChange={(e) => updateInsp("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Ward</span>
                <input value={inspForm.ward} onChange={(e) => updateInsp("ward", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Ward 12" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Inspector */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider">3. Inspector Details</legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Inspector Name *</span>
                <input value={inspForm.inspectorName} onChange={(e) => updateInsp("inspectorName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Designation</span>
                <input value={inspForm.inspectorDesignation} onChange={(e) => updateInsp("inspectorDesignation", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Environmental Health Officer" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Date of Inspection</span>
                <input type="date" value={inspForm.inspectionDate} onChange={(e) => updateInsp("inspectionDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 4: Scoring & Findings */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <Star className="w-3.5 h-3.5" /> 4. Scoring & Findings
            </legend>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Overall Score (0-100)</span>
                <input type="number" min="0" max="100" value={inspForm.overallScore} onChange={(e) => updateInsp("overallScore", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Critical Findings</span>
                <input type="number" min="0" value={inspForm.criticalFindings} onChange={(e) => updateInsp("criticalFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Major Findings</span>
                <input type="number" min="0" value={inspForm.majorFindings} onChange={(e) => updateInsp("majorFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Minor Findings</span>
                <input type="number" min="0" value={inspForm.minorFindings} onChange={(e) => updateInsp("minorFindings", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Findings Narrative</span>
              <textarea value={inspForm.findingsNarrative} onChange={(e) => updateInsp("findingsNarrative", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="Detailed description of findings, observations, and non-compliances..." />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Corrective Actions Required</span>
              <textarea value={inspForm.correctiveActions} onChange={(e) => updateInsp("correctiveActions", e.target.value)} rows={2}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="List required corrective actions and timelines..." />
            </label>
          </fieldset>

          {/* Section 5: Follow-up & Evidence */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> 5. Follow-up & Evidence
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Follow-up Date</span>
                <input type="date" value={inspForm.followUpDate} onChange={(e) => updateInsp("followUpDate", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Photos / Evidence Reference Numbers</span>
                <input value={inspForm.photoReferences} onChange={(e) => updateInsp("photoReferences", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. IMG-20260414-001, IMG-20260414-002" />
              </label>
            </div>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-gray-400">* Required fields</p>
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
        <strong className="ml-1">Live write:</strong> inspection submissions call
        <code className="text-[10px]"> POST /internal/v1/public-health/site-registry/inspections</code>.
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          { label: "Registered premises", value: isLoading ? "…" : String(sites.length), Icon: MapPin, tone: "text-violet-800" },
          { label: "Inspections endpoint", value: "live", Icon: ClipboardCheck, tone: "text-impilo-700" },
          { label: "Enforcement endpoint", value: "—", Icon: AlertTriangle, tone: "text-red-700" },
          { label: "Compliance endpoint", value: "—", Icon: Star, tone: "text-amber-800" },
          { label: "Write endpoint", value: "available", Icon: FileText, tone: "text-gray-800" },
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
          { key: "inspections" as const, label: "Inspection register (pending)" },
          { key: "enforcement" as const, label: "Enforcement (pending)" },
          { key: "compliance" as const, label: "Compliance overview (pending)" },
        ].map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => tab.key === "premises" && setActiveSubTab(tab.key)}
            disabled={tab.key !== "premises"}
            className={`px-3 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeSubTab === tab.key
                ? "border-amber-600 text-amber-600"
                : "border-transparent text-gray-500 hover:text-gray-700"
            } ${tab.key !== "premises" ? "cursor-not-allowed opacity-50" : ""}`}
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
          <h4 className="text-sm font-semibold text-gray-900">Inspection register</h4>
          <p className="text-xs text-gray-600">
            This tab is intentionally disabled in production mode until inspection register read/write endpoints are exposed.
          </p>
        </div>
      )}

      {activeSubTab === "enforcement" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-2 text-sm font-semibold text-gray-900">Enforcement actions</h4>
          <p className="text-xs text-gray-600">
            Enforcement workflows are disabled until authoritative enforcement endpoints are wired through the BFF.
          </p>
        </div>
      )}

      {activeSubTab === "compliance" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-2 text-sm font-semibold text-gray-900">Compliance overview</h4>
          <p className="text-xs text-gray-600">
            Compliance analytics are unavailable until production compliance aggregation APIs are provided.
          </p>
        </div>
      )}
    </div>
  );
}
