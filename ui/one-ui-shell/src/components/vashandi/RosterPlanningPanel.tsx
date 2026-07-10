"use client";

/**
 * Roster planning: create a roster, add shifts to it, approve it.
 * Approval is the governance step — draft rosters are visible but not
 * authoritative until approved (backend: roster status draft → approved).
 */

import { useState } from "react";
import { CalendarPlus, CheckCircle2, ChevronDown, ChevronRight, Loader2, Plus } from "lucide-react";
import type { Roster } from "@/lib/vashandi/types";
import {
  useApproveRoster,
  useCreateRoster,
  useCreateShift,
  useRosterShifts,
} from "@/hooks/useVashandi";

const inputCls = "mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground";
const labelCls = "text-xs text-muted-foreground";

export function RosterCreateForm() {
  const create = useCreateRoster();
  const [rosterType, setRosterType] = useState("facility_monthly");
  const [facilityId, setFacilityId] = useState("");
  const [periodStart, setPeriodStart] = useState("");
  const [periodEnd, setPeriodEnd] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    setError(null);
    if (!periodStart || !periodEnd) {
      setError("Both period dates are required.");
      return;
    }
    create.mutate(
      {
        rosterType: rosterType.trim() || "facility_monthly",
        facilityId: facilityId.trim() || undefined,
        periodStart,
        periodEnd,
      },
      {
        onSuccess: (response) => {
          if (response.success === false) {
            setError(response.friendlyMessage ?? "The roster was not created.");
            return;
          }
          setPeriodStart("");
          setPeriodEnd("");
        },
        onError: (e) => setError(e instanceof Error ? e.message : "The roster was not created."),
      },
    );
  };

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="roster-create-form">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <CalendarPlus className="h-4 w-4" />
        Plan a roster
      </h2>
      {error ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{error}</p>
      ) : null}
      <div className="mt-3 grid gap-3 md:grid-cols-4">
        <label className={labelCls}>
          Roster type
          <input value={rosterType} onChange={(e) => setRosterType(e.target.value)} data-testid="roster-type" className={inputCls} />
        </label>
        <label className={labelCls}>
          Facility ID (optional)
          <input value={facilityId} onChange={(e) => setFacilityId(e.target.value)} data-testid="roster-facility" className={inputCls} />
        </label>
        <label className={labelCls}>
          Period start
          <input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} data-testid="roster-start" className={inputCls} />
        </label>
        <label className={labelCls}>
          Period end
          <input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} data-testid="roster-end" className={inputCls} />
        </label>
      </div>
      <button
        type="button"
        disabled={create.isPending}
        onClick={submit}
        data-testid="roster-create-submit"
        className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {create.isPending ? "Creating…" : "Create draft roster"}
      </button>
    </section>
  );
}

function ShiftAddForm({ rosterId, onDone }: { rosterId: string; onDone: () => void }) {
  const createShift = useCreateShift();
  const [workforceProfileId, setWorkforceProfileId] = useState("");
  const [shiftType, setShiftType] = useState("day");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    setError(null);
    if (!workforceProfileId.trim() || !startTime || !endTime) {
      setError("Workforce profile and both shift times are required.");
      return;
    }
    createShift.mutate(
      {
        rosterId,
        workforceProfileId: workforceProfileId.trim(),
        shiftType: shiftType.trim() || "day",
        startTime: new Date(startTime).toISOString(),
        endTime: new Date(endTime).toISOString(),
      },
      {
        onSuccess: (response) => {
          if (response.success === false) {
            setError(response.friendlyMessage ?? "The shift was not added.");
            return;
          }
          setWorkforceProfileId("");
          onDone();
        },
        onError: (e) => setError(e instanceof Error ? e.message : "The shift was not added."),
      },
    );
  };

  return (
    <div className="mt-3 rounded-xl border border-dashed border-border p-3" data-testid={`shift-add-form-${rosterId}`}>
      {error ? (
        <p className="mb-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{error}</p>
      ) : null}
      <div className="grid gap-2 md:grid-cols-4">
        <label className={labelCls}>
          Workforce profile ID
          <input value={workforceProfileId} onChange={(e) => setWorkforceProfileId(e.target.value)} data-testid="shift-profile" className={inputCls} />
        </label>
        <label className={labelCls}>
          Shift type
          <input value={shiftType} onChange={(e) => setShiftType(e.target.value)} data-testid="shift-type" className={inputCls} />
        </label>
        <label className={labelCls}>
          Starts
          <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} data-testid="shift-start" className={inputCls} />
        </label>
        <label className={labelCls}>
          Ends
          <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} data-testid="shift-end" className={inputCls} />
        </label>
      </div>
      <button
        type="button"
        disabled={createShift.isPending}
        onClick={submit}
        data-testid="shift-add-submit"
        className="mt-2 rounded-lg border border-primary px-3 py-1 text-xs font-medium text-primary hover:bg-primary/5 disabled:opacity-50"
      >
        {createShift.isPending ? "Adding…" : "Add shift"}
      </button>
    </div>
  );
}

