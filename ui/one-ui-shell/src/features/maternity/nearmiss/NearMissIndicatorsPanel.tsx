"use client";

/**
 * Severe maternal outcome indicators — the near-miss-to-death ratio and mortality index for a
 * reporting period, computed from a list of cases and their outcomes.
 *
 * Backend: `POST /internal/v1/clinical/maternal/near-miss/indicators` via `useNearMissIndicators` —
 * see that hook's doc comment for the two laws this panel must not break: a null ratio/index is
 * mathematically undefined and must render as such (never "0.00"), and the indeterminate count is
 * always shown alongside the near-miss and death counts, never folded away because it does not feed
 * either ratio.
 */

import { useState } from "react";
import { AlertTriangle, Loader2, Plus, ShieldAlert, Trash2 } from "lucide-react";
import {
  isIndicatorsRejected,
  isIndicatorsUnavailable,
  parseIndicatorsRejection,
  useNearMissIndicators,
  type NearMissIndicatorOutcome,
  type NearMissIndicatorsResult,
} from "./useMaternalNearMiss";

const OUTCOME_OPTIONS: { value: NearMissIndicatorOutcome; label: string }[] = [
  { value: "NEAR_MISS", label: "Near-miss" },
  { value: "MATERNAL_DEATH", label: "Maternal death" },
  { value: "NEAR_MISS_INDETERMINATE", label: "Indeterminate" },
  { value: "NOT_SEVERE", label: "Not severe" },
];

interface CaseRow {
  id: string;
  outcome: NearMissIndicatorOutcome;
}

let rowSeq = 0;
function newRow(outcome: NearMissIndicatorOutcome = "NEAR_MISS"): CaseRow {
  rowSeq += 1;
  return { id: `row-${rowSeq}`, outcome };
}

/** `null` is rendered as an explicit "not computable" phrase — never as 0 or a blank cell. */
function formatRatio(value: number | null, unit: string): string {
  if (value === null) return "Not computable (undefined)";
  return `${value.toFixed(2)}${unit}`;
}

function ResultCard({ result }: { result: NearMissIndicatorsResult }) {
  return (
    <div className="mt-3 rounded-lg border border-border/70 bg-background/60 p-3" data-testid="near-miss-indicators-result">
      <div className="grid gap-3 sm:grid-cols-4">
        <Stat label="Near-miss" value={result.near_miss_count} testId="near-miss-indicators-count" />
        <Stat label="Maternal death" value={result.maternal_death_count} testId="near-miss-indicators-death-count" />
        {/* Never dropped from the display, even though it feeds neither ratio. */}
        <Stat label="Indeterminate" value={result.indeterminate_count} testId="near-miss-indicators-indeterminate-count" />
        <Stat
          label="Severe maternal outcomes"
          value={result.severe_maternal_outcome_count}
          testId="near-miss-indicators-severe-count"
        />
      </div>

      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <div className="rounded-md border border-border/60 bg-card px-3 py-2">
          <p className="text-xs font-medium text-muted-foreground">Near-miss-to-death ratio</p>
          <p className="text-sm font-semibold text-foreground" data-testid="near-miss-indicators-ratio">
            {formatRatio(result.near_miss_to_death_ratio, ":1")}
          </p>
          {result.near_miss_to_death_ratio === null && result.near_miss_to_death_ratio_upper_bound !== null && (
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              Upper bound if a death occurs next: {result.near_miss_to_death_ratio_upper_bound.toFixed(2)}:1
            </p>
          )}
        </div>
        <div className="rounded-md border border-border/60 bg-card px-3 py-2">
          <p className="text-xs font-medium text-muted-foreground">
            Mortality index ({result.mortality_index_direction === "HIGHER_IS_WORSE" ? "higher is worse" : result.mortality_index_direction})
          </p>
          <p className="text-sm font-semibold text-foreground" data-testid="near-miss-indicators-mortality-index">
            {formatRatio(result.mortality_index, "%")}
          </p>
          {result.mortality_index === null && result.mortality_index_lower_bound !== null && (
            <p className="mt-0.5 text-[11px] text-muted-foreground">
              Lower bound: {result.mortality_index_lower_bound.toFixed(2)}%
            </p>
          )}
        </div>
      </div>

      {result.note && (
        <p className="mt-2 text-xs italic text-muted-foreground" data-testid="near-miss-indicators-note">
          {result.note}
        </p>
      )}
    </div>
  );
}

function Stat({ label, value, testId }: { label: string; value: number; testId: string }) {
  return (
    <div className="rounded-md border border-border/60 bg-card px-3 py-2 text-center">
      <p className="text-lg font-semibold text-foreground" data-testid={testId}>
        {value}
      </p>
      <p className="text-[11px] text-muted-foreground">{label}</p>
    </div>
  );
}

