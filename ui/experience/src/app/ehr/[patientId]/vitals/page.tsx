"use client";

/**
 * Vitals — View past vitals and record new readings.
 * Route: /ehr/[patientId]/vitals | pageTitle: "Vitals"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, AlertTriangle, ClipboardList, Droplets, Loader2, Plus, Truck } from "lucide-react";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEarlyWarningScores, useRecordEWS } from "@/hooks/queries/useEWS";
import { useFluidBalance, useRecordFluid } from "@/hooks/queries/useFluidBalance";
import { useObservations, useRecordObservation } from "@/hooks/queries/useObservations";
import {
  useAcceptPatientTransfer,
  usePatientTransfers,
  useRequestPatientTransfer,
} from "@/hooks/queries/usePatientTransfers";
import {
  useVitals,
  useRecordVitals,
  type VitalsResource,
} from "@/hooks/queries/useVitals";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { VitalsTrendPanel } from "@/components/VitalsTrendChart";

function localCalendarDateISO() {
  const t = new Date();
  const y = t.getFullYear();
  const m = String(t.getMonth() + 1).padStart(2, "0");
  const d = String(t.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export default function VitalsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (e) => e.attributes.status === "IN_PROGRESS" || e.attributes.status === "ACTIVE"
  );
  const encounterId = activeEncounter?.id ?? "";
  const { data: vitalsData, isLoading } = useVitals(patientId);
  const recordVitals = useRecordVitals();
  const {
    data: ewsData,
    isLoading: ewsLoading,
    isError: ewsError,
    refetch: refetchEws,
  } = useEarlyWarningScores(patientId);
  const recordEws = useRecordEWS();
  const ewsRows = ewsData?.data ?? [];

  const [fluidDate, setFluidDate] = useState(localCalendarDateISO);
  const {
    data: fluidPayload,
    isLoading: fluidLoading,
    isError: fluidError,
    refetch: refetchFluid,
  } = useFluidBalance(patientId, fluidDate);
  const recordFluid = useRecordFluid();
  const fluidRows = fluidPayload?.data ?? [];
  const fluidSummary = fluidPayload?.summary ?? { totalIntake: 0, totalOutput: 0, balance: 0 };

  const {
    data: obsData,
    isLoading: obsLoading,
    isError: obsError,
    refetch: refetchObs,
  } = useObservations(patientId);
  const recordObs = useRecordObservation();
  const obsRows = obsData?.data ?? [];

  const {
    data: xferData,
    isLoading: xferLoading,
    isError: xferError,
    refetch: refetchXfer,
  } = usePatientTransfers(patientId);
  const requestXfer = useRequestPatientTransfer();
  const acceptXfer = useAcceptPatientTransfer();
  const xferRows = xferData?.data ?? [];

  const vitals: VitalsResource[] = vitalsData?.data ?? [];

  const [showForm, setShowForm] = useState(false);
  const [systolic, setSystolic] = useState("");
  const [diastolic, setDiastolic] = useState("");
  const [heartRate, setHeartRate] = useState("");
  const [temperature, setTemperature] = useState("");
  const [respiratoryRate, setRespiratoryRate] = useState("");
  const [oxygenSaturation, setOxygenSaturation] = useState("");
  const [weight, setWeight] = useState("");
  const [height, setHeight] = useState("");
  const [painScore, setPainScore] = useState("");
  const [notes, setNotes] = useState("");
  const [showEwsForm, setShowEwsForm] = useState(false);
  const [ewsTotal, setEwsTotal] = useState("");
  const [showFluidForm, setShowFluidForm] = useState(false);
  const [fluidEntryType, setFluidEntryType] = useState<"INTAKE" | "OUTPUT">("INTAKE");
  const [fluidCategory, setFluidCategory] = useState("ORAL");
  const [fluidVolume, setFluidVolume] = useState("");
  const [fluidDesc, setFluidDesc] = useState("");
  const [showObsForm, setShowObsForm] = useState(false);
  const [obsChartType, setObsChartType] = useState("VITALS");
  const [obsSummary, setObsSummary] = useState("");
  const [showXferForm, setShowXferForm] = useState(false);
  const [xferReason, setXferReason] = useState("CLINICAL");
  const [xferNotes, setXferNotes] = useState("");

  function formatParamsCell(p: unknown): string {
    if (p == null) return "—";
    if (typeof p === "string") return p.length > 160 ? `${p.slice(0, 160)}…` : p;
    try {
      return JSON.stringify(p);
    } catch {
      return String(p);
    }
  }

  function resetForm() {
    setSystolic("");
    setDiastolic("");
    setHeartRate("");
    setTemperature("");
    setRespiratoryRate("");
    setOxygenSaturation("");
    setWeight("");
    setHeight("");
    setPainScore("");
    setNotes("");
  }

  function handleEwsSubmit(e: React.FormEvent) {
    e.preventDefault();
    const n = Number(ewsTotal);
    if (!Number.isFinite(n) || ewsTotal.trim() === "") return;
    recordEws.mutate(
      {
        patientId,
        encounterId: encounterId || null,
        totalScore: Math.round(n),
        recordedBy: user?.id ?? user?.displayName ?? "unknown",
      },
      {
        onSuccess: () => {
          setEwsTotal("");
          setShowEwsForm(false);
        },
      },
    );
  }

  function handleObsSubmit(e: React.FormEvent) {
    e.preventDefault();
    const summary = obsSummary.trim() || "(no summary)";
    const parametersJson = JSON.stringify({ summary });
    recordObs.mutate(
      {
        patientId,
        encounterId: encounterId || null,
        chartType: obsChartType,
        parametersJson,
        recordedBy: user?.id ?? user?.displayName ?? "unknown",
      },
      {
        onSuccess: () => {
          setObsSummary("");
          setShowObsForm(false);
        },
      },
    );
  }

  function handleXferSubmit(e: React.FormEvent) {
    e.preventDefault();
    requestXfer.mutate(
      {
        patientId,
        reason: xferReason,
        clinicalNotes: xferNotes.trim(),
        requestedBy: user?.id ?? user?.displayName ?? "unknown",
      },
      {
        onSuccess: () => {
          setXferNotes("");
          setXferReason("CLINICAL");
          setShowXferForm(false);
        },
      },
    );
  }

  function handleFluidSubmit(e: React.FormEvent) {
    e.preventDefault();
    const vol = Number(fluidVolume);
    if (!Number.isFinite(vol) || vol <= 0 || fluidVolume.trim() === "") return;
    recordFluid.mutate(
      {
        patientId,
        encounterId: encounterId || null,
        entryType: fluidEntryType,
        category: fluidCategory,
        volumeMl: Math.round(vol),
        description: fluidDesc.trim() || undefined,
        recordedBy: user?.id ?? user?.displayName ?? "unknown",
      },
      {
        onSuccess: () => {
          setFluidVolume("");
          setFluidDesc("");
          setShowFluidForm(false);
        },
      },
    );
  }

  function riskBadgeClass(level: string) {
    const u = level.toUpperCase();
    if (u === "HIGH") return "bg-red-100 text-red-800";
    if (u === "MEDIUM") return "bg-amber-100 text-amber-900";
    if (u === "LOW") return "bg-blue-100 text-blue-800";
    return "bg-gray-100 text-gray-700";
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();

    const toNum = (v: string) => (v.trim() === "" ? null : Number(v));

    recordVitals.mutate(
      {
        patientId,
        encounterId,
        recorded_by: user?.id ?? "system",
        systolic: toNum(systolic),
        diastolic: toNum(diastolic),
        heartRate: toNum(heartRate),
        temperature: toNum(temperature),
        respiratoryRate: toNum(respiratoryRate),
        oxygenSaturation: toNum(oxygenSaturation),
        weight: toNum(weight),
        height: toNum(height),
        painScore: toNum(painScore),
        notes: notes.trim() || null },
      {
        onSuccess: () => {
          resetForm();
          setShowForm(false);
        } },
    );
  }

  function fmt(v: number | null | undefined) {
    return v != null ? String(v) : "—";
  }

  return (
    <EHRLayout>
      <PageShell
        title="Vitals"
        subtitle="Vitals, EWS, fluid I/O, observation entries, and transfer requests — live BFF data for this patient"
      >

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading vitals...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-5 h-5 text-red-600" />
                <h2 className="text-lg font-semibold text-gray-900">
                  Recorded Vitals
                </h2>
              </div>
              {isClinical && (
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Record Vitals
              </button>
              )}
            </div>

            {/* Early warning scores — existing /internal/v1/ews */}
            <div className="rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-amber-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-gray-900">Early warning scores</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowEwsForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-1.5 text-sm font-medium text-amber-950 hover:bg-amber-100"
                  >
                    <Plus className="h-4 w-4" />
                    Record EWS
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Scores are stored per patient in <code className="rounded bg-gray-100 px-1">early_warning_scores</code> via the
                experience BFF. Escalation flags follow server rules (score ≥ 7 → escalation).
              </p>

              {ewsError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load EWS history</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchEws()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}

              {showEwsForm && isClinical && (
                <form onSubmit={handleEwsSubmit} className="mt-4 space-y-3 rounded-lg border border-gray-100 bg-gray-50/80 p-4">
                  <div className="flex flex-wrap items-end gap-3">
                    <label className="text-xs font-medium text-gray-600">
                      Total score (NEWS2)
                      <input
                        type="number"
                        min={0}
                        max={30}
                        required
                        value={ewsTotal}
                        onChange={(e) => setEwsTotal(e.target.value)}
                        className="mt-1 block w-32 rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      />
                    </label>
                    <button
                      type="submit"
                      disabled={recordEws.isPending}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
                    >
                      {recordEws.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                      Save score
                    </button>
                  </div>
                  {recordEws.isError && <p className="text-xs text-red-600">Failed to save EWS. Check BFF and try again.</p>}
                </form>
              )}

              {ewsLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading scores…
                </div>
              ) : ewsRows.length === 0 ? (
                <p className="mt-4 text-sm text-gray-500">No early warning scores recorded for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-gray-100">
                  <table className="w-full min-w-[480px] text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wide text-gray-500">
                        <th className="px-3 py-2">Recorded</th>
                        <th className="px-3 py-2">Type</th>
                        <th className="px-3 py-2">Score</th>
                        <th className="px-3 py-2">Risk</th>
                        <th className="px-3 py-2">Escalation</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {ewsRows.map((row, i) => {
                        const id = String(row.id ?? `ews-${i}`);
                        const rec = row.recorded_at != null ? String(row.recorded_at) : "";
                        const when = rec ? new Date(rec).toLocaleString() : "—";
                        const score = row.total_score != null ? Number(row.total_score) : null;
                        const risk = row.risk_level != null ? String(row.risk_level) : "—";
                        const esc = Boolean(row.escalation_required);
                        const st = row.score_type != null ? String(row.score_type) : "—";
                        return (
                          <tr key={id} className="hover:bg-gray-50/80">
                            <td className="px-3 py-2 text-gray-800">{when}</td>
                            <td className="px-3 py-2 text-gray-700">{st}</td>
                            <td className="px-3 py-2 font-medium text-gray-900">{score != null && !Number.isNaN(score) ? score : "—"}</td>
                            <td className="px-3 py-2">
                              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${riskBadgeClass(risk)}`}>{risk}</span>
                            </td>
                            <td className="px-3 py-2 text-gray-700">{esc ? "Yes" : "No"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Fluid balance — existing /internal/v1/fluid-balance */}
            <div className="rounded-lg border border-cyan-200/80 bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Droplets className="h-5 w-5 text-cyan-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-gray-900">Fluid balance</h2>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="text-xs font-medium text-gray-600">
                    Day
                    <input
                      type="date"
                      value={fluidDate}
                      onChange={(e) => setFluidDate(e.target.value)}
                      className="ml-2 rounded-lg border border-gray-300 px-2 py-1 text-sm"
                    />
                  </label>
                  {isClinical && (
                    <button
                      type="button"
                      onClick={() => setShowFluidForm((p) => !p)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-cyan-200 bg-cyan-50 px-3 py-1.5 text-sm font-medium text-cyan-950 hover:bg-cyan-100"
                    >
                      <Plus className="h-4 w-4" />
                      Add I/O
                    </button>
                  )}
                </div>
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Intake and output volumes for the selected calendar day via{" "}
                <code className="rounded bg-gray-100 px-1">fluid_balance_records</code>.
              </p>

              {fluidError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load fluid balance</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchFluid()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}

              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                <div className="rounded-lg border border-gray-100 bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-gray-500">Intake (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.totalIntake}</p>
                </div>
                <div className="rounded-lg border border-gray-100 bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-gray-500">Output (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.totalOutput}</p>
                </div>
                <div className="rounded-lg border border-gray-100 bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-gray-500">Balance (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.balance}</p>
                </div>
              </div>

              {showFluidForm && isClinical && (
                <form onSubmit={handleFluidSubmit} className="mt-4 space-y-3 rounded-lg border border-gray-100 bg-gray-50/80 p-4">
                  <div className="flex flex-wrap gap-3">
                    <label className="text-xs font-medium text-gray-600">
                      Type
                      <select
                        value={fluidEntryType}
                        onChange={(e) => {
                          const v = e.target.value as "INTAKE" | "OUTPUT";
                          setFluidEntryType(v);
                          setFluidCategory(v === "INTAKE" ? "ORAL" : "URINE");
                        }}
                        className="mt-1 block rounded-lg border border-gray-300 px-2 py-2 text-sm"
                      >
                        <option value="INTAKE">Intake</option>
                        <option value="OUTPUT">Output</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-gray-600">
                      Category
                      <select
                        value={fluidCategory}
                        onChange={(e) => setFluidCategory(e.target.value)}
                        className="mt-1 block min-w-[8rem] rounded-lg border border-gray-300 px-2 py-2 text-sm"
                      >
                        {fluidEntryType === "INTAKE" ? (
                          <>
                            <option value="ORAL">Oral</option>
                            <option value="IV">IV</option>
                            <option value="NG">NG</option>
                            <option value="ENTERAL">Enteral</option>
                            <option value="OTHER">Other</option>
                          </>
                        ) : (
                          <>
                            <option value="URINE">Urine</option>
                            <option value="DRAIN">Drain</option>
                            <option value="EMESIS">Emesis</option>
                            <option value="OTHER">Other</option>
                          </>
                        )}
                      </select>
                    </label>
                    <label className="text-xs font-medium text-gray-600">
                      Volume (ml)
                      <input
                        type="number"
                        min={1}
                        required
                        value={fluidVolume}
                        onChange={(e) => setFluidVolume(e.target.value)}
                        className="mt-1 block w-28 rounded-lg border border-gray-300 px-2 py-2 text-sm"
                      />
                    </label>
                  </div>
                  <label className="block text-xs font-medium text-gray-600">
                    Note (optional)
                    <input
                      value={fluidDesc}
                      onChange={(e) => setFluidDesc(e.target.value)}
                      className="mt-1 w-full max-w-md rounded-lg border border-gray-300 px-3 py-2 text-sm"
                    />
                  </label>
                  <div className="flex items-center gap-2">
                    <button
                      type="submit"
                      disabled={recordFluid.isPending}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-cyan-700 px-4 py-2 text-sm font-medium text-white hover:bg-cyan-800 disabled:opacity-50"
                    >
                      {recordFluid.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                      Save entry
                    </button>
                    {recordFluid.isError && <span className="text-xs text-red-600">Save failed.</span>}
                  </div>
                </form>
              )}

              {fluidLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading fluid balance…
                </div>
              ) : fluidRows.length === 0 ? (
                <p className="mt-4 text-sm text-gray-500">No intake or output entries for this day.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-gray-100">
                  <table className="w-full min-w-[520px] text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wide text-gray-500">
                        <th className="px-3 py-2">Time</th>
                        <th className="px-3 py-2">I/O</th>
                        <th className="px-3 py-2">Category</th>
                        <th className="px-3 py-2">Volume (ml)</th>
                        <th className="px-3 py-2">Note</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {fluidRows.map((row, i) => {
                        const id = String(row.id ?? `fluid-${i}`);
                        const rec = row.recorded_at != null ? String(row.recorded_at) : "";
                        const when = rec ? new Date(rec).toLocaleString() : "—";
                        const et = row.entry_type != null ? String(row.entry_type) : "—";
                        const cat = row.category != null ? String(row.category) : "—";
                        const ml = row.volume_ml != null ? Number(row.volume_ml) : null;
                        const desc = row.description != null ? String(row.description) : "";
                        return (
                          <tr key={id} className="hover:bg-gray-50/80">
                            <td className="px-3 py-2 text-gray-800">{when}</td>
                            <td className="px-3 py-2 font-medium text-gray-900">{et}</td>
                            <td className="px-3 py-2 text-gray-700">{cat}</td>
                            <td className="px-3 py-2 text-gray-900">{ml != null && !Number.isNaN(ml) ? ml : "—"}</td>
                            <td className="px-3 py-2 text-gray-600">{desc || "—"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Observation entries — GET/POST /internal/v1/observations */}
            <div className="rounded-lg border border-violet-200/80 bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ClipboardList className="h-5 w-5 text-violet-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-gray-900">Observation entries</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowObsForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-violet-200 bg-violet-50 px-3 py-1.5 text-sm font-medium text-violet-950 hover:bg-violet-100"
                  >
                    <Plus className="h-4 w-4" />
                    Add entry
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Structured chart rows in <code className="rounded bg-gray-100 px-1">observation_entries</code>. Parameters are stored as JSON; free text is saved under{" "}
                <code className="rounded bg-gray-100 px-1">summary</code>.
              </p>
              {obsError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load observations</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchObs()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}
              {showObsForm && isClinical && (
                <form onSubmit={handleObsSubmit} className="mt-4 space-y-3 rounded-lg border border-gray-100 bg-gray-50/80 p-4">
                  <label className="text-xs font-medium text-gray-600">
                    Chart type
                    <select
                      value={obsChartType}
                      onChange={(e) => setObsChartType(e.target.value)}
                      className="mt-1 block rounded-lg border border-gray-300 px-2 py-2 text-sm"
                    >
                      <option value="VITALS">Vitals</option>
                      <option value="PAIN">Pain</option>
                      <option value="NEURO">Neuro</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </label>
                  <label className="block text-xs font-medium text-gray-600">
                    Summary / note
                    <textarea
                      required
                      rows={2}
                      value={obsSummary}
                      onChange={(e) => setObsSummary(e.target.value)}
                      className="mt-1 w-full max-w-lg rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      placeholder="What was observed"
                    />
                  </label>
                  <button
                    type="submit"
                    disabled={recordObs.isPending}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-violet-700 px-4 py-2 text-sm font-medium text-white hover:bg-violet-800 disabled:opacity-50"
                  >
                    {recordObs.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Save observation
                  </button>
                  {recordObs.isError && <p className="text-xs text-red-600">Save failed.</p>}
                </form>
              )}
              {obsLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading observations…
                </div>
              ) : obsRows.length === 0 ? (
                <p className="mt-4 text-sm text-gray-500">No observation entries for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-gray-100">
                  <table className="w-full min-w-[520px] text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wide text-gray-500">
                        <th className="px-3 py-2">Recorded</th>
                        <th className="px-3 py-2">Chart</th>
                        <th className="px-3 py-2">Parameters</th>
                        <th className="px-3 py-2">By</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {obsRows.map((row, i) => {
                        const id = String(row.id ?? `obs-${i}`);
                        const rec = row.recorded_at != null ? String(row.recorded_at) : "";
                        const when = rec ? new Date(rec).toLocaleString() : "—";
                        const ct = row.chart_type != null ? String(row.chart_type) : "—";
                        const by = row.recorded_by != null ? String(row.recorded_by) : "—";
                        return (
                          <tr key={id} className="hover:bg-gray-50/80">
                            <td className="px-3 py-2 text-gray-800">{when}</td>
                            <td className="px-3 py-2 font-medium text-gray-900">{ct}</td>
                            <td className="max-w-xs truncate px-3 py-2 font-mono text-xs text-gray-700">{formatParamsCell(row.parameters)}</td>
                            <td className="px-3 py-2 text-gray-600">{by}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Patient transfers — GET/POST /internal/v1/transfers */}
            <div className="rounded-lg border border-slate-300 bg-white p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Truck className="h-5 w-5 text-slate-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-gray-900">Transfers</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowXferForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-slate-50 px-3 py-1.5 text-sm font-medium text-slate-900 hover:bg-slate-100"
                  >
                    <Plus className="h-4 w-4" />
                    Request transfer
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-gray-500">
                Internal transfer requests for this patient. Ward/bed UUIDs are optional in the API; this form logs clinical intent and notes only.
              </p>
              {xferError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-950">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load transfers</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchXfer()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}
              {showXferForm && isClinical && (
                <form onSubmit={handleXferSubmit} className="mt-4 space-y-3 rounded-lg border border-gray-100 bg-gray-50/80 p-4">
                  <label className="text-xs font-medium text-gray-600">
                    Reason
                    <select
                      value={xferReason}
                      onChange={(e) => setXferReason(e.target.value)}
                      className="mt-1 block rounded-lg border border-gray-300 px-2 py-2 text-sm"
                    >
                      <option value="CLINICAL">Clinical</option>
                      <option value="ADMIN">Administrative</option>
                      <option value="CAPACITY">Capacity</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </label>
                  <label className="block text-xs font-medium text-gray-600">
                    Clinical notes
                    <textarea
                      required
                      rows={2}
                      value={xferNotes}
                      onChange={(e) => setXferNotes(e.target.value)}
                      className="mt-1 w-full max-w-lg rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      placeholder="Where and why the patient should move"
                    />
                  </label>
                  <button
                    type="submit"
                    disabled={requestXfer.isPending}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-900 disabled:opacity-50"
                  >
                    {requestXfer.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Submit request
                  </button>
                  {requestXfer.isError && <p className="text-xs text-red-600">Request failed.</p>}
                </form>
              )}
              {xferLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-gray-500">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading transfers…
                </div>
              ) : xferRows.length === 0 ? (
                <p className="mt-4 text-sm text-gray-500">No transfer records for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-gray-100">
                  <table className="w-full min-w-[560px] text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wide text-gray-500">
                        <th className="px-3 py-2">Requested</th>
                        <th className="px-3 py-2">Status</th>
                        <th className="px-3 py-2">Reason</th>
                        <th className="px-3 py-2">Type</th>
                        <th className="px-3 py-2">Notes</th>
                        <th className="px-3 py-2">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {xferRows.map((row, i) => {
                        const id = String(row.id ?? `xfer-${i}`);
                        const rq = row.requested_at != null ? String(row.requested_at) : "";
                        const when = rq ? new Date(rq).toLocaleString() : "—";
                        const st = row.status != null ? String(row.status) : "—";
                        const reason = row.reason != null ? String(row.reason) : "—";
                        const tt = row.transfer_type != null ? String(row.transfer_type) : "—";
                        const cn = row.clinical_notes != null ? String(row.clinical_notes) : "";
                        const open = st.toUpperCase() === "REQUESTED";
                        return (
                          <tr key={id} className="hover:bg-gray-50/80">
                            <td className="px-3 py-2 text-gray-800">{when}</td>
                            <td className="px-3 py-2">
                              <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-800">{st}</span>
                            </td>
                            <td className="px-3 py-2 text-gray-800">{reason}</td>
                            <td className="px-3 py-2 text-gray-700">{tt}</td>
                            <td className="max-w-[200px] truncate px-3 py-2 text-gray-600" title={cn}>
                              {cn || "—"}
                            </td>
                            <td className="px-3 py-2">
                              {open && isClinical && row.id ? (
                                <button
                                  type="button"
                                  onClick={() =>
                                    acceptXfer.mutate({
                                      id: String(row.id),
                                      patientId,
                                      acceptedBy: user?.id ?? user?.displayName ?? "unknown",
                                    })
                                  }
                                  disabled={acceptXfer.isPending}
                                  className="text-xs font-medium text-blue-700 underline disabled:opacity-50"
                                >
                                  Accept
                                </button>
                              ) : (
                                <span className="text-xs text-gray-400">—</span>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Vitals Trend Charts */}
            {vitals.length >= 2 && (
              <VitalsTrendPanel vitals={vitals.map((v) => ({ attributes: v.attributes as Record<string, unknown> }))} />
            )}

            {/* New vitals form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <h3 className="font-medium text-gray-900 mb-4">New Vitals Entry</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Systolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={systolic}
                        onChange={(e) => setSystolic(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="120"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Diastolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={diastolic}
                        onChange={(e) => setDiastolic(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="80"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Heart Rate (bpm)
                      </label>
                      <input
                        type="number"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="72"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Temperature (°C)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="36.6"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Resp. Rate (breaths/min)
                      </label>
                      <input
                        type="number"
                        value={respiratoryRate}
                        onChange={(e) => setRespiratoryRate(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="16"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        SpO2 (%)
                      </label>
                      <input
                        type="number"
                        value={oxygenSaturation}
                        onChange={(e) => setOxygenSaturation(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="98"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Weight (kg)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={weight}
                        onChange={(e) => setWeight(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Height (cm)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={height}
                        onChange={(e) => setHeight(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="170"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Pain Score (0-10)
                      </label>
                      <input
                        type="number"
                        min="0"
                        max="10"
                        value={painScore}
                        onChange={(e) => setPainScore(e.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="0"
                      />
                    </div>
                    <div className="col-span-2 md:col-span-3 lg:col-span-5">
                      <label className="block text-xs font-medium text-gray-600 mb-1">
                        Notes
                      </label>
                      <textarea
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        rows={2}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        placeholder="Additional observations..."
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-3 pt-2">
                    <button
                      type="submit"
                      disabled={recordVitals.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    >
                      {recordVitals.isPending && (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      )}
                      Save Vitals
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        resetForm();
                        setShowForm(false);
                      }}
                      className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>

                  {recordVitals.isError && (
                    <p className="text-sm text-red-600">
                      Failed to record vitals. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Vitals table */}
            {vitals.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <Activity className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No vitals recorded yet</p>
              </div>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Date
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          BP (mmHg)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          HR (bpm)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Temp (°C)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          RR (breaths/min)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          SpO2 (%)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">
                          Weight (kg)
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {vitals.map((v) => {
                        const a = v.attributes;
                        const bp =
                          a.systolic != null && a.diastolic != null
                            ? `${a.systolic}/${a.diastolic}`
                            : "—";
                        return (
                          <tr
                            key={v.id}
                            className="border-b border-gray-100 hover:bg-gray-50 transition-colors"
                          >
                            <td className="px-4 py-3 text-gray-900">
                              {new Date(a.recordedAt).toLocaleString()}
                            </td>
                            <td className="px-4 py-3 text-gray-700">{bp}</td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.heartRate)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.temperature)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.respiratoryRate)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.oxygenSaturation)}
                            </td>
                            <td className="px-4 py-3 text-gray-700">
                              {fmt(a.weight)}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
