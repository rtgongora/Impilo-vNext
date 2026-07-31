"use client";

/**
 * Maternal near-miss assessment — clinician web panel.
 *
 * Renders form-21 (`impilo.maternal.nearmiss.assessment.v1`) directly from the fetched DAK
 * definition rather than through `DakFormRenderer`: the form only uses two field kinds
 * (`coded_single` PRESENT/ABSENT and `decimal`), and `decimal` is not one `DakFormRenderer`
 * currently understands (it has `numeric_with_unit` instead) — see the mobile lane's
 * `MaternityWorkspaces.tsx` for the same decision made the same way for the partograph/CTG forms.
 *
 * The three-way answer this panel must never collapse:
 *   - left blank    -> omitted from the request entirely (not sent as ABSENT)
 *   - PRESENT/ABSENT -> a real coded answer
 *   - anything else  -> cannot be produced by this UI (the buttons only emit the two codes), but if
 *                        the BFF ever refuses an answer as unrecognised, that refusal is shown, not
 *                        swallowed.
 */

import { useMemo, useState } from "react";
import { AlertTriangle, Loader2, ShieldAlert, CircleCheck, HelpCircle } from "lucide-react";
import {
  useNearMissFormDefinition,
  useClassifyMaternalNearMiss,
  isUnrecognisedAnswerRefusal,
  isNearMissUnavailable,
  isNearMissRefusal,
  type NearMissClassifyResponse,
} from "./useMaternalNearMiss";

const LINK_PREFIX = "nearMiss.";

function bareId(linkId: string): string {
  return linkId.startsWith(LINK_PREFIX) ? linkId.slice(LINK_PREFIX.length) : linkId;
}

/** Humanises a tier code like `NEAR_MISS_CARDIOVASCULAR` into "Cardiovascular". */
function humaniseTierCode(code: string): string {
  return code
    .replace(/^NEAR_MISS_/, "")
    .split("_")
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(" ");
}

export interface NearMissAssessmentPanelProps {
  /** Purely descriptive — this panel does not persist against any patient record; CKP stores nothing. */
  patientId: string;
}

