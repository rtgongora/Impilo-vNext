"use client";

import { useState } from "react";
import { ClipboardList, Loader2, Plus, Target } from "lucide-react";
import type { CarePlanUi } from "@/hooks/queries/useCareContinuity";
import {
  useAddCarePlanGoal,
  useAddCarePlanIntervention,
  usePerformCarePlanIntervention,
  useUpdateCarePlanGoal,
} from "@/hooks/queries/useCareContinuity";

interface CarePlanOrchestrationRailProps {
  patientId: string;
  plans: CarePlanUi[];
  onPlanChanged?: () => void;
}

export function CarePlanOrchestrationRail({
  patientId,
  plans,
  onPlanChanged,
}: CarePlanOrchestrationRailProps) {
  const activePlan = plans.find((p) => p.status === "Active") ?? plans[0];
  const addGoal = useAddCarePlanGoal();
  const updateGoal = useUpdateCarePlanGoal();
  const addIntervention = useAddCarePlanIntervention();
  const performIntervention = usePerformCarePlanIntervention();
  const [goalText, setGoalText] = useState("");
  const [interventionText, setInterventionText] = useState("");

  if (!activePlan) {
    return (
      <section
        className="rounded-xl border border-border bg-background px-4 py-3 text-sm text-muted-foreground"
        data-testid="care-plan-orchestration-rail"
      >
        <div className="flex items-start gap-2">
          <ClipboardList className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <p>Create a care plan to start chronic-care goal and intervention orchestration.</p>
        </div>
      </section>
    );
  }

  const nextIntervention = activePlan.interventions.find((i) => !i.completed);

  // These four writes used to be answered optimistically by the BFF — {"updated": true},
  // {"performed": true}, and a 201 carrying a UUID for a goal PCT never stored — so onSuccess
  // fired, the input cleared, and the clinician had every reason to believe the plan had changed.
  // Now they fail honestly, and a silent failure is its own version of the same lie: the form
  // resets nothing and says nothing, so "performed" still looks like care that was delivered.
  const writeFailures = [
    performIntervention.isError
      ? "The intervention was not recorded. No care was logged against this plan — retry before signing off."
      : null,
    addGoal.isError ? "The goal was not saved to the care plan." : null,
    updateGoal.isError ? "The goal status was not updated — it is unchanged upstream." : null,
    addIntervention.isError ? "The intervention was not added to the care plan." : null,
  ].filter((m): m is string => m !== null);

  return (
    <section
      className="rounded-xl border border-info/25 bg-info-soft/50 px-4 py-4"
      data-testid="care-plan-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-primary-hover">Chronic care orchestration</p>
          <p className="mt-1 text-sm font-medium text-foreground">{activePlan.title}</p>
          <p className="text-xs text-muted-foreground">
            {activePlan.goals.length} goals · {activePlan.interventions.filter((i) => !i.completed).length} open
            interventions
          </p>
        </div>
        {nextIntervention?.id ? (
          <button
            type="button"
            disabled={performIntervention.isPending}
            onClick={() =>
              performIntervention.mutate(
                { planId: activePlan.id, interventionId: nextIntervention.id, patientId },
                { onSuccess: () => onPlanChanged?.() },
              )
            }
            className="rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
          >
            {performIntervention.isPending ? "Recording…" : "Perform next intervention"}
          </button>
        ) : null}
      </div>

      {writeFailures.length > 0 && (
        <ul
          className="mt-3 space-y-1 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-xs font-medium text-red-700"
          data-testid="care-plan-write-failures"
        >
          {writeFailures.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}

      <form
        className="mt-3 flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!goalText.trim()) return;
          addGoal.mutate(
            {
              planId: activePlan.id,
              patientId,
              description: goalText.trim(),
              category: activePlan.category || "Clinical",
            },
            {
              onSuccess: () => {
                setGoalText("");
                onPlanChanged?.();
              },
            },
          );
        }}
      >
        <div className="min-w-[200px] flex-1">
          <label className="mb-1 flex items-center gap-1 text-xs font-medium text-muted-foreground">
            <Target className="h-3.5 w-3.5" />
            Add goal to active plan
          </label>
          <input
            value={goalText}
            onChange={(e) => setGoalText(e.target.value)}
            placeholder="e.g. HbA1c below 7% within 90 days"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={addGoal.isPending || !goalText.trim()}
          className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-card px-3 py-2 text-xs font-medium text-primary-hover hover:bg-info-soft disabled:opacity-50"
        >
          {addGoal.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          Add goal
        </button>
      </form>

      {activePlan.goals[0]?.id ? (
        <button
          type="button"
          disabled={updateGoal.isPending}
          onClick={() =>
            updateGoal.mutate(
              {
                planId: activePlan.id,
                goalId: activePlan.goals[0]!.id,
                patientId,
                status: "ACHIEVED",
                progress: 100,
              },
              { onSuccess: () => onPlanChanged?.() },
            )
          }
          className="mt-2 text-xs font-medium text-primary-hover hover:text-primary-hover disabled:opacity-50"
        >
          Mark primary goal achieved
        </button>
      ) : null}

      <form
        className="mt-3 flex flex-wrap items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!interventionText.trim()) return;
          addIntervention.mutate(
            { planId: activePlan.id, patientId, label: interventionText.trim() },
            {
              onSuccess: () => {
                setInterventionText("");
                onPlanChanged?.();
              },
            },
          );
        }}
      >
        <div className="min-w-[200px] flex-1">
          <label className="mb-1 block text-xs font-medium text-muted-foreground">Add intervention</label>
          <input
            value={interventionText}
            onChange={(e) => setInterventionText(e.target.value)}
            placeholder="e.g. Schedule foot exam"
            className="w-full rounded-lg border border-border px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={addIntervention.isPending || !interventionText.trim()}
          className="rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium text-foreground hover:bg-background disabled:opacity-50"
        >
          {addIntervention.isPending ? "Adding…" : "Add intervention"}
        </button>
      </form>
    </section>
  );
}
