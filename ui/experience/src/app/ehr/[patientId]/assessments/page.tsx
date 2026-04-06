"use client";

/**
 * Assessments — Standardized clinical scoring tools (PHQ-9, GAD-7, MMSE, etc.).
 * Route: /ehr/[patientId]/assessments | pageTitle: "Assessments"
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import { ClipboardList, Loader2, Plus, ChevronDown, ChevronUp, TrendingUp, TrendingDown } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface AssessmentItem {
  question: string;
  score: number;
  maxScore: number;
}

interface AssessmentResult {
  id: string;
  tool: string;
  date: string;
  assessor: string;
  totalScore: number;
  maxScore: number;
  interpretation: string;
  severity: "None" | "Mild" | "Moderate" | "Moderately Severe" | "Severe" | "Normal" | "Abnormal";
  items: AssessmentItem[];
}

interface AssessmentTool {
  id: string;
  name: string;
  description: string;
  category: string;
  latestResult: AssessmentResult | null;
  history: { date: string; score: number }[];
}

const MOCK_TOOLS: AssessmentTool[] = [
  {
    id: "phq9", name: "PHQ-9", description: "Patient Health Questionnaire (Depression)", category: "Mental Health",
    latestResult: {
      id: "ar-1", tool: "PHQ-9", date: "2026-04-01", assessor: "Dr. S. Chirwa", totalScore: 8, maxScore: 27, interpretation: "Mild depression — monitor and reassess in 4 weeks", severity: "Mild",
      items: [
        { question: "Little interest or pleasure in doing things", score: 1, maxScore: 3 },
        { question: "Feeling down, depressed, or hopeless", score: 1, maxScore: 3 },
        { question: "Trouble falling or staying asleep", score: 2, maxScore: 3 },
        { question: "Feeling tired or having little energy", score: 1, maxScore: 3 },
        { question: "Poor appetite or overeating", score: 0, maxScore: 3 },
        { question: "Feeling bad about yourself", score: 1, maxScore: 3 },
        { question: "Trouble concentrating", score: 1, maxScore: 3 },
        { question: "Moving or speaking slowly/being fidgety", score: 0, maxScore: 3 },
        { question: "Thoughts of self-harm", score: 1, maxScore: 3 },
      ],
    },
    history: [{ date: "2026-04-01", score: 8 }, { date: "2026-01-15", score: 12 }, { date: "2025-10-10", score: 14 }, { date: "2025-07-05", score: 16 }],
  },
  {
    id: "gad7", name: "GAD-7", description: "Generalized Anxiety Disorder Scale", category: "Mental Health",
    latestResult: {
      id: "ar-2", tool: "GAD-7", date: "2026-04-01", assessor: "Dr. S. Chirwa", totalScore: 6, maxScore: 21, interpretation: "Mild anxiety — consider watchful waiting", severity: "Mild",
      items: [
        { question: "Feeling nervous, anxious, or on edge", score: 1, maxScore: 3 },
        { question: "Not being able to stop worrying", score: 1, maxScore: 3 },
        { question: "Worrying too much about different things", score: 1, maxScore: 3 },
        { question: "Trouble relaxing", score: 1, maxScore: 3 },
        { question: "Being so restless its hard to sit still", score: 1, maxScore: 3 },
        { question: "Becoming easily annoyed or irritable", score: 1, maxScore: 3 },
        { question: "Feeling afraid something awful might happen", score: 0, maxScore: 3 },
      ],
    },
    history: [{ date: "2026-04-01", score: 6 }, { date: "2026-01-15", score: 9 }, { date: "2025-10-10", score: 11 }],
  },
  {
    id: "mmse", name: "MMSE", description: "Mini-Mental State Examination", category: "Cognitive",
    latestResult: {
      id: "ar-3", tool: "MMSE", date: "2026-03-20", assessor: "Dr. M. Ndlovu", totalScore: 27, maxScore: 30, interpretation: "Normal cognitive function", severity: "Normal",
      items: [
        { question: "Orientation to time", score: 5, maxScore: 5 },
        { question: "Orientation to place", score: 5, maxScore: 5 },
        { question: "Registration (3 words)", score: 3, maxScore: 3 },
        { question: "Attention and calculation", score: 4, maxScore: 5 },
        { question: "Recall", score: 2, maxScore: 3 },
        { question: "Language and praxis", score: 8, maxScore: 9 },
      ],
    },
    history: [{ date: "2026-03-20", score: 27 }, { date: "2025-09-15", score: 28 }],
  },
  {
    id: "falls", name: "Morse Falls Scale", description: "Fall Risk Assessment", category: "Safety",
    latestResult: {
      id: "ar-4", tool: "Morse Falls Scale", date: "2026-04-02", assessor: "Sr. N. Phiri", totalScore: 35, maxScore: 125, interpretation: "Low fall risk — standard precautions", severity: "Mild",
      items: [
        { question: "History of falling", score: 0, maxScore: 25 },
        { question: "Secondary diagnosis", score: 15, maxScore: 15 },
        { question: "Ambulatory aid", score: 15, maxScore: 30 },
        { question: "IV/Heparin Lock", score: 0, maxScore: 20 },
        { question: "Gait", score: 0, maxScore: 20 },
        { question: "Mental status", score: 5, maxScore: 15 },
      ],
    },
    history: [{ date: "2026-04-02", score: 35 }, { date: "2026-01-05", score: 40 }],
  },
  {
    id: "braden", name: "Braden Scale", description: "Pressure Ulcer Risk", category: "Safety",
    latestResult: {
      id: "ar-5", tool: "Braden Scale", date: "2026-04-02", assessor: "Sr. N. Phiri", totalScore: 19, maxScore: 23, interpretation: "No risk for pressure ulcer", severity: "Normal",
      items: [
        { question: "Sensory perception", score: 4, maxScore: 4 },
        { question: "Moisture", score: 3, maxScore: 4 },
        { question: "Activity", score: 3, maxScore: 4 },
        { question: "Mobility", score: 3, maxScore: 4 },
        { question: "Nutrition", score: 3, maxScore: 4 },
        { question: "Friction and shear", score: 3, maxScore: 3 },
      ],
    },
    history: [{ date: "2026-04-02", score: 19 }],
  },
  {
    id: "pain", name: "Numeric Pain Scale", description: "Pain Assessment (0-10)", category: "Pain",
    latestResult: {
      id: "ar-6", tool: "Numeric Pain Scale", date: "2026-04-03", assessor: "Sr. N. Phiri", totalScore: 3, maxScore: 10, interpretation: "Mild pain — monitor and treat PRN", severity: "Mild",
      items: [
        { question: "Current pain intensity", score: 3, maxScore: 10 },
      ],
    },
    history: [{ date: "2026-04-03", score: 3 }, { date: "2026-03-28", score: 5 }, { date: "2026-03-20", score: 4 }, { date: "2026-03-10", score: 6 }],
  },
];

const SEVERITY_STYLES: Record<string, string> = {
  "None": "bg-green-100 text-green-700",
  "Mild": "bg-yellow-100 text-yellow-700",
  "Moderate": "bg-amber-100 text-amber-700",
  "Moderately Severe": "bg-orange-100 text-orange-700",
  "Severe": "bg-red-100 text-red-700",
  "Normal": "bg-green-100 text-green-700",
  "Abnormal": "bg-red-100 text-red-700",
};

export default function AssessmentsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;

  const { data, isLoading } = useQuery({
    queryKey: ["assessments", patientId],
    queryFn: async () => ({ data: MOCK_TOOLS }),
  });

  const tools = data?.data ?? [];
  const [expandedTool, setExpandedTool] = useState<string | null>(null);
  const [filterCategory, setFilterCategory] = useState("All");

  const categories = ["All", ...new Set(tools.map((t) => t.category))];
  const filtered = filterCategory === "All" ? tools : tools.filter((t) => t.category === filterCategory);

  return (
    <EHRLayout>
      <PageShell title="Clinical Assessments" subtitle="Standardized scoring tools and screening results">
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
                <ClipboardList className="w-5 h-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-gray-900">Clinical Assessments</h2>
              </div>
              <div className="flex items-center gap-3">
                <select value={filterCategory} onChange={(e) => setFilterCategory(e.target.value)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  {categories.map((c) => <option key={c}>{c}</option>)}
                </select>
                <button className="inline-flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                  <Plus className="w-4 h-4" /> New Assessment
                </button>
              </div>
            </div>

            {/* Assessment Cards */}
            {filtered.length === 0 ? (
              <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
                <ClipboardList className="w-10 h-10 text-gray-300 mx-auto mb-3" />
                <p className="text-gray-400 text-sm">No assessments found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {filtered.map((tool) => {
                  const r = tool.latestResult;
                  const isExpanded = expandedTool === tool.id;
                  const trend = tool.history.length >= 2 ? tool.history[0].score - tool.history[1].score : 0;

                  return (
                    <div key={tool.id} className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                      <button
                        type="button"
                        onClick={() => setExpandedTool(isExpanded ? null : tool.id)}
                        className="w-full px-5 py-4 flex items-center justify-between hover:bg-gray-50 transition-colors text-left"
                      >
                        <div className="flex items-center gap-4">
                          <div>
                            <div className="flex items-center gap-2">
                              <h3 className="font-semibold text-gray-900 text-sm">{tool.name}</h3>
                              <span className="text-xs text-gray-400">{tool.category}</span>
                            </div>
                            <p className="text-xs text-gray-500">{tool.description}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-4">
                          {r && (
                            <>
                              <div className="text-right">
                                <div className="flex items-center gap-1">
                                  <span className="text-lg font-bold text-gray-900">{r.totalScore}</span>
                                  <span className="text-xs text-gray-400">/{r.maxScore}</span>
                                  {trend !== 0 && (
                                    <span className={`flex items-center text-xs ${trend < 0 ? "text-green-500" : "text-red-500"}`}>
                                      {trend < 0 ? <TrendingDown className="w-3 h-3" /> : <TrendingUp className="w-3 h-3" />}
                                      {Math.abs(trend)}
                                    </span>
                                  )}
                                </div>
                                <span className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${SEVERITY_STYLES[r.severity]}`}>{r.severity}</span>
                              </div>
                            </>
                          )}
                          {isExpanded ? <ChevronUp className="w-4 h-4 text-gray-400" /> : <ChevronDown className="w-4 h-4 text-gray-400" />}
                        </div>
                      </button>

                      {isExpanded && r && (
                        <div className="border-t border-gray-200 px-5 py-4 space-y-4">
                          <p className="text-sm text-gray-700">{r.interpretation}</p>
                          <p className="text-xs text-gray-400">{r.date} &middot; {r.assessor}</p>

                          {/* Item Scores */}
                          <div className="space-y-2">
                            {r.items.map((item, idx) => (
                              <div key={idx} className="flex items-center gap-3">
                                <span className="text-xs text-gray-600 flex-1">{item.question}</span>
                                <div className="w-24">
                                  <div className="w-full bg-gray-100 rounded-full h-2">
                                    <div className={`h-2 rounded-full ${item.score === 0 ? "bg-green-400" : item.score <= item.maxScore / 2 ? "bg-amber-400" : "bg-red-400"}`} style={{ width: `${(item.score / item.maxScore) * 100}%` }} />
                                  </div>
                                </div>
                                <span className="text-xs font-medium text-gray-700 w-8 text-right">{item.score}/{item.maxScore}</span>
                              </div>
                            ))}
                          </div>

                          {/* History */}
                          {tool.history.length > 1 && (
                            <div>
                              <h4 className="text-xs font-medium text-gray-500 mb-2">Score History</h4>
                              <div className="flex items-end gap-2 h-16">
                                {tool.history.slice().reverse().map((h, i) => {
                                  const height = (h.score / (r.maxScore || 1)) * 100;
                                  return (
                                    <div key={i} className="flex flex-col items-center gap-1 flex-1">
                                      <span className="text-[10px] font-medium text-gray-600">{h.score}</span>
                                      <div className="w-full bg-blue-200 rounded-t" style={{ height: `${Math.max(4, height)}%` }} />
                                      <span className="text-[9px] text-gray-400">{h.date.slice(5)}</span>
                                    </div>
                                  );
                                })}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
