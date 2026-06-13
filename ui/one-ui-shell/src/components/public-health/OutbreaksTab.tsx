"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Calendar,
  Loader2,
  MapPin,
  Plus,
  Shield,
  Siren,
  Users,
} from "lucide-react";
import {
  useCreatePublicHealthOutbreak,
  usePublicHealthCases,
  usePublicHealthOutbreaks,
  useTransitionPublicHealthOutbreak,
} from "@/hooks/queries/usePublicHealth";

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
  { value: "GRADE_2", label: "Grade 2 — Moderate / Multi-site", color: "bg-amber-100 text-warning-foreground border-warning/35" },
  { value: "GRADE_3", label: "Grade 3 — Major / Regional", color: "bg-red-100 text-red-800 border-danger/28" },
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

const OUTBREAK_TRANSITIONS: Record<string, string> = {
  RECORDED: "CONFIRMED",
  CONFIRMED: "INVESTIGATING",
  INVESTIGATING: "CONTAINED",
  CONTAINED: "CLOSED",
};

export function OutbreaksTab() {
  const { data: cases = [], isLoading: casesLoading } = usePublicHealthCases();
  const { data: outbreaks = [], isLoading: outbreaksLoading } = usePublicHealthOutbreaks();
  const createOutbreak = useCreatePublicHealthOutbreak();
  const transitionOutbreak = useTransitionPublicHealthOutbreak();
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
      await createOutbreak.mutateAsync({
        name: form.title,
        description: form.description,
        eventType: form.eventType,
        severity: form.severity,
        province: form.province,
        district: form.district,
        gpsLat: form.gpsLat,
        gpsLng: form.gpsLng,
        disease: form.disease === "Other (specify below)" ? form.diseaseOther : form.disease,
        initialCases: Number(form.initialCases) || 0,
      });
    } catch {
      setSubmitting(false);
      setError("Failed to submit outbreak event. Verify the BFF and surveillance service are available.");
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
          <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
            <Siren className="w-5 h-5 text-red-600" /> Outbreaks & Incident Register
          </h3>
          <p className="text-xs text-muted-foreground mt-0.5">
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

      <div className="rounded-lg border border-warning/35 bg-warning-soft px-4 py-3 text-xs text-warning-foreground">
        Outbreak register and lifecycle transitions use persisted outbreak events (signal-linked). Surveillance cases remain below for case-level tracking.
      </div>

      <div className="bg-card rounded-xl border border-border">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <h4 className="text-sm font-semibold text-foreground">Outbreak register (lifecycle)</h4>
          <span className="text-xs text-muted-foreground">{outbreaks.length} outbreaks</span>
        </div>
        <div className="p-4 overflow-x-auto">
          {outbreaksLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground py-4"><Loader2 className="w-4 h-4 animate-spin" /> Loading outbreaks…</div>
          ) : outbreaks.length === 0 ? (
            <p className="text-sm text-muted-foreground">No outbreaks recorded yet.</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-background text-left text-xs text-muted-foreground">
                <tr>
                  <th className="px-3 py-2">Name</th>
                  <th className="px-3 py-2">Status</th>
                  <th className="px-3 py-2">Severity</th>
                  <th className="px-3 py-2">Location</th>
                  <th className="px-3 py-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {outbreaks.map((o) => {
                  const next = OUTBREAK_TRANSITIONS[o.lifecycleStatus];
                  return (
                    <tr key={o.id} className="border-t border-border">
                      <td className="px-3 py-2 font-medium">{o.name}</td>
                      <td className="px-3 py-2"><span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-100 text-warning-foreground">{o.lifecycleStatus}</span></td>
                      <td className="px-3 py-2 text-muted-foreground">{o.severity}</td>
                      <td className="px-3 py-2 text-muted-foreground text-xs">{o.district !== "—" ? `${o.district}, ${o.province}` : o.province}</td>
                      <td className="px-3 py-2">
                        {next ? (
                          <button
                            type="button"
                            disabled={transitionOutbreak.isPending}
                            onClick={() => transitionOutbreak.mutate({ outbreakId: o.id, lifecycleStatus: next })}
                            className="text-[10px] text-primary hover:underline"
                          >
                            → {next}
                          </button>
                        ) : (
                          <span className="text-[10px] text-muted-foreground">Closed</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* Success banner */}
      {submitted && (
        <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800 flex items-center gap-2">
          <Shield className="w-4 h-4" /> Event recorded successfully and submitted for verification.
        </div>
      )}

      {/* ═══ NEW EVENT FORM ═══ */}
      {showForm && (
        <div className="bg-card rounded-xl border-2 border-danger/28 p-6 space-y-6">
          <h4 className="text-sm font-semibold text-red-800 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" /> New Outbreak / Incident Report
          </h4>

          {error && (
            <div className="p-3 bg-danger-soft border border-danger/28 rounded-lg text-sm text-danger">{error}</div>
          )}

          {/* Section 1: Classification */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">1. Event Classification</legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Event Type *</span>
                <select value={form.eventType} onChange={(e) => update("eventType", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select event type...</option>
                  {EVENT_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Severity / Grade *</span>
                <select value={form.severity} onChange={(e) => update("severity", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select severity...</option>
                  {SEVERITY_LEVELS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
                </select>
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Notifiable Disease / Condition</span>
                <select value={form.disease} onChange={(e) => update("disease", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm focus:ring-2 focus:ring-red-400 focus:border-red-400">
                  <option value="">Select disease...</option>
                  {NOTIFIABLE_DISEASES.map((d) => <option key={d} value={d}>{d}</option>)}
                </select>
              </label>
              {form.disease === "Other (specify below)" && (
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Specify other</span>
                  <input value={form.diseaseOther} onChange={(e) => update("diseaseOther", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Disease or condition name" />
                </label>
              )}
            </div>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Event Title / Summary *</span>
              <input value={form.title} onChange={(e) => update("title", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Cholera outbreak — Mbare suburb, Harare" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Description / Narrative</span>
              <textarea value={form.description} onChange={(e) => update("description", e.target.value)} rows={3}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm resize-none" placeholder="Detailed description of the event, circumstances, and initial findings..." />
            </label>
          </fieldset>

          {/* Section 2: Dates */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Calendar className="w-3.5 h-3.5" /> 2. Key Dates
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Date of onset (first case)</span>
                <input type="date" value={form.dateOnset} onChange={(e) => update("dateOnset", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Date detected</span>
                <input type="date" value={form.dateDetected} onChange={(e) => update("dateDetected", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Date notified</span>
                <input type="date" value={form.dateNotified} onChange={(e) => update("dateNotified", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
              </label>
            </div>
          </fieldset>

          {/* Section 3: Location */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <MapPin className="w-3.5 h-3.5" /> 3. Location
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Province</span>
                <select value={form.province} onChange={(e) => update("province", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                  <option value="">Select province...</option>
                  {["Harare Metropolitan", "Bulawayo Metropolitan", "Manicaland", "Mashonaland Central", "Mashonaland East", "Mashonaland West", "Masvingo", "Matabeleland North", "Matabeleland South", "Midlands"].map((p) => (
                    <option key={p} value={p}>{p}</option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">District</span>
                <input value={form.district} onChange={(e) => update("district", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Harare Urban" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Ward / Suburb / Village</span>
                <input value={form.ward} onChange={(e) => update("ward", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Mbare" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Reporting facility</span>
                <input value={form.facilityName} onChange={(e) => update("facilityName", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Beatrice Road Infectious Disease Hospital" />
              </label>
              <div className="grid grid-cols-2 gap-2">
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Lat</span>
                  <input value={form.gpsLat} onChange={(e) => update("gpsLat", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="-17.83" />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">Lng</span>
                  <input value={form.gpsLng} onChange={(e) => update("gpsLng", e.target.value)}
                    className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="31.05" />
                </label>
              </div>
            </div>
          </fieldset>

          {/* Section 4: Epidemiological */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider flex items-center gap-1.5">
              <Users className="w-3.5 h-3.5" /> 4. Epidemiological Summary
            </legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Initial cases</span>
                <input type="number" min="0" value={form.initialCases} onChange={(e) => update("initialCases", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Initial deaths</span>
                <input type="number" min="0" value={form.initialDeaths} onChange={(e) => update("initialDeaths", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="0" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Population at risk</span>
                <input type="number" min="0" value={form.populationAtRisk} onChange={(e) => update("populationAtRisk", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. 50000" />
              </label>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Age groups affected</span>
                <input value={form.ageGroups} onChange={(e) => update("ageGroups", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. 0-5 years, 20-40 years" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Mode of transmission</span>
                <input value={form.modeOfTransmission} onChange={(e) => update("modeOfTransmission", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Waterborne, Person-to-person, Vector" />
              </label>
            </div>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Source identified</span>
              <input value={form.sourceIdentified} onChange={(e) => update("sourceIdentified", e.target.value)}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. Contaminated borehole at intersection of 5th St and Remembrance Drive" />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">Initial response actions taken</span>
              <textarea value={form.responseActions} onChange={(e) => update("responseActions", e.target.value)} rows={2}
                className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm resize-none" placeholder="e.g. Water source shut off, ORS distributed, health education teams deployed..." />
            </label>
          </fieldset>

          {/* Section 5: Reporter */}
          <fieldset className="space-y-3">
            <legend className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">5. Reporting Officer</legend>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <label className="block">
                <span className="text-sm font-medium text-foreground">Name</span>
                <input value={form.reportedBy} onChange={(e) => update("reportedBy", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Full name" />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Phone</span>
                <input value={form.reporterPhone} onChange={(e) => update("reporterPhone", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="+263 7..." />
              </label>
              <label className="block">
                <span className="text-sm font-medium text-foreground">Designation</span>
                <input value={form.reporterDesignation} onChange={(e) => update("reporterDesignation", e.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="e.g. District Environmental Health Officer" />
              </label>
            </div>
          </fieldset>

          {/* Submit */}
          <div className="flex items-center justify-between pt-2 border-t">
            <p className="text-xs text-muted-foreground">* Required fields</p>
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
      <div className="bg-card rounded-xl border border-border">
        <div className="px-4 py-3 border-b flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-amber-600" />
            <h4 className="text-sm font-semibold text-foreground">Active Events & Surveillance Cases</h4>
          </div>
          <span className="text-xs text-muted-foreground">{cases.length} records</span>
        </div>
        <div className="p-4">
          {casesLoading ? (
            <div className="flex items-center gap-2 text-sm text-muted-foreground py-8 justify-center">
              <Loader2 className="w-4 h-4 animate-spin" /> Loading...
            </div>
          ) : cases.length === 0 ? (
            <p className="text-sm text-muted-foreground py-6 text-center">No active events or cases. Click &quot;Record New Event&quot; to report an outbreak or incident.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-background text-left text-xs text-muted-foreground">
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
                    <tr key={c.id} className="border-t border-border hover:bg-background">
                      <td className="px-3 py-2 font-medium text-foreground">{c.disease}</td>
                      <td className="px-3 py-2 text-muted-foreground">{c.facility}</td>
                      <td className="px-3 py-2 text-muted-foreground text-xs">{c.patientRef}</td>
                      <td className="px-3 py-2 text-muted-foreground text-xs">{c.reportedAt || "—"}</td>
                      <td className="px-3 py-2">
                        <span className={`text-[10px] px-2 py-0.5 rounded-full ${
                          c.status.includes("ACTIVE") || c.status.includes("OPEN") ? "bg-red-100 text-red-800" :
                          c.status.includes("CLOSED") ? "bg-neutral-100 text-muted-foreground" : "bg-amber-100 text-warning-foreground"
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

      <p className="text-[10px] text-muted-foreground">
        For threshold signals and weekly IDSR aggregation, use{" "}
        <Link href="/public-health?tab=surveillance" className="text-primary hover:underline">Surveillance / eIDSR</Link>.
      </p>
    </div>
  );
}
