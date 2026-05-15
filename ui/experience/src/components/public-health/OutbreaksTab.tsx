"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Calendar,
  ChevronDown,
  Loader2,
  MapPin,
  Plus,
  Shield,
  Siren,
  Users,
  X,
} from "lucide-react";
import { usePublicHealthCases } from "@/hooks/queries/usePublicHealth";
import { apiClient } from "@/lib/api-client";

const EVENT_TYPES = [
  "Disease Outbreak",
  "Foodborne Illness",
  "Waterborne Illness",
  "Vector-borne Disease",
  "Zoonotic Event",
  "Chemical / Toxicological",
  "Radiological / Nuclear",
  "Environmental Hazard",
  "Mass Casualty Incident",
  "Unknown Cluster",
] as const;

const SEVERITY_LEVELS = [
  { value: "GRADE_1", label: "Grade 1 — Minor / Localised", color: "bg-green-100 text-green-800 border-green-200" },
  { value: "GRADE_2", label: "Grade 2 — Moderate / Multi-site", color: "bg-amber-100 text-amber-800 border-amber-200" },
  { value: "GRADE_3", label: "Grade 3 — Major / Regional", color: "bg-red-100 text-red-800 border-red-200" },
  { value: "EMERGENCY", label: "Public Health Emergency", color: "bg-red-200 text-red-900 border-red-300" },
] as const;

const NOTIFIABLE_DISEASES = [
  "Cholera", "Typhoid", "Dysentery", "Measles", "Anthrax",
  "Rabies", "Malaria (epidemic)", "Plague", "Yellow Fever",
  "Meningitis", "Polio (AFP)", "COVID-19", "Ebola / VHF",
  "Influenza (novel)", "Tuberculosis (MDR)", "HIV (cluster)",
  "Other (specify below)",
] as const;

interface NewEvent {
  eventType: string;
  severity: string;
  disease: string;
  diseaseOther: string;
  title: string;
  description: string;
  dateDetected: string;
  dateOnset: string;
  dateNotified: string;
  facilityName: string;
  district: string;
  province: string;
  ward: string;
  gpsLat: string;
  gpsLng: string;
  initialCases: string;
  initialDeaths: string;
  populationAtRisk: string;
  ageGroups: string;
  sourceIdentified: string;
  modeOfTransmission: string;
  responseActions: string;
  reportedBy: string;
  reporterPhone: string;
  reporterDesignation: string;
}

const EMPTY_EVENT: NewEvent = {
  eventType: "",
  severity: "",
  disease: "",
  diseaseOther: "",
  title: "",
  description: "",
  dateDetected: new Date().toISOString().slice(0, 10),
  dateOnset: "",
  dateNotified: new Date().toISOString().slice(0, 10),
  facilityName: "",
  district: "",
  province: "",
  ward: "",
  gpsLat: "",
  gpsLng: "",
  initialCases: "",
  initialDeaths: "",
  populationAtRisk: "",
  ageGroups: "",
  sourceIdentified: "",
  modeOfTransmission: "",
  responseActions: "",
  reportedBy: "",
  reporterPhone: "",
  reporterDesignation: "",
};

