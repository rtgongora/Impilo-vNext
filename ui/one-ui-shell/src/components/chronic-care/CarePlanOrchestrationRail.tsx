"use client";

import { useState } from "react";
import { ClipboardList, Loader2, Plus, Target } from "lucide-react";
import type { CarePlanUi } from "@/hooks/queries/useCareContinuity";
import {
  useAddCarePlanGoal,
  usePerformCarePlanIntervention,
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
  const performIntervention = usePerformCarePlanIntervention();
  const [goalText, setGoalText] = useState("");

  if (!activePlan) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600"
        data-testid="care-plan-orchestration-rail"
      >
        <div className="flex items-start gap-2">
          <ClipboardList className="mt-0.5 h-4 w-4 shrink-0 text-impilo-600" />
          <p>Create a care plan to start chronic-care goal and intervention orchestration.</p>
        </div>
      </section>
    );
  }

  const nextIntervention = activePlan.interventions.find((i) => !i.completed);

  return (
    <section
      className="rounded-xl border border-indigo-200 bg-indigo-50/50 px-4 py-4"
      data-testid="care-plan-orchestration-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-indigo-700">Chronic care orchestration</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{activePlan.title}</p>
          <p className="text-xs text-slate-600">
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
          <label className="mb-1 flex items-center gap-1 text-xs font-medium text-slate-600">
            <Target className="h-3.5 w-3.5" />
            Add goal to active plan
          </label>
          <input
            value={goalText}
            onChange={(e) => setGoalText(e.target.value)}
            placeholder="e.g. HbA1c below 7% within 90 days"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </div>
        <button
          type="submit"
          disabled={addGoal.isPending || !goalText.trim()}
          className="inline-flex items-center gap-1 rounded-lg border border-indigo-300 bg-white px-3 py-2 text-xs font-medium text-indigo-800 hover:bg-indigo-50 disabled:opacity-50"
        >
          {addGoal.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
          Add goal
        </button>
      </form>
    </section>
  );
}
