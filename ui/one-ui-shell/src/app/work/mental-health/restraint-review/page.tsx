"use client";

/**
 * The facility-wide restraint review backlog.
 * Route: /work/mental-health/restraint-review
 *
 * Every episode of restraint is meant to be reviewed afterwards by somebody who was not the person
 * who applied it. mental-health-service has served this backlog since W13 and nothing read it, so
 * the review obligation existed in the database and nowhere a human could see it.
 *
 * An empty list here is good news and says so. A failed read says something different, in different
 * words — a backlog that cannot be read is not a backlog of zero, and this is the one page where
 * that mistake would let unreviewed restraint sit indefinitely.
 */

import Link from "next/link";
import { useState } from "react";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useMentalHealthRestraintActions,
  useUnreviewedRestraintEvents,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export default function RestraintReviewPage() {
  const user = useAuthStore((state) => state.user);
  const actor = user?.providerId ?? user?.staffId ?? user?.id ?? "";

  const backlog = useUnreviewedRestraintEvents();
  const actions = useMentalHealthRestraintActions();

  const [outcomes, setOutcomes] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);

  const rows = backlog.data ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Restraint review"
        subtitle="Restraint that has happened and has not yet been reviewed"
      >
        <div className="mb-4 text-sm">
          <Link href="/work/mental-health" className="text-primary underline">
            ← Referral queue
          </Link>
        </div>

        {backlog.isLoading ? (
          <p className="text-sm text-muted-foreground">Loading the review backlog…</p>
        ) : backlog.isError ? (
          <p
            className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger"
            data-testid="restraint-backlog-unreadable"
          >
            {bffErrorMessage(backlog.error, "the restraint review backlog")} An unreadable backlog is
            not an empty one — unreviewed restraint may be waiting behind this failure.
          </p>
        ) : rows.length === 0 ? (
          <p
            className="rounded border border-dashed border-border px-3 py-4 text-sm text-muted-foreground"
            data-testid="restraint-backlog-empty"
          >
            No restraint is awaiting review. Every recorded episode has been reviewed.
          </p>
        ) : (
          <>
            <p className="mb-3 text-sm text-muted-foreground" data-testid="restraint-backlog-count">
              {rows.length} episode{rows.length === 1 ? "" : "s"} awaiting review.
            </p>
            {actionError ? (
              <p className="mb-3 text-sm text-danger" data-testid="restraint-backlog-error">
                {actionError}
              </p>
            ) : null}
            <ul className="space-y-3">
              {rows.map((row, index) => {
                const id = String(row.id ?? index);
                return (
                  <li
                    key={id}
                    className="rounded-xl border border-border bg-card p-4 text-sm"
                    data-testid="restraint-backlog-row"
                  >
                    <p className="font-medium text-foreground">
                      {String(row.restraint_type ?? "—").toLowerCase()} · {when(row.initiated_at)}
                    </p>
                    <p className="mt-1 text-muted-foreground">{String(row.reason ?? "")}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      De-escalation attempted: {String(row.de_escalation_attempted ?? "not recorded")}
                    </p>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      Initiated by {String(row.initiated_by ?? "—")} ·{" "}
                      {row.ended_at ? `ended ${when(row.ended_at)}` : "still in place"}
                    </p>
                    {row.referral_id ? (
                      <Link
                        href={`/work/mental-health/${String(row.referral_id)}`}
                        className="mt-1 inline-block text-xs text-primary underline"
                      >
                        Open the full record
                      </Link>
                    ) : null}

                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <input
                        className="flex-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
                        placeholder="Review outcome — was this proportionate, and what follows from it"
                        value={outcomes[id] ?? ""}
                        onChange={(event) =>
                          setOutcomes((prev) => ({ ...prev, [id]: event.target.value }))
                        }
                        data-testid="restraint-backlog-outcome"
                      />
                      <button
                        type="button"
                        className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
                        disabled={actions.review.isPending || !(outcomes[id] ?? "").trim()}
                        data-testid="restraint-backlog-review"
                        onClick={async () => {
                          setActionError(null);
                          try {
                            await actions.review.mutateAsync({
                              eventId: id,
                              reviewOutcome: (outcomes[id] ?? "").trim(),
                              reviewedBy: actor || undefined,
                            });
                          } catch (error) {
                            setActionError(bffErrorMessage(error, "this review"));
                          }
                        }}
                      >
                        Record review
                      </button>
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}
