"use client";

/**
 * Clinical calculators.
 *
 * Both the list and the arithmetic come from the Clinical Knowledge Platform. Nothing here is
 * hard-coded and nothing here computes: a calculator appears in this list because the platform
 * serves it, and it produces a number because the platform ran it. That is the correction to a
 * surface which previously listed instruments from a local array, claimed a connection it did not
 * have, and badged them "Validated".
 *
 * Four states, kept visually distinct because a clinician must be able to tell them apart:
 *
 *  1. RESULT   — the calculator ran and produced values.
 *  2. REFUSAL  — the calculator ran and declined, with a reason and an explanation. This is the
 *                instrument working correctly and is not shown as an error.
 *  3. FAILURE  — the platform could not be reached. Deliberately loud, and never rendered as an
 *                empty result, because a blank where a number was expected reads as normal.
 *  4. EMPTY    — nothing has been submitted yet.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  ArrowLeft,
  Calculator,
  Info,
  Loader2,
  RefreshCw,
  Search,
} from "lucide-react";
import {
  submittableInputs,
  useCalculatorCatalogue,
  useCalculatorDescriptor,
  useEvaluateCalculator,
  type CalculatorDescriptor,
  type CalculatorInputField,
  type CalculatorResult,
} from "@/hooks/queries/useClinicalCalculators";

/**
 * Instruments that belong to a patient's record rather than to a detached scratchpad.
 *
 * APGAR is scored against a named newborn at one and five minutes and is persisted through the
 * neonatal surface. Offering a second, unattached APGAR here would invite a clinician to score a
 * baby into a box that saves nothing, so this points at the real one instead of duplicating it.
 */
const RECORDED_ELSEWHERE = [
  {
    name: "APGAR score",
    where: "Recorded against the newborn on the patient's vitals surface, where it is persisted and timed.",
    href: "/ehr",
  },
];

