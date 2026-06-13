"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import { ClipboardList, Calendar, Edit3, FileText, Link2, Loader2, Plus, Target, Users, X } from "lucide-react";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { PatientGoalUi } from "@/hooks/queries/useCareContinuity";
import { usePatientGoalsFromCarePlans, useCreateGoal } from "@/hooks/queries/useCareContinuity";
import { useFacilityStore } from "@/hooks/useFacilityStore";

type PatientGoal = PatientGoalUi;

const STATUS_STYLES: Record<string, string> = {
  "On Track": "bg-green-100 text-green-700",
  "At Risk": "bg-amber-100 text-warning-foreground",
  Behind: "bg-red-100 text-danger",
  Achieved: "bg-primary-soft text-primary",
  Cancelled: "bg-neutral-100 text-muted-foreground",
};

const PRIORITY_STYLES: Record<string, string> = {
  High: "bg-danger-soft text-red-600 border-danger/28",
  Medium: "bg-warning-soft text-amber-600 border-warning/35",
  Low: "bg-green-50 text-green-600 border-green-200",
};

function progressColor(progress: number): string {
  if (progress >= 75) return "bg-green-500";
  if (progress >= 50) return "bg-primary";
  if (progress >= 25) return "bg-amber-500";
  return "bg-red-500";
}

