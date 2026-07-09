"use client";

import { useMemo, useState } from "react";
import { Route, Loader2, CheckCircle2, Circle, ShieldAlert, ArrowRight } from "lucide-react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useWellnessPlans,
  useWellnessEnrollments,
  useEnrollmentTasks,
  useEnrollPlan,
  useCompleteEnrollmentTask,
} from "@/hooks/queries/useSimbaDepth";

function asRows(payload: unknown): Array<Record<string, unknown>> {
  const data = (payload as { data?: unknown })?.data ?? payload;
  if (Array.isArray(data)) return data as Array<Record<string, unknown>>;
  return [];
}

/** Wellness Plans & Journeys — structured Simba journeys with enrolment + task progress. */
export default function WellnessPlansPage() {
  const user = useAuthStore((s) => s.user);
  const cpid = user?.id;

  const plansQ = useWellnessPlans();
  const enrollmentsQ = useWellnessEnrollments(cpid);
  const enroll = useEnrollPlan();
  const completeTask = useCompleteEnrollmentTask();

  const [openEnrollment, setOpenEnrollment] = useState<string | null>(null);
  const tasksQ = useEnrollmentTasks(openEnrollment);

  const plans = useMemo(() => asRows(plansQ.data), [plansQ.data]);
  const enrollments = useMemo(() => asRows(enrollmentsQ.data), [enrollmentsQ.data]);
  const enrolledPlanIds = useMemo(
    () => new Set(enrollments.map((e) => String(e.planId))),
    [enrollments],
  );

  function onEnroll(planId: string) {
    if (!cpid) return;
    enroll.mutate({ plan_id: planId, person_cpid: cpid });
  }

  return (
    <AppLayout>
      <PageShell
        title="Wellness plans & journeys"
        subtitle="Structured prevention journeys from Simba — enrol, follow the tasks, track your progress"
        icon={<Route className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <NompiloContextualGuidance routePath="/wellness/plans" />

        {!cpid ? (
          <div className="rounded-xl border border-warning/35 bg-warning-soft p-5 text-sm text-warning-foreground">
            Sign in to view and enrol in wellness journeys.
          </div>
        ) : (
          <>
            {/* My journeys */}
            <section className="mb-8">
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">My journeys</h2>
              {enrollmentsQ.isLoading ? (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading enrolments…
                </p>
              ) : enrollments.length === 0 ? (
                <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">
                  You have not enrolled in a journey yet. Pick one below to get started.
                </p>
              ) : (
                <div className="space-y-3">
                  {enrollments.map((e) => {
                    const enrollmentId = String(e.enrollmentId);
                    const pct = Number(e.progressPct ?? 0);
                    const isOpen = openEnrollment === enrollmentId;
                    return (
                      <GlassSurface key={enrollmentId} className="p-0">
                        <button
                          type="button"
                          onClick={() => setOpenEnrollment(isOpen ? null : enrollmentId)}
                          className="flex w-full items-center justify-between px-5 py-4 text-left"
                        >
                          <div>
                            <p className="font-medium text-foreground">Journey {enrollmentId.slice(0, 8)}</p>
                            <p className="text-xs text-muted-foreground">Status: {String(e.status ?? "ACTIVE")}</p>
                          </div>
                          <div className="flex items-center gap-3">
                            <div className="h-2 w-28 overflow-hidden rounded-full bg-muted">
                              <div className="h-full rounded-full bg-emerald-500" style={{ width: `${pct}%` }} />
                            </div>
                            <span className="w-10 text-right text-sm font-semibold text-emerald-600">{pct}%</span>
                          </div>
                        </button>
                        {isOpen && (
                          <div className="border-t border-border px-5 py-3">
                            {tasksQ.isLoading ? (
                              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                                <Loader2 className="h-4 w-4 animate-spin" /> Loading tasks…
                              </p>
                            ) : (
                              <ul className="space-y-2">
                                {asRows(tasksQ.data).map((t) => {
                                  const taskId = String(t.enrollmentTaskId);
                                  const done = String(t.status) === "DONE";
                                  return (
                                    <li key={taskId} className="flex items-center gap-3">
                                      <button
                                        type="button"
                                        disabled={done || completeTask.isPending}
                                        onClick={() => completeTask.mutate(taskId)}
                                        className="text-emerald-600 disabled:opacity-60"
                                        aria-label={done ? "Task done" : "Mark task done"}
                                      >
                                        {done ? <CheckCircle2 className="h-5 w-5" /> : <Circle className="h-5 w-5" />}
                                      </button>
                                      <span className={`text-sm ${done ? "text-muted-foreground line-through" : "text-foreground"}`}>
                                        {String(t.title ?? "Task")}
                                      </span>
                                    </li>
                                  );
                                })}
                                {asRows(tasksQ.data).length === 0 && (
                                  <li className="text-sm text-muted-foreground">No tasks for this journey.</li>
                                )}
                              </ul>
                            )}
                          </div>
                        )}
                      </GlassSurface>
                    );
                  })}
                </div>
              )}
            </section>

            {/* Available journeys */}
            <section>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">Available journeys</h2>
              {plansQ.isLoading ? (
                <p className="flex items-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" /> Loading journeys…
                </p>
              ) : plans.length === 0 ? (
                <p className="rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">
                  No wellness journeys are available for your tenant yet.
                </p>
              ) : (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  {plans.map((p) => {
                    const planId = String(p.planId);
                    const enrolled = enrolledPlanIds.has(planId);
                    const caution = Boolean(p.clinicalCaution);
                    return (
                      <GlassSurface key={planId} className="flex flex-col p-5">
                        <div className="mb-2 flex items-center gap-2">
                          <span className="rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
                            {String(p.category ?? "WELLNESS")}
                          </span>
                          {p.durationDays != null && (
                            <span className="text-xs text-muted-foreground">{String(p.durationDays)} days</span>
                          )}
                        </div>
                        <h3 className="font-semibold text-foreground">{String(p.name ?? "Wellness journey")}</h3>
                        <p className="mt-1 flex-1 text-sm text-muted-foreground">{String(p.description ?? "")}</p>
                        {caution && (
                          <p className="mt-2 flex items-start gap-1.5 rounded-lg bg-amber-50 px-2.5 py-1.5 text-xs text-amber-800">
                            <ShieldAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                            Touches a health risk — enrolling will suggest a screening or a chat with a provider.
                          </p>
                        )}
                        <button
                          type="button"
                          disabled={enrolled || enroll.isPending}
                          onClick={() => onEnroll(planId)}
                          className="mt-3 inline-flex items-center justify-center gap-1.5 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
                        >
                          {enrolled ? "Enrolled" : enroll.isPending ? "Enrolling…" : (<>Enrol <ArrowRight className="h-4 w-4" /></>)}
                        </button>
                      </GlassSurface>
                    );
                  })}
                </div>
              )}
            </section>
          </>
        )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