export function NearMissAssessmentPanel({ patientId }: NearMissAssessmentPanelProps) {
  const { isLoading, isError, definition } = useNearMissFormDefinition();
  const classify = useClassifyMaternalNearMiss();

  // Undefined/absent key === left blank. Never defaulted, never carried over between submissions.
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [result, setResult] = useState<NearMissClassifyResponse | null>(null);

  const labelById = useMemo(() => {
    const map = new Map<string, string>();
    for (const section of definition?.sections ?? []) {
      for (const field of section.fields) {
        map.set(bareId(field.linkId), field.label);
      }
    }
    return map;
  }, [definition]);

  function setCoded(linkId: string, code: string) {
    setAnswers((prev) => {
      const next = { ...prev };
      // Clicking the already-selected option clears it back to blank rather than toggling nothing —
      // a clinician must be able to retract an accidental answer to "not assessed", not just switch it.
      if (next[linkId] === code) {
        delete next[linkId];
      } else {
        next[linkId] = code;
      }
      return next;
    });
  }

  function setDecimal(linkId: string, raw: string) {
    setAnswers((prev) => {
      const next = { ...prev };
      if (raw.trim() === "") {
        delete next[linkId];
      } else {
        next[linkId] = raw;
      }
      return next;
    });
  }

  function handleSubmit() {
    setResult(null);
    classify.mutate(answers, {
      onSuccess: (data) => setResult(data),
    });
  }

  function handleReset() {
    setAnswers({});
    setResult(null);
    classify.reset();
  }

  const recordedCount = Object.keys(answers).length;

  return (
    <div className="mt-4 rounded-xl border border-red-200/80 bg-gradient-to-b from-red-50/40 to-card p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-red-950">Maternal near-miss assessment</h3>
          <p className="mt-1 text-xs text-muted-foreground">
            WHO organ-dysfunction criteria for {patientId ? <code className="rounded bg-neutral-100 px-1">{patientId}</code> : "this patient"}.
            Leave a field blank if it was not assessed — a blank is never treated as a negative finding.
          </p>
        </div>
        {recordedCount > 0 && (
          <span className="rounded-full bg-red-100 px-2.5 py-1 text-xs font-medium text-red-800">
            {recordedCount} answered
          </span>
        )}
      </div>

      {isLoading && (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          Loading the near-miss assessment form…
        </div>
      )}

      {isError && (
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>The form catalog could not be reached, so the assessment form is unavailable right now.</p>
        </div>
      )}

      {!isLoading && !isError && !definition && (
        <div className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-sm text-warning-foreground">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>
            Form <code className="rounded bg-neutral-100 px-1">impilo.maternal.nearmiss.assessment.v1</code> was not found
            in the catalog.
          </p>
        </div>
      )}

      {definition && (
        <div className="mt-3 space-y-4">
          {definition.sections.map((section) => (
            <fieldset key={section.id} className="rounded-lg border border-border/70 bg-background/60 p-3">
              <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-red-900">
                {section.title}
              </legend>
              {section.description && <p className="mb-2 text-xs text-muted-foreground">{section.description}</p>}
              <div className="grid gap-3 sm:grid-cols-2">
                {section.fields.map((field) => (
                  <NearMissField
                    key={field.linkId}
                    linkId={field.linkId}
                    label={field.label}
                    kind={field.kind}
                    unit={field.unit}
                    description={field.description}
                    options={field.options}
                    value={answers[field.linkId]}
                    onSetCoded={(code) => setCoded(field.linkId, code)}
                    onSetDecimal={(v) => setDecimal(field.linkId, v)}
                  />
                ))}
              </div>
            </fieldset>
          ))}

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={handleSubmit}
              disabled={classify.isPending}
              className="inline-flex items-center gap-1.5 rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50"
            >
              {classify.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
              Classify near-miss
            </button>
            <button
              type="button"
              onClick={handleReset}
              className="rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium text-foreground hover:bg-background"
            >
              Clear form
            </button>
          </div>

          {classify.isError && <RefusalBanner error={classify.error} />}
          {result && <OutcomePanel result={result} labelById={labelById} />}
        </div>
      )}
    </div>
  );
}

function NearMissField({
  linkId,
  label,
  kind,
  unit,
  description,
  options,
  value,
  onSetCoded,
  onSetDecimal,
}: {
  linkId: string;
  label: string;
  kind: string;
  unit?: string;
  description?: string;
  options?: { code: string; display: string }[];
  value: string | undefined;
  onSetCoded: (code: string) => void;
  onSetDecimal: (raw: string) => void;
}) {
  if (kind === "coded_single" && options && options.length > 0) {
    return (
      <div className="text-xs">
        <span className="font-medium text-foreground">{label}</span>
        {description && <p className="text-[11px] text-muted-foreground">{description}</p>}
        <div className="mt-1 flex gap-1.5">
          {options.map((opt) => {
            const active = value === opt.code;
            return (
              <button
                key={opt.code}
                type="button"
                data-testid={`near-miss-field-${linkId}-${opt.code}`}
                onClick={() => onSetCoded(opt.code)}
                aria-pressed={active}
                className={
                  "rounded-md border px-3 py-1 text-xs font-medium " +
                  (active
                    ? opt.code === "PRESENT"
                      ? "border-red-600 bg-red-600 text-white"
                      : "border-emerald-600 bg-emerald-600 text-white"
                    : "border-border bg-card text-foreground hover:bg-background")
                }
              >
                {opt.display}
              </button>
            );
          })}
          {value === undefined && <span className="self-center text-[11px] text-muted-foreground">not assessed</span>}
        </div>
      </div>
    );
  }

  // "decimal" — form-21's only other field kind. Not DakFormRenderer's `numeric_with_unit`; see the
  // panel-level comment for why this form is rendered directly rather than through that renderer.
  return (
    <label className="text-xs font-medium text-muted-foreground" data-testid={`near-miss-field-${linkId}`}>
      {label}
      {unit ? ` (${unit})` : ""}
      {description && <p className="text-[11px] font-normal text-muted-foreground">{description}</p>}
      <input
        type="number"
        step="any"
        inputMode="decimal"
        value={value ?? ""}
        onChange={(e) => onSetDecimal(e.target.value)}
        placeholder="Not assessed"
        className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
      />
    </label>
  );
}

function RefusalBanner({ error }: { error: unknown }) {
  if (isUnrecognisedAnswerRefusal(error) && isNearMissRefusal(error)) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
        <div>
          <p className="font-semibold">These answers could not be read</p>
          <p className="mt-0.5 text-xs">
            {error.message ??
              "An unreadable answer being silently treated as a negative is how a near-miss is recorded as a normal birth. Correct them and resubmit."}
          </p>
          {error.unrecognised_answers && error.unrecognised_answers.length > 0 && (
            <ul className="mt-1.5 list-disc pl-4 text-xs">
              {error.unrecognised_answers.map((a) => (
                <li key={a}>{a}</li>
              ))}
            </ul>
          )}
        </div>
      </div>
    );
  }

  if (isNearMissUnavailable(error) && isNearMissRefusal(error)) {
    return (
      <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900">
        <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
        <div>
          <p className="font-semibold">The near-miss criteria could not be evaluated</p>
          <p className="mt-0.5 text-xs">
            {error.message ??
              "This is not a finding of \u2018no near-miss\u2019. Classify clinically and resubmit when the service returns."}
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
      <p className="text-xs">Could not classify this assessment. Nothing was recorded — try again.</p>
    </div>
  );
}

