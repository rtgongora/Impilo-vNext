"use client";

/**
 * Record the episode-level disposition — the finer clinical fact inside the FSM's coarse
 * `CLOSED_*` state.
 *
 * The form asks for the fields pct requires for the chosen type, but it does not enforce them: the
 * submit button stays live and pct's own refusal is shown verbatim when it comes. Enforcing locally
 * would put a second copy of the mandatory-content gate in the browser, and the copy that drifts is
 * always the one the clinician meets first.
 */

import { useState } from "react";

import {
  dispositionGroups,
  dispositionType,
  missingFields,
  type DispositionDraft,
} from "./dispositionModel";

export interface DispositionFormProps {
  onSubmit: (draft: DispositionDraft) => Promise<void>;
  isSubmitting?: boolean;
  /** pct's own refusal, shown as it arrived. */
  serverError?: string | null;
  /** An already-recorded disposition, if one exists. */
  existing?: Record<string, unknown> | null;
}

export function DispositionForm({ onSubmit, isSubmitting, serverError, existing }: DispositionFormProps) {
  const [draft, setDraft] = useState<DispositionDraft>({
    dispositionType: "",
    dispositionReason: "",
  });

  const selected = dispositionType(draft.dispositionType);
  const missing = missingFields(draft);

  if (existing?.disposition_type) {
    return (
      <div className="rounded-lg border border-border bg-card p-4" data-testid="disposition-existing">
        <h3 className="text-sm font-semibold text-foreground">Disposition recorded</h3>
        <dl className="mt-2 grid grid-cols-[10rem_1fr] gap-1 text-sm">
          <dt className="text-muted-foreground">Type</dt>
          <dd className="text-foreground">
            {dispositionType(String(existing.disposition_type))?.label ??
              String(existing.disposition_type)}
          </dd>
          <dt className="text-muted-foreground">Reason</dt>
          <dd className="text-foreground">{String(existing.disposition_reason ?? "—")}</dd>
          <dt className="text-muted-foreground">Recorded by</dt>
          <dd className="text-foreground">{String(existing.disposed_by ?? "—")}</dd>
          <dt className="text-muted-foreground">Recorded at</dt>
          <dd className="text-foreground">{String(existing.disposed_at ?? "—")}</dd>
        </dl>
        <p className="mt-3 text-xs text-muted-foreground">
          One disposition per episode. A correction is an amendment, not a second competing record.
        </p>
      </div>
    );
  }

  return (
    <form
      className="space-y-4 rounded-lg border border-border bg-card p-4"
      data-testid="disposition-form"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit(draft);
      }}
    >
      <div>
        <label htmlFor="disposition-type" className="block text-sm font-medium text-foreground">
          Disposition
        </label>
        <select
          id="disposition-type"
          className="mt-1 w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
          value={draft.dispositionType}
          onChange={(event) => setDraft({ ...draft, dispositionType: event.target.value })}
          data-testid="disposition-type"
        >
          <option value="">Select a disposition…</option>
          {dispositionGroups().map(({ group, types }) => (
            <optgroup key={group} label={group}>
              {types.map((type) => (
                <option key={type.code} value={type.code}>
                  {type.label}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        {selected?.hint ? (
          <p className="mt-1 text-xs text-muted-foreground" data-testid="disposition-hint">
            {selected.hint}
          </p>
        ) : null}
      </div>

      <div>
        <label htmlFor="disposition-reason" className="block text-sm font-medium text-foreground">
          Reason
        </label>
        <textarea
          id="disposition-reason"
          rows={2}
          className="mt-1 w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
          value={draft.dispositionReason}
          onChange={(event) => setDraft({ ...draft, dispositionReason: event.target.value })}
          data-testid="disposition-reason"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          Every disposition states who, when and why. Without a reason it is a status flip nobody can
          audit later.
        </p>
      </div>

      {selected?.requires.includes("destination") ? (
        <div>
          <label htmlFor="disposition-destination" className="block text-sm font-medium text-foreground">
            Destination
          </label>
          <input
            id="disposition-destination"
            className="mt-1 w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
            value={draft.destination ?? ""}
            onChange={(event) => setDraft({ ...draft, destination: event.target.value })}
            data-testid="disposition-destination"
          />
        </div>
      ) : null}

      {selected?.requires.includes("causeOrCircumstances") ? (
        <div>
          <label htmlFor="disposition-circumstances" className="block text-sm font-medium text-foreground">
            Cause or circumstances
          </label>
          <textarea
            id="disposition-circumstances"
            rows={2}
            className="mt-1 w-full rounded border border-border bg-background px-2 py-1.5 text-sm"
            value={draft.causeOrCircumstances ?? ""}
            onChange={(event) => setDraft({ ...draft, causeOrCircumstances: event.target.value })}
            data-testid="disposition-circumstances"
          />
        </div>
      ) : null}

      {selected?.requires.includes("lastSeenAt") ? (
        <div>
          <label htmlFor="disposition-last-seen" className="block text-sm font-medium text-foreground">
            Last seen at
          </label>
          <input
            id="disposition-last-seen"
            type="datetime-local"
            className="mt-1 rounded border border-border bg-background px-2 py-1.5 text-sm"
            value={draft.lastSeenAt ?? ""}
            onChange={(event) => setDraft({ ...draft, lastSeenAt: event.target.value })}
            data-testid="disposition-last-seen"
          />
        </div>
      ) : null}

      {serverError ? (
        <p className="rounded border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" data-testid="disposition-server-error">
          {serverError}
        </p>
      ) : null}

      {missing.length > 0 && draft.dispositionType ? (
        <p className="text-xs text-warning-foreground" data-testid="disposition-missing">
          pct will refuse this until {missing.join(", ")} {missing.length === 1 ? "is" : "are"} given.
        </p>
      ) : null}

      <button
        type="submit"
        className="rounded bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
        disabled={isSubmitting || !draft.dispositionType}
        data-testid="disposition-submit"
      >
        {isSubmitting ? "Recording…" : "Record disposition"}
      </button>
    </form>
  );
}
