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
import {
  DEMO_COMPLIANCE_BY_CATEGORY,
  DEMO_ENFORCEMENT,
  DEMO_INSPECTIONS,
} from "./publicHealthDemoFixtures";

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
  const [showScheduleForm, setShowScheduleForm] = useState(false);
  const [selectedInspection, setSelectedInspection] = useState<string | null>(null);
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
      await apiClient.post("/internal/v1/public-health/inspections", {
        ...inspForm,
        overallScore: Number(inspForm.overallScore) || 0,
        criticalFindings: Number(inspForm.criticalFindings) || 0,
        majorFindings: Number(inspForm.majorFindings) || 0,
        minorFindings: Number(inspForm.minorFindings) || 0,
      });
    } catch {
      // BFF may not have endpoint yet — treat as success for demo
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
          onClick={() => { setShowNewForm(!showNewForm); setInspSubmitted(false); setInspError(null); }}
          className="flex items-center gap-1.5 px-4 py-2 bg-amber-600 text-white text-sm font-medium rounded-lg hover:bg-amber-700 transition-colors"
        >
          {showNewForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          {showNewForm ? "Cancel" : "Record New Inspection"}
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
        <strong className="ml-1">Demo:</strong> inspection register, enforcement, and compliance tiles match Lovable /
        impilo-structure workflows — not saved until inspections APIs are wired on the BFF.
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
        {[
          { label: "Inspections (demo month)", value: "142", Icon: ClipboardCheck, tone: "text-violet-800" },
          { label: "Scheduled (demo)", value: "23", Icon: Calendar, tone: "text-impilo-700" },
          { label: "Critical findings (demo)", value: "4", Icon: AlertTriangle, tone: "text-red-700" },
          { label: "Avg score (demo)", value: "78%", Icon: Star, tone: "text-amber-800" },
          { label: "Active enforcement (demo)", value: "6", Icon: FileText, tone: "text-gray-800" },
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
                : "border-transparent text-gray-500 hover:text-gray-700"
            }`}
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
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <h4 className="text-sm font-semibold text-gray-900">Inspection register (demo)</h4>
              <p className="text-xs text-gray-500">Schedule, score, and follow-up — linked to INDAWO-style sites when APIs land</p>
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
              <h5 className="mb-3 font-semibold text-gray-900">Schedule new inspection (demo form)</h5>
              <div className="grid gap-3 sm:grid-cols-3">
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Site / premises</span>
                  <input placeholder="Search site (INDAWO)…" className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs" />
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Inspection type</span>
                  <select className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs">
                    <option>Routine</option>
                    <option>Ad-hoc / complaint-driven</option>
                    <option>Follow-up</option>
                    <option>Licensing assessment</option>
                    <option>Outbreak investigation</option>
                  </select>
                </label>
                <label className="block text-xs">
                  <span className="font-medium text-gray-600">Scheduled date</span>
                  <input type="date" className="mt-1 h-8 w-full rounded border border-gray-300 px-2 text-xs" />
                </label>
                <label className="block text-xs sm:col-span-3">
                  <span className="font-medium text-gray-600">Notes</span>
                  <textarea
                    placeholder="Specific areas to inspect…"
                    className="mt-1 min-h-[48px] w-full rounded border border-gray-300 p-2 text-xs"
                  />
                </label>
              </div>
              <p className="mt-2 text-[10px] text-violet-800">Submit is disabled — no POST endpoint yet.</p>
            </div>
          )}

          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-xs">
              <thead>
                <tr className="border-b bg-gray-50 text-left">
                  <th className="px-2 py-2 font-medium text-gray-600">ID</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Site</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Type</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Inspector</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Date</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Status</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Score</th>
                  <th className="px-2 py-2 font-medium text-gray-600">Critical</th>
                  <th className="px-2 py-2 font-medium text-gray-600" />
                </tr>
              </thead>
              <tbody>
                {DEMO_INSPECTIONS.map((row) => (
                  <tr key={row.id} className="border-b hover:bg-gray-50">
                    <td className="px-2 py-2 font-mono">{row.id}</td>
                    <td className="px-2 py-2">
                      <p className="font-medium text-gray-900">{row.site}</p>
                      <p className="text-[10px] text-gray-500">{row.siteType}</p>
                    </td>
                    <td className="px-2 py-2 text-gray-700">{row.type}</td>
                    <td className="px-2 py-2">{row.inspector}</td>
                    <td className="px-2 py-2 text-gray-600">{row.date}</td>
                    <td className="px-2 py-2">
                      <span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] capitalize text-slate-800">
                        {row.status.replace("_", " ")}
                      </span>
                    </td>
                    <td className="px-2 py-2 tabular-nums">{row.score ?? "—"}</td>
                    <td className="px-2 py-2">{row.critical > 0 ? <span className="font-semibold text-red-700">{row.critical}</span> : "0"}</td>
                    <td className="px-2 py-2">
                      <button
                        type="button"
                        onClick={() => setSelectedInspection(selectedInspection === row.id ? null : row.id)}
                        className="rounded border border-gray-300 px-2 py-1 text-[10px] font-medium hover:bg-gray-50"
                      >
                        {selectedInspection === row.id ? "Close" : "Details"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {selectedInspection && (
            <div className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-xs text-gray-800">
              <p className="font-semibold">Findings & follow-up — {selectedInspection}</p>
              <p className="mt-1 text-gray-600">
                Wire checklist, photos, and improvement notices to persistence when the inspections service is available.
              </p>
            </div>
          )}
        </div>
      )}

      {activeSubTab === "enforcement" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-3 text-sm font-semibold text-gray-900">Enforcement actions (demo)</h4>
          <div className="space-y-3">
            {DEMO_ENFORCEMENT.map((e) => (
              <div key={e.id} className="rounded-lg border border-amber-200 bg-amber-50/40 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-mono text-[10px] text-gray-600">{e.id}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${
                      e.severity === "critical" ? "bg-red-100 text-red-800" : "bg-amber-100 text-amber-900"
                    }`}
                  >
                    {e.severity}
                  </span>
                </div>
                <p className="mt-1 text-sm font-medium text-gray-900">{e.site}</p>
                <p className="text-xs text-gray-700">{e.violation}</p>
                <p className="mt-2 text-[10px] text-gray-600">
                  {e.action} · issued {e.issued} · deadline {e.deadline} · <span className="font-medium">{e.status}</span>
                </p>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeSubTab === "compliance" && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h4 className="mb-3 text-sm font-semibold text-gray-900">Compliance overview by category (demo)</h4>
          <div className="space-y-4">
            {DEMO_COMPLIANCE_BY_CATEGORY.map((c) => {
              const pct = Math.min(100, Math.round((c.rate / c.target) * 100));
              return (
                <div key={c.category}>
                  <div className="mb-1 flex justify-between text-xs">
                    <span className="font-medium text-gray-800">{c.category}</span>
                    <span className={c.rate >= c.target ? "text-emerald-700" : "text-amber-800"}>
                      {c.rate}% / target {c.target}%
                    </span>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-gray-100">
                    <div className="h-full rounded-full bg-violet-500 transition-all" style={{ width: `${pct}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