export default function GoalsPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading, isError, refetch } = usePatientGoalsFromCarePlans(patientId);
  const createGoal = useCreateGoal();
  const goals: PatientGoal[] = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.status === "IN_PROGRESS" || encounter.attributes.status === "ACTIVE"
  );
  const linkedPlans = new Set(goals.filter((goal) => goal.linkedCarePlan).map((goal) => goal.linkedCarePlan)).size;
  const attentionGoals = goals.filter((goal) => goal.status === "At Risk" || goal.status === "Behind").length;

  const [showForm, setShowForm] = useState(false);
  const [filterStatus, setFilterStatus] = useState("All");
  const [newTitle, setNewTitle] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newPriority, setNewPriority] = useState("Medium");
  const [newTarget, setNewTarget] = useState("");

  const filtered = filterStatus === "All" ? goals : goals.filter((goal) => goal.status === filterStatus);
  const summary = {
    onTrack: goals.filter((goal) => goal.status === "On Track").length,
    atRisk: goals.filter((goal) => goal.status === "At Risk").length,
    behind: goals.filter((goal) => goal.status === "Behind").length,
    achieved: goals.filter((goal) => goal.status === "Achieved").length,
  };

  return (
    <EHRLayout>
      <PageShell title="Goals" subtitle="Patient health goals with SMART tracking">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading goals...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-muted-foreground">Unable to load goals (care-plan source failed).</p>
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
              badge="Patient goals"
              badgeIcon={Target}
              title="Track where the care plan is winning, slipping, or needs a new next step before the encounter closes"
              description="Goals now sit inside the same care-planning loop as plans, care team, and assessments so teams can see which target needs action and move directly into the owning surface."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/care-plans`, label: "Care Plans", icon: ClipboardList },
                { href: `/ehr/${patientId}/assessments`, label: "Assessments", icon: FileText, tone: "secondary" },
                { href: `/ehr/${patientId}/care-team`, label: "Care Team", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "On track",
                  value: String(summary.onTrack),
                  detail: "Targets progressing as expected.",
                },
                {
                  label: "Needs attention",
                  value: String(attentionGoals),
                  detail: "Goals that should drive the next intervention or follow-up note.",
                },
                {
                  label: "Linked plans",
                  value: String(linkedPlans),
                  detail: linkedPlans > 0 ? "Goals already tied back to structured care plans." : "No goals are linked to plans yet.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">Goal continuity</p>
              <p className="mt-2 text-sm text-foreground">
                {attentionGoals > 0
                  ? `${attentionGoals} goal${attentionGoals === 1 ? " is" : "s are"} off-track or at risk, so this page should point the next step back into care plans, assessments, or team ownership rather than leaving the target floating alone.`
                  : "The tracked goals are moving well; the next continuity step is keeping the plan, assessment evidence, and notes in sync as progress changes."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use linked care plans for execution, assessments for evidence, and notes to record barriers or achieved milestones.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Target className="h-5 w-5 text-green-600" />
                <h2 className="text-lg font-semibold text-foreground">Patient Goals</h2>
              </div>
              <div className="flex items-center gap-3">
                <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)} className="rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40">
                  <option>All</option>
                  <option>On Track</option>
                  <option>At Risk</option>
                  <option>Behind</option>
                  <option>Achieved</option>
                </select>
                <button type="button" onClick={() => setShowForm(true)} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover">
                  <Plus className="h-4 w-4" /> New Goal
                </button>
              </div>
            </div>

            <div className="grid grid-cols-4 gap-4">
              {[
                { label: "On Track", count: summary.onTrack, color: "text-green-600 bg-green-50" },
                { label: "At Risk", count: summary.atRisk, color: "text-amber-600 bg-warning-soft" },
                { label: "Behind", count: summary.behind, color: "text-red-600 bg-danger-soft" },
                { label: "Achieved", count: summary.achieved, color: "text-primary bg-primary-soft" },
              ].map((item) => (
                <div key={item.label} className={`rounded-lg p-3 ${item.color}`}>
                  <p className="text-2xl font-bold">{item.count}</p>
                  <p className="text-xs font-medium">{item.label}</p>
                </div>
              ))}
            </div>

            {showForm && (
              <div className="rounded-lg border border-border bg-card p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-medium text-foreground">Create New Goal</h3>
                  <button onClick={() => setShowForm(false)} className="text-muted-foreground hover:text-muted-foreground"><X className="h-4 w-4" /></button>
                </div>
                <div className="space-y-4">
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Goal Title</label>
                    <input type="text" value={newTitle} onChange={(e) => setNewTitle(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" placeholder="e.g., Reduce blood pressure to normal range" />
                  </div>
                  <div>
                    <label className="mb-1 block text-xs font-medium text-muted-foreground">Description</label>
                    <textarea value={newDescription} onChange={(e) => setNewDescription(e.target.value)} rows={2} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" placeholder="Specific, measurable details..." />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Priority</label>
                      <select value={newPriority} onChange={(e) => setNewPriority(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40">
                        <option>High</option>
                        <option>Medium</option>
                        <option>Low</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Target Date</label>
                      <input type="date" value={newTarget} onChange={(e) => setNewTarget(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                    </div>
                  </div>
                </div>
                {createGoal.isError && (
                  <p className="text-xs text-red-600 pt-1">Failed to create goal. Please try again.</p>
                )}
                <div className="flex items-center gap-3 pt-4">
                  <button
                    type="button"
                    disabled={createGoal.isPending || !newTitle}
                    onClick={() => {
                      createGoal.mutate(
                        { patientId, title: newTitle, description: newDescription, priority: newPriority, targetDate: newTarget },
                        {
                          onSuccess: () => {
                            setNewTitle("");
                            setNewDescription("");
                            setNewPriority("Medium");
                            setNewTarget("");
                            setShowForm(false);
                          },
                        },
                      );
                    }}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-50"
                  >
                    {createGoal.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                    Create Goal
                  </button>
                  <button onClick={() => setShowForm(false)} className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background">Cancel</button>
                </div>
              </div>
            )}

            {filtered.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <Target className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No goals found</p>
              </div>
            ) : (
              <div className="space-y-3">
                {filtered.map((goal) => (
                  <div key={goal.id} className="rounded-lg border border-border bg-card p-5">
                    <div className="mb-3 flex items-start justify-between">
                      <div className="flex-1">
                        <div className="mb-1 flex items-center gap-2">
                          <h3 className="text-sm font-medium text-foreground">{goal.title}</h3>
                          <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLES[goal.status]}`}>{goal.status}</span>
                          <span className={`rounded border px-2 py-0.5 text-xs font-medium ${PRIORITY_STYLES[goal.priority]}`}>{goal.priority}</span>
                        </div>
                        <p className="text-xs text-muted-foreground">{goal.description}</p>
                      </div>
                      <button className="p-1 text-muted-foreground transition-colors hover:text-primary">
                        <Edit3 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <div className="mb-3 flex items-center gap-3">
                      <div className="flex-1">
                        <div className="h-2.5 w-full rounded-full bg-neutral-100">
                          <div className={`h-2.5 rounded-full ${progressColor(goal.progress)}`} style={{ width: `${goal.progress}%` }} />
                        </div>
                      </div>
                      <span className="w-12 text-right text-sm font-medium text-foreground">{goal.progress}%</span>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1"><Calendar className="h-3 w-3" /> Target: {goal.targetDate}</span>
                      <span className="text-muted-foreground">|</span>
                      <span>{goal.category}</span>
                      {goal.linkedCarePlan && (
                        <>
                          <span className="text-muted-foreground">|</span>
                          <span className="flex items-center gap-1 text-impilo-400"><Link2 className="h-3 w-3" /> {goal.linkedCarePlan}</span>
                        </>
                      )}
                    </div>
                    {goal.notes && <p className="mt-2 text-xs italic text-muted-foreground">{goal.notes}</p>}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </PageShell>
    </EHRLayout>
  );
}
