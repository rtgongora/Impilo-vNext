"use client";

/**
 * Discharge / follow-up record (SB-2). GET 404 = not recorded yet (isNotRecorded);
 * any other error is unavailable. PUT refines the single row.
 */

import { useState } from "react";
import {
  isNotRecorded,
  useRecordSurgicalFollowup,
  useSurgicalFollowup,
  type FollowupRecordRequest,
} from "@/hooks/queries/useSurgeryEpisodes";

export function FollowupPanel({ episodeId }: { episodeId: string }) {
  const followupQ = useSurgicalFollowup(episodeId);
  const record = useRecordSurgicalFollowup(episodeId);
  const [editing, setEditing] = useState(false);
  const [restrictions, setRestrictions] = useState("");
  const [fitNoteNotes, setFitNoteNotes] = useState("");
  const [transportArranged, setTransportArranged] = useState(false);
  const [transportNotes, setTransportNotes] = useState("");
  const [futureSurgeryIntent, setFutureSurgeryIntent] = useState("");
  const [surveillancePlanRef, setSurveillancePlanRef] = useState("");

  const startEdit = () => {
    const d = followupQ.data;
    setRestrictions(d?.restrictions ?? "");
    setFitNoteNotes(d?.fitNoteNotes ?? "");
    setTransportArranged(!!d?.transportArranged);
    setTransportNotes(d?.transportNotes ?? "");
    setFutureSurgeryIntent(d?.futureSurgeryIntent ?? "");
    setSurveillancePlanRef(d?.surveillancePlanRef ?? "");
    setEditing(true);
  };

  const save = () => {
    const payload: FollowupRecordRequest = { transportArranged };
    if (restrictions.trim()) payload.restrictions = restrictions.trim();
    if (fitNoteNotes.trim()) payload.fitNoteNotes = fitNoteNotes.trim();
    if (transportNotes.trim()) payload.transportNotes = transportNotes.trim();
    if (futureSurgeryIntent.trim()) payload.futureSurgeryIntent = futureSurgeryIntent.trim();
    if (surveillancePlanRef.trim()) payload.surveillancePlanRef = surveillancePlanRef.trim();
    record.mutate(payload, { onSuccess: () => setEditing(false) });
  };

  return (
    <div className="mt-4 rounded-lg border border-gray-100 p-3" data-testid="surgery-followup-panel">
      <div className="flex items-center justify-between">
        <h4 className="text-xs font-semibold uppercase text-muted-foreground">
          Discharge &amp; follow-up (course of care)
        </h4>
        {!editing && (
          <button
            type="button"
            onClick={startEdit}
            className="rounded-md border border-gray-200 px-2 py-1 text-xs"
            data-testid="surgery-followup-edit"
          >
            {followupQ.data ? "Refine" : "Record"}
          </button>
        )}
      </div>

      {followupQ.isLoading ? (
        <p className="mt-2 text-sm text-muted-foreground">Loading follow-up…</p>
      ) : followupQ.isError && !isNotRecorded(followupQ.error) ? (
        <p className="mt-2 text-sm text-danger" role="alert" data-testid="surgery-followup-unavailable">
          Could not read the follow-up record. This is not the same as none being recorded.
        </p>
      ) : !editing && followupQ.isError && isNotRecorded(followupQ.error) ? (
        <p className="mt-2 text-sm text-muted-foreground" data-testid="surgery-followup-not-recorded">
          No discharge / follow-up recorded for this episode yet.
        </p>
      ) : !editing && followupQ.data ? (
        <div className="mt-2 space-y-1 text-sm" data-testid="surgery-followup-view">
          {followupQ.data.restrictions && (
            <p>
              <span className="text-xs font-semibold uppercase text-muted-foreground">
                Restrictions:{" "}
              </span>
              {followupQ.data.restrictions}
            </p>
          )}
          {followupQ.data.fitNoteNotes && (
            <p>
              <span className="text-xs font-semibold uppercase text-muted-foreground">
                Fit note:{" "}
              </span>
              {followupQ.data.fitNoteNotes}
              {followupQ.data.fitNoteIssuedBy
                ? ` (issued by ${followupQ.data.fitNoteIssuedBy})`
                : ""}
            </p>
          )}
          <p className="text-xs text-muted-foreground">
            Transport arranged: {followupQ.data.transportArranged ? "yes" : "no"}
            {followupQ.data.transportNotes ? ` — ${followupQ.data.transportNotes}` : ""}
          </p>
          {followupQ.data.futureSurgeryIntent && (
            <p>
              <span className="text-xs font-semibold uppercase text-muted-foreground">
                Future surgery intent:{" "}
              </span>
              {followupQ.data.futureSurgeryIntent}
            </p>
          )}
          {followupQ.data.recordedBy && (
            <p className="text-xs text-muted-foreground">
              Last refined by {followupQ.data.recordedBy}
              {followupQ.data.recordedAt ? ` at ${followupQ.data.recordedAt}` : ""}
            </p>
          )}
        </div>
      ) : null}

      {editing && (
        <div className="mt-2 space-y-2">
          <label className="block text-xs">
            <span className="font-semibold uppercase text-muted-foreground">Restrictions</span>
            <textarea
              rows={2}
              value={restrictions}
              onChange={(e) => setRestrictions(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1 text-sm"
              data-testid="surgery-followup-restrictions"
            />
          </label>
          <label className="block text-xs">
            <span className="font-semibold uppercase text-muted-foreground">Fit note notes</span>
            <textarea
              rows={2}
              value={fitNoteNotes}
              onChange={(e) => setFitNoteNotes(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1 text-sm"
              data-testid="surgery-followup-fit-note"
            />
          </label>
          <label className="flex items-center gap-1 text-xs">
            <input
              type="checkbox"
              checked={transportArranged}
              onChange={(e) => setTransportArranged(e.target.checked)}
              data-testid="surgery-followup-transport"
            />
            Transport arranged
          </label>
          <label className="block text-xs">
            <span className="font-semibold uppercase text-muted-foreground">Transport notes</span>
            <input
              type="text"
              value={transportNotes}
              onChange={(e) => setTransportNotes(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
              data-testid="surgery-followup-transport-notes"
            />
          </label>
          <label className="block text-xs">
            <span className="font-semibold uppercase text-muted-foreground">
              Future surgery intent
            </span>
            <input
              type="text"
              value={futureSurgeryIntent}
              onChange={(e) => setFutureSurgeryIntent(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
              data-testid="surgery-followup-future-intent"
            />
          </label>
          <label className="block text-xs">
            <span className="font-semibold uppercase text-muted-foreground">
              Surveillance plan ref (PCT)
            </span>
            <input
              type="text"
              value={surveillancePlanRef}
              onChange={(e) => setSurveillancePlanRef(e.target.value)}
              className="mt-0.5 block w-full rounded-md border border-gray-200 px-2 py-1.5 text-sm"
              data-testid="surgery-followup-surveillance-ref"
            />
          </label>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={save}
              disabled={record.isPending}
              className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
              data-testid="surgery-followup-save"
            >
              {record.isPending ? "Saving…" : "Save follow-up"}
            </button>
            <button
              type="button"
              onClick={() => setEditing(false)}
              className="rounded-md border border-gray-200 px-3 py-1.5 text-sm"
            >
              Cancel
            </button>
            {record.isError && (
              <span
                className="text-sm text-danger"
                role="alert"
                data-testid="surgery-followup-write-failed"
              >
                Save failed — the follow-up has NOT been recorded.
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