export function NearMissIndicatorsPanel() {
  const [period, setPeriod] = useState("");
  const [rows, setRows] = useState<CaseRow[]>([newRow()]);
  const compute = useNearMissIndicators();

  function addRow() {
    setRows((prev) => [...prev, newRow()]);
  }

  function removeRow(id: string) {
    setRows((prev) => (prev.length > 1 ? prev.filter((r) => r.id !== id) : prev));
  }

  function setOutcome(id: string, outcome: NearMissIndicatorOutcome) {
    setRows((prev) => prev.map((r) => (r.id === id ? { ...r, outcome } : r)));
  }

  function handleCompute() {
    compute.mutate({
      indicatorPeriod: period,
      cases: rows.map((r) => ({ outcome: r.outcome })),
    });
  }

  const unavailable = compute.isError && isIndicatorsUnavailable(compute.error);
  const rejected = compute.isError && isIndicatorsRejected(compute.error);
  const rejection =
    rejected && typeof compute.error === "object" && compute.error !== null
      ? parseIndicatorsRejection(compute.error as unknown as { upstream_body?: string; status: number })
      : null;

  return (
    <div className="mt-4 rounded-xl border border-red-200/80 bg-gradient-to-b from-red-50/40 to-card p-4" data-testid="near-miss-indicators-panel">
      <h3 className="text-sm font-semibold text-red-950">Severe maternal outcome indicators</h3>
      <p className="mt-1 text-xs text-muted-foreground">
        The near-miss-to-death ratio and mortality index for a reporting period. A ratio computed
        over zero deaths is undefined, not zero — this panel never substitutes a number for that.
      </p>

      <label className="mt-3 block text-xs font-medium text-muted-foreground">
        Reporting period
        <input
          type="text"
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
          placeholder="e.g. 2026-Q2"
          data-testid="near-miss-indicators-period"
          className="mt-1 block w-full max-w-xs rounded-lg border border-border px-3 py-2 text-sm"
        />
      </label>

      <div className="mt-3 space-y-2">
        {rows.map((row, idx) => (
          <div key={row.id} className="flex items-center gap-2" data-testid={`near-miss-indicators-row-${idx}`}>
            <span className="w-6 text-xs text-muted-foreground">{idx + 1}.</span>
            <select
              value={row.outcome}
              onChange={(e) => setOutcome(row.id, e.target.value as NearMissIndicatorOutcome)}
              data-testid={`near-miss-indicators-outcome-${idx}`}
              className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
            >
              {OUTCOME_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => removeRow(row.id)}
              disabled={rows.length === 1}
              aria-label={`Remove case ${idx + 1}`}
              className="rounded-md p-2 text-muted-foreground hover:bg-background disabled:opacity-30"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={addRow}
          data-testid="near-miss-indicators-add-row"
          className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-3 py-2 text-xs font-medium text-foreground hover:bg-background"
        >
          <Plus className="h-3.5 w-3.5" /> Add case
        </button>
        <button
          type="button"
          onClick={handleCompute}
          disabled={compute.isPending || rows.length === 0}
          data-testid="near-miss-indicators-compute"
          className="inline-flex items-center gap-1.5 rounded-lg bg-red-700 px-4 py-2 text-sm font-medium text-white hover:bg-red-800 disabled:opacity-50"
        >
          {compute.isPending && <Loader2 className="h-4 w-4 animate-spin" />}
          Compute
        </button>
      </div>

      {unavailable && (
        <div
          className="mt-3 flex items-start gap-2 rounded-lg border border-danger/28 bg-danger-soft px-3 py-3 text-sm text-red-900"
          data-testid="near-miss-indicators-unavailable"
          role="alert"
        >
          <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" />
          <p className="text-xs">
            The near-miss criteria service could not be reached, so nothing was computed. This is not
            a ratio of zero — try again when the service returns.
          </p>
        </div>
      )}

      {rejected && (
        <div
          className="mt-3 flex items-start gap-2 rounded-lg border border-warning/35 bg-warning-soft px-3 py-3 text-sm text-warning-foreground"
          data-testid="near-miss-indicators-rejected"
          role="alert"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <div>
            <p className="font-semibold">Some cases could not be classified</p>
            <p className="mt-0.5 text-xs">
              {rejection?.message ?? "One or more outcomes were not recognised, and were not guessed at."}
            </p>
            {rejection && rejection.rejectedCases.length > 0 && (
              <ul className="mt-1.5 list-disc pl-4 text-xs">
                {rejection.rejectedCases.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}

      {compute.data && !unavailable && !rejected && <ResultCard result={compute.data.data} />}
    </div>
  );
}