function OutcomePanel({
  result,
  labelById,
}: {
  result: NearMissClassifyResponse;
  labelById: Map<string, string>;
}) {
  const { data, meta } = result;
  const blank = meta.criteria_left_blank ?? [];

  const indeterminate = data.status === "INDETERMINATE";
  const isNearMiss = data.is_near_miss;
  const inconclusive = indeterminate || data.provisional;

  return (
    <div className="mt-2 space-y-3">
      {/* The classification headline. INDETERMINATE and a provisional CLASSIFIED both get the loudest
          styling — never the calm one — because both mean "cannot rule out a near-miss", which is a
          more urgent unknown than a resolved NOT_MET. */}
      {isNearMiss ? (
        <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900">
          <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="font-semibold">Near-miss identified — {data.classification_name}</p>
            {data.provisional && (
              <p className="mt-1 text-xs font-medium">
                Provisional: a more severe finding above this one could not be excluded. This is the least
                severe classification this woman could have, not necessarily the right one.
              </p>
            )}
            {data.rationale && <p className="mt-1 text-xs">{data.rationale}</p>}
            {data.review_note && <p className="mt-2 text-xs italic">{data.review_note}</p>}
          </div>
        </div>
      ) : inconclusive ? (
        <div className="flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900">
          <HelpCircle className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="font-semibold">Indeterminate — a near-miss cannot be ruled out</p>
            <p className="mt-1 text-xs">
              {data.rationale ??
                "A severity row could not be excluded on what was recorded. This is not the same as \u2018no near-miss\u2019 — it means more information is needed before a safe classification can be issued."}
            </p>
            {data.unresolved_criteria.length > 0 && (
              <div className="mt-2">
                <p className="text-xs font-medium">Could not exclude:</p>
                <ul className="mt-1 list-disc pl-4 text-xs">
                  {data.unresolved_criteria.map((code) => (
                    <li key={code}>{humaniseTierCode(code)}</li>
                  ))}
                </ul>
              </div>
            )}
            {data.missing_inputs.length > 0 && (
              <div className="mt-2">
                <p className="text-xs font-medium">Complete these to resolve it:</p>
                <ul className="mt-1 list-disc pl-4 text-xs">
                  {data.missing_inputs.map((linkId) => (
                    <li key={linkId}>{labelById.get(bareId(linkId)) ?? linkId}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div className="flex items-start gap-2 rounded-lg border border-emerald-300/70 bg-emerald-50 px-3 py-3 text-sm text-emerald-900">
          <CircleCheck className="mt-0.5 h-5 w-5 shrink-0" />
          <div>
            <p className="font-semibold">WHO near-miss criteria not met</p>
            <p className="mt-1 text-xs">
              {data.rationale ??
                "No organ-dysfunction criterion was met on the information recorded. This is not a statement that the woman was well \u2014 a severe maternal outcome can be present without a recorded criterion."}
            </p>
          </div>
        </div>
      )}

      {/* Blank criteria, named rather than counted, regardless of which branch above rendered — a
          clinician acting on this outcome needs to know what was never looked at even when the
          engine still reached a definite classification. */}
      {blank.length > 0 && (
        <div className="rounded-lg border border-border/70 bg-background/60 px-3 py-2 text-xs text-muted-foreground">
          <p className="font-medium text-foreground">Left blank — not assessed, not recorded as absent:</p>
          <ul className="mt-1 list-disc pl-4">
            {blank.map((field) => (
              <li key={field}>{labelById.get(field) ?? field}</li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
