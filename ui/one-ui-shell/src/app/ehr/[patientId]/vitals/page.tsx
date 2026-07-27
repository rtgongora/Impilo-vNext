"use client";

/**
 * Vitals — View past vitals and record new readings.
 * Route: /ehr/[patientId]/vitals | pageTitle: "Vitals"
 *
 * Coherence audit: verified 2026-04-11 — all 9 sections (vitals, EWS, fluid balance,
 * observations, labour monitoring, patient transfers, Apgar, CTG, partograph) render
 * independently with correct hook bindings. No cross-section data conflicts.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, AlertTriangle, Baby, ClipboardList, Droplets, HeartPulse, Loader2, Plus, Truck } from "lucide-react";
import { StickyActionBar } from "shared-ui";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEarlyWarningScores, useRecordEWS } from "@/hooks/queries/useEWS";
import { useFluidBalance, useRecordFluid } from "@/hooks/queries/useFluidBalance";
import {
  useLabourMonitoring,
  useRecordLabourMonitoring,
} from "@/hooks/queries/useLabourMonitoring";
import { useObservations, useRecordObservation } from "@/hooks/queries/useObservations";
import {
  useAcceptPatientTransfer,
  usePatientTransfers,
  useRequestPatientTransfer,
} from "@/hooks/queries/usePatientTransfers";
import { useApgarScores, useRecordApgar } from "@/hooks/queries/useApgar";
import {
  useVitals,
  useRecordVitals,
  type VitalsResource,
} from "@/hooks/queries/useVitals";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { VitalsTrendPanel } from "@/components/VitalsTrendChart";
import { VitalsCtgSection } from "@/features/maternity/ctg/VitalsCtgSection";
import { VitalsPartographSection } from "@/features/maternity/partograph/VitalsPartographSection";

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
  const { data: vitalsData, isLoading, isError: vitalsUnavailable } = useVitals(patientId);
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

  const [labourShowAllPatientHistory, setLabourShowAllPatientHistory] = useState(false);
  const labourListEncounterId =
    activeEncounter && encounterId && !labourShowAllPatientHistory ? encounterId : null;
  const {
    data: labourData,
    isLoading: labourLoading,
    isError: labourError,
    refetch: refetchLabour,
  } = useLabourMonitoring(patientId, labourListEncounterId);
  const recordLabour = useRecordLabourMonitoring();
  const labourRows = labourData?.data ?? [];
  const latestLabour = labourRows[0];

  const {
    data: xferData,
    isLoading: xferLoading,
    isError: xferError,
    refetch: refetchXfer,
  } = usePatientTransfers(patientId);
  const requestXfer = useRequestPatientTransfer();
  const acceptXfer = useAcceptPatientTransfer();
  const xferRows = xferData?.data ?? [];

  const {
    data: apgarData,
    isLoading: apgarLoading,
    isError: apgarError,
    refetch: refetchApgar,
  } = useApgarScores(patientId);
  const recordApgar = useRecordApgar();
  const apgarRows = apgarData?.data ?? [];

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
  const [showApgarForm, setShowApgarForm] = useState(false);
  const [apgarMinute, setApgarMinute] = useState("1");
  const [apgarAppearance, setApgarAppearance] = useState("");
  const [apgarPulse, setApgarPulse] = useState("");
  const [apgarGrimace, setApgarGrimace] = useState("");
  const [apgarActivity, setApgarActivity] = useState("");
  const [apgarRespiration, setApgarRespiration] = useState("");
  const [showLabourForm, setShowLabourForm] = useState(false);
  const [labourPhase, setLabourPhase] = useState("ACTIVE_LABOUR");
  const [labourFetalHeart, setLabourFetalHeart] = useState("");
  const [labourContractions, setLabourContractions] = useState("");
  const [labourDuration, setLabourDuration] = useState("");
  const [labourCervix, setLabourCervix] = useState("");
  const [labourDescent, setLabourDescent] = useState("");
  const [labourPulse, setLabourPulse] = useState("");
  const [labourSystolic, setLabourSystolic] = useState("");
  const [labourDiastolic, setLabourDiastolic] = useState("");
  const [labourTemperature, setLabourTemperature] = useState("");
  const [labourLiquor, setLabourLiquor] = useState("CLEAR");
  const [labourMoulding, setLabourMoulding] = useState("NONE");
  const [labourCaput, setLabourCaput] = useState("NONE");
  const [labourNotes, setLabourNotes] = useState("");

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

  function handleApgarSubmit(e: React.FormEvent) {
    e.preventDefault();
    const minute = Number(apgarMinute);
    const appearance = Number(apgarAppearance);
    const pulse = Number(apgarPulse);
    const grimace = Number(apgarGrimace);
    const activity = Number(apgarActivity);
    const respiration = Number(apgarRespiration);
    const fields = [appearance, pulse, grimace, activity, respiration];
    if (!Number.isFinite(minute) || fields.some((n) => !Number.isFinite(n))) return;
    if (fields.some((n) => n < 0 || n > 2)) return;
    recordApgar.mutate(
      {
        patientId,
        encounterId: encounterId || null,
        minute: Math.round(minute),
        appearance: Math.round(appearance),
        pulse: Math.round(pulse),
        grimace: Math.round(grimace),
        activity: Math.round(activity),
        respiration: Math.round(respiration),
      },
      {
        onSuccess: () => {
          setApgarAppearance("");
          setApgarPulse("");
          setApgarGrimace("");
          setApgarActivity("");
          setApgarRespiration("");
          setShowApgarForm(false);
        },
      },
    );
  }

  function parseOptionalNumber(value: string) {
    const trimmed = value.trim();
    if (!trimmed) return null;
    const number = Number(trimmed);
    return Number.isFinite(number) ? number : null;
  }

  function handleLabourSubmit(e: React.FormEvent) {
    e.preventDefault();
    recordLabour.mutate(
      {
        patientId,
        encounterId: encounterId || null,
        phase: labourPhase,
        recordedBy: user?.id ?? user?.displayName ?? "unknown",
        fetalHeartRateBpm: parseOptionalNumber(labourFetalHeart),
        contractionFrequency10Min: parseOptionalNumber(labourContractions),
        contractionDurationSec: parseOptionalNumber(labourDuration),
        cervicalDilationCm: parseOptionalNumber(labourCervix),
        fetalDescentFifths: parseOptionalNumber(labourDescent),
        maternalPulseBpm: parseOptionalNumber(labourPulse),
        systolicBp: parseOptionalNumber(labourSystolic),
        diastolicBp: parseOptionalNumber(labourDiastolic),
        temperatureC: parseOptionalNumber(labourTemperature),
        liquor: labourLiquor,
        moulding: labourMoulding,
        caput: labourCaput,
        notes: labourNotes.trim() || null,
      },
      {
        onSuccess: () => {
          setLabourPhase("ACTIVE_LABOUR");
          setLabourFetalHeart("");
          setLabourContractions("");
          setLabourDuration("");
          setLabourCervix("");
          setLabourDescent("");
          setLabourPulse("");
          setLabourSystolic("");
          setLabourDiastolic("");
          setLabourTemperature("");
          setLabourLiquor("CLEAR");
          setLabourMoulding("NONE");
          setLabourCaput("NONE");
          setLabourNotes("");
          setShowLabourForm(false);
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
    if (u === "MEDIUM") return "bg-amber-100 text-warning-foreground";
    if (u === "LOW") return "bg-primary-soft text-primary-hover";
    return "bg-neutral-100 text-foreground";
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
        subtitle="Vitals, maternity partograph & CTG, labour compatibility rows, EWS, neonatal APGAR, fluid I/O, observation entries, and transfer requests — live BFF data for this patient"
      >

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading vitals...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header row with action button */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-5 h-5 text-red-600" />
                <h2 className="text-lg font-semibold text-foreground">
                  Recorded Vitals
                </h2>
              </div>
              {isClinical && (
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover transition-colors"
              >
                <Plus className="w-4 h-4" />
                Record Vitals
              </button>
              )}
            </div>

            {/* Maternity: partograph + CTG (same feature family) + legacy labour rows */}
            <section
              className="rounded-lg border border-pink-200/90 bg-card p-5"
              aria-labelledby="labour-monitoring-heading"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <HeartPulse className="h-5 w-5 text-pink-600" aria-hidden />
                  <h2 id="labour-monitoring-heading" className="text-lg font-semibold text-foreground">
                    Maternity: partograph, CTG & labour
                  </h2>
                </div>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Partograph: <code className="rounded bg-neutral-100 px-1">/internal/v1/maternity/partograph/sessions</code> (cervical
                progress plot). CTG: <code className="rounded bg-neutral-100 px-1">/internal/v1/maternity/ctg/sessions</code> with
                polled trace chunks — <span className="font-medium text-foreground">not a websocket stream</span>. Legacy flat rows
                remain under the fold for <code className="rounded bg-neutral-100 px-1">labour_monitoring_entries</code> only.
              </p>

              <VitalsPartographSection
                patientId={patientId}
                encounterId={encounterId}
                hasActiveEncounter={Boolean(activeEncounter && encounterId)}
                isClinical={isClinical}
                recordedBy={user?.id ?? user?.displayName ?? "unknown"}
              />

              <VitalsCtgSection
                patientId={patientId}
                encounterId={encounterId}
                hasActiveEncounter={Boolean(activeEncounter && encounterId)}
                isClinical={isClinical}
                recordedBy={user?.id ?? user?.displayName ?? "unknown"}
              />

              <details className="mt-5 rounded-lg border border-dashed border-pink-200/80 bg-pink-50/30 p-3">
                <summary className="cursor-pointer text-sm font-medium text-pink-950">
                  Legacy labour rows (<code className="text-xs">labour_monitoring_entries</code>) — compatibility only
                </summary>
                <p className="mt-2 text-xs text-muted-foreground">
                  Flat table via <code className="rounded bg-neutral-100 px-1">GET/POST /internal/v1/labour-monitoring</code>. Not
                  the canonical plotted record; prefer partograph sessions above.
                </p>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  {activeEncounter && (
                    <button
                      type="button"
                      onClick={() => setLabourShowAllPatientHistory((p) => !p)}
                      className="rounded-lg border border-pink-200 bg-card px-3 py-1.5 text-xs font-medium text-pink-900 hover:bg-pink-50"
                    >
                      {labourShowAllPatientHistory
                        ? "Show this encounter only"
                        : "Show full patient labour history"}
                    </button>
                  )}
                  {isClinical && (
                    <button
                      type="button"
                      onClick={() => setShowLabourForm((p) => !p)}
                      className="inline-flex items-center gap-1.5 rounded-lg border border-pink-200 bg-pink-50 px-3 py-1.5 text-sm font-medium text-pink-950 hover:bg-pink-100"
                    >
                      <Plus className="h-4 w-4" />
                      Record legacy labour row
                    </button>
                  )}
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  {activeEncounter ? (
                    labourShowAllPatientHistory ? (
                      <>
                        Showing <span className="font-medium">all legacy rows</span> for this patient (GET without{" "}
                        <code className="rounded bg-neutral-100 px-1">encounterId</code>).
                      </>
                    ) : (
                      <>
                        Filtered to <span className="font-medium">active encounter</span>{" "}
                        <code className="rounded bg-neutral-100 px-1">{encounterId}</code>.
                      </>
                    )
                  ) : (
                    <>
                      No active encounter; legacy list shows <span className="font-medium">all rows</span> for this patient.
                    </>
                  )}
                </p>

              {labourError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load labour monitoring</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchLabour()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}

              {showLabourForm && isClinical && (
                <form onSubmit={handleLabourSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <label className="text-xs font-medium text-muted-foreground">
                      Phase
                      <select
                        value={labourPhase}
                        onChange={(e) => setLabourPhase(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      >
                        <option value="LATENT_LABOUR">Latent labour</option>
                        <option value="ACTIVE_LABOUR">Active labour</option>
                        <option value="SECOND_STAGE">Second stage</option>
                        <option value="POSTPARTUM">Postpartum monitoring</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Fetal heart bpm
                      <input value={labourFetalHeart} onChange={(e) => setLabourFetalHeart(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Contractions / 10 min
                      <input value={labourContractions} onChange={(e) => setLabourContractions(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Duration sec
                      <input value={labourDuration} onChange={(e) => setLabourDuration(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Cervical dilation cm
                      <input value={labourCervix} onChange={(e) => setLabourCervix(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Descent fifths
                      <input value={labourDescent} onChange={(e) => setLabourDescent(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Maternal pulse
                      <input value={labourPulse} onChange={(e) => setLabourPulse(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Temperature C
                      <input value={labourTemperature} onChange={(e) => setLabourTemperature(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Systolic BP
                      <input value={labourSystolic} onChange={(e) => setLabourSystolic(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Diastolic BP
                      <input value={labourDiastolic} onChange={(e) => setLabourDiastolic(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" />
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Liquor
                      <select value={labourLiquor} onChange={(e) => setLabourLiquor(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                        <option value="CLEAR">Clear</option>
                        <option value="MECONIUM">Meconium</option>
                        <option value="BLOOD_STAINED">Blood-stained</option>
                        <option value="ABSENT">Absent / drained</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Moulding
                      <select value={labourMoulding} onChange={(e) => setLabourMoulding(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                        <option value="NONE">None</option>
                        <option value="+">+</option>
                        <option value="++">++</option>
                        <option value="+++">+++</option>
                      </select>
                    </label>
                    <label className="text-xs font-medium text-muted-foreground">
                      Caput
                      <select value={labourCaput} onChange={(e) => setLabourCaput(e.target.value)} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm">
                        <option value="NONE">None</option>
                        <option value="+">+</option>
                        <option value="++">++</option>
                        <option value="+++">+++</option>
                      </select>
                    </label>
                  </div>
                  <label className="block text-xs font-medium text-muted-foreground">
                    Notes
                    <textarea value={labourNotes} onChange={(e) => setLabourNotes(e.target.value)} rows={2} className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm" placeholder="Maternal condition, escalation, membrane status, augmentation notes..." />
                  </label>
                  <button
                    type="submit"
                    disabled={recordLabour.isPending}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-pink-600 px-4 py-2 text-sm font-medium text-white hover:bg-pink-700 disabled:opacity-50"
                  >
                    {recordLabour.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Save legacy labour row
                  </button>
                  {recordLabour.isError && (
                    <p className="text-xs text-red-600">Failed to save legacy labour row. Check BFF and try again.</p>
                  )}
                </form>
              )}

              {labourLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading legacy labour rows…
                </div>
              ) : labourRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No labour monitoring rows recorded for this patient yet.</p>
              ) : (
                <>
                  {latestLabour && (
                    <div className="mt-4 rounded-xl border border-pink-100 bg-gradient-to-r from-pink-50/90 to-card px-4 py-3">
                      <p className="text-xs font-semibold uppercase tracking-wide text-pink-900">Latest reading</p>
                      <p className="mt-1 text-xs text-muted-foreground">
                        {latestLabour.recorded_at
                          ? new Date(String(latestLabour.recorded_at)).toLocaleString()
                          : "—"}
                        {latestLabour.phase ? ` · ${latestLabour.phase}` : ""}
                        {latestLabour.derived_stage ? ` · derived ${latestLabour.derived_stage}` : ""}
                      </p>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-foreground ring-1 ring-pink-100">
                          FHR {latestLabour.fetal_heart_rate_bpm ?? "—"} bpm
                        </span>
                        <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-foreground ring-1 ring-pink-100">
                          Cx {latestLabour.cervical_dilation_cm ?? "—"} cm
                        </span>
                        <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-foreground ring-1 ring-pink-100">
                          Ctx {latestLabour.contraction_frequency_10min ?? "—"}/10 min
                        </span>
                        <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-foreground ring-1 ring-pink-100">
                          BP{" "}
                          {latestLabour.systolic_bp != null || latestLabour.diastolic_bp != null
                            ? `${latestLabour.systolic_bp ?? "—"}/${latestLabour.diastolic_bp ?? "—"}`
                            : "—"}
                        </span>
                        <span className="rounded-full bg-card px-3 py-1 text-xs font-medium text-foreground ring-1 ring-pink-100">
                          Temp {latestLabour.temperature_c ?? "—"} °C
                        </span>
                      </div>
                      {Array.isArray(latestLabour.alert_flags) && latestLabour.alert_flags.length > 0 ? (
                        <div className="mt-3 rounded-lg border border-danger/28 bg-danger-soft px-3 py-2 text-xs font-medium text-red-900">
                          Alerts: {latestLabour.alert_flags.join(", ")}
                        </div>
                      ) : null}
                    </div>
                  )}
                  <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                    <table className="w-full min-w-[980px] text-sm">
                      <thead>
                        <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          <th className="px-3 py-2">Recorded</th>
                          <th className="px-3 py-2">Phase</th>
                          <th className="px-3 py-2">Derived stage</th>
                          <th className="px-3 py-2">FHR</th>
                          <th className="px-3 py-2">Ctx/10</th>
                          <th className="px-3 py-2">Duration</th>
                          <th className="px-3 py-2">Cx cm</th>
                          <th className="px-3 py-2">BP</th>
                          <th className="px-3 py-2">Temp</th>
                          <th className="px-3 py-2">Liquor</th>
                          <th className="px-3 py-2">Alerts</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {labourRows.map((row, i) => {
                          const id = String(row.id ?? `labour-${i}`);
                          const rec = row.recorded_at != null ? String(row.recorded_at) : "";
                          const when = rec ? new Date(rec).toLocaleString() : "—";
                          const alerts = Array.isArray(row.alert_flags) ? row.alert_flags : [];
                          return (
                            <tr key={id} className="hover:bg-background/80">
                              <td className="px-3 py-2 text-foreground">{when}</td>
                              <td className="px-3 py-2 text-foreground">{row.phase ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.derived_stage ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.fetal_heart_rate_bpm ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.contraction_frequency_10min ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.contraction_duration_sec ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.cervical_dilation_cm ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">
                                {row.systolic_bp != null || row.diastolic_bp != null
                                  ? `${row.systolic_bp ?? "—"}/${row.diastolic_bp ?? "—"}`
                                  : "—"}
                              </td>
                              <td className="px-3 py-2 text-foreground">{row.temperature_c ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">{row.liquor ?? "—"}</td>
                              <td className="px-3 py-2 text-foreground">
                                {alerts.length === 0 ? "None" : alerts.join(", ")}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </>
              )}
              </details>
            </section>

            {/* Early warning scores — existing /internal/v1/ews */}
            <div className="rounded-lg border border-border bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <AlertTriangle className="h-5 w-5 text-amber-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-foreground">Early warning scores</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowEwsForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-warning/35 bg-warning-soft px-3 py-1.5 text-sm font-medium text-warning-foreground hover:bg-amber-100"
                  >
                    <Plus className="h-4 w-4" />
                    Record EWS
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Scores are stored per patient in <code className="rounded bg-neutral-100 px-1">early_warning_scores</code> via the
                experience BFF. Escalation flags follow server rules (score ≥ 7 → escalation).
              </p>

              {ewsError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
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
                <form onSubmit={handleEwsSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <div className="flex flex-wrap items-end gap-3">
                    <label className="text-xs font-medium text-muted-foreground">
                      Total score (NEWS2)
                      <input
                        type="number"
                        min={0}
                        max={30}
                        required
                        value={ewsTotal}
                        onChange={(e) => setEwsTotal(e.target.value)}
                        className="mt-1 block w-32 rounded-lg border border-border px-3 py-2 text-sm"
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
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading scores…
                </div>
              ) : ewsRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No early warning scores recorded for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                  <table className="w-full min-w-[480px] text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
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
                          <tr key={id} className="hover:bg-background/80">
                            <td className="px-3 py-2 text-foreground">{when}</td>
                            <td className="px-3 py-2 text-foreground">{st}</td>
                            <td className="px-3 py-2 font-medium text-foreground">{score != null && !Number.isNaN(score) ? score : "—"}</td>
                            <td className="px-3 py-2">
                              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${riskBadgeClass(risk)}`}>{risk}</span>
                            </td>
                            <td className="px-3 py-2 text-foreground">{esc ? "Yes" : "No"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Neonatal APGAR — GET/POST /internal/v1/apgar (apgar_scores) */}
            <div className="rounded-lg border border-danger/28/90 bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Baby className="h-5 w-5 text-rose-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-foreground">Neonatal APGAR</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowApgarForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-danger/28 bg-danger-soft px-3 py-1.5 text-sm font-medium text-danger hover:bg-rose-100"
                  >
                    <Plus className="h-4 w-4" />
                    Record APGAR
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Maternity monitoring slice supported here: scores persist in{" "}
                <code className="rounded bg-neutral-100 px-1">apgar_scores</code> via{" "}
                <code className="rounded bg-neutral-100 px-1">/internal/v1/apgar</code>. Labour monitoring rows now persist separately;
                CTG traces are still not in the experience BFF.
              </p>

              {apgarError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <p className="font-medium">Could not load APGAR history</p>
                    <button type="button" className="mt-1 text-xs underline" onClick={() => void refetchApgar()}>
                      Retry
                    </button>
                  </div>
                </div>
              )}

              {showApgarForm && isClinical && (
                <form onSubmit={handleApgarSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-6">
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Minute
                      <input
                        type="number"
                        min={1}
                        max={10}
                        required
                        value={apgarMinute}
                        onChange={(e) => setApgarMinute(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Appearance (0–2)
                      <input
                        type="number"
                        min={0}
                        max={2}
                        required
                        value={apgarAppearance}
                        onChange={(e) => setApgarAppearance(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Pulse (0–2)
                      <input
                        type="number"
                        min={0}
                        max={2}
                        required
                        value={apgarPulse}
                        onChange={(e) => setApgarPulse(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Grimace (0–2)
                      <input
                        type="number"
                        min={0}
                        max={2}
                        required
                        value={apgarGrimace}
                        onChange={(e) => setApgarGrimace(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Activity (0–2)
                      <input
                        type="number"
                        min={0}
                        max={2}
                        required
                        value={apgarActivity}
                        onChange={(e) => setApgarActivity(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Respiration (0–2)
                      <input
                        type="number"
                        min={0}
                        max={2}
                        required
                        value={apgarRespiration}
                        onChange={(e) => setApgarRespiration(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                      />
                    </label>
                  </div>
                  <button
                    type="submit"
                    disabled={recordApgar.isPending}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
                  >
                    {recordApgar.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Save APGAR
                  </button>
                  {recordApgar.isError && (
                    <p className="text-xs text-red-600">Failed to save APGAR. Check BFF and try again.</p>
                  )}
                </form>
              )}

              {apgarLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading APGAR…
                </div>
              ) : apgarRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No APGAR scores recorded for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                  <table className="w-full min-w-[640px] text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        <th className="px-3 py-2">Recorded</th>
                        <th className="px-3 py-2">Min</th>
                        <th className="px-3 py-2">Ap</th>
                        <th className="px-3 py-2">P</th>
                        <th className="px-3 py-2">G</th>
                        <th className="px-3 py-2">Ac</th>
                        <th className="px-3 py-2">R</th>
                        <th className="px-3 py-2">Total</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {apgarRows.map((row, i) => {
                        const id = String(row.id ?? `apgar-${i}`);
                        const rec = row.recorded_at != null ? String(row.recorded_at) : "";
                        const when = rec ? new Date(rec).toLocaleString() : "—";
                        const total =
                          row.total != null
                            ? Number(row.total)
                            : Number(row.appearance) +
                              Number(row.pulse) +
                              Number(row.grimace) +
                              Number(row.activity) +
                              Number(row.respiration);
                        return (
                          <tr key={id} className="hover:bg-background/80">
                            <td className="px-3 py-2 text-foreground">{when}</td>
                            <td className="px-3 py-2 text-foreground">{row.minute}</td>
                            <td className="px-3 py-2 text-foreground">{row.appearance}</td>
                            <td className="px-3 py-2 text-foreground">{row.pulse}</td>
                            <td className="px-3 py-2 text-foreground">{row.grimace}</td>
                            <td className="px-3 py-2 text-foreground">{row.activity}</td>
                            <td className="px-3 py-2 text-foreground">{row.respiration}</td>
                            <td className="px-3 py-2 font-medium text-foreground">{Number.isFinite(total) ? total : "—"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Fluid balance — existing /internal/v1/fluid-balance */}
            <div className="rounded-lg border border-cyan-200/80 bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Droplets className="h-5 w-5 text-cyan-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-foreground">Fluid balance</h2>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <label className="text-xs font-medium text-muted-foreground">
                    Day
                    <input
                      type="date"
                      value={fluidDate}
                      onChange={(e) => setFluidDate(e.target.value)}
                      className="ml-2 rounded-lg border border-border px-2 py-1 text-sm"
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
              <p className="mt-1 text-xs text-muted-foreground">
                Intake and output volumes for the selected calendar day via{" "}
                <code className="rounded bg-neutral-100 px-1">fluid_balance_records</code>.
              </p>

              {fluidError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
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
                <div className="rounded-lg border border-border bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-muted-foreground">Intake (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.totalIntake}</p>
                </div>
                <div className="rounded-lg border border-border bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-muted-foreground">Output (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.totalOutput}</p>
                </div>
                <div className="rounded-lg border border-border bg-cyan-50/50 px-3 py-2 text-sm">
                  <p className="text-xs font-medium text-muted-foreground">Balance (ml)</p>
                  <p className="text-lg font-semibold text-cyan-900">{fluidSummary.balance}</p>
                </div>
              </div>

              {showFluidForm && isClinical && (
                <form onSubmit={handleFluidSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Type
                      <select
                        value={fluidEntryType}
                        onChange={(e) => {
                          const v = e.target.value as "INTAKE" | "OUTPUT";
                          setFluidEntryType(v);
                          setFluidCategory(v === "INTAKE" ? "ORAL" : "URINE");
                        }}
                        className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                      >
                        <option value="INTAKE">Intake</option>
                        <option value="OUTPUT">Output</option>
                      </select>
                    </label>
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Category
                      <select
                        value={fluidCategory}
                        onChange={(e) => setFluidCategory(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
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
                    <label className="min-w-0 text-xs font-medium text-muted-foreground">
                      Volume (ml)
                      <input
                        type="number"
                        min={1}
                        required
                        value={fluidVolume}
                        onChange={(e) => setFluidVolume(e.target.value)}
                        className="mt-1 block w-full rounded-lg border border-border px-2 py-2 text-sm"
                      />
                    </label>
                  </div>
                  <label className="block text-xs font-medium text-muted-foreground">
                    Note (optional)
                    <input
                      value={fluidDesc}
                      onChange={(e) => setFluidDesc(e.target.value)}
                      className="mt-1 w-full max-w-md rounded-lg border border-border px-3 py-2 text-sm"
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
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading fluid balance…
                </div>
              ) : fluidRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No intake or output entries for this day.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                  <table className="w-full min-w-[520px] text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
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
                          <tr key={id} className="hover:bg-background/80">
                            <td className="px-3 py-2 text-foreground">{when}</td>
                            <td className="px-3 py-2 font-medium text-foreground">{et}</td>
                            <td className="px-3 py-2 text-foreground">{cat}</td>
                            <td className="px-3 py-2 text-foreground">{ml != null && !Number.isNaN(ml) ? ml : "—"}</td>
                            <td className="px-3 py-2 text-muted-foreground">{desc || "—"}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Observation entries — GET/POST /internal/v1/observations */}
            <div className="rounded-lg border border-violet-200/80 bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ClipboardList className="h-5 w-5 text-violet-600" aria-hidden />
                  <h2 className="text-lg font-semibold text-foreground">Observation entries</h2>
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
              <p className="mt-1 text-xs text-muted-foreground">
                Structured chart rows in <code className="rounded bg-neutral-100 px-1">observation_entries</code>. Parameters are stored as JSON; free text is saved under{" "}
                <code className="rounded bg-neutral-100 px-1">summary</code>.
              </p>
              {obsError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
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
                <form onSubmit={handleObsSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <label className="text-xs font-medium text-muted-foreground">
                    Chart type
                    <select
                      value={obsChartType}
                      onChange={(e) => setObsChartType(e.target.value)}
                      className="mt-1 block rounded-lg border border-border px-2 py-2 text-sm"
                    >
                      <option value="VITALS">Vitals</option>
                      <option value="PAIN">Pain</option>
                      <option value="NEURO">Neuro</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </label>
                  <label className="block text-xs font-medium text-muted-foreground">
                    Summary / note
                    <textarea
                      required
                      rows={2}
                      value={obsSummary}
                      onChange={(e) => setObsSummary(e.target.value)}
                      className="mt-1 w-full max-w-lg rounded-lg border border-border px-3 py-2 text-sm"
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
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading observations…
                </div>
              ) : obsRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No observation entries for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                  <table className="w-full min-w-[520px] text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
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
                          <tr key={id} className="hover:bg-background/80">
                            <td className="px-3 py-2 text-foreground">{when}</td>
                            <td className="px-3 py-2 font-medium text-foreground">{ct}</td>
                            <td className="max-w-xs truncate px-3 py-2 font-mono text-xs text-foreground">{formatParamsCell(row.parameters)}</td>
                            <td className="px-3 py-2 text-muted-foreground">{by}</td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Patient transfers — GET/POST /internal/v1/transfers */}
            <div className="rounded-lg border border-border bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Truck className="h-5 w-5 text-muted-foreground" aria-hidden />
                  <h2 className="text-lg font-semibold text-foreground">Transfers</h2>
                </div>
                {isClinical && (
                  <button
                    type="button"
                    onClick={() => setShowXferForm((p) => !p)}
                    className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-background px-3 py-1.5 text-sm font-medium text-foreground hover:bg-neutral-100"
                  >
                    <Plus className="h-4 w-4" />
                    Request transfer
                  </button>
                )}
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                Internal transfer requests for this patient. Ward/bed UUIDs are optional in the API; this form logs clinical intent and notes only.
              </p>
              {xferError && (
                <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
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
                <form onSubmit={handleXferSubmit} className="mt-4 space-y-3 rounded-lg border border-border bg-background/80 p-4">
                  <label className="text-xs font-medium text-muted-foreground">
                    Reason
                    <select
                      value={xferReason}
                      onChange={(e) => setXferReason(e.target.value)}
                      className="mt-1 block rounded-lg border border-border px-2 py-2 text-sm"
                    >
                      <option value="CLINICAL">Clinical</option>
                      <option value="ADMIN">Administrative</option>
                      <option value="CAPACITY">Capacity</option>
                      <option value="OTHER">Other</option>
                    </select>
                  </label>
                  <label className="block text-xs font-medium text-muted-foreground">
                    Clinical notes
                    <textarea
                      required
                      rows={2}
                      value={xferNotes}
                      onChange={(e) => setXferNotes(e.target.value)}
                      className="mt-1 w-full max-w-lg rounded-lg border border-border px-3 py-2 text-sm"
                      placeholder="Where and why the patient should move"
                    />
                  </label>
                  <button
                    type="submit"
                    disabled={requestXfer.isPending}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-neutral-900 disabled:opacity-50"
                  >
                    {requestXfer.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Submit request
                  </button>
                  {requestXfer.isError && <p className="text-xs text-red-600">Request failed.</p>}
                </form>
              )}
              {xferLoading ? (
                <div className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Loading transfers…
                </div>
              ) : xferRows.length === 0 ? (
                <p className="mt-4 text-sm text-muted-foreground">No transfer records for this patient yet.</p>
              ) : (
                <div className="mt-4 overflow-x-auto rounded-lg border border-border">
                  <table className="w-full min-w-[560px] text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background text-left text-xs font-medium uppercase tracking-wide text-muted-foreground">
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
                          <tr key={id} className="hover:bg-background/80">
                            <td className="px-3 py-2 text-foreground">{when}</td>
                            <td className="px-3 py-2">
                              <span className="rounded-full bg-neutral-100 px-2 py-0.5 text-xs font-medium text-foreground">{st}</span>
                            </td>
                            <td className="px-3 py-2 text-foreground">{reason}</td>
                            <td className="px-3 py-2 text-foreground">{tt}</td>
                            <td className="max-w-[200px] truncate px-3 py-2 text-muted-foreground" title={cn}>
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
                                  className="text-xs font-medium text-primary underline disabled:opacity-50"
                                >
                                  Accept
                                </button>
                              ) : (
                                <span className="text-xs text-muted-foreground">—</span>
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
              <div className="bg-card rounded-lg border border-border p-5">
                <h3 className="font-medium text-foreground mb-4">New Vitals Entry</h3>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Systolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={systolic}
                        onChange={(e) => setSystolic(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="120"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Diastolic (mmHg)
                      </label>
                      <input
                        type="number"
                        value={diastolic}
                        onChange={(e) => setDiastolic(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="80"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Heart Rate (bpm)
                      </label>
                      <input
                        type="number"
                        value={heartRate}
                        onChange={(e) => setHeartRate(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="72"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Temperature (°C)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={temperature}
                        onChange={(e) => setTemperature(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="36.6"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Resp. Rate (breaths/min)
                      </label>
                      <input
                        type="number"
                        value={respiratoryRate}
                        onChange={(e) => setRespiratoryRate(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="16"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        SpO2 (%)
                      </label>
                      <input
                        type="number"
                        value={oxygenSaturation}
                        onChange={(e) => setOxygenSaturation(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="98"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Weight (kg)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={weight}
                        onChange={(e) => setWeight(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Height (cm)
                      </label>
                      <input
                        type="number"
                        step="0.1"
                        value={height}
                        onChange={(e) => setHeight(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="170"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Pain Score (0-10)
                      </label>
                      <input
                        type="number"
                        min="0"
                        max="10"
                        value={painScore}
                        onChange={(e) => setPainScore(e.target.value)}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="0"
                      />
                    </div>
                    <div className="col-span-2 md:col-span-3 lg:col-span-5">
                      <label className="block text-xs font-medium text-muted-foreground mb-1">
                        Notes
                      </label>
                      <textarea
                        value={notes}
                        onChange={(e) => setNotes(e.target.value)}
                        rows={2}
                        className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 focus:border-impilo-400"
                        placeholder="Additional observations..."
                      />
                    </div>
                  </div>

                  <StickyActionBar status="New vitals entry — save or cancel">
                    <button
                      type="submit"
                      disabled={recordVitals.isPending}
                      className="inline-flex items-center gap-1.5 px-4 py-2 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
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
                      className="px-4 py-2 text-sm font-medium text-foreground rounded-lg border border-border hover:bg-background transition-colors"
                    >
                      Cancel
                    </button>
                  </StickyActionBar>

                  {recordVitals.isError && (
                    <p className="text-sm text-red-600">
                      Failed to record vitals. Please try again.
                    </p>
                  )}
                </form>
              </div>
            )}

            {/* Vitals table. A failed read is not an empty history: "No vitals recorded yet"
                on error would assert an absence of observations the system cannot vouch for. */}
            {vitalsUnavailable ? (
              <div className="bg-card rounded-lg border border-red-200 p-12 text-center">
                <Activity className="w-10 h-10 text-red-500 mx-auto mb-3" />
                <p className="text-red-600 text-sm font-medium">Vitals unavailable</p>
                <p className="text-muted-foreground text-sm mt-1">
                  Vital signs could not be retrieved. Do not treat this as an absence of
                  observations.
                </p>
              </div>
            ) : vitals.length === 0 ? (
              <div className="bg-card rounded-lg border border-border p-12 text-center">
                <Activity className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
                <p className="text-muted-foreground text-sm">No vitals recorded yet</p>
              </div>
            ) : (
              <div className="bg-card rounded-lg border border-border overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-background">
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          Date
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          BP (mmHg)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          HR (bpm)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          Temp (°C)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          RR (breaths/min)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
                          SpO2 (%)
                        </th>
                        <th className="text-left px-4 py-3 font-medium text-muted-foreground">
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
                            className="border-b border-border hover:bg-background transition-colors"
                          >
                            <td className="px-4 py-3 text-foreground">
                              {new Date(a.recordedAt).toLocaleString()}
                            </td>
                            <td className="px-4 py-3 text-foreground">{bp}</td>
                            <td className="px-4 py-3 text-foreground">
                              {fmt(a.heartRate)}
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {fmt(a.temperature)}
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {fmt(a.respiratoryRate)}
                            </td>
                            <td className="px-4 py-3 text-foreground">
                              {fmt(a.oxygenSaturation)}
                            </td>
                            <td className="px-4 py-3 text-foreground">
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