export function CalculatorsPanel() {
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { calculators, isLoading, isError, refetch } = useCalculatorCatalogue();

  const categories = useMemo(
    () => [...new Set(calculators.map((c) => c.category))].sort(),
    [calculators],
  );

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return calculators;
    return calculators.filter(
      (c) =>
        c.name.toLowerCase().includes(term) ||
        c.category.toLowerCase().includes(term) ||
        c.summary.toLowerCase().includes(term),
    );
  }, [calculators, search]);

  if (selectedId) {
    return <CalculatorDetail calculatorId={selectedId} onBack={() => setSelectedId(null)} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <Calculator className="w-6 h-6 text-primary" />
        <h2 className="text-lg font-semibold text-foreground">Clinical calculators</h2>
      </div>
      <p className="text-sm text-muted-foreground">
        Scoring tools and calculators computed by the Clinical Knowledge Platform. Each names its
        published source and states what it cannot tell you.
      </p>

      <div className="relative">
        <Search className="absolute left-3 top-3 w-4 h-4 text-muted-foreground" />
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search calculators..."
          aria-label="Search calculators"
          className="w-full rounded-lg border border-border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent"
        />
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground py-8 justify-center">
          <Loader2 className="w-4 h-4 animate-spin" />
          Loading the calculators this platform serves...
        </div>
      )}

      {isError && (
        <div
          role="alert"
          className="bg-danger-soft border border-danger/30 rounded-lg p-4 space-y-2"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-danger" />
            <p className="text-sm font-semibold text-foreground">
              The calculator catalogue could not be loaded
            </p>
          </div>
          <p className="text-xs text-muted-foreground">
            No calculators are shown because we do not know which ones are available — not because
            there are none.
          </p>
          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 text-xs border border-border rounded-lg px-3 py-1.5 hover:bg-muted transition"
          >
            <RefreshCw className="w-3 h-3" />
            Try again
          </button>
        </div>
      )}

      {!isLoading && !isError && (
        <>
          {categories.length > 0 && (
            <div className="flex gap-2 flex-wrap">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => setSearch(cat)}
                  className="text-xs border border-border rounded-full px-3 py-1 hover:bg-success-soft hover:border-emerald-300 transition"
                >
                  {cat}
                </button>
              ))}
            </div>
          )}

          {filtered.length === 0 ? (
            <p className="text-sm text-muted-foreground py-6 text-center">
              No calculator matches &ldquo;{search}&rdquo;.
            </p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {filtered.map((calc) => (
                <button
                  key={calc.id}
                  onClick={() => setSelectedId(calc.id)}
                  className="bg-card rounded-lg border border-border p-4 text-left hover:border-emerald-300 hover:bg-success-soft/50 transition"
                >
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">{calc.name}</p>
                    <span className="text-xs bg-emerald-100 text-primary-hover rounded-full px-2 py-0.5 shrink-0">
                      {calc.category}
                    </span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">{calc.summary}</p>
                </button>
              ))}
            </div>
          )}

          {!search && (
            <div className="bg-muted/40 border border-border rounded-lg p-4 space-y-2">
              <p className="text-xs font-semibold text-foreground">Scored against a patient</p>
              {RECORDED_ELSEWHERE.map((tool) => (
                <p key={tool.name} className="text-xs text-muted-foreground">
                  <Link href={tool.href} className="text-primary hover:underline">
                    {tool.name}
                  </Link>{" "}
                  — {tool.where}
                </p>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function CalculatorDetail({
  calculatorId,
  onBack,
}: {
  calculatorId: string;
  onBack: () => void;
}) {
  const { descriptor, isLoading, isError, refetch } = useCalculatorDescriptor(calculatorId);
  const [entered, setEntered] = useState<Record<string, unknown>>({});
  const evaluate = useEvaluateCalculator(calculatorId);

  const setValue = (key: string, value: unknown) => {
    setEntered((prev) => ({ ...prev, [key]: value }));
    // A previous answer describes the previous inputs. Leaving it on screen while the fields change
    // under it is how a clinician reads a stale number as a current one.
    evaluate.reset();
  };

  return (
    <div className="bg-card rounded-lg border border-border p-6 space-y-5">
      <div className="flex items-start justify-between gap-4">
        <button
          onClick={onBack}
          className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="w-3 h-3" />
          All calculators
        </button>
      </div>

      {isLoading && (
        <div className="flex items-center gap-2 text-sm text-muted-foreground py-8 justify-center">
          <Loader2 className="w-4 h-4 animate-spin" />
          Loading this calculator&rsquo;s inputs...
        </div>
      )}

      {isError && (
        <div role="alert" className="bg-danger-soft border border-danger/30 rounded-lg p-4 space-y-2">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-danger" />
            <p className="text-sm font-semibold text-foreground">This calculator could not be loaded</p>
          </div>
          <p className="text-xs text-muted-foreground">
            Its inputs come from the Clinical Knowledge Platform, so the form cannot be shown without
            them. Nothing is computed here.
          </p>
          <button
            onClick={() => refetch()}
            className="inline-flex items-center gap-1.5 text-xs border border-border rounded-lg px-3 py-1.5 hover:bg-muted transition"
          >
            <RefreshCw className="w-3 h-3" />
            Try again
          </button>
        </div>
      )}

      {descriptor && (
        <>
          <div>
            <h3 className="text-base font-semibold text-foreground">{descriptor.name}</h3>
            <p className="text-xs text-muted-foreground mt-0.5">{descriptor.summary}</p>
          </div>

          <form
            className="space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              evaluate.mutate(submittableInputs(entered));
            }}
          >
            {descriptor.inputs.map((field) => (
              <CalculatorField
                key={field.key}
                field={field}
                value={entered[field.key]}
                onChange={(value) => setValue(field.key, value)}
              />
            ))}

            <div className="flex items-center gap-2">
              <button
                type="submit"
                disabled={evaluate.isPending}
                className="inline-flex items-center gap-2 bg-primary text-primary-foreground rounded-lg px-4 py-2 text-sm font-medium hover:bg-primary-hover disabled:opacity-60 transition"
              >
                {evaluate.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                Calculate
              </button>
              <button
                type="button"
                onClick={() => {
                  setEntered({});
                  evaluate.reset();
                }}
                className="text-xs text-muted-foreground hover:text-foreground px-2 py-2"
              >
                Clear
              </button>
            </div>
          </form>

          {evaluate.isError && <EvaluationFailed />}
          {evaluate.data && <EvaluationOutcome result={evaluate.data} />}

          <CalculatorProvenance descriptor={descriptor} />
        </>
      )}
    </div>
  );
}

/** One field, rendered from the kind the platform declared for it. */
function CalculatorField({
  field,
  value,
  onChange,
}: {
  field: CalculatorInputField;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const label = (
    <span className="text-xs font-medium text-foreground">
      {field.label}
      {field.unit && <span className="text-muted-foreground font-normal"> ({field.unit})</span>}
      {!field.required && <span className="text-muted-foreground font-normal"> — optional</span>}
    </span>
  );

  const help = field.help && <p className="text-[11px] text-muted-foreground mt-1">{field.help}</p>;

  if (field.kind === "BOOLEAN") {
    const current = value === true ? "true" : value === false ? "false" : "";
    return (
      <div>
        <label htmlFor={field.key} className="block mb-1.5">
          {label}
        </label>
        <select
          id={field.key}
          value={current}
          onChange={(e) =>
            onChange(e.target.value === "" ? undefined : e.target.value === "true")
          }
          className="w-full rounded-lg border border-border px-3 py-2 text-sm bg-card focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          {/* Not answered is a distinct third value, not a default of "no". Several calculators
              refuse rather than read an unasked question as a negative. */}
          <option value="">Not assessed</option>
          <option value="true">Yes</option>
          <option value="false">No</option>
        </select>
        {help}
      </div>
    );
  }

  if (field.kind === "ENUM") {
    return (
      <div>
        <label htmlFor={field.key} className="block mb-1.5">
          {label}
        </label>
        <select
          id={field.key}
          value={typeof value === "string" ? value : ""}
          onChange={(e) => onChange(e.target.value === "" ? undefined : e.target.value)}
          className="w-full rounded-lg border border-border px-3 py-2 text-sm bg-card focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">Select...</option>
          {field.options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {help}
      </div>
    );
  }

  if (field.kind === "ENUM_MULTI") {
    const selected = Array.isArray(value) ? (value as string[]) : null;
    return (
      <fieldset>
        <legend className="mb-1.5">{label}</legend>
        <div className="space-y-1.5 border border-border rounded-lg p-3">
          {field.options.map((opt) => (
            <label key={opt.value} className="flex items-start gap-2 text-xs">
              <input
                type="checkbox"
                checked={selected?.includes(opt.value) ?? false}
                onChange={(e) => {
                  const base = selected ?? [];
                  onChange(
                    e.target.checked
                      ? [...base, opt.value]
                      : base.filter((v) => v !== opt.value),
                  );
                }}
                className="mt-0.5"
              />
              <span>
                <span className="text-foreground">{opt.label}</span>
                {opt.description && (
                  <span className="block text-[11px] text-muted-foreground">{opt.description}</span>
                )}
              </span>
            </label>
          ))}
          {/* Ticking nothing and never opening the list are different answers. This makes the
              first one sayable, so "assessed, none present" is not confused with "not assessed". */}
          <label className="flex items-start gap-2 text-xs border-t border-border pt-2 mt-2">
            <input
              type="checkbox"
              checked={selected !== null && selected.length === 0}
              onChange={(e) => onChange(e.target.checked ? [] : undefined)}
              className="mt-0.5"
            />
            <span className="text-foreground">None of these are present</span>
          </label>
        </div>
        {help}
      </fieldset>
    );
  }

  return (
    <div>
      <label htmlFor={field.key} className="block mb-1.5">
        {label}
      </label>
      <input
        id={field.key}
        type="text"
        inputMode="decimal"
        value={typeof value === "string" ? value : ""}
        onChange={(e) => onChange(e.target.value)}
        placeholder={field.unit ?? ""}
        className="w-full rounded-lg border border-border px-3 py-2 text-sm bg-card focus:outline-none focus:ring-2 focus:ring-emerald-500"
      />
      {help}
    </div>
  );
}

/** A result or a refusal — the two things the calculator itself can say. */
function EvaluationOutcome({ result }: { result: CalculatorResult }) {
  const primary = result.outputs.filter((o) => o.primary);
  const secondary = result.outputs.filter((o) => !o.primary);

  return (
    <div className="space-y-3">
      {result.computed ? (
        <div className="bg-success-soft border border-success/25 rounded-lg p-4 space-y-3">
          {primary.map((output) => (
            <div key={output.key}>
              <p className="text-xs text-muted-foreground">{output.label}</p>
              <p className="text-xl font-semibold text-foreground">
                {formatValue(output.value)}
                {output.unit && (
                  <span className="text-sm font-normal text-muted-foreground"> {output.unit}</span>
                )}
              </p>
              {output.note && (
                <p className="text-[11px] text-muted-foreground mt-1">{output.note}</p>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div
          role="status"
          className="bg-warning-soft border border-warning/30 rounded-lg p-4 space-y-2"
        >
          <div className="flex items-center gap-2">
            <Info className="w-4 h-4 text-warning" />
            <p className="text-sm font-semibold text-foreground">No result — and this is the answer</p>
          </div>
          {/* The explanation, not the code, is what tells a clinician what to do next. */}
          <p className="text-xs text-foreground">{result.refusal_detail}</p>
          {result.refusal_reason && (
            <p className="text-[11px] text-muted-foreground font-mono">{result.refusal_reason}</p>
          )}
        </div>
      )}

      {secondary.length > 0 && (
        <div className="border border-border rounded-lg divide-y divide-border">
          {secondary.map((output) => (
            <div key={output.key} className="flex items-start justify-between gap-3 px-3 py-2">
              <span className="text-xs text-muted-foreground">{output.label}</span>
              <span className="text-xs text-foreground text-right">
                {formatValue(output.value)}
                {output.unit ? ` ${output.unit}` : ""}
                {output.note && (
                  <span className="block text-[11px] text-muted-foreground">{output.note}</span>
                )}
              </span>
            </div>
          ))}
        </div>
      )}

      {result.caveats.length > 0 && (
        <ul className="space-y-1.5">
          {result.caveats.map((caveat) => (
            <li key={caveat} className="text-[11px] text-muted-foreground flex gap-1.5">
              <AlertTriangle className="w-3 h-3 shrink-0 mt-0.5 text-warning" />
              <span>{caveat}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** The platform could not be reached. Never rendered as a blank result. */
function EvaluationFailed() {
  return (
    <div role="alert" className="bg-danger-soft border border-danger/30 rounded-lg p-4 space-y-1.5">
      <div className="flex items-center gap-2">
        <AlertTriangle className="w-4 h-4 text-danger" />
        <p className="text-sm font-semibold text-foreground">This calculator could not be run</p>
      </div>
      <p className="text-xs text-muted-foreground">
        The Clinical Knowledge Platform did not answer, so there is no result — not a normal one and
        not a reassuring one. Check the values and try again.
      </p>
    </div>
  );
}

/** Where the arithmetic came from and which version produced it. */
function CalculatorProvenance({ descriptor }: { descriptor: CalculatorDescriptor }) {
  return (
    <div className="border-t border-border pt-4 space-y-2">
      <div className="flex gap-2 text-xs flex-wrap">
        <span className="bg-neutral-100 text-muted-foreground rounded-full px-2.5 py-1">
          {descriptor.category}
        </span>
        <span className="bg-neutral-100 text-muted-foreground rounded-full px-2.5 py-1 font-mono">
          {descriptor.content_version}
        </span>
      </div>
      <p className="text-[11px] text-muted-foreground">
        <span className="font-medium text-foreground">Source:</span> {descriptor.source_citation}
      </p>
      {descriptor.caveats.length > 0 && (
        <ul className="space-y-1">
          {descriptor.caveats.map((caveat) => (
            <li key={caveat} className="text-[11px] text-muted-foreground">
              {caveat}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function formatValue(value: CalculatorResult["outputs"][number]["value"]): string {
  if (value === null || value === undefined) return "—";
  if (Array.isArray(value)) return value.length === 0 ? "None" : value.join(", ");
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
}
