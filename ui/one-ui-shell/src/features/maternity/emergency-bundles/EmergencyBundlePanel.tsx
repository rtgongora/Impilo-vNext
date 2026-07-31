"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2 } from "lucide-react";
import {
  useEmergencyBundleAssess,
  isEmergencyBundleUnavailable,
  type EmergencyBundleAssessment,
  type EmergencyBundleKind,
  type EmergencyBundleStepStatus,
} from "./useEmergencyBundle";

const STATUS_HEADING: Record<EmergencyBundleAssessment["status"], string> = {
  ACTIVE: "In progress — mandatory steps may remain",
  CLOSABLE: "May close — all mandatory steps done and control confirmed",
  LAPSED_UNRESOLVED: "Escalate — no recent observation on an open episode",
};

function stepTone(status: EmergencyBundleStepStatus): string {
  if (status === "OVERDUE" || status === "DUE") return "border-red-300 bg-red-50 text-red-950";
  if (status === "DONE") return "border-green-300 bg-green-50 text-green-950";
  return "border-border bg-muted/40 text-muted-foreground";
}

function verdictTone(status: EmergencyBundleAssessment["status"]): string {
  if (status === "LAPSED_UNRESOLVED") return "border-red-300 bg-red-50 text-red-950";
  if (status === "CLOSABLE") return "border-green-300 bg-green-50 text-green-950";
  return "border-amber-300 bg-amber-50 text-amber-950";
}

export function EmergencyBundlePanel({
  kind,
  title,
  controlLabel,
  testIdPrefix,
}: {
  kind: EmergencyBundleKind;
  title: string;
  controlLabel: string;
  testIdPrefix: string;
}) {
  const [completed, setCompleted] = useState<Set<string>>(new Set());
  const [minutesSinceTrigger, setMinutesSinceTrigger] = useState(0);
  const [minutesSinceLastObservation, setMinutesSinceLastObservation] = useState(0);
  const [controlConfirmed, setControlConfirmed] = useState<boolean | null>(null);
  const [clinicianConfirmedClose, setClinicianConfirmedClose] = useState(false);

  const assess = useEmergencyBundleAssess(kind);
  const result = assess.data?.data ?? null;

  useEffect(() => {
    assess.mutate({ completedSteps: [], minutesSinceTrigger: 0, minutesSinceLastObservation: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [kind]);

  const unavailable = assess.isError && isEmergencyBundleUnavailable(assess.error);
  const steps = result?.steps ?? [];

  return (
    <div className="mt-4 rounded-xl border border-red-200/80 bg-gradient-to-b from-red-50/30 to-card p-4" data-testid={`${testIdPrefix}-panel`}>
      <h3 className="text-sm font-semibold text-red-950">{title}</h3>
      <p className="mt-1 text-xs text-muted-foreground">
        Time-critical checklist assessment — nothing is saved here. Unknown control is never treated as controlled.
      </p>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <label className="text-xs font-medium text-muted-foreground">
          Minutes since trigger
          <input type="number" min={0} className="mt-1 w-full rounded-md border border-border px-2 py-1.5 text-sm"
            value={minutesSinceTrigger} onChange={(e) => setMinutesSinceTrigger(Number(e.target.value) || 0)}
            data-testid={`${testIdPrefix}-minutes-trigger`} />
        </label>
        <label className="text-xs font-medium text-muted-foreground">
          Minutes since last observation
          <input type="number" min={0} className="mt-1 w-full rounded-md border border-border px-2 py-1.5 text-sm"
            value={minutesSinceLastObservation} onChange={(e) => setMinutesSinceLastObservation(Number(e.target.value) || 0)}
            data-testid={`${testIdPrefix}-minutes-last-obs`} />
        </label>
      </div>

      <fieldset className="mt-3">
        <legend className="text-xs font-medium text-muted-foreground">{controlLabel}</legend>
        <div className="mt-1 flex flex-wrap gap-2">
          {([["unknown", "Not assessed", null], ["yes", "Confirmed", true], ["no", "Not confirmed", false]] as const).map(
            ([key, label, value]) => (
              <button key={key} type="button"
                className={`rounded-md border px-3 py-1.5 text-xs ${controlConfirmed === value ? "border-primary bg-primary/10 font-semibold" : "border-border"}`}
                onClick={() => setControlConfirmed(value)} data-testid={`${testIdPrefix}-control-${key}`}>
                {label}
              </button>
            ),
          )}
        </div>
      </fieldset>

      <label className="mt-3 flex items-center gap-2 text-xs text-muted-foreground">
        <input type="checkbox" checked={clinicianConfirmedClose} onChange={(e) => setClinicianConfirmedClose(e.target.checked)}
          data-testid={`${testIdPrefix}-clinician-close`} />
        Clinician confirmed closure
      </label>

      {steps.length > 0 && (
        <div className="mt-4 space-y-2" data-testid={`${testIdPrefix}-checklist`}>
          {steps.map((step) => (
            <button key={step.code} type="button" onClick={() => setCompleted((prev) => {
              const next = new Set(prev);
              if (next.has(step.code)) next.delete(step.code); else next.add(step.code);
              return next;
            })}
              className={`w-full rounded-lg border p-3 text-left text-sm ${stepTone(step.status)}`}
              data-testid={`${testIdPrefix}-step-${step.code}`}>
              <span className="font-medium">{completed.has(step.code) ? "✓ " : ""}{step.name}{step.mandatory ? " *" : ""}</span>
              <span className="ml-2 text-xs opacity-80">({step.status})</span>
              {step.action ? <p className="mt-1 text-xs">{step.action}</p> : null}
            </button>
          ))}
        </div>
      )}

      <button type="button" className="mt-4 rounded-md bg-red-700 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        disabled={assess.isPending}
        onClick={() => assess.mutate({ completedSteps: [...completed], minutesSinceTrigger, minutesSinceLastObservation, controlConfirmed, clinicianConfirmedClose })}
        data-testid={`${testIdPrefix}-assess`}>
        {assess.isPending ? "Assessing…" : "Assess bundle"}
      </button>

      {unavailable && (
        <div className="mt-4 rounded-lg border border-red-300 bg-red-50 p-3 text-sm text-red-950" role="alert" data-testid={`${testIdPrefix}-unavailable`}>
          <p className="flex items-center gap-2 font-semibold"><AlertTriangle className="h-4 w-4" />The bundle could not be evaluated</p>
          <p className="mt-1 text-xs">This is not a statement that the emergency is resolved.</p>
        </div>
      )}

      {result && !unavailable && (
        <div className={`mt-4 rounded-lg border p-3 text-sm ${verdictTone(result.status)}`} data-testid={`${testIdPrefix}-verdict`}>
          <p className="flex items-center gap-2 font-semibold">
            {result.status === "CLOSABLE" ? <CheckCircle2 className="h-4 w-4" /> : <AlertTriangle className="h-4 w-4" />}
            {STATUS_HEADING[result.status]}
          </p>
          <p className="mt-1 text-xs">{result.note}</p>
        </div>
      )}
    </div>
  );
}
