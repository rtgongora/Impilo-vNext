"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  Activity,
  AlertTriangle,
  ClipboardList,
  FileText,
  Loader2,
  Plus,
  Syringe,
  TrendingUp,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useGrowth, useRecordGrowth } from "@/hooks/queries/useGrowth";
import { usePatient } from "@/hooks/queries/usePatients";
import { useVitals, type VitalsResource } from "@/hooks/queries/useVitals";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";

function ageMonthsApprox(dobIso: string, at: Date): number | null {
  const dob = new Date(dobIso);
  if (Number.isNaN(dob.getTime())) return null;
  let months = (at.getFullYear() - dob.getFullYear()) * 12 + (at.getMonth() - dob.getMonth());
  if (at.getDate() < dob.getDate()) months -= 1;
  return Math.max(0, months);
}

function hasGrowthMetric(a: VitalsResource["attributes"]) {
  return (
    (a.weight != null && Number(a.weight) > 0) ||
    (a.height != null && Number(a.height) > 0) ||
    (a.bmi != null && Number(a.bmi) > 0)
  );
}

export default function GrowthChartPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { user } = useAuthStore();
  const { isClinical } = useRoleGroup();
  const { data: patientData } = usePatient(patientId);
  const { data: encountersData } = useEncounters(patientId);
  const { data: growthRows = [], isLoading: growthLoading } = useGrowth(patientId);
  const recordGrowth = useRecordGrowth();
  const { data: vitalsData, isLoading: vitalsLoading } = useVitals(patientId);
  const [showForm, setShowForm] = useState(false);
  const [weightKg, setWeightKg] = useState("");
  const [lengthCm, setLengthCm] = useState("");
  const [heightCm, setHeightCm] = useState("");
  const [headCircumferenceCm, setHeadCircumferenceCm] = useState("");
  const [notes, setNotes] = useState("");

  const patient = patientData?.data;
  const dob = patient?.attributes.dateOfBirth;
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE",
  );

  const legacyVitalsRows = useMemo(() => {
    const list = vitalsData?.data ?? [];
    const filtered = list.filter((r) => hasGrowthMetric(r.attributes));
    const rows = filtered.map((r) => {
      const a = r.attributes;
      const recordedAt = new Date(a.recordedAt);
      const ageMo = typeof dob === "string" ? ageMonthsApprox(dob, recordedAt) : null;
      return {
        id: r.id,
        date: a.recordedAt.slice(0, 10),
        recordedAtDisplay: recordedAt.toLocaleString(),
        ageMonths: ageMo,
        weight: a.weight,
        height: a.height,
        bmi: a.bmi,
      };
    });
    return rows.sort((x, y) => y.date.localeCompare(x.date));
  }, [vitalsData, dob]);

  const ageDaysNow = useMemo(() => {
    if (!dob) return null;
    const birth = new Date(dob);
    if (Number.isNaN(birth.getTime())) return null;
    return Math.max(0, Math.floor((Date.now() - birth.getTime()) / (1000 * 60 * 60 * 24)));
  }, [dob]);

  const whoSupported = ageDaysNow != null && ageDaysNow <= 1856;
  const hasStructuredGrowth = growthRows.length > 0;
  const latestStructured = growthRows[0];
  const latestLegacy = legacyVitalsRows[0];

  function resetForm() {
    setWeightKg("");
    setLengthCm("");
    setHeightCm("");
    setHeadCircumferenceCm("");
    setNotes("");
  }

  function scoreLabel(value: number | null | undefined) {
    return value != null ? value.toFixed(2) : "—";
  }

  function percentileLabel(value: number | null | undefined) {
    return value != null ? `${value.toFixed(1)}%` : "—";
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    recordGrowth.mutate(
      {
        patientId,
        encounterId: activeEncounter?.id ?? null,
        recordedBy: user?.id ?? user?.displayName ?? "unknown",
        weightKg: weightKg.trim() ? Number(weightKg) : null,
        lengthCm: lengthCm.trim() ? Number(lengthCm) : null,
        heightCm: heightCm.trim() ? Number(heightCm) : null,
        headCircumferenceCm: headCircumferenceCm.trim() ? Number(headCircumferenceCm) : null,
        measurementMode: lengthCm.trim() ? "LENGTH" : heightCm.trim() ? "HEIGHT" : "AUTO",
        notes: notes.trim() || null,
      },
      {
        onSuccess: () => {
          resetForm();
          setShowForm(false);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell
        title="Growth Chart"
        subtitle={
          hasStructuredGrowth
            ? "Structured growth measurements with WHO under-5 z-scores"
            : "Structured growth capture is live; legacy vitals remain as fallback until measurements are recorded here"
        }
      >
        {growthLoading || vitalsLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading growth review…</span>
          </div>
        ) : (
          <div className="space-y-6">
            {hasStructuredGrowth ? (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950">
                <p className="font-medium">Real path: WHO-backed structured growth measurements</p>
                <p className="mt-1 text-emerald-900/80">
                  This page now reads from <code className="rounded bg-white px-1">GET /internal/v1/growth?patient_id=…</code>{" "}
                  and computes under-5 z-scores from embedded WHO 2006 LMS standards. Head circumference is recorded here, not
                  inferred from generic vitals.
                </p>
              </div>
            ) : (
              <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950">
                <p className="font-medium">Legacy fallback: vitals-backed anthropometrics</p>
                <p className="mt-1 text-amber-900/80">
                  No structured growth measurements have been recorded yet, so the table below falls back to weight, height, and
                  BMI from{" "}
                  <code className="rounded bg-white px-1">GET /internal/v1/vitals?patient_id=…</code>. Record a structured
                  measurement here to populate head circumference and WHO-backed z-scores.
                </p>
              </div>
            )}

            <ClinicalReviewHeader
              badge="Growth continuity"
              badgeIcon={TrendingUp}
              title="Keep paediatric growth trends tied to follow-up plans, preventive work, and real anthropometric standards."
              description={
                hasStructuredGrowth
                  ? "This page shows structured measurements with server-side WHO z-scores when the patient falls within the under-5 standards window."
                  : "Legacy vitals remain visible, but WHO z-scores and head circumference require structured growth capture on this page."
              }
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                ...(isClinical && whoSupported
                  ? [{ href: "#record-growth", label: "Record Measurement", icon: Plus }]
                  : [{ href: `/ehr/${patientId}/vitals`, label: "Vitals", icon: Activity }]),
                {
                  href: `/ehr/${patientId}/care-plans`,
                  label: "Care Plans",
                  icon: ClipboardList,
                  tone: "secondary",
                },
                {
                  href: `/ehr/${patientId}/immunizations`,
                  label: "Immunizations",
                  icon: Syringe,
                  tone: "secondary",
                },
                {
                  href: `/ehr/${patientId}/notes`,
                  label: "Notes",
                  icon: FileText,
                  tone: "secondary",
                },
              ]}
              metrics={[
                {
                  label: hasStructuredGrowth ? "Latest growth row" : "Latest anthropometric row",
                  value: hasStructuredGrowth
                    ? latestStructured?.measuredAt.slice(0, 10) ?? "None"
                    : latestLegacy?.date ?? "None",
                  detail: hasStructuredGrowth
                    ? latestStructured
                      ? `Recorded ${new Date(latestStructured.measuredAt).toLocaleString()}${latestStructured.derived.ageDays != null ? ` · ${latestStructured.derived.ageDays} days` : ""}`
                      : "Record a measurement on this page to populate WHO-backed rows."
                    : latestLegacy
                      ? `Recorded ${latestLegacy.recordedAtDisplay}${latestLegacy.ageMonths != null ? ` · ~${latestLegacy.ageMonths} mo` : ""}`
                      : "No growth or vitals-backed anthropometrics recorded yet.",
                },
                {
                  label: "WHO standard support",
                  value: whoSupported ? "Under-5 ready" : "Age not supported",
                  detail: whoSupported
                    ? hasStructuredGrowth
                      ? latestStructured?.derived.standard ?? "Structured rows will compute WHO scores."
                      : "Record a structured measurement to compute WHO z-scores and percentiles."
                    : "This slice currently embeds WHO 2006 child growth standards for birth to 5 years only.",
                },
                {
                  label: "Latest BMI-for-age z",
                  value: hasStructuredGrowth ? scoreLabel(latestStructured?.derived.bodyMassIndexForAge?.zScore) : "—",
                  detail: hasStructuredGrowth
                    ? `Percentile ${percentileLabel(latestStructured?.derived.bodyMassIndexForAge?.percentile)}`
                    : "BMI-for-age is unavailable until a structured growth row exists.",
                },
              ]}
            />

            {isClinical && whoSupported && (
              <div id="record-growth" className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Growth capture</p>
                    <p className="mt-2 text-sm text-slate-700">
                      Record weight, stature, and head circumference here to generate WHO-backed under-5 z-scores.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setShowForm((prev) => !prev)}
                    className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-800 hover:bg-slate-50"
                  >
                    {showForm ? "Close" : "Record Measurement"}
                  </button>
                </div>

                {showForm && (
                  <form onSubmit={handleSubmit} className="mt-4 grid gap-4 md:grid-cols-2">
                    <label className="text-sm text-slate-700">
                      Weight (kg)
                      <input
                        value={weightKg}
                        onChange={(event) => setWeightKg(event.target.value)}
                        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                        inputMode="decimal"
                      />
                    </label>
                    <label className="text-sm text-slate-700">
                      Length (cm)
                      <input
                        value={lengthCm}
                        onChange={(event) => setLengthCm(event.target.value)}
                        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                        inputMode="decimal"
                      />
                    </label>
                    <label className="text-sm text-slate-700">
                      Height (cm)
                      <input
                        value={heightCm}
                        onChange={(event) => setHeightCm(event.target.value)}
                        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                        inputMode="decimal"
                      />
                    </label>
                    <label className="text-sm text-slate-700">
                      Head circumference (cm)
                      <input
                        value={headCircumferenceCm}
                        onChange={(event) => setHeadCircumferenceCm(event.target.value)}
                        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                        inputMode="decimal"
                      />
                    </label>
                    <label className="text-sm text-slate-700 md:col-span-2">
                      Notes
                      <textarea
                        value={notes}
                        onChange={(event) => setNotes(event.target.value)}
                        className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
                        rows={2}
                      />
                    </label>
                    <div className="md:col-span-2 flex items-center gap-3">
                      <button
                        type="submit"
                        disabled={recordGrowth.isPending}
                        className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
                      >
                        {recordGrowth.isPending ? "Saving…" : "Save measurement"}
                      </button>
                      {recordGrowth.isError && (
                        <p className="text-sm text-red-600">Could not save growth measurement.</p>
                      )}
                    </div>
                  </form>
                )}
              </div>
            )}

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Growth review</p>
              <p className="mt-2 text-sm text-slate-800">
                Structured growth rows now live in the Experience BFF. WHO z-scores in this slice are authoritative for under-5
                patients only; older ages remain honest unsupported territory until the 5–19 reference set is wired in.
              </p>
            </div>

            <div className="flex items-center gap-2">
              <TrendingUp className="h-5 w-5 text-emerald-600" />
              <h2 className="text-lg font-semibold text-gray-900">
                {hasStructuredGrowth ? "Structured growth measurements" : "Anthropometrics (legacy vitals fallback)"}
              </h2>
            </div>

            {!hasStructuredGrowth && legacyVitalsRows.length === 0 ? (
              <p className="text-sm text-gray-500">
                No growth measurements or vitals-backed anthropometrics yet.
              </p>
            ) : hasStructuredGrowth ? (
              <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Recorded</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Age (days)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Weight (kg)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Stature (cm)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">BMI</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Head circ. (cm)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">WFA z</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">L/HFA z</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">BFA z</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">HCFA z</th>
                      </tr>
                    </thead>
                    <tbody>
                      {growthRows.map((row) => (
                        <tr key={row.id} className="border-b border-gray-100 transition-colors hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-900">{new Date(row.measuredAt).toLocaleString()}</td>
                          <td className="px-4 py-3 text-gray-700">{row.derived.ageDays ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{row.weightKg ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">
                            {row.derived.normalizedStatureCm ?? row.lengthCm ?? row.heightCm ?? "—"}
                          </td>
                          <td className="px-4 py-3 text-gray-700">{row.derived.bodyMassIndex ?? row.bmi ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{row.headCircumferenceCm ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{scoreLabel(row.derived.weightForAge?.zScore)}</td>
                          <td className="px-4 py-3 text-gray-700">{scoreLabel(row.derived.lengthHeightForAge?.zScore)}</td>
                          <td className="px-4 py-3 text-gray-700">{scoreLabel(row.derived.bodyMassIndexForAge?.zScore)}</td>
                          <td className="px-4 py-3 text-gray-700">{scoreLabel(row.derived.headCircumferenceForAge?.zScore)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-gray-200 bg-gray-50">
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Recorded</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Age (mo, approx.)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Weight (kg)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Height (cm)</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">BMI</th>
                        <th className="px-4 py-3 text-left font-medium text-gray-600">Head circ.</th>
                      </tr>
                    </thead>
                    <tbody>
                      {legacyVitalsRows.map((row) => (
                        <tr key={row.id} className="border-b border-gray-100 transition-colors hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-900">{row.recordedAtDisplay}</td>
                          <td className="px-4 py-3 text-gray-700">{row.ageMonths != null ? row.ageMonths : "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{row.weight ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{row.height ?? "—"}</td>
                          <td className="px-4 py-3 text-gray-700">{row.bmi != null ? row.bmi.toFixed(1) : "—"}</td>
                          <td className="px-4 py-3 text-gray-500">Not in vitals API</td>
                        </tr>
                      ))}
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
