"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, Calendar, ClipboardList, FileText, Loader2, TrendingUp } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useFacilityStore } from "@/hooks/useFacilityStore";

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
    interpretation: "Moderate dependence - needs some assistance with daily activities",
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
    interpretation: "Moderately independent - needs assistance with one ADL",
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
  const percent = score / max;
  if (percent >= 0.8) return "text-green-600";
  if (percent >= 0.5) return "text-amber-600";
  return "text-red-600";
}

function barColor(score: number, max: number): string {
  const percent = score / max;
  if (percent >= 0.8) return "bg-green-500";
  if (percent >= 0.5) return "bg-amber-500";
  return "bg-red-500";
}

export default function FunctionalStatusPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading } = useQuery({
    queryKey: ["functional-status", patientId],
    queryFn: async () => ({ data: MOCK_ASSESSMENTS }),
  });

  const assessments = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const [activeTab, setActiveTab] = useState<AssessmentType>("barthel");
  const current = assessments.find((assessment) => assessment.type === activeTab);
  const assistanceFlags = current?.activities.filter((activity) => activity.score < activity.maxScore).length ?? 0;

  return (
    <EHRLayout>
      <PageShell title="Functional Status" subtitle="ADL and IADL assessments with standardized scoring">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading assessments...</span>
          </div>
        ) : (
          <div className="space-y-6">
            <ClinicalReviewHeader
              badge="Functional review"
              badgeIcon={Activity}
              title="Keep daily-function risk visible so plans, team actions, and follow-up needs stay grounded in what the patient can actually do"
              description="Functional status now lives inside the same continuity loop as care plans, goals, and social context so teams can act on dependence or recovery without losing encounter context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList },
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: TrendingUp, tone: "secondary" },
                { href: `/ehr/${patientId}/social-history`, label: "Social History", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Barthel",
                  value: String(assessments.find((assessment) => assessment.type === "barthel")?.totalScore ?? 0),
                  detail: "Current ADL independence signal.",
                },
                {
                  label: "IADL score",
                  value: String(assessments.find((assessment) => assessment.type === "lawton")?.totalScore ?? 0),
                  detail: "Instrumental function that affects home follow-through.",
                },
                {
                  label: "Assist needs",
                  value: String(assistanceFlags),
                  detail: "Areas below full independence in the currently selected assessment.",
                },
              ]}
            />

            <div className="rounded-3xl border border-slate-200 bg-slate-50/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Functional continuity</p>
              <p className="mt-2 text-sm text-slate-800">
                {assistanceFlags > 0
                  ? `${assistanceFlags} current activity area${assistanceFlags === 1 ? " is" : "s are"} below full independence, so this review should feed directly into the care plan, social support, and documented next actions.`
                  : "Current function is stable; the continuity need is keeping that status visible when plans or discharge-related steps are updated elsewhere."}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                Use Care Plans for intervention ownership, Social History for environmental barriers, and Notes for the clinical handoff.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="h-5 w-5 text-orange-600" />
                <h2 className="text-lg font-semibold text-gray-900">Functional Status</h2>
              </div>
              <button className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-blue-700">
                New Assessment
              </button>
            </div>

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {assessments.map((assessment) => (
                <button
                  key={assessment.id}
                  onClick={() => setActiveTab(assessment.type)}
                  className={`rounded-lg border bg-white p-4 text-left transition-all ${activeTab === assessment.type ? "border-blue-300 ring-2 ring-blue-100" : "border-gray-200 hover:border-gray-300"}`}
                >
                  <p className="text-xs font-medium uppercase tracking-wide text-gray-500">{TAB_LABELS[assessment.type]}</p>
                  <div className="mt-1 flex items-baseline gap-1">
                    <span className={`text-2xl font-bold ${scoreColor(assessment.totalScore, assessment.maxScore)}`}>{assessment.totalScore}</span>
                    <span className="text-sm text-gray-400">/ {assessment.maxScore}</span>
                  </div>
                  <div className="mt-2 h-2 w-full rounded-full bg-gray-200">
                    <div className={`h-2 rounded-full ${barColor(assessment.totalScore, assessment.maxScore)}`} style={{ width: `${(assessment.totalScore / assessment.maxScore) * 100}%` }} />
                  </div>
                  <p className="mt-1 text-[10px] text-gray-400">{assessment.date} · {assessment.assessor}</p>
                </button>
              ))}
            </div>

            {current && (
              <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
                <div className="border-b border-gray-200 px-5 py-4">
                  <h3 className="font-medium text-gray-900">{TAB_LABELS[current.type]}</h3>
                  <p className="mt-1 text-xs text-gray-500">{current.interpretation}</p>
                </div>
                <div className="p-5">
                  <div className="space-y-3">
                    {current.activities.map((activity) => (
                      <div key={activity.activity} className="flex items-center gap-4">
                        <span className="w-40 shrink-0 text-sm text-gray-700">{activity.activity}</span>
                        <div className="flex-1">
                          <div className="relative h-4 w-full rounded-full bg-gray-100">
                            <div className={`h-4 rounded-full ${barColor(activity.score, activity.maxScore)}`} style={{ width: `${(activity.score / activity.maxScore) * 100}%` }} />
                          </div>
                        </div>
                        <span className={`w-12 text-right text-sm font-medium ${scoreColor(activity.score, activity.maxScore)}`}>{activity.score}/{activity.maxScore}</span>
                        <span className={`w-28 text-right text-xs ${activity.score === activity.maxScore ? "text-green-600" : "text-amber-600"}`}>{activity.level}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}

            <div className="overflow-hidden rounded-lg border border-gray-200 bg-white">
              <div className="flex items-center gap-2 border-b border-gray-200 px-5 py-4">
                <TrendingUp className="h-4 w-4 text-gray-500" />
                <h3 className="font-medium text-gray-900">Score Trend</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-200 bg-gray-50">
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Date</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Barthel (/100)</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Katz (/6)</th>
                      <th className="px-4 py-3 text-left font-medium text-gray-600">Lawton (/8)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {HISTORY.map((history, index) => (
                      <tr key={history.date} className="border-b border-gray-100 transition-colors hover:bg-gray-50">
                        <td className="flex items-center gap-1.5 px-4 py-3 text-gray-900">
                          <Calendar className="h-3.5 w-3.5 text-gray-400" />
                          {history.date}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.barthel, 100)}`}>
                          {history.barthel}
                          {index < HISTORY.length - 1 && history.barthel > HISTORY[index + 1].barthel && <span className="ml-1 text-xs text-green-500">+{history.barthel - HISTORY[index + 1].barthel}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.katz, 6)}`}>
                          {history.katz}
                          {index < HISTORY.length - 1 && history.katz > HISTORY[index + 1].katz && <span className="ml-1 text-xs text-green-500">+{history.katz - HISTORY[index + 1].katz}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.lawton, 8)}`}>
                          {history.lawton}
                          {index < HISTORY.length - 1 && history.lawton > HISTORY[index + 1].lawton && <span className="ml-1 text-xs text-green-500">+{history.lawton - HISTORY[index + 1].lawton}</span>}
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
