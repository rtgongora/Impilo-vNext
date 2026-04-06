"use client";

/**
 * Growth Chart — Paediatric growth charts with WHO z-score display.
 * Route: /ehr/[patientId]/growth-chart | pageTitle: "Growth Chart"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { TrendingUp, Loader2, Plus, X } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

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

const MOCK_MEASUREMENTS: GrowthMeasurement[] = [
  { id: "gm-1", date: "2026-04-01", ageMonths: 24, weight: 12.5, height: 87.0, headCircumference: 48.2, bmi: 16.5, weightZScore: 0.3, heightZScore: 0.1, bmiZScore: 0.5, hcZScore: 0.2, weightPercentile: 62, heightPercentile: 54, bmiPercentile: 69, hcPercentile: 58 },
  { id: "gm-2", date: "2026-01-10", ageMonths: 21, weight: 11.8, height: 84.5, headCircumference: 47.8, bmi: 16.5, weightZScore: 0.2, heightZScore: 0.0, bmiZScore: 0.4, hcZScore: 0.1, weightPercentile: 58, heightPercentile: 50, bmiPercentile: 66, hcPercentile: 54 },
  { id: "gm-3", date: "2025-10-05", ageMonths: 18, weight: 10.9, height: 81.0, headCircumference: 47.2, bmi: 16.6, weightZScore: 0.1, heightZScore: -0.1, bmiZScore: 0.4, hcZScore: 0.0, weightPercentile: 54, heightPercentile: 46, bmiPercentile: 66, hcPercentile: 50 },
  { id: "gm-4", date: "2025-07-12", ageMonths: 15, weight: 10.2, height: 77.5, headCircumference: 46.5, bmi: 17.0, weightZScore: 0.0, heightZScore: -0.2, bmiZScore: 0.3, hcZScore: -0.1, weightPercentile: 50, heightPercentile: 42, bmiPercentile: 62, hcPercentile: 46 },
  { id: "gm-5", date: "2025-04-20", ageMonths: 12, weight: 9.5, height: 74.0, headCircumference: 45.8, bmi: 17.3, weightZScore: -0.1, heightZScore: -0.3, bmiZScore: 0.2, hcZScore: -0.2, weightPercentile: 46, heightPercentile: 38, bmiPercentile: 58, hcPercentile: 42 },
];

type ChartTab = "weight" | "height" | "bmi" | "hc";

const TAB_LABELS: Record<ChartTab, string> = {
  weight: "Weight-for-Age",
  height: "Height-for-Age",
  bmi: "BMI-for-Age",
  hc: "Head Circumference",
};

function getZScoreColor(z: number | null): string {
  if (z === null) return "text-gray-400";
  const abs = Math.abs(z);
  if (abs <= 1) return "text-green-600";
  if (abs <= 2) return "text-amber-600";
  return "text-red-600";
}

function getZScoreLabel(z: number | null): string {
  if (z === null) return "N/A";
  if (z > 2) return "Above normal";
  if (z > 1) return "High normal";
  if (z >= -1) return "Normal";
  if (z >= -2) return "Low normal";
  return "Below normal";
}

export default function GrowthChartPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["growth-chart", patientId],
    queryFn: async () => ({ data: MOCK_MEASUREMENTS }),
  });

  const measurements = data?.data ?? [];
  const [activeTab, setActiveTab] = useState<ChartTab>("weight");
  const [showForm, setShowForm] = useState(false);
  const [newWeight, setNewWeight] = useState("");
  const [newHeight, setNewHeight] = useState("");
  const [newHC, setNewHC] = useState("");
  const [newAge, setNewAge] = useState("");

  const latest = measurements[0];

  return (
    <EHRLayout>
      <PageShell title="Growth Chart" subtitle="Paediatric growth tracking with WHO z-scores">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading growth data...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-purple-600" />
                <h2 className="text-lg font-semibold text-gray-900">Growth Tracking</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowForm(!showForm)}
                className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
              >
                <Plus className="w-4 h-4" />
                Record Measurement
              </button>
            </div>

            {/* Latest Summary Cards */}
            {latest && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                {[
                  { label: "Weight", value: `${latest.weight} kg`, z: latest.weightZScore, pct: latest.weightPercentile },
                  { label: "Height", value: `${latest.height} cm`, z: latest.heightZScore, pct: latest.heightPercentile },
                  { label: "BMI", value: latest.bmi?.toFixed(1) ?? "N/A", z: latest.bmiZScore, pct: latest.bmiPercentile },
                  { label: "Head Circ.", value: `${latest.headCircumference} cm`, z: latest.hcZScore, pct: latest.hcPercentile },
                ].map((card) => (
                  <div key={card.label} className="bg-white rounded-lg border border-gray-200 p-4">
                    <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{card.label}</p>
                    <p className="text-xl font-bold text-gray-900 mt-1">{card.value}</p>
                    <div className="flex items-center gap-2 mt-1">
                      <span className={`text-xs font-medium ${getZScoreColor(card.z)}`}>
                        z={card.z?.toFixed(1) ?? "N/A"}
                      </span>
                      <span className="text-xs text-gray-400">
                        P{card.pct ?? "N/A"} &middot; {getZScoreLabel(card.z)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {/* Data Entry Form */}
            {showForm && (
              <div className="bg-white rounded-lg border border-gray-200 p-5">
                <div className="flex items-center justify-between mb-4">
                  <h3 className="font-medium text-gray-900">New Measurement</h3>
                  <button onClick={() => setShowForm(false)} className="text-gray-400 hover:text-gray-600">
                    <X className="w-4 h-4" />
                  </button>
                </div>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Age (months)</label>
                    <input type="number" value={newAge} onChange={(e) => setNewAge(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="24" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Weight (kg)</label>
                    <input type="number" step="0.1" value={newWeight} onChange={(e) => setNewWeight(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="12.5" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Height (cm)</label>
                    <input type="number" step="0.1" value={newHeight} onChange={(e) => setNewHeight(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="87.0" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1">Head Circ. (cm)</label>
                    <input type="number" step="0.1" value={newHC} onChange={(e) => setNewHC(e.target.value)} className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" placeholder="48.2" />
                  </div>
                </div>
                <div className="flex items-center gap-3 pt-4">
                  <button className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">Save Measurement</button>
                  <button onClick={() => setShowForm(false)} className="px-4 py-2 text-sm font-medium text-gray-700 rounded-lg border border-gray-300 hover:bg-gray-50 transition-colors">Cancel</button>
                </div>
              </div>
            )}

            {/* Chart Tab Selection */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="flex border-b border-gray-200">
                {(Object.keys(TAB_LABELS) as ChartTab[]).map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setActiveTab(tab)}
                    className={`flex-1 px-4 py-3 text-sm font-medium transition-colors ${activeTab === tab ? "text-blue-600 border-b-2 border-blue-600 bg-blue-50" : "text-gray-500 hover:text-gray-700 hover:bg-gray-50"}`}
                  >
                    {TAB_LABELS[tab]}
                  </button>
                ))}
              </div>

              {/* Percentile Visual */}
              <div className="p-5">
                <div className="space-y-3">
                  {measurements.map((m) => {
                    const zScore = activeTab === "weight" ? m.weightZScore : activeTab === "height" ? m.heightZScore : activeTab === "bmi" ? m.bmiZScore : m.hcZScore;
                    const percentile = activeTab === "weight" ? m.weightPercentile : activeTab === "height" ? m.heightPercentile : activeTab === "bmi" ? m.bmiPercentile : m.hcPercentile;
                    const value = activeTab === "weight" ? m.weight : activeTab === "height" ? m.height : activeTab === "bmi" ? m.bmi : m.headCircumference;
                    const unit = activeTab === "weight" ? "kg" : activeTab === "bmi" ? "" : "cm";

                    return (
                      <div key={m.id} className="flex items-center gap-4">
                        <span className="text-xs text-gray-500 w-24 shrink-0">{m.date}</span>
                        <span className="text-xs text-gray-500 w-16 shrink-0">{m.ageMonths}mo</span>
                        <div className="flex-1 relative">
                          <div className="w-full bg-gray-100 rounded-full h-6 relative overflow-hidden">
                            <div className="absolute inset-y-0 left-[3%] w-[19%] bg-amber-50 rounded-l" />
                            <div className="absolute inset-y-0 left-[22%] w-[56%] bg-green-50" />
                            <div className="absolute inset-y-0 left-[78%] w-[19%] bg-amber-50 rounded-r" />
                            <div
                              className="absolute top-0.5 w-5 h-5 bg-blue-600 rounded-full border-2 border-white shadow"
                              style={{ left: `${Math.max(2, Math.min(95, (percentile ?? 50)))}%`, transform: "translateX(-50%)" }}
                            />
                          </div>
                        </div>
                        <span className="text-xs font-medium text-gray-700 w-16 text-right">{value}{unit}</span>
                        <span className={`text-xs font-medium w-12 text-right ${getZScoreColor(zScore)}`}>P{percentile}</span>
                      </div>
                    );
                  })}
                </div>
                <div className="flex items-center justify-center gap-6 mt-4 text-[10px] text-gray-400">
                  <span className="flex items-center gap-1"><span className="w-3 h-3 bg-amber-50 border border-amber-200 rounded" /> Caution (&lt;P3 or &gt;P97)</span>
                  <span className="flex items-center gap-1"><span className="w-3 h-3 bg-green-50 border border-green-200 rounded" /> Normal (P3-P97)</span>
                </div>
              </div>
            </div>

            {/* Measurements Table */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Age</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Weight (kg)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Height (cm)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">BMI</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Head Circ. (cm)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {measurements.map((m) => (
                      <tr key={m.id} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 text-gray-900">{m.date}</td>
                        <td className="px-4 py-3 text-gray-700">{m.ageMonths} months</td>
                        <td className="px-4 py-3 text-gray-700">{m.weight ?? "—"}</td>
                        <td className="px-4 py-3 text-gray-700">{m.height ?? "—"}</td>
                        <td className="px-4 py-3 text-gray-700">{m.bmi?.toFixed(1) ?? "—"}</td>
                        <td className="px-4 py-3 text-gray-700">{m.headCircumference ?? "—"}</td>
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
