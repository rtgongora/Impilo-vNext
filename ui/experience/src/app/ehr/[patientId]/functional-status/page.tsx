"use client";

/**
 * Functional Status — ADL/IADL assessments with Barthel Index, Katz, and Lawton scoring.
 * Route: /ehr/[patientId]/functional-status | pageTitle: "Functional Status"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, Loader2, TrendingUp, Calendar } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

type AssessmentType = "barthel" | "katz" | "lawton";

interface ActivityScore {
  activity: string;
  score: number;
  maxScore: number;
  level: string;
}

interface FunctionalAssessment {
  id: string;
  type: AssessmentType;
  date: string;
  assessor: string;
  totalScore: number;
  maxScore: number;
  interpretation: string;
  activities: ActivityScore[];
}

const MOCK_ASSESSMENTS: FunctionalAssessment[] = [
  {
    id: "fa-1", type: "barthel", date: "2026-04-01", assessor: "Sr. N. Phiri", totalScore: 85, maxScore: 100,
    interpretation: "Moderate dependence — needs some assistance with daily activities",
    activities: [
      { activity: "Feeding", score: 10, maxScore: 10, level: "Independent" },
      { activity: "Bathing", score: 5, maxScore: 5, level: "Independent" },
      { activity: "Grooming", score: 5, maxScore: 5, level: "Independent" },
      { activity: "Dressing", score: 10, maxScore: 10, level: "Independent" },
      { activity: "Bowels", score: 10, maxScore: 10, level: "Continent" },
      { activity: "Bladder", score: 10, maxScore: 10, level: "Continent" },
      { activity: "Toilet use", score: 10, maxScore: 10, level: "Independent" },
      { activity: "Transfers", score: 10, maxScore: 15, level: "Minor help" },
      { activity: "Mobility", score: 10, maxScore: 15, level: "Walks with help" },
      { activity: "Stairs", score: 5, maxScore: 10, level: "Needs help" },
    ],
  },
  {
    id: "fa-2", type: "katz", date: "2026-04-01", assessor: "Sr. N. Phiri", totalScore: 5, maxScore: 6,
    interpretation: "Moderately independent — needs assistance with one ADL",
    activities: [
      { activity: "Bathing", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Dressing", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Toileting", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Transferring", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Continence", score: 1, maxScore: 1, level: "Continent" },
      { activity: "Feeding", score: 0, maxScore: 1, level: "Needs assistance" },
    ],
  },
  {
    id: "fa-3", type: "lawton", date: "2026-04-01", assessor: "Sr. N. Phiri", totalScore: 6, maxScore: 8,
    interpretation: "Some dependence in instrumental activities",
    activities: [
      { activity: "Telephone use", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Shopping", score: 0, maxScore: 1, level: "Needs assistance" },
      { activity: "Food preparation", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Housekeeping", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Laundry", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Transportation", score: 0, maxScore: 1, level: "Needs assistance" },
      { activity: "Medication management", score: 1, maxScore: 1, level: "Independent" },
      { activity: "Finances", score: 1, maxScore: 1, level: "Independent" },
    ],
  },
];

const HISTORY: { date: string; barthel: number; katz: number; lawton: number }[] = [
  { date: "2026-04-01", barthel: 85, katz: 5, lawton: 6 },
  { date: "2026-01-10", barthel: 80, katz: 4, lawton: 5 },
  { date: "2025-10-05", barthel: 75, katz: 4, lawton: 5 },
  { date: "2025-07-15", barthel: 70, katz: 3, lawton: 4 },
];

const TAB_LABELS: Record<AssessmentType, string> = {
  barthel: "Barthel Index (ADL)",
  katz: "Katz ADL",
  lawton: "Lawton IADL",
};

function scoreColor(score: number, max: number): string {
  const pct = score / max;
  if (pct >= 0.8) return "text-green-600";
  if (pct >= 0.5) return "text-amber-600";
  return "text-red-600";
}

function barColor(score: number, max: number): string {
  const pct = score / max;
  if (pct >= 0.8) return "bg-green-500";
  if (pct >= 0.5) return "bg-amber-500";
  return "bg-red-500";
}

export default function FunctionalStatusPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["functional-status", patientId],
    queryFn: async () => ({ data: MOCK_ASSESSMENTS }),
  });

  const assessments = data?.data ?? [];
  const [activeTab, setActiveTab] = useState<AssessmentType>("barthel");
  const current = assessments.find((a) => a.type === activeTab);

  return (
    <EHRLayout>
      <PageShell title="Functional Status" subtitle="ADL and IADL assessments with standardized scoring">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading assessments...</span>
          </div>
        ) : (
          <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="w-5 h-5 text-orange-600" />
                <h2 className="text-lg font-semibold text-gray-900">Functional Status</h2>
              </div>
              <button className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                New Assessment
              </button>
            </div>

            {/* Score Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {assessments.map((a) => (
                <button
                  key={a.id}
                  onClick={() => setActiveTab(a.type)}
                  className={`bg-white rounded-lg border p-4 text-left transition-all ${activeTab === a.type ? "border-blue-300 ring-2 ring-blue-100" : "border-gray-200 hover:border-gray-300"}`}
                >
                  <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{TAB_LABELS[a.type]}</p>
                  <div className="flex items-baseline gap-1 mt-1">
                    <span className={`text-2xl font-bold ${scoreColor(a.totalScore, a.maxScore)}`}>{a.totalScore}</span>
                    <span className="text-sm text-gray-400">/ {a.maxScore}</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-2 mt-2">
                    <div className={`h-2 rounded-full ${barColor(a.totalScore, a.maxScore)}`} style={{ width: `${(a.totalScore / a.maxScore) * 100}%` }} />
                  </div>
                  <p className="text-[10px] text-gray-400 mt-1">{a.date} &middot; {a.assessor}</p>
                </button>
              ))}
            </div>

            {/* Current Assessment Detail */}
            {current && (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                <div className="px-5 py-4 border-b border-gray-200">
                  <h3 className="font-medium text-gray-900">{TAB_LABELS[current.type]}</h3>
                  <p className="text-xs text-gray-500 mt-1">{current.interpretation}</p>
                </div>
                <div className="p-5">
                  <div className="space-y-3">
                    {current.activities.map((act) => (
                      <div key={act.activity} className="flex items-center gap-4">
                        <span className="text-sm text-gray-700 w-40 shrink-0">{act.activity}</span>
                        <div className="flex-1">
                          <div className="w-full bg-gray-100 rounded-full h-4 relative">
                            <div
                              className={`h-4 rounded-full ${barColor(act.score, act.maxScore)}`}
                              style={{ width: `${(act.score / act.maxScore) * 100}%` }}
                            />
                          </div>
                        </div>
                        <span className={`text-sm font-medium w-12 text-right ${scoreColor(act.score, act.maxScore)}`}>
                          {act.score}/{act.maxScore}
                        </span>
                        <span className={`text-xs w-28 text-right ${act.score === act.maxScore ? "text-green-600" : "text-amber-600"}`}>
                          {act.level}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {/* Trend Tracking */}
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <div className="px-5 py-4 border-b border-gray-200 flex items-center gap-2">
                <TrendingUp className="w-4 h-4 text-gray-500" />
                <h3 className="font-medium text-gray-900">Score Trend</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Date</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Barthel (/100)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Katz (/6)</th>
                      <th className="text-left px-4 py-3 font-medium text-gray-600">Lawton (/8)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {HISTORY.map((h, i) => (
                      <tr key={h.date} className="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 text-gray-900 flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5 text-gray-400" />
                          {h.date}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(h.barthel, 100)}`}>
                          {h.barthel}
                          {i < HISTORY.length - 1 && h.barthel > HISTORY[i + 1].barthel && <span className="text-green-500 text-xs ml-1">+{h.barthel - HISTORY[i + 1].barthel}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(h.katz, 6)}`}>
                          {h.katz}
                          {i < HISTORY.length - 1 && h.katz > HISTORY[i + 1].katz && <span className="text-green-500 text-xs ml-1">+{h.katz - HISTORY[i + 1].katz}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(h.lawton, 8)}`}>
                          {h.lawton}
                          {i < HISTORY.length - 1 && h.lawton > HISTORY[i + 1].lawton && <span className="text-green-500 text-xs ml-1">+{h.lawton - HISTORY[i + 1].lawton}</span>}
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
