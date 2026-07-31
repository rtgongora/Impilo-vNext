"use client";

/**
 * Citizen contraception — current coverage only via the `"me"` sentinel.
 * No TOP or loss surfaces on citizen routes (clinician-primary).
 */

import { Loader2, Pill } from "lucide-react";
import {
  isReproductiveReadUnavailable,
  REPRODUCTIVE_SELF,
  useContraceptionCoverage,
} from "@/hooks/queries/useConfidentialReproductive";
import {
  REPRODUCTIVE_EMPTY_HINT,
  REPRODUCTIVE_UNAVAILABLE_BODY,
  REPRODUCTIVE_UNAVAILABLE_TITLE,
} from "@/features/maternity/reproductive/reproductiveWording";

export function CitizenContraceptionSection() {
  const coverage = useContraceptionCoverage(REPRODUCTIVE_SELF);

  if (coverage.isLoading) {
    return (
      <p className="flex items-center gap-2 text-sm text-slate-500" data-testid="citizen-contraception-loading">
        <Loader2 className="h-4 w-4 animate-spin" /> Loading your contraception record…
      </p>
    );
  }

  if (coverage.isError) {
    if (isReproductiveReadUnavailable(coverage.error)) {
      return (
        <section
          className="rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-900"
          data-testid="citizen-contraception-unavailable"
          role="alert"
        >
          <p className="font-semibold">{REPRODUCTIVE_UNAVAILABLE_TITLE}</p>
          <p className="mt-1 text-xs">{REPRODUCTIVE_UNAVAILABLE_BODY}</p>
        </section>
      );
    }
    return (
      <p className="text-sm text-slate-600">
        We couldn&apos;t load your contraception record right now. Please try again.
      </p>
    );
  }

  const methods = coverage.data ?? [];

  return (
    <section
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
      data-testid="citizen-contraception-section"
    >
      <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
        <Pill className="h-5 w-5 text-violet-600" /> Your contraception
      </h2>
      <p className="mt-1 text-sm text-slate-600">
        Methods your care team has recorded for you. If nothing appears, it may mean no method is on
        file or that the record is not visible to you right now.
      </p>

      {methods.length === 0 ? (
        <p className="mt-3 text-xs text-slate-500">{REPRODUCTIVE_EMPTY_HINT}</p>
      ) : (
        <ul className="mt-4 space-y-3">
          {methods.map((m) => (
            <li
              key={m.contraceptive_episode_id}
              className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm"
              data-testid="citizen-contraception-item"
            >
              <p className="font-medium text-slate-900">
                {(m.method_code ?? m.method_class ?? "Method").replace(/_/g, " ").toLowerCase()}
              </p>
              <p className="text-xs text-slate-600">
                Coverage: {(m.coverage_status ?? "unknown").replace(/_/g, " ").toLowerCase()}
                {m.next_due_on
                  ? ` · next due ${new Date(m.next_due_on).toLocaleDateString()}`
                  : ""}
              </p>
              {m.coverage_unknown_why ? (
                <p className="mt-1 text-xs text-amber-800">{m.coverage_unknown_why}</p>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
