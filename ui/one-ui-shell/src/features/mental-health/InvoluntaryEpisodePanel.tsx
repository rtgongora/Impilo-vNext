"use client";

/**
 * Involuntary episode — the record of a detention.
 *
 * This is the one panel on this page where the record is also the legal instrument. Three things
 * follow from that and are deliberate:
 *
 * - **The legal basis is free text.** mental-health-service holds no vocabulary for it, and this
 *   file does not invent one. A dropdown of sections would be a legal claim written in TypeScript.
 * - **Closing states a reason.** The service refuses a closure without one, and a detention that
 *   ended for no recorded reason is not auditable afterwards.
 * - **One open episode at a time.** The service enforces it; a second attempt returns a conflict
 *   with its own message, which is shown rather than being translated into "something went wrong".
 */

import { useState } from "react";

import {
  useMentalHealthInvoluntaryEpisodeActions,
  useMentalHealthInvoluntaryEpisodes,
} from "@/hooks/queries/useMentalHealth";
import { bffErrorMessage } from "@/lib/bff-errors";

import { PanelShell } from "./PanelShell";

function when(value: unknown): string {
  if (!value) return "—";
  const parsed = new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? String(value) : parsed.toLocaleString();
}

export function InvoluntaryEpisodePanel({ referralId, actor }: { referralId: string; actor: string }) {
  const episodes = useMentalHealthInvoluntaryEpisodes(referralId);
  const actions = useMentalHealthInvoluntaryEpisodeActions(referralId);

  const [legalBasis, setLegalBasis] = useState("");
  const [reviewDueAt, setReviewDueAt] = useState("");
  const [endedReason, setEndedReason] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const rows = episodes.data ?? [];
  const open = rows.find((row) => row.status === "OPEN");

  return (
    <PanelShell
      title="Involuntary episode"
      subject="this patient's involuntary episodes"
      description="mh_involuntary_episode — a detention record; one open episode at a time, closure states a reason"
      isLoading={episodes.isLoading}
      isError={episodes.isError}
      error={episodes.error}
      isEmpty={rows.length === 0}
      emptyMessage="This patient has no involuntary episode on record. Care here is voluntary."
      data-testid="mh-involuntary"
      actions={
        open ? (
          <form
            className="space-y-3"
            data-testid="mh-involuntary-close-form"
            onSubmit={async (event) => {
              event.preventDefault();
              setFormError(null);
              try {
                await actions.close.mutateAsync({
                  episodeId: String(open.id),
                  endedReason: endedReason.trim(),
                });
                setEndedReason("");
              } catch (error) {
                setFormError(bffErrorMessage(error, "this closure"));
              }
            }}
          >
            <label className="block text-sm">
              <span className="font-medium text-foreground">Why the detention is ending</span>
              <input
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={endedReason}
                onChange={(event) => setEndedReason(event.target.value)}
                data-testid="mh-involuntary-ended-reason"
              />
            </label>

            {formError ? (
              <p className="text-sm text-danger" data-testid="mh-involuntary-error">
                {formError}
              </p>
            ) : null}

            <button
              type="submit"
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              disabled={actions.close.isPending || !endedReason.trim()}
              data-testid="mh-involuntary-close"
            >
              {actions.close.isPending ? "Closing…" : "Close involuntary episode"}
            </button>
          </form>
        ) : (
          <form
            className="space-y-3"
            data-testid="mh-involuntary-open-form"
            onSubmit={async (event) => {
              event.preventDefault();
              setFormError(null);
              try {
                await actions.open.mutateAsync({
                  legalBasis: legalBasis.trim(),
                  authorisedBy: actor || undefined,
                  reviewDueAt: reviewDueAt ? new Date(reviewDueAt).toISOString() : undefined,
                });
                setLegalBasis("");
                setReviewDueAt("");
              } catch (error) {
                setFormError(bffErrorMessage(error, "this involuntary episode"));
              }
            }}
          >
            <label className="block text-sm">
              <span className="font-medium text-foreground">Legal basis</span>
              <input
                className="mt-1 block w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={legalBasis}
                onChange={(event) => setLegalBasis(event.target.value)}
                placeholder="The provision relied on, in full"
                data-testid="mh-involuntary-legal-basis"
              />
              <span className="mt-1 block text-xs text-muted-foreground">
                Free text, deliberately. The service holds no list of provisions, and offering one
                here would be a legal claim made by the shell rather than by the record.
              </span>
            </label>

            <label className="block text-sm">
              <span className="font-medium text-foreground">Review due</span>
              <input
                type="datetime-local"
                className="mt-1 block rounded border border-border bg-background px-2 py-1.5 text-sm"
                value={reviewDueAt}
                onChange={(event) => setReviewDueAt(event.target.value)}
                data-testid="mh-involuntary-review-due"
              />
            </label>

            {formError ? (
              <p className="text-sm text-danger" data-testid="mh-involuntary-error">
                {formError}
              </p>
            ) : null}

            <button
              type="submit"
              className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
              disabled={actions.open.isPending || !legalBasis.trim()}
              data-testid="mh-involuntary-open"
            >
              {actions.open.isPending ? "Opening…" : "Open involuntary episode"}
            </button>
          </form>
        )
      }
    >
      <ul className="space-y-2">
        {rows.map((row, index) => (
          <li
            key={String(row.id ?? index)}
            className="rounded border border-border p-3 text-sm"
            data-testid="mh-involuntary-row"
          >
            <p className="font-medium text-foreground">
              {row.status === "OPEN" ? "Currently detained" : "Detention ended"} · {String(row.legal_basis ?? "—")}
            </p>
            <p className="mt-1 text-xs text-muted-foreground">
              Authorised by {String(row.authorised_by ?? "—")} · {when(row.authorised_at)}
            </p>
            {row.review_due_at ? (
              <p className="mt-0.5 text-xs text-muted-foreground">Review due {when(row.review_due_at)}</p>
            ) : (
              <p className="mt-0.5 text-xs text-muted-foreground">No review date was recorded.</p>
            )}
            {row.status === "CLOSED" ? (
              <p className="mt-0.5 text-xs text-muted-foreground">
                Ended {when(row.ended_at)} — {String(row.ended_reason ?? "no reason recorded")}
              </p>
            ) : null}
          </li>
        ))}
      </ul>
    </PanelShell>
  );
}
