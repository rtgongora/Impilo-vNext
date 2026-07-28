"use client";

import { useState, type ReactNode } from "react";
import { AlertTriangle, Loader2, ShieldAlert } from "lucide-react";
import { useMedicineCds, type MedicineCdsTopic } from "@/hooks/queries/useMedicineCds";

/**
 * One governed decision-support topic, evaluated on demand.
 *
 * The rule this component exists to hold: **the absence of alerts is not an all-clear.** Three
 * different situations can produce an empty alert list —
 *   1. the rules ran and nothing fired (a genuine finding),
 *   2. the rules ran but some required facts were missing (incomplete — a partial assessment),
 *   3. the evaluation failed (no assessment at all).
 * A screen that renders all three as blank space tells a clinician the patient is fine in two cases
 * where nobody has established that. So each is rendered distinctly, and the failure case says so
 * in words.
 *
 * The content behind every topic is ENGINEERING_SEED pending MoHCC ratification, which the panel
 * states rather than implying the guidance is national protocol.
 */

export interface CdsFactField {
  key: string;
  label: string;
  /** number inputs are parsed before sending; the engine's numeric operators need real numbers. */
  type: "number" | "text" | "select";
  options?: { value: string; label: string }[];
  placeholder?: string;
}

export interface CdsTopicPanelProps {
  topic: MedicineCdsTopic;
  title: string;
  description: string;
  fields: CdsFactField[];
  footer?: ReactNode;
}

const SEVERITY_CLASS: Record<string, string> = {
  HIGH: "border-danger/40 bg-red-50",
  MEDIUM: "border-warning/40 bg-amber-50",
  LOW: "border-border bg-muted/30",
};

export function CdsTopicPanel({ topic, title, description, fields, footer }: CdsTopicPanelProps) {
  const [values, setValues] = useState<Record<string, string>>({});
  const cds = useMedicineCds();
  const result = cds.data?.data;

  const evaluate = () => {
    // Only non-empty facts are sent. An empty box means "not stated", and sending it as "" would
    // make the engine treat an unanswered question as an answer — the engine reports unstated facts
    // as unassessed, which is the behaviour we want to preserve.
    const facts: Record<string, unknown> = {};
    for (const f of fields) {
      const raw = values[f.key];
      if (raw === undefined || raw === "") continue;
      facts[f.key] = f.type === "number" ? Number(raw) : raw;
    }
    cds.mutate({ topic, facts });
  };

  return (
    <div className="rounded-lg border border-border bg-card p-5 space-y-3" data-testid={`cds-${topic}`}>
      <div>
        <h3 className="font-semibold text-sm">{title}</h3>
        <p className="text-xs text-muted-foreground">{description}</p>
      </div>

      <div className="flex flex-wrap gap-2">
        {fields.map((f) => (
          <label key={f.key} className="text-xs text-muted-foreground">
            <span className="block mb-0.5">{f.label}</span>
            {f.type === "select" ? (
              <select
                className="rounded border border-border px-2 py-1 text-sm text-foreground"
                value={values[f.key] ?? ""}
                onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
                data-testid={`field-${f.key}`}
              >
                <option value="">Not stated</option>
                {(f.options ?? []).map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            ) : (
              <input
                type={f.type === "number" ? "number" : "text"}
                className="rounded border border-border px-2 py-1 text-sm w-40"
                placeholder={f.placeholder}
                value={values[f.key] ?? ""}
                onChange={(e) => setValues((v) => ({ ...v, [f.key]: e.target.value }))}
                data-testid={`field-${f.key}`}
              />
            )}
          </label>
        ))}
      </div>

      <button
        type="button"
        onClick={evaluate}
        disabled={cds.isPending}
        className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-60"
        data-testid={`evaluate-${topic}`}
      >
        {cds.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : "Evaluate"}
      </button>

      {/* A failed evaluation is the case most likely to be misread as "nothing wrong". */}
      {cds.isError && (
        <div className="flex items-start gap-2 rounded border border-danger/30 bg-red-50 p-3" data-testid={`cds-failed-${topic}`}>
          <AlertTriangle className="w-4 h-4 text-danger mt-0.5" />
          <p className="text-sm text-danger">
            Decision support did not run. The absence of alerts here is <strong>not</strong> an
            all-clear — nothing was assessed.
          </p>
        </div>
      )}

      {result && !cds.isError && (
        <div className="space-y-2" data-testid={`cds-result-${topic}`}>
          {result.alerts.length === 0 ? (
            <p className="text-sm text-muted-foreground" data-testid={`cds-noalerts-${topic}`}>
              No alerts fired for the facts supplied.
            </p>
          ) : (
            result.alerts.map((a) => (
              <div
                key={a.code}
                className={`rounded border p-3 ${SEVERITY_CLASS[a.severity] ?? SEVERITY_CLASS.LOW}`}
                data-testid={`alert-${a.code}`}
              >
                <div className="flex items-start gap-2">
                  <ShieldAlert className="w-4 h-4 mt-0.5 text-warning-foreground" />
                  <div>
                    <p className="text-sm font-medium">{a.message}</p>
                    <p className="text-sm text-muted-foreground">{a.required_action}</p>
                    {a.referral_required && (
                      <p className="text-xs font-medium text-danger mt-1">Referral required</p>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}

          {/* Always rendered when present — an assessment built on missing facts is partial, and
              saying so is the difference between "we checked" and "we checked what we had". */}
          {result.incomplete && (
            <p className="text-sm text-warning-foreground" data-testid={`cds-incomplete-${topic}`}>
              Incomplete — not assessed: {result.not_assessed.join(", ")}. This is not an all-clear.
            </p>
          )}

          <p className="text-xs text-muted-foreground">
            Engineering seed pending MoHCC ratification · content {result.content_version}
          </p>
        </div>
      )}

      {footer}
    </div>
  );
}
