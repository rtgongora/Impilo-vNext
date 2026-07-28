"use client";

import { useState } from "react";
import { useParams } from "next/navigation";
import {
  Activity,
  CheckCircle2,
  Circle,
  ClipboardList,
  Clock,
  FileText,
  Loader2,
  Plus,
  Target,
  Users,
  X,
} from "lucide-react";
import { CarePlanOrchestrationRail } from "@/components/chronic-care/CarePlanOrchestrationRail";
import { ClinicalReviewHeader } from "@/components/ehr/ClinicalReviewHeader";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { useEncounters } from "@/hooks/queries/useEncounters";
import type { CarePlanUi } from "@/hooks/queries/useCareContinuity";
import { useCarePlans, useCreateCarePlan } from "@/hooks/queries/useCareContinuity";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const STATUS_STYLES: Record<string, string> = {
  Active: "bg-green-100 text-green-700",
  Completed: "bg-primary-soft text-primary",
  Draft: "bg-neutral-100 text-muted-foreground",
};

export default function CarePlansPage() {
  const params = useParams<{ patientId: string }>();
  const patientId = params.patientId;
  const facility = useFacilityStore((state) => state.facility);
  const { data: encountersData } = useEncounters(patientId);

  const { data, isLoading, isError, refetch } = useCarePlans(patientId);
  const createPlan = useCreateCarePlan();
  const carePlans: CarePlanUi[] = data?.data ?? [];
  const activeEncounter = (encountersData?.data ?? []).find(
    (encounter) =>
      encounter.attributes.isOpen
  );
  const activePlans = carePlans.filter((plan) => plan.status === "Active");
  const draftPlans = carePlans.filter((plan) => plan.status === "Draft");
  const activeGoals = activePlans.flatMap((plan) => plan.goals).filter((goal) => goal.progress < 100).length;
  const pendingInterventions = activePlans
    .flatMap((plan) => plan.interventions)
    .filter((intervention) => !intervention.completed).length;

  const [showForm, setShowForm] = useState(false);
  const [formTitle, setFormTitle] = useState("");
  const [formCategory, setFormCategory] = useState("");
  const [formTargetDate, setFormTargetDate] = useState("");
  const [expandedPlan, setExpandedPlan] = useState<string | null>(null);

  return (
    <EHRLayout>
      <PageShell title="Care Plans" subtitle="Active and historical care plan management">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading care plans...</span>
          </div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center gap-3 py-16">
            <p className="text-sm text-muted-foreground">Unable to load care plans from the server.</p>
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
            <CarePlanOrchestrationRail
              patientId={patientId}
              plans={carePlans}
              onPlanChanged={() => void refetch()}
            />

            <ClinicalReviewHeader
              badge="Care planning"
              badgeIcon={ClipboardList}
              title="Keep goals, interventions, and ownership visible while the current encounter is still in motion"
              description="Care plans now sit in the same orchestration layer as goals, care team, and notes so teams can see who owns the next intervention and move the plan forward without losing patient context."
              facilityName={facility?.name}
              encounterLabel={
                activeEncounter
                  ? `${activeEncounter.attributes.encounterType} since ${new Date(activeEncounter.attributes.startedAt).toLocaleString()}`
                  : null
              }
              actions={[
                { href: `/ehr/${patientId}/summary`, label: "Summary", icon: Activity },
                { href: `/ehr/${patientId}/goals`, label: "Goals", icon: Target, tone: "secondary" },
                { href: `/ehr/${patientId}/care-team`, label: "Care Team", icon: Users, tone: "secondary" },
                { href: `/ehr/${patientId}/notes`, label: "Notes", icon: FileText, tone: "secondary" },
              ]}
              metrics={[
                {
                  label: "Active plans",
                  value: String(activePlans.length),
                  detail: "Plans currently guiding the patient journey.",
                },
                {
                  label: "Goals in motion",
                  value: String(activeGoals),
                  detail: "Goal targets still being worked through across active plans.",
                },
                {
                  label: "Pending interventions",
                  value: String(pendingInterventions),
                  detail: draftPlans.length > 0
                    ? `${draftPlans.length} draft plan${draftPlans.length === 1 ? "" : "s"} still need activation.`
                    : "No draft plans are waiting to be activated.",
                },
              ]}
            />

            <div className="rounded-3xl border border-border bg-background/70 p-4">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-muted-foreground">
                Care plan loop status
              </p>
              <p className="mt-2 text-sm text-foreground">
                {pendingInterventions > 0
                  ? `${pendingInterventions} intervention${pendingInterventions === 1 ? " is" : "s are"} still open across active plans, so the next step should stay visible here before the encounter closes.`
                  : "The active plans are documented, and the next continuity step is keeping goals, care team, and notes aligned as the plan progresses."}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                Use Goals to track outcome movement, Care Team to confirm ownership, and Notes to record execution or barriers without leaving the patient workspace.
              </p>
            </div>

            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5 text-primary" />
                <h2 className="text-lg font-semibold text-foreground">Care Plans</h2>
                <span className="text-sm text-muted-foreground">({carePlans.length})</span>
              </div>
              <button
                type="button"
                onClick={() => setShowForm((prev) => !prev)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover"
              >
                <Plus className="h-4 w-4" />
                New Care Plan
              </button>
            </div>

            {showForm && (
              <div className="rounded-lg border border-border bg-card p-5">
                <div className="mb-4 flex items-center justify-between">
                  <h3 className="font-medium text-foreground">New Care Plan</h3>
                  <button type="button" onClick={() => setShowForm(false)} className="text-muted-foreground hover:text-muted-foreground">
                    <X className="h-4 w-4" />
                  </button>
                </div>
                <form onSubmit={(e) => {
                  e.preventDefault();
                  if (!formTitle) return;
                  createPlan.mutate(
                    { patientId, title: formTitle, category: formCategory, targetDate: formTargetDate },
                    {
                      onSuccess: () => {
                        setFormTitle("");
                        setFormCategory("");
                        setFormTargetDate("");
                        setShowForm(false);
                      },
                    },
                  );
                }} className="space-y-4">
                  <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Plan Title</label>
                      <input type="text" value={formTitle} onChange={(e) => setFormTitle(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" placeholder="e.g. Diabetes Management" />
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Category</label>
                      <select value={formCategory} onChange={(e) => setFormCategory(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40">
                        <option value="">Select category</option>
                        <option value="Chronic Disease">Chronic Disease</option>
                        <option value="Cardiovascular">Cardiovascular</option>
                        <option value="Rehabilitation">Rehabilitation</option>
                        <option value="Behavioural Health">Behavioural Health</option>
                        <option value="Preventive">Preventive</option>
                      </select>
                    </div>
                    <div>
                      <label className="mb-1 block text-xs font-medium text-muted-foreground">Target Date</label>
                      <input type="date" value={formTargetDate} onChange={(e) => setFormTargetDate(e.target.value)} className="w-full rounded-lg border border-border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40" />
                    </div>
                  </div>
                  {createPlan.isError && (
                    <p className="text-xs text-red-600">Failed to create care plan. Please try again.</p>
                  )}
                  <div className="flex items-center gap-3 pt-2">
                    <button type="submit" disabled={createPlan.isPending || !formTitle} className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-hover disabled:opacity-50">
                      {createPlan.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
                      Create Plan
                    </button>
                    <button type="button" onClick={() => setShowForm(false)} className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-background">Cancel</button>
                  </div>
                </form>
              </div>
            )}

            {carePlans.length === 0 ? (
              <div className="rounded-lg border border-border bg-card p-12 text-center">
                <ClipboardList className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
                <p className="text-sm text-muted-foreground">No care plans created yet</p>
              </div>
            ) : (
              <div className="space-y-4">
                {carePlans.map((plan) => {
                  const isExpanded = expandedPlan === plan.id;
                  return (
                    <div key={plan.id} className="overflow-hidden rounded-lg border border-border bg-card">
                      <button
                        type="button"
                        onClick={() => setExpandedPlan(isExpanded ? null : plan.id)}
                        className="flex w-full items-center justify-between p-4 text-left transition-colors hover:bg-background"
                      >
                        <div className="flex items-center gap-3">
                          <ClipboardList className="h-5 w-5 text-primary" />
                          <div>
                            <div className="font-medium text-foreground">{plan.title}</div>
                            <div className="text-xs text-muted-foreground">
                              {plan.category} · {plan.author}
                            </div>
                          </div>
                        </div>
                        <div className="flex items-center gap-3">
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                              STATUS_STYLES[plan.status] ?? "bg-neutral-100 text-muted-foreground"
                            }`}
                          >
                            {plan.status}
                          </span>
                          {plan.startDate && (
                            <span className="text-xs text-muted-foreground">
                              {plan.startDate}
                              {plan.targetDate ? ` → ${plan.targetDate}` : ""}
                            </span>
                          )}
                        </div>
                      </button>

                      {isExpanded && (
                        <div className="space-y-4 border-t border-border p-4">
                          <div>
                            <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-foreground">
                              <Target className="h-4 w-4 text-amber-600" /> Goals
                            </h4>
                            <div className="space-y-2">
                              {plan.goals.map((goal) => (
                                <div key={goal.id} className="flex items-center gap-3">
                                  <span className="w-48 truncate text-sm text-foreground">{goal.description}</span>
                                  <div className="h-2 flex-1 rounded-full bg-neutral-100">
                                    <div className="h-2 rounded-full bg-primary transition-all" style={{ width: `${goal.progress}%` }} />
                                  </div>
                                  <span className="w-10 text-right text-xs text-muted-foreground">{goal.progress}%</span>
                                </div>
                              ))}
                            </div>
                          </div>

                          <div>
                            <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-foreground">
                              <CheckCircle2 className="h-4 w-4 text-green-600" /> Interventions
                            </h4>
                            <div className="space-y-1.5">
                              {plan.interventions.map((item) => (
                                <div key={item.id || item.label} className="flex items-center gap-2 text-sm">
                                  {item.completed ? (
                                    <CheckCircle2 className="h-4 w-4 text-green-500" />
                                  ) : (
                                    <Circle className="h-4 w-4 text-muted-foreground" />
                                  )}
                                  <span className={item.completed ? "text-muted-foreground line-through" : "text-foreground"}>{item.label}</span>
                                </div>
                              ))}
                            </div>
                          </div>

                          <div className="flex flex-wrap gap-2 pt-2">
                            <a href={`/ehr/${patientId}/goals`} className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background">
                              <Target className="h-3.5 w-3.5" /> Open goals
                            </a>
                            <a href={`/ehr/${patientId}/care-team`} className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background">
                              <Users className="h-3.5 w-3.5" /> Confirm ownership
                            </a>
                            <a href={`/ehr/${patientId}/notes`} className="inline-flex items-center gap-1 rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background">
                              <FileText className="h-3.5 w-3.5" /> Document progress
                            </a>
                            {plan.targetDate && (
                              <span className="inline-flex items-center gap-1 rounded-lg bg-warning-soft px-3 py-1.5 text-xs font-medium text-warning-foreground">
                                <Clock className="h-3.5 w-3.5" /> Due {plan.targetDate}
                              </span>
                            )}
                          </div>
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
