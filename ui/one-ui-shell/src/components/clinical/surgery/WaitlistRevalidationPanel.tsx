"use client";

/**
 * Waiting-list clinical revalidation (SB-2). Append-only POST + list.
 * A failed history must never render as "never revalidated".
 */

import { useState } from "react";
import {
  useAppendWaitlistRevalidation,
  useWaitlistRevalidations,
} from "@/hooks/queries/useSurgeryEpisodes";

export function WaitlistRevalidationPanel({ episodeId }: { episodeId: string }) {
  const listQ = useWaitlistRevalidations(episodeId);
  const append = useAppendWaitlistRevalidation(episodeId);
  const [appropriate, setAppropriate] = useState<"" | "yes" | "no">("");
  const [notes, setNotes] = useState("");
  const [nextDue, setNextDue] = useState("");

  const reset = () => {
    setAppropriate("");
    setNotes("");
    setNextDue("");
  };

  return (
    <div
      className="mt-4 rounded-lg border border-gray-100 p-3"
      data-testid="surgery-waitlist-panel"
    >
      <h4 className="text-xs font-semibold uppercase text-muted-foreground">
        Waiting-list revalidation (course of care)
      </h4>
      <p className="mt-1 text-xs text-muted-foreground">
        Append-only clinical reviews. Each entry must answer whether surgery is still
        clinically appropriate — an explicit yes or no, never assumed.
      </p>

      {listQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading revalidations…</p>
      ) : listQ.isError ? (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-waitlist-unavailable"
        >
          Could not read revalidation history. This is not the same as never having been
          revalidated.
        </p>
      ) : listQ.data && listQ.data.length === 0 ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-waitlist-empty">
          No revalidations recorded for this episode yet.
        </p>
      ) : (
        <ul className="mt-2 space-y-1" data-testid="surgery-waitlist-list">
          {listQ.data?.map((r) => (
            <li key={r.id} className="text-sm">
              <span className="font-medium">
                {r.stillClinicallyAppropriate ? "Still appropriate" : "No longer appropriate"}
              </span>
              <span className="ml-2 text-xs text-muted-foreground">
                {r.revalidatedAt} by {r.revalidatedBy}
              </span>
              {r.notes && (
                <span className="ml-2 text-xs text-muted-foreground">{r.notes}</span>
              )}
              {r.nextRevalidationDue && (
                <span className="ml-2 text-xs text-muted-foreground">
                  next due {r.nextRevalidationDue}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}

      <div className="mt-3 flex flex-wrap items-end gap-2">
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">
            Still clinically appropriate
          </span>
          <select
            value={appropriate}
            onChange={(e) => setAppropriate(e.target.value as "" | "yes" | "no")}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-waitlist-appropriate"
          >
            <option value="">Choose…</option>
            <option value="yes">Yes</option>
            <option value="no">No</option>
          </select>
        </label>
        <label className="block flex-1 text-xs min-w-[160px]">
          <span className="font-semibold uppercase text-muted-foreground">Notes</span>
          <input
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-waitlist-notes"
          />
        </label>
        <label className="block text-xs">
          <span className="font-semibold uppercase text-muted-foreground">Next due</span>
          <input
            type="date"
            value={nextDue}
            onChange={(e) => setNextDue(e.target.value)}
            className="mt-0.5 block rounded-md border border-gray-200 px-2 py-1.5 text-sm"
            data-testid="surgery-waitlist-next-due"
          />
        </label>
        <button
          type="button"
          disabled={!appropriate || append.isPending}
          onClick={() =>
            append.mutate(
              {
                stillClinicallyAppropriate: appropriate === "yes",
                notes: notes.trim() || null,
                nextRevalidationDue: nextDue || null,
              },
              { onSuccess: reset },
            )
          }
          className="rounded-md border border-gray-200 px-3 py-1.5 text-sm disabled:opacity-50"
          data-testid="surgery-waitlist-append"
        >
          {append.isPending ? "Recording…" : "Record revalidation"}
        </button>
      </div>
      {append.isError && (
        <p
          className="mt-2 text-sm text-danger"
          role="alert"
          data-testid="surgery-waitlist-write-failed"
        >
          That revalidation was NOT saved — the history is unchanged.
        </p>
      )}
    </div>
  );
}
