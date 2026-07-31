"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { Activity, Calendar, ClipboardList, FileText, Loader2, TrendingUp } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { AssessmentType, FunctionalAssessment } from "@/hooks/queries/useStructuredHistory";
import {
  useFunctionalAssessments,
  useRecordFunctionalAssessment,
} from "@/hooks/queries/useStructuredHistory";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const MAX_BY_TYPE: Record<AssessmentType, number> = {
  barthel: 100,
  katz: 6,
  lawton: 8,
};

function buildTrendHistory(assessments: FunctionalAssessment[]): { date: string; barthel: number; katz: number; lawton: number }[] {
  const byDate = new Map<string, { date: string; barthel?: number; katz?: number; lawton?: number }>();
  for (const a of assessments) {
    const row = byDate.get(a.date) ?? { date: a.date };
    if (a.type === "barthel") row.barthel = a.totalScore;
    if (a.type === "katz") row.katz = a.totalScore;
    if (a.type === "lawton") row.lawton = a.totalScore;
    byDate.set(a.date, row);
  }
  return Array.from(byDate.values())
    .filter((r) => r.barthel != null || r.katz != null || r.lawton != null)
    .sort((a, b) => b.date.localeCompare(a.date))
    .map((r) => ({
      date: r.date,
      barthel: r.barthel ?? 0,
      katz: r.katz ?? 0,
      lawton: r.lawton ?? 0,
    }));
}

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

  const { data, isLoading, isError, refetch } = useFunctionalAssessments(patientId);
  const recordAssessment = useRecordFunctionalAssessment(patientId);
  const assessments: FunctionalAssessment[] = data?.data ?? [];
  const trendHistory = buildTrendHistory(assessments);
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.isOpen
  );
  const [activeTab, setActiveTab] = useState<AssessmentType>("barthel");
  const [showNew, setShowNew] = useState(false);
  const [newType, setNewType] = useState<AssessmentType>("barthel");
  const [newScore, setNewScore] = useState("");
  const [newAssessor, setNewAssessor] = useState("");
  const [newInterpretation, setNewInterpretation] = useState("");
  const [scoreAbsent, setScoreAbsent] = useState("");
  const current = assessments.find((assessment) => assessment.type === activeTab);
  const assistanceFlags = current?.activities.filter((activity) => activity.score < activity.maxScore).length ?? 0;

  function saveAssessment() {
    const score = newScore.trim() === "" ? undefined : Number(newScore);
    const reason = scoreAbsent.trim() || undefined;
    if ((score == null) === (reason == null)) return;
    recordAssessment.mutate(
      {
        assessmentType: newType,
        assessor: newAssessor.trim() || undefined,
        totalScore: score,
        maxScore: score != null ? MAX_BY_TYPE[newType] : undefined,
        scoreAbsentReason: reason,
        interpretation: newInterpretation.trim() || undefined,
      },
      {
        onSuccess: () => {
          setShowNew(false);
          setNewScore("");
          setNewAssessor("");
          setNewInterpretation("");
          setScoreAbsent("");
          setActiveTab(newType);
        },
      },
    );
  }

  return (
    <EHRLayout>
      <PageShell title="Functional Status" subtitle="ADL and IADL assessments with standardized scoring">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading assessments...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-muted-foreground">Unable to load functional assessments for this patient.</p>
            <button
              type="button"
              onClick={() => void refetch()}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              Retry
            </button>
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

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Functional continuity</p>
              <p className="mt-2 text-sm text-foreground">
                {assistanceFlags > 0
                  ? `${assistanceFlags} current activity area${assistanceFlags === 1 ? " is" : "s are"} below full independence, so this review should feed directly into the care plan, social support, and documented next actions.`
                  : "Current function is stable; the continuity need is keeping that status visible when plans or discharge-related steps are updated elsewhere."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use Care Plans for intervention ownership, Social History for environmental barriers, and Notes for the clinical handoff.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Activity className="h-5 w-5 text-orange-600" />
                <h2 className="text-lg font-semibold text-foreground">Functional Status</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowNew((v) => !v)}
                className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
                data-testid="functional-new-toggle"
              >
                {showNew ? "Cancel" : "New Assessment"}
              </button>
            </div>

            {showNew && (
              <div
                className="rounded-lg border border-border bg-card p-4 space-y-3"
                data-testid="functional-new"
              >
                <p className="text-sm text-muted-foreground">
                  Append-only: saving records a new assessment. Carry a score <strong>or</strong> a
                  reason the score could not be obtained — never both, never neither (a fabricated
                  zero reads as total dependence).
                </p>
                <div className="flex flex-wrap gap-2">
                  <select
                    className="rounded border border-border px-2 py-1 text-sm"
                    value={newType}
                    onChange={(e) => setNewType(e.target.value as AssessmentType)}
                    data-testid="functional-type"
                  >
                    {(Object.keys(TAB_LABELS) as AssessmentType[]).map((t) => (
                      <option key={t} value={t}>
                        {TAB_LABELS[t]}
                      </option>
                    ))}
                  </select>
                  <input
                    type="number"
                    className="rounded border border-border px-2 py-1 text-sm w-28"
                    placeholder={`Score / ${MAX_BY_TYPE[newType]}`}
                    value={newScore}
                    onChange={(e) => {
                      setNewScore(e.target.value);
                      if (e.target.value.trim() !== "") setScoreAbsent("");
                    }}
                    data-testid="functional-score"
                  />
                  <input
                    type="text"
                    className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
                    placeholder="Or reason score could not be obtained"
                    value={scoreAbsent}
                    onChange={(e) => {
                      setScoreAbsent(e.target.value);
                      if (e.target.value.trim() !== "") setNewScore("");
                    }}
                    data-testid="functional-absent-reason"
                  />
                  <input
                    type="text"
                    className="rounded border border-border px-2 py-1 text-sm w-40"
                    placeholder="Assessor"
                    value={newAssessor}
                    onChange={(e) => setNewAssessor(e.target.value)}
                    data-testid="functional-assessor"
                  />
                  <input
                    type="text"
                    className="flex-1 min-w-48 rounded border border-border px-2 py-1 text-sm"
                    placeholder="Interpretation (optional)"
                    value={newInterpretation}
                    onChange={(e) => setNewInterpretation(e.target.value)}
                    data-testid="functional-interpretation"
                  />
                  <button
                    type="button"
                    onClick={saveAssessment}
                    disabled={
                      recordAssessment.isPending ||
                      (newScore.trim() === "") === (scoreAbsent.trim() === "")
                    }
                    className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
                    data-testid="functional-save"
                  >
                    {recordAssessment.isPending ? (
                      <Loader2 className="h-4 w-4 animate-spin inline" />
                    ) : (
                      "Save assessment"
                    )}
                  </button>
                </div>
                {recordAssessment.isError && (
                  <p className="text-sm text-danger" data-testid="functional-save-failed">
                    The assessment was <strong>not</strong> saved. Nothing has been recorded.
                  </p>
                )}
              </div>
            )}

            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {assessments.map((assessment) => (
                <button
                  key={assessment.id}
                  onClick={() => setActiveTab(assessment.type)}
                  className={`rounded-lg border bg-card p-4 text-left transition-all ${activeTab === assessment.type ? "border-primary/25 ring-2 ring-blue-100" : "border-border hover:border-border"}`}
                >
                  <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{TAB_LABELS[assessment.type]}</p>
                  <div className="mt-1 flex items-baseline gap-1">
                    <span className={`text-2xl font-bold ${scoreColor(assessment.totalScore, assessment.maxScore)}`}>{assessment.totalScore}</span>
                    <span className="text-sm text-muted-foreground">/ {assessment.maxScore}</span>
                  </div>
                  <div className="mt-2 h-2 w-full rounded-full bg-neutral-100">
                    <div className={`h-2 rounded-full ${barColor(assessment.totalScore, assessment.maxScore)}`} style={{ width: `${(assessment.totalScore / assessment.maxScore) * 100}%` }} />
                  </div>
                                    <p className="mt-1 text-[10px] text-muted-foreground">
                    {assessment.date} · {assessment.assessor}
                  </p>
                </button>
              ))}
            </div>

            {current && (
              <div className="overflow-hidden rounded-lg border border-border bg-card">
                <div className="border-b border-border px-5 py-4">
                  <h3 className="font-medium text-foreground">{TAB_LABELS[current.type]}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{current.interpretation}</p>
                </div>
                <div className="p-5">
                  <div className="space-y-3">
                    {current.activities.map((activity) => (
                      <div key={activity.activity} className="flex items-center gap-4">
                        <span className="w-40 shrink-0 text-sm text-foreground">{activity.activity}</span>
                        <div className="flex-1">
                          <div className="relative h-4 w-full rounded-full bg-neutral-100">
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

            <div className="overflow-hidden rounded-lg border border-border bg-card">
              <div className="flex items-center gap-2 border-b border-border px-5 py-4">
                <TrendingUp className="h-4 w-4 text-muted-foreground" />
                <h3 className="font-medium text-foreground">Score Trend</h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border bg-background">
                      <th className="px-4 py-3 text-left font-medium text-muted-foreground">Date</th>
                      <th className="px-4 py-3 text-left font-medium text-muted-foreground">Barthel (/100)</th>
                      <th className="px-4 py-3 text-left font-medium text-muted-foreground">Katz (/6)</th>
                      <th className="px-4 py-3 text-left font-medium text-muted-foreground">Lawton (/8)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {trendHistory.map((history, index) => (
                      <tr key={history.date} className="border-b border-border transition-colors hover:bg-background">
                        <td className="flex items-center gap-1.5 px-4 py-3 text-foreground">
                          <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                          {history.date}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.barthel, 100)}`}>
                          {history.barthel}
                          {index < trendHistory.length - 1 && history.barthel > trendHistory[index + 1].barthel && <span className="ml-1 text-xs text-green-500">+{history.barthel - trendHistory[index + 1].barthel}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.katz, 6)}`}>
                          {history.katz}
                          {index < trendHistory.length - 1 && history.katz > trendHistory[index + 1].katz && <span className="ml-1 text-xs text-green-500">+{history.katz - trendHistory[index + 1].katz}</span>}
                        </td>
                        <td className={`px-4 py-3 font-medium ${scoreColor(history.lawton, 8)}`}>
                          {history.lawton}
                          {index < trendHistory.length - 1 && history.lawton > trendHistory[index + 1].lawton && <span className="ml-1 text-xs text-green-500">+{history.lawton - trendHistory[index + 1].lawton}</span>}
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
