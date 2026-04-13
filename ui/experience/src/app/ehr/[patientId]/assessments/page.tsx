"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  ChevronDown,
  ChevronUp,
  ClipboardList,
  FileText,
  Loader2,
  Plus,
  TrendingDown,
  TrendingUp,
  Users,
} from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

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

const SEVERITY_STYLES: Record<string, string> = {
  None: "bg-green-100 text-green-700",
  Mild: "bg-yellow-100 text-yellow-700",
  Moderate: "bg-amber-100 text-amber-700",
  "Moderately Severe": "bg-orange-100 text-orange-700",
  Severe: "bg-red-100 text-red-700",
  Normal: "bg-green-100 text-green-700",
  Abnormal: "bg-red-100 text-red-700",
};

export default function AssessmentsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  /** Structured screening tools (PHQ-9, GAD-7, etc.) ? no BFF longitudinal store yet; ADL/IADL lives under Functional Status. */
  const tools: AssessmentTool[] = [];
  const isLoading = false;
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const attentionTools = tools.filter((tool) => {
    const severity = tool.latestResult?.severity;
    return severity && severity !== "None" && severity !== "Normal";
  }).length;
  const recentTools = tools.filter((tool) => tool.latestResult?.date.startsWith("2026-04")).length;

  const [expandedTool, setExpandedTool] = useState<string | null>(null);
  const [filterCategory, setFilterCategory] = useState("All");

  const categories = ["All", ...new Set(tools.map((tool) => tool.category))];
  const filtered = filterCategory === "All" ? tools : tools.filter((tool) => tool.category === filterCategory);

  return (
    <EHRLayout>
      <PageShell title="Clinical Assessments" subtitle="Standardized scoring tools and screening results">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading assessments...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-800">
              <p className="font-medium text-slate-900">Clinical screening tools not loaded</p>
              <p className="mt-1 text-slate-600">
                There is no experience-BFF API yet for longitudinal structured instruments (e.g. PHQ-9, GAD-7) tied to this
                chart. For functional ADL/IADL scores, open{" "}
                <a href={`/ehr/${patientId}/functional-status`} className="font-medium text-impilo-600 underline">
                  Functional Status
                </a>
                . Agent 0: patient-scoped assessment-results store + read API.
              </p>
            </div>
            <ClinicalReviewHeader
              badge="Assessments"
              badgeIcon={ClipboardList}
              title="Use structured scores as live evidence for goals, plans, and follow-up decisions"
              description="Assessments now sit in the same continuity loop as care plans, goals, and notes so abnormal scores immediately point to the next planning or communication step."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: Activity },
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList, tone: "secondary" },
                { href: `/ehr/${patientId}/care-team`, label: "Care Team", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Tools tracked",
                  value: String(tools.length),
                  detail: "Assessment instruments available in this longitudinal view.",
                },
                {
                  label: "Need follow-up",
                  value: String(attentionTools),
                  detail: "Latest results that should feed a plan, goal, or escalation decision.",
                },
                {
                  label: "Recent updates",
                  value: String(recentTools),
                  detail: "Assessments captured this month and still relevant to the current loop.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Assessment continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {attentionTools > 0
                  ? `${attentionTools} assessment result${attentionTools === 1 ? " needs" : "s need"} active follow-through, so this page should lead directly into goals, plans, and team communication rather than acting as an isolated score archive.`
                  : "Current assessment results are stable; the continuity need is keeping the evidence trail available when plans and goals are updated elsewhere."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Goals to translate score movement into outcomes, Care Plans to assign interventions, and Notes to communicate the clinical meaning of the score.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-violet-600" />
                <h2 className="text-lg font-semibold text-gray-900">Clinical Assessments</h2>
              </div>
              <div className="flex items-center gap-3">
                <select value={filterCategory} onChange={(e) => setFilterCategory(e.target.value)} className="rounded-lg border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400">
                  {categories.map((category) => <option key={category}>{category}</option>)}
                </select>
                <button className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-impilo-600">
                  <Plus className="h-4 w-4" /> New Assessment
                </button>
              </div>
            </div>

            {filtered.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
                <ClipboardList className="mx-auto mb-3 h-10 w-10 text-gray-300" />
                <p className="text-sm text-gray-400">No assessments found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {filtered.map((tool) => {
                  const result = tool.latestResult;
                  const isExpanded = expandedTool === tool.id;
                  const trend = tool.history.length >= 2 ? tool.history[0].score - tool.history[1].score : 0;

                  return (
                    <div key={tool.id} className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                      <button
                        type="button"
                        onClick={() => setExpandedTool(isExpanded ? null : tool.id)}
                        className="flex w-full items-center justify-between px-5 py-4 text-left transition-colors hover:bg-gray-50"
                      >
                        <div className="flex items-center gap-4">
                          <div>
                            <div className="flex items-center gap-2">
                              <h3 className="text-sm font-semibold text-gray-900">{tool.name}</h3>
                              <span className="text-xs text-gray-400">{tool.category}</span>
                            </div>
                            <p className="text-xs text-gray-500">{tool.description}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-4">
                          {result && (
                            <div className="text-right">
                              <div className="flex items-center gap-1">
                                <span className="text-lg font-bold text-gray-900">{result.totalScore}</span>
                                <span className="text-xs text-gray-400">/{result.maxScore}</span>
                                {trend !== 0 && (
                                  <span className={`flex items-center text-xs ${trend < 0 ? "text-green-500" : "text-red-500"}`}>
                                    {trend < 0 ? <TrendingDown className="h-3 w-3" /> : <TrendingUp className="h-3 w-3" />}
                                    {Math.abs(trend)}
                                  </span>
                                )}
                              </div>
                              <span className={`rounded-full px-2 py-0.5 text-[10px] font-medium ${SEVERITY_STYLES[result.severity]}`}>{result.severity}</span>
                            </div>
                          )}
                          {isExpanded ? <ChevronUp className="h-4 w-4 text-gray-400" /> : <ChevronDown className="h-4 w-4 text-gray-400" />}
                        </div>
                      </button>

                      {isExpanded && result && (
                        <div className="space-y-4 border-t border-gray-200 px-5 py-4">
                          <p className="text-sm text-gray-700">{result.interpretation}</p>
                          <p className="text-xs text-gray-400">{result.date} ? {result.assessor}</p>

                          <div className="space-y-2">
                            {result.items.map((item, index) => (
                              <div key={index} className="flex items-center gap-3">
                                <span className="flex-1 text-xs text-gray-600">{item.question}</span>
                                <div className="w-24">
                                  <div className="h-2 w-full rounded-full bg-gray-100">
                                    <div className={`h-2 rounded-full ${item.score === 0 ? "bg-green-400" : item.score <= item.maxScore / 2 ? "bg-amber-400" : "bg-red-400"}`} style={{ width: `${(item.score / item.maxScore) * 100}%` }} />
                                  </div>
                                </div>
                                <span className="w-8 text-right text-xs font-medium text-gray-700">{item.score}/{item.maxScore}</span>
                              </div>
                            ))}
                          </div>

                          {tool.history.length > 1 && (
                            <div>
                              <h4 className="mb-2 text-xs font-medium text-gray-500">Score History</h4>
                              <div className="flex h-16 items-end gap-2">
                                {tool.history.slice().reverse().map((history, index) => {
                                  const height = (history.score / (result.maxScore || 1)) * 100;
                                  return (
                                    <div key={index} className="flex flex-1 flex-col items-center gap-1">
                                      <span className="text-[10px] font-medium text-gray-600">{history.score}</span>
                                      <div className="w-full rounded-t bg-blue-200" style={{ height: `${Math.max(4, height)}%` }} />
                                      <span className="text-[9px] text-gray-400">{history.date.slice(5)}</span>
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
