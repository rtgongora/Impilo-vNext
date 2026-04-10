"use client";

import { FormEvent, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  ClipboardList,
  FileText,
  Loader2,
  Plus,
  Syringe,
  TrendingUp,
  X,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

interface GrowthMeasurement {
  id: string;
  date: string;
  ageMonths: number;
  weight: number | null;
  height: number | null;
  headCircumference: number | null;
  bmi: number | null;
  weightZScore: number | null;
  heightZScore: number | null;
  bmiZScore: number | null;
  hcZScore: number | null;
  weightPercentile: number | null;
  heightPercentile: number | null;
  bmiPercentile: number | null;
  hcPercentile: number | null;
}

type ChartTab = "weight" | "height" | "bmi" | "hc";

const TAB_LABELS: Record<ChartTab, string> = {
  weight: "Weight-for-Age",
  height: "Height-for-Age",
  bmi: "BMI-for-Age",
  hc: "Head Circumference",
};

function sortMeasurements(measurements: GrowthMeasurement[]) {
  return [...measurements].sort((left, right) => right.date.localeCompare(left.date));
}

function parseNumber(value: string) {
  return value.trim() === "" ? null : Number(value);
}

function getZScoreColor(z: number | null): string {
  if (z === null) return "text-gray-400";
  const abs = Math.abs(z);
  if (abs <= 1) return "text-green-600";
  if (abs <= 2) return "text-amber-600";
  return "text-red-600";
}

function getZScoreLabel(z: number | null): string {
  if (z === null) return "Awaiting percentile calculation";
  if (z > 2) return "Above normal";
  if (z > 1) return "High normal";
  if (z >= -1) return "Normal";
  if (z >= -2) return "Low normal";
  return "Below normal";
}

function countFlags(measurement?: GrowthMeasurement) {
  if (!measurement) return 0;
  return [
    measurement.weightPercentile,
    measurement.heightPercentile,
    measurement.bmiPercentile,
    measurement.hcPercentile,
  ].filter((value) => value != null && (value < 3 || value > 97)).length;
}

export default function GrowthChartPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );

  /** No BFF growth/WHO module; entries below are browser-session only unless Agent 0 adds a persisted API. */
  const [recordedMeasurements, setRecordedMeasurements] = useState<GrowthMeasurement[]>([]);
  const measurements = useMemo(() => sortMeasurements([...recordedMeasurements]), [recordedMeasurements]);
  const isLoading = false;
  const latest = measurements[0];
  const flaggedMetrics = countFlags(latest);
  const [activeTab, setActiveTab] = useState<ChartTab>("weight");
  const [showForm, setShowForm] = useState(false);
  const [newWeight, setNewWeight] = useState("");
  const [newHeight, setNewHeight] = useState("");
  const [newHC, setNewHC] = useState("");
  const [newAge, setNewAge] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  function resetForm() {
    setNewWeight("");
    setNewHeight("");
    setNewHC("");
    setNewAge("");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!activeEncounter) return;

    const ageMonths = Number(newAge);
    const weight = parseNumber(newWeight);
    const height = parseNumber(newHeight);
    const headCircumference = parseNumber(newHC);
    const bmi = weight != null && height != null && height > 0
      ? Number((weight / ((height / 100) * (height / 100))).toFixed(1))
      : null;

    const nextMeasurement: GrowthMeasurement = {
      id: `growth-${Date.now()}`,
      date: new Date().toISOString().slice(0, 10),
      ageMonths,
      weight,
      height,
      headCircumference,
      bmi,
      weightZScore: null,
      heightZScore: null,
      bmiZScore: null,
      hcZScore: null,
      weightPercentile: null,
      heightPercentile: null,
      bmiPercentile: null,
      hcPercentile: null,
    };

    setIsSaving(true);
    try {
      setRecordedMeasurements((current) => sortMeasurements([nextMeasurement, ...current]));
      resetForm();
      setShowForm(false);
    } finally {
      setIsSaving(false);
    }
  }

  return (
    <EHRLayout>
      <PageShell title="Growth Chart" subtitle="Paediatric growth tracking with WHO z-scores">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading growth data...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-800">
              <p className="font-medium text-slate-900">Growth data not persisted</p>
              <p className="mt-1 text-slate-600">
                There is no supported experience-BFF growth-chart API or WHO z-score pipeline wired here. Measurements you add
                stay in this browser session only. Agent 0: paediatric growth observations store + read path (or integration
                to an authoritative service).
              </p>
            </div>
            <ClinicalReviewHeader
              badge="Growth continuity"
              badgeIcon={TrendingUp}
              title="Keep paediatric growth trends tied to the live encounter, follow-up plan, and preventive work rather than leaving them as an isolated chart."
              description="Growth review now keeps the next measurement, abnormal percentile follow-up, and related planning surfaces visible from the same page."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/vitals`, label: "Vitals", icon: Activity },
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
                  label: "Latest review",
                  value: latest?.date ?? "None",
                  detail: latest
                    ? `${latest.ageMonths} months old at the latest recorded measurement.`
                    : "No growth review recorded yet.",
                },
                {
                  label: "Flagged metrics",
                  value: String(flaggedMetrics),
                  detail:
                    flaggedMetrics > 0
                      ? "One or more latest percentiles need plan or nutrition follow-up."
                      : "Latest tracked metrics remain inside the expected percentile band.",
                },
                {
                  label: "Measurements",
                  value: String(measurements.length),
                  detail:
                    activeEncounter
                      ? "A new measurement can be captured from this encounter without leaving the chart."
                      : "Open an encounter to record a new measurement in the same workflow.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">
                Growth loop status
              </p>
              <p className="mt-2 text-sm text-slate-800">
                {activeEncounter
                  ? "You can review the trend, capture a fresh measurement, and hand the outcome into plans, immunization follow-up, or notes without leaving this page."
                  : "Trend review is visible here, but recording a new measurement should begin from an active encounter so the facility and care-team context stay attached."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Vitals for the broader observation record, Care Plans for nutrition or developmental follow-up, Immunizations for age-linked preventive care, and Notes for caregiver communication.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp className="h-5 w-5 text-emerald-600" />
                <h2 className="text-lg font-semibold text-gray-900">Growth Tracking</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((current) => !current)}
                disabled={!activeEncounter}
                className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-slate-300"
              >
                <Plus className="h-4 w-4" />
                Record Measurement
              </button>
            </div>

            {showForm && (
              <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm">
                <div className="mb-4 flex items-center justify-between">
                  <div>
                    <h3 className="font-medium text-gray-900">Capture New Measurement</h3>
                    <p className="text-sm text-gray-500">
                      Save growth data against the active encounter so the next action stays visible.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      resetForm();
                      setShowForm(false);
                    }}
                    className="text-gray-400 transition-colors hover:text-gray-600"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
                <form onSubmit={handleSubmit} className="space-y-4">
                  <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Age (months)</label>
                      <input
                        type="number"
                        min="0"
                        aria-label="Age (months)"
                        value={newAge}
                        onChange={(event) => setNewAge(event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="24"
                        required
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Weight (kg)</label>
                      <input
                        type="number"
                        min="0"
                        step="0.1"
                        aria-label="Weight (kg)"
                        value={newWeight}
                        onChange={(event) => setNewWeight(event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="12.5"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Height (cm)</label>
                      <input
                        type="number"
                        min="0"
                        step="0.1"
                        aria-label="Height (cm)"
                        value={newHeight}
                        onChange={(event) => setNewHeight(event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="87.0"
                      />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-gray-600">Head Circ. (cm)</label>
                      <input
                        type="number"
                        min="0"
                        step="0.1"
                        aria-label="Head Circ. (cm)"
                        value={newHC}
                        onChange={(event) => setNewHC(event.target.value)}
                        className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="48.2"
                      />
                    </div>
                  </div>
                  <div className="flex items-center gap-3 pt-1">
                    <button
                      type="submit"
                      disabled={isSaving}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700 disabled:cursor-not-allowed disabled:bg-blue-300"
                    >
                      {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
                      Save Measurement
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        resetForm();
                        setShowForm(false);
                      }}
                      className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50"
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              </div>
            )}

            {latest && (
              <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                {[
                  {
                    label: "Weight",
                    value: latest.weight != null ? `${latest.weight} kg` : "Not recorded",
                    z: latest.weightZScore,
                    percentile: latest.weightPercentile,
                  },
                  {
                    label: "Height",
                    value: latest.height != null ? `${latest.height} cm` : "Not recorded",
                    z: latest.heightZScore,
                    percentile: latest.heightPercentile,
                  },
                  {
                    label: "BMI",
                    value: latest.bmi != null ? latest.bmi.toFixed(1) : "Not recorded",
                    z: latest.bmiZScore,
                    percentile: latest.bmiPercentile,
                  },
                  {
                    label: "Head Circ.",
                    value:
                      latest.headCircumference != null
                        ? `${latest.headCircumference} cm`
                        : "Not recorded",
                    z: latest.hcZScore,
                    percentile: latest.hcPercentile,
                  },
                ].map((card) => (
                  <div key={card.label} className="rounded-2xl border border-gray-200 bg-white p-4 shadow-sm">
                    <p className="text-xs font-medium uppercase tracking-wide text-gray-500">
                      {card.label}
                    </p>
                    <p className="mt-1 text-xl font-bold text-gray-900">{card.value}</p>
                    <div className="mt-1 flex items-center gap-2">
                      <span className={`text-xs font-medium ${getZScoreColor(card.z)}`}>
                        {card.z == null ? "z pending" : `z=${card.z.toFixed(1)}`}
                      </span>
                      <span className="text-xs text-gray-400">
                        {card.percentile == null
                          ? "Percentile pending"
                          : `P${card.percentile} � ${getZScoreLabel(card.z)}`}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
              <div className="flex border-b border-gray-200">
                {(Object.keys(TAB_LABELS) as ChartTab[]).map((tab) => (
                  <button
                    key={tab}
                    type="button"
                    onClick={() => setActiveTab(tab)}
                    className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${
                      activeTab === tab
                        ? "border-b-2 border-blue-600 bg-blue-50 text-blue-600"
                        : "text-gray-500 hover:bg-gray-50 hover:text-gray-700"
                    }`}
                  >
                    {TAB_LABELS[tab]}
                  </button>
                ))}
              </div>

              <div className="p-5">
                <div className="space-y-3">
                  {measurements.map((measurement) => {
                    const zScore =
                      activeTab === "weight"
                        ? measurement.weightZScore
                        : activeTab === "height"
                          ? measurement.heightZScore
                          : activeTab === "bmi"
                            ? measurement.bmiZScore
                            : measurement.hcZScore;
                    const percentile =
                      activeTab === "weight"
                        ? measurement.weightPercentile
                        : activeTab === "height"
                          ? measurement.heightPercentile
                          : activeTab === "bmi"
                            ? measurement.bmiPercentile
                            : measurement.hcPercentile;
                    const value =
                      activeTab === "weight"
                        ? measurement.weight
                        : activeTab === "height"
                          ? measurement.height
                          : activeTab === "bmi"
                            ? measurement.bmi
                            : measurement.headCircumference;
                    const unit = activeTab === "weight" ? "kg" : activeTab === "bmi" ? "" : "cm";

                    return (
                      <div key={measurement.id} className="flex items-center gap-4">
                        <span className="w-24 shrink-0 text-xs text-gray-500">{measurement.date}</span>
                        <span className="w-16 shrink-0 text-xs text-gray-500">{measurement.ageMonths}mo</span>
                        <div className="relative flex-1">
                          <div className="relative h-6 w-full overflow-hidden rounded-full bg-gray-100">
                            <div className="absolute inset-y-0 left-[3%] w-[19%] rounded-l bg-amber-50" />
                            <div className="absolute inset-y-0 left-[22%] w-[56%] bg-green-50" />
                            <div className="absolute inset-y-0 left-[78%] w-[19%] rounded-r bg-amber-50" />
                            <div
                              className="absolute top-0.5 h-5 w-5 rounded-full border-2 border-white bg-blue-600 shadow"
                              style={{
                                left: `${Math.max(2, Math.min(95, percentile ?? 50))}%`,
                                transform: "translateX(-50%)",
                              }}
                            />
                          </div>
                        </div>
                        <span className="w-20 text-right text-xs font-medium text-gray-700">
                          {value == null ? "Pending" : `${value}${unit}`}
                        </span>
                        <span className={`w-16 text-right text-xs font-medium ${getZScoreColor(zScore)}`}>
                          {percentile == null ? "Pending" : `P${percentile}`}
                        </span>
                      </div>
                    );
                  })}
                </div>
                <div className="mt-4 flex items-center justify-center gap-6 text-[10px] text-gray-400">
                  <span className="flex items-center gap-1">
                    <span className="h-3 w-3 rounded border border-amber-200 bg-amber-50" />
                    Caution (&lt;P3 or &gt;P97)
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="h-3 w-3 rounded border border-green-200 bg-green-50" />
                    Normal (P3-P97)
                  </span>
                </div>
              </div>
            </div>

            <div className="overflow-hidden rounded-2xl border border-gray-200 bg-white shadow-sm">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Age</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Weight (kg)</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Height (cm)</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">BMI</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Head Circ. (cm)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {measurements.map((measurement) => (
                      <tr
                        key={measurement.id}
                        className="border-b border-gray-100 transition-colors hover:bg-gray-50"
                      >
                        <td className="px-4 py-3 text-gray-900">{measurement.date}</td>
                        <td className="px-4 py-3 text-gray-700">{measurement.ageMonths} months</td>
                        <td className="px-4 py-3 text-gray-700">{measurement.weight ?? "-"}</td>
                        <td className="px-4 py-3 text-gray-700">{measurement.height ?? "-"}</td>
                        <td className="px-4 py-3 text-gray-700">
                          {measurement.bmi != null ? measurement.bmi.toFixed(1) : "-"}
                        </td>
                        <td className="px-4 py-3 text-gray-700">
                          {measurement.headCircumference ?? "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
