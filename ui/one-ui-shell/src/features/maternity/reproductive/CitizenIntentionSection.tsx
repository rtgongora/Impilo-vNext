"use client";

/** Light citizen view of reproductive intention via the "me" sentinel on /my/pregnancy. */

import { Loader2 } from "lucide-react";
import {
  isReproductiveReadUnavailable,
  REPRODUCTIVE_SELF,
  useCurrentReproductiveIntention,
  useRecordReproductiveIntention,
} from "@/hooks/queries/useConfidentialReproductive";

function humanise(code: string | null | undefined): string {
  if (!code) return "Not recorded yet";
  return code.replace(/_/g, " ").toLowerCase();
}

export function CitizenIntentionSection() {
  const current = useCurrentReproductiveIntention(REPRODUCTIVE_SELF);
  const record = useRecordReproductiveIntention();

  if (current.isLoading) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading your reproductive intention…
        </p>
      </section>
    );
  }

  if (current.isError && isReproductiveReadUnavailable(current.error)) {
    return (
      <section
        className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900"
        data-testid="citizen-intention-unavailable"
      >
        We couldn&apos;t load your reproductive intention right now. This is not the same as you
        having none on file — please try again later.
      </section>
    );
  }

  const row = current.data;

  return (
    <section
      className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
      data-testid="citizen-intention-section"
    >
      <h2 className="text-base font-semibold text-slate-900">Your reproductive intention</h2>
      <p className="mt-1 text-xs text-slate-500">
        What you want for your own fertility — only what you choose to record, never inferred.
      </p>

      {row ? (
        <p className="mt-3 text-sm text-slate-800">
          Currently recorded: <span className="font-medium">{humanise(row.intention)}</span>
        </p>
      ) : (
        <p className="mt-3 text-xs text-slate-500">
          Nothing visible right now — that may mean nothing is recorded, or that it is not visible
          to you here.
        </p>
      )}

      <button
        type="button"
        disabled={record.isPending}
        className="mt-4 rounded-lg border border-rose-200 px-4 py-2 text-sm text-rose-800 hover:bg-rose-50 disabled:opacity-50"
        data-testid="citizen-intention-record"
        onClick={() =>
          void record.mutateAsync({
            subjectCpid: REPRODUCTIVE_SELF,
            intention: "UNDECIDED",
            recordedBy: "citizen-self",
          })
        }
      >
        {record.isPending ? "Saving…" : "Update my intention (undecided)"}
      </button>
    </section>
  );
}