export function OutbreaksTab() {
  const { data: cases = [], isLoading } = usePublicHealthCases();
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState<NewEvent>(EMPTY_EVENT);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function update(field: keyof NewEvent, value: string) {
    setForm((f) => ({ ...f, [field]: value }));
  }

  async function handleSubmit() {
    if (!form.eventType || !form.severity || !form.title) {
      setError("Event type, severity, and title are required.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await apiClient.post("/internal/v1/public-health/outbreaks", {
        ...form,
        initialCases: Number(form.initialCases) || 0,
        initialDeaths: Number(form.initialDeaths) || 0,
        populationAtRisk: Number(form.populationAtRisk) || 0,
        coordinates: form.gpsLat && form.gpsLng
          ? { latitude: parseFloat(form.gpsLat), longitude: parseFloat(form.gpsLng) }
          : null,
      });
    } catch {
      setSubmitting(false);
      setError("Outbreak service is unavailable. The event was not saved.");
      return;
    }
    setSubmitting(false);
    setSubmitted(true);
    setForm(EMPTY_EVENT);
    setTimeout(() => { setSubmitted(false); setShowForm(false); }, 3000);
  }

  return (
    <div className="space-y-4">
      {/* Header + New Event button */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
            <Siren className="w-5 h-5 text-red-600" /> Outbreaks & Incident Register
          </h3>
          <p className="text-xs text-gray-500 mt-0.5">
            Record, track, and manage public health events, outbreaks, and notifiable disease incidents
          </p>
        </div>
        <button
          type="button"
          onClick={() => setShowForm((s) => !s)}
          className="flex items-center gap-1.5 px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700"
        >
          <Plus className="w-4 h-4" />
          Record New Event
        </button>
      </div>

      <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-xs text-amber-950">
        New outbreak submissions are routed to a governed BFF endpoint and fail closed on upstream errors.
        Live case visibility remains available below from surveillance-service.
      </div>

      {/* Success banner */}
      {submitted && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center gap-2">
          <Shield className="w-4 h-4" /> Event recorded successfully and submitted for verification.
        </div>
      )}

      {/* ═══ NEW EVENT FORM ═══ */}
      {showForm && (
        <div className="bg-white rounded-xl border-2 border-red-200 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-red-800 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" /> New Outbreak / Incident Report
          </h4>

          {error && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">{error}</div>
          )}

          {/* Section 1: Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider">1. Event Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Event Type *</span>
                <select value={form.eventType} onChange={(e) => update("eventType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select event type...</option>
                  {EVENT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Severity / Grade *</span>
                <select value={form.severity} onChange={(e) => update("severity", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select severity...</option>
                  {SEVERITY_LEVELS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Notifiable Disease / Condition</span>
                <select value={form.disease} onChange={(e) => update("disease", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select disease...</option>
                  {NOTIFIABLE_DISEASES.map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              </label>
              {form.disease === "Other (specify below)" && (
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Specify other</span>
                  <input value={form.diseaseOther} onChange={(e) => update("diseaseOther", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Disease or condition name" />
                </label>
              )}
            </div>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Event Title / Summary *</span>
              <input value={form.title} onChange={(e) => update("title", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Cholera outbreak — Mbare suburb, Harare" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Description / Narrative</span>
              <textarea value={form.description} onChange={(e) => update("description", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="Detailed description of the event, circumstances, and initial findings..." />
            </label>
          </fieldset>

          {/* Section 2: Dates */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> 2. Key Dates
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Date of onset (first case)</span>
                <input type="date" value={form.dateOnset} onChange={(e) => update("dateOnset", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Date detected</span>
                <input type="date" value={form.dateDetected} onChange={(e) => update("dateDetected", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Date notified</span>
                <input type="date" value={form.dateNotified} onChange={(e) => update("dateNotified", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Location */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 3. Location
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Province</span>
                <select value={form.province} onChange={(e) => update("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">District</span>
                <input value={form.district} onChange={(e) => update("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Ward / Suburb / Village</span>
                <input value={form.ward} onChange={(e) => update("ward", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Mbare" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Reporting facility</span>
                <input value={form.facilityName} onChange={(e) => update("facilityName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Beatrice Road Infectious Disease Hospital" />
              </label>
              <div className="grid grid-cols-2 gap-2">
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Lat</span>
                  <input value={form.gpsLat} onChange={(e) => update("gpsLat", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="-17.83" />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-gray-700">Lng</span>
                  <input value={form.gpsLng} onChange={(e) => update("gpsLng", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="31.05" />
                </label>
              </div>
            </div>
          </fieldset>

          {/* Section 4: Epidemiological */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
              <Users className="w-3.5 h-3.5" /> 4. Epidemiological Summary
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Initial cases</span>
                <input type="number" min="0" value={form.initialCases} onChange={(e) => update("initialCases", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Initial deaths</span>
                <input type="number" min="0" value={form.initialDeaths} onChange={(e) => update("initialDeaths", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Population at risk</span>
                <input type="number" min="0" value={form.populationAtRisk} onChange={(e) => update("populationAtRisk", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. 50000" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Age groups affected</span>
                <input value={form.ageGroups} onChange={(e) => update("ageGroups", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. 0-5 years, 20-40 years" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Mode of transmission</span>
                <input value={form.modeOfTransmission} onChange={(e) => update("modeOfTransmission", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Waterborne, Person-to-person, Vector" />
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Source identified</span>
              <input value={form.sourceIdentified} onChange={(e) => update("sourceIdentified", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. Contaminated borehole at intersection of 5th St and Remembrance Drive" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700">Initial response actions taken</span>
              <textarea value={form.responseActions} onChange={(e) => update("responseActions", e.target.value)} rows={2}
                className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm resize-none" placeholder="e.g. Water source shut off, ORS distributed, health education teams deployed..." />
            </label>
          </fieldset>

          {/* Section 5: Reporter */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-gray-500 uppercase tracking-wider">5. Reporting Officer</legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Name</span>
                <input value={form.reportedBy} onChange={(e) => update("reportedBy", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Phone</span>
                <input value={form.reporterPhone} onChange={(e) => update("reporterPhone", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="+263 7..." />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-gray-700">Designation</span>
                <input value={form.reporterDesignation} onChange={(e) => update("reporterDesignation", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-gray-300 px-3 py-2 text-sm" placeholder="e.g. District Environmental Health Officer" />
              </label>
            </div>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-gray-400">* Required fields</p>
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="flex items-center gap-2 px-6 py-2.5 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors"
            >
              {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Siren className="w-4 h-4" />}
              {submitting ? "Submitting..." : "Submit Outbreak / Incident Report"}
            </button>
          </div>
        </div>
      )}

      {/* ═══ EXISTING EVENTS LIST ═══ */}
      <div className="bg-white rounded-xl border border-gray-200">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-600" />
            <h4 className="text-sm font-semibold text-gray-900">Active Events & Surveillance Cases</h4>
          </div>
          <span className="text-xs text-gray-400">{cases.length} records</span>
        </div>
        <div className="p-4">
          {isLoading ? (
            <div className="flex items-center gap-2 text-sm text-gray-500 py-8 justify-center">
              <Loader2 className="w-4 h-4 animate-spin" /> Loading...
            </div>
          ) : cases.length === 0 ? (
            <p className="text-sm text-gray-400 py-6 text-center">No active events or cases. Click &quot;Record New Event&quot; to report an outbreak or incident.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-gray-50 text-left text-xs text-gray-600">
                  <tr>
                    <th className="px-3 py-2">Disease / Event</th>
                    <th className="px-3 py-2">Facility</th>
                    <th className="px-3 py-2">Patient / Ref</th>
                    <th className="px-3 py-2">Reported</th>
                    <th className="px-3 py-2">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {cases.slice(0, 25).map((c) => (
                    <tr key={c.id} className="border-t border-gray-100 hover:bg-gray-50">
                      <td className="px-3 py-2 font-medium text-gray-900">{c.disease}</td>
                      <td className="px-3 py-2 text-gray-600">{c.facility}</td>
                      <td className="px-3 py-2 text-gray-500 text-xs">{c.patientRef}</td>
                      <td className="px-3 py-2 text-gray-500 text-xs">{c.reportedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <span className={`text-[10px] px-2 py-0.5 rounded-full ${
                          c.status.includes("ACTIVE") || c.status.includes("OPEN") ? "bg-red-100 text-red-800" :
                          c.status.includes("CLOSED") ? "bg-gray-100 text-gray-600" : "bg-amber-100 text-amber-800"
                        }`}>{c.status}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      <p className="text-[10px] text-gray-400">
        For threshold signals and weekly IDSR aggregation, use{" "}
        <Link href="/public-health?tab=surveillance" className="text-impilo-500 hover:underline">Surveillance / eIDSR</Link>.
      </p>
    </div>
  );
}
