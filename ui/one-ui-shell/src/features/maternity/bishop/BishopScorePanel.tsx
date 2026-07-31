"use client";

import { useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  BISHOP_FIELD_LABELS,
  BISHOP_FIELD_OPTIONS,
  BISHOP_FORM_KEY,
  isBishopScoreUnavailable,
  useBishopScoreClassifyForm,
  type BishopInterpretation,
} from "./useBishopScore";

const INTERPRETATION_HEADING: Record<BishopInterpretation, string> = {
  UNFAVOURABLE: "Unfavourable cervix (≤ 6)",
  INTERMEDIATE: "Intermediate favourability (7–9)",
  FAVOURABLE: "Favourable cervix (≥ 10)",
  INCOMPLETE: "Incomplete — not all components assessed",
};

function interpretationTone(interpretation: BishopInterpretation): string {
  if (interpretation === "FAVOURABLE") return "border-green-300 bg-green-50 text-green-950";
  if (interpretation === "INTERMEDIATE") return "border-amber-300 bg-amber-50 text-amber-950";
  if (interpretation === "INCOMPLETE") return "border-red-300 bg-red-50 text-red-950";
  return "border-orange-300 bg-orange-50 text-orange-950";
}

type ComponentKey = keyof typeof BISHOP_FIELD_OPTIONS;

export function BishopScorePanel() {
  const [answers, setAnswers] = useState<Record<string, string | undefined>>({});
  const assess = useBishopScoreClassifyForm();
  const result = assess.data ?? null;
  const unavailable = assess.isError && isBishopScoreUnavailable(assess.error);

  function setField(field: ComponentKey, code: string | undefined) {
    setAnswers((prev) => ({ ...prev, [field]: code }));
  }

  function handleAssess() {
    const formAnswers: Record<string, unknown> = {};
    for (const [field, value] of Object.entries(answers)) {
      if (value) {
        formAnswers[`bishop.${field}`] = value;
      }
    }
    assess.mutate({ formKey: BISHOP_FORM_KEY, answers: formAnswers });
  }

  function handleClear() {
    setAnswers({});
    assess.reset();
  }

  return (
    <div className="mt-4 rounded-xl border border-pink-200/80 bg-card p-4" data-testid="bishop-score-panel">
      <h3 className="text-sm font-semibold text-foreground">Bishop score</h3>
      <p className="mt-1 text-xs text-muted-foreground">
        Cervical favourability for induction readiness — assessment only, nothing saved here. Leave a
        component blank if it was not assessed; blank is never treated as zero.
      </p>

      <div className="mt-4 grid gap-4">
        {(Object.keys(BISHOP_FIELD_OPTIONS) as ComponentKey[]).map((field) => (
          <fieldset key={field} className="rounded-lg border border-border/70 p-3">
            <legend className="px-1 text-xs font-medium text-muted-foreground">
              {BISHOP_FIELD_LABELS[field]}
            </legend>
            <div className="mt-2 flex flex-wrap gap-2">
              {BISHOP_FIELD_OPTIONS[field].map((opt) => {
                const selected = answers[field] === opt.code;
                return (
                  <button
                    key={opt.code}
                    type="button"
                    onClick={() => setField(field, selected ? undefined : opt.code)}
                    className={`rounded-md border px-2.5 py-1.5 text-xs ${
                      selected
                        ? "border-blue-500 bg-blue-50 text-blue-950"
                        : "border-border bg-background text-muted-foreground hover:text-foreground"
                    }`}
                    data-testid={`bishop-${field}-${opt.code}`}
                  >
                    {opt.label}
                  </button>
                );
              })}
              {!answers[field] && (
                <span className="self-center text-xs text-muted-foreground" data-testid={`bishop-${field}-blank`}>
                  not assessed
                </span>
              )}
            </div>
          </fieldset>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap gap-2">
        <button
          type="button"
          onClick={handleAssess}
          disabled={assess.isPending}
          className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground disabled:opacity-50"
          data-testid="bishop-assess"
        >
          {assess.isPending ? "Assessing…" : "Assess Bishop score"}
        </button>
        <button
          type="button"
          onClick={handleClear}
          className="rounded-md border border-border px-3 py-1.5 text-xs font-medium text-muted-foreground"
          data-testid="bishop-clear"
        >
          Clear
        </button>
      </div>

      {unavailable && (
        <div className="mt-4 flex gap-2 rounded-lg border border-red-300 bg-red-50 p-3 text-red-950" role="alert" data-testid="bishop-unavailable">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="text-sm font-semibold">The Bishop score could not be evaluated</p>
            <p className="mt-1 text-xs">
              This is not a statement that the cervix is favourable. Assess clinically and resubmit when the service returns.
            </p>
          </div>
        </div>
      )}

      {result && !unavailable && (
        <div className={`mt-4 rounded-lg border p-3 ${interpretationTone(result.interpretation)}`} data-testid="bishop-verdict">
          <p className="text-sm font-semibold">{INTERPRETATION_HEADING[result.interpretation]}</p>
          {result.score != null && (
            <p className="mt-1 text-xs">Total score: {result.score} / 13</p>
          )}
          {result.missing.length > 0 && (
            <div className="mt-2 text-xs" data-testid="bishop-missing">
              <p className="font-medium">Complete these to compute a score:</p>
              <ul className="mt-1 list-disc pl-4">
                {result.missing.map((field) => (
                  <li key={field}>{BISHOP_FIELD_LABELS[field] ?? field}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