function RosterShiftsList({ rosterId }: { rosterId: string }) {
  const { data, isLoading } = useRosterShifts(rosterId);
  if (isLoading) return <p className="mt-2 text-xs text-muted-foreground">Loading shifts…</p>;
  const shifts = data?.items ?? [];
  if (shifts.length === 0) {
    return <p className="mt-2 text-xs text-muted-foreground">No shifts scheduled on this roster yet.</p>;
  }
  return (
    <ul className="mt-2 space-y-1.5" data-testid={`roster-shifts-${rosterId}`}>
      {shifts.map((shift) => (
        <li key={shift.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-2.5 py-1.5 text-xs">
          <span className="font-medium capitalize">{shift.shiftType ?? "shift"}</span>
          <span className="text-muted-foreground">
            {shift.startTime ? new Date(shift.startTime).toLocaleString() : "—"} –{" "}
            {shift.endTime ? new Date(shift.endTime).toLocaleString() : "—"}
          </span>
          <span className="rounded-full border border-border px-2 py-0.5 capitalize">{shift.status ?? "scheduled"}</span>
        </li>
      ))}
    </ul>
  );
}

export function RosterPlanningCard({ roster }: { roster: Roster }) {
  const approve = useApproveRoster();
  const [expanded, setExpanded] = useState(false);
  const [addingShift, setAddingShift] = useState(false);
  const [approveError, setApproveError] = useState<string | null>(null);
  const isDraft = (roster.status ?? "").toLowerCase() === "draft";

  return (
    <div className="rounded-2xl border border-border bg-card p-4" data-testid={`roster-card-${roster.id}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="flex items-center gap-2 text-left"
          data-testid={`roster-toggle-${roster.id}`}
        >
          {expanded ? <ChevronDown className="h-4 w-4 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" />}
          <span>
            <span className="font-medium text-foreground">{roster.rosterType ?? "Roster"}</span>
            <span className="ml-2 text-sm text-muted-foreground">
              {roster.periodStart} → {roster.periodEnd}
            </span>
          </span>
        </button>
        <div className="flex items-center gap-2">
          <span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize">{roster.status}</span>
          {isDraft ? (
            <button
              type="button"
              disabled={approve.isPending}
              data-testid={`roster-approve-${roster.id}`}
              onClick={() => {
                setApproveError(null);
                approve.mutate(roster.id, {
                  onSuccess: (response) => {
                    if (response.success === false) {
                      setApproveError(response.friendlyMessage ?? "Approval was not accepted.");
                    }
                  },
                  onError: (e) => setApproveError(e instanceof Error ? e.message : "Approval failed."),
                });
              }}
              className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-300 px-2.5 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
            >
              {approve.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
              Approve roster
            </button>
          ) : null}
        </div>
      </div>
      {approveError ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{approveError}</p>
      ) : null}
      {expanded ? (
        <div className="mt-3 border-t border-border pt-3">
          <RosterShiftsList rosterId={roster.id} />
          {addingShift ? (
            <ShiftAddForm rosterId={roster.id} onDone={() => setAddingShift(false)} />
          ) : (
            <button
              type="button"
              onClick={() => setAddingShift(true)}
              data-testid={`roster-add-shift-${roster.id}`}
              className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1 text-xs font-medium text-foreground hover:bg-background"
            >
              <Plus className="h-3.5 w-3.5" />
              Add shift
            </button>
          )}
        </div>
      ) : null}
    </div>
  );
}
