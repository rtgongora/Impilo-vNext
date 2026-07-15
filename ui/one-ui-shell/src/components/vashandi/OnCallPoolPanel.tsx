"use client";

/**
 * On-call virtual-pool management. A "pool" is not a table — it is a distinct
 * `vsh_shift.virtual_pool_id` key (e.g. `trauma-oncall:<facilityId>`). A member
 * is on duty only when a shift against the pool has status `confirmed` or
 * `checked_in` AND its window covers now — this is exactly what the ED
 * trauma-team resolver reads, so what you compose here is what activation finds.
 *
 * Composing a member reuses the existing roster/shift writes: we ensure a
 * dedicated on-call roster exists for the pool's facility, then hang a shift
 * carrying the pool key. No new persistence.
 */

import { useMemo, useState } from "react";
import { Plus, RadioTower, ShieldCheck, UserCheck, Loader2, CircleDot } from "lucide-react";
import type { Roster, Shift, VirtualPool } from "@/lib/vashandi/types";
import {
  useVirtualPools,
  usePoolOnDuty,
  usePoolShifts,
  useRosters,
  useCreateRoster,
  useCreateShift,
  useUpdateShift,
} from "@/hooks/useVashandi";

const inputCls = "mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground";
const labelCls = "text-xs text-muted-foreground";

const ON_CALL_ROSTER_TYPE = "trauma_oncall";

/** Derive the facility UUID from a `trauma-oncall:<facilityId>` pool key, if present. */
function facilityFromPoolId(poolId: string): string | undefined {
  const marker = "trauma-oncall:";
  return poolId.startsWith(marker) ? poolId.slice(marker.length) : undefined;
}

function DefinePoolForm({ onDefined }: { onDefined: (poolId: string) => void }) {
  const [facilityId, setFacilityId] = useState("");
  const [advancedPoolId, setAdvancedPoolId] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = () => {
    setError(null);
    const poolId = advancedPoolId.trim() || (facilityId.trim() ? `trauma-oncall:${facilityId.trim()}` : "");
    if (!poolId) {
      setError("Enter a facility ID (or an advanced pool key).");
      return;
    }
    onDefined(poolId);
    setFacilityId("");
    setAdvancedPoolId("");
  };

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="define-pool-form">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <RadioTower className="h-4 w-4" />
        Define an on-call pool
      </h2>
      <p className="mt-1 text-xs text-muted-foreground">
        The trauma on-call pool key is <code>trauma-oncall:&lt;facilityId&gt;</code> — this is exactly what ED
        trauma-team activation resolves.
      </p>
      {error ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{error}</p>
      ) : null}
      <div className="mt-3 grid gap-3 md:grid-cols-2">
        <label className={labelCls}>
          Facility ID
          <input
            value={facilityId}
            onChange={(e) => setFacilityId(e.target.value)}
            data-testid="define-pool-facility"
            placeholder="facility UUID"
            className={inputCls}
          />
        </label>
        <label className={labelCls}>
          Advanced pool key (optional)
          <input
            value={advancedPoolId}
            onChange={(e) => setAdvancedPoolId(e.target.value)}
            data-testid="define-pool-advanced"
            placeholder="e.g. trauma-oncall:… or a custom key"
            className={inputCls}
          />
        </label>
      </div>
      <button
        type="button"
        onClick={submit}
        data-testid="define-pool-submit"
        className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white"
      >
        Open pool
      </button>
    </section>
  );
}

function AddMemberForm({ poolId }: { poolId: string }) {
  const { data: rostersData } = useRosters();
  const createRoster = useCreateRoster();
  const createShift = useCreateShift();
  const [workforceProfileId, setWorkforceProfileId] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const facilityId = facilityFromPoolId(poolId);

  const existingOnCallRoster = useMemo<Roster | undefined>(() => {
    const rosters = rostersData?.items ?? [];
    return rosters.find(
      (r) => (r.rosterType ?? "") === ON_CALL_ROSTER_TYPE && (!facilityId || r.facilityId === facilityId),
    );
  }, [rostersData, facilityId]);

  const submit = async () => {
    setError(null);
    if (!workforceProfileId.trim() || !startTime || !endTime) {
      setError("Workforce profile and both duty-window times are required.");
      return;
    }
    setBusy(true);
    try {
      let rosterId = existingOnCallRoster?.id;
      if (!rosterId) {
        const rosterResp = await createRoster.mutateAsync({
          rosterType: ON_CALL_ROSTER_TYPE,
          facilityId: facilityId ?? undefined,
          periodStart: startTime.slice(0, 10),
          periodEnd: endTime.slice(0, 10),
        });
        if (rosterResp.success === false) {
          setError(rosterResp.friendlyMessage ?? "Could not open an on-call roster for this pool.");
          return;
        }
        rosterId = (rosterResp.data as Roster | undefined)?.id;
      }
      if (!rosterId) {
        setError("Could not resolve an on-call roster to schedule against.");
        return;
      }
      const shiftResp = await createShift.mutateAsync({
        rosterId,
        workforceProfileId: workforceProfileId.trim(),
        virtualPoolId: poolId,
        shiftType: "on_call",
        facilityId: facilityId ?? undefined,
        startTime: new Date(startTime).toISOString(),
        endTime: new Date(endTime).toISOString(),
      });
      if (shiftResp.success === false) {
        setError(shiftResp.friendlyMessage ?? "The on-call member was not added.");
        return;
      }
      setWorkforceProfileId("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "The on-call member was not added.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mt-3 rounded-xl border border-dashed border-border p-3" data-testid="add-member-form">
      {error ? (
        <p className="mb-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{error}</p>
      ) : null}
      <div className="grid gap-2 md:grid-cols-3">
        <label className={labelCls}>
          Workforce profile ID
          <input
            value={workforceProfileId}
            onChange={(e) => setWorkforceProfileId(e.target.value)}
            data-testid="member-profile"
            className={inputCls}
          />
        </label>
        <label className={labelCls}>
          On duty from
          <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} data-testid="member-start" className={inputCls} />
        </label>
        <label className={labelCls}>
          Until
          <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} data-testid="member-end" className={inputCls} />
        </label>
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        Added members start <span className="font-medium">scheduled</span> — mark them Confirm or Check in below to
        put them on duty.
      </p>
      <button
        type="button"
        disabled={busy}
        onClick={submit}
        data-testid="member-add-submit"
        className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-primary px-3 py-1 text-xs font-medium text-primary hover:bg-primary/5 disabled:opacity-50"
      >
        {busy ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Plus className="h-3.5 w-3.5" />}
        Add on-call member
      </button>
    </div>
  );
}

function ShiftStatusRow({ shift }: { shift: Shift }) {
  const updateShift = useUpdateShift();
  const onDuty = shift.status === "confirmed" || shift.status === "checked_in";

  const setStatus = (status: string) => {
    updateShift.mutate({ shiftId: shift.id, body: { status } });
  };

  return (
    <li
      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-2.5 py-1.5 text-xs"
      data-testid={`pool-shift-${shift.id}`}
    >
      <span className="font-mono text-[11px] text-muted-foreground">{shift.workforceProfileId}</span>
      <span className="text-muted-foreground">
        {shift.startTime ? new Date(shift.startTime).toLocaleString() : "—"} –{" "}
        {shift.endTime ? new Date(shift.endTime).toLocaleString() : "—"}
      </span>
      <span
        className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 capitalize ${
          onDuty ? "border-emerald-300 bg-emerald-50 text-emerald-700" : "border-border text-muted-foreground"
        }`}
      >
        <CircleDot className="h-3 w-3" />
        {(shift.status ?? "scheduled").replace("_", " ")}
      </span>
      <span className="flex items-center gap-1">
        <button
          type="button"
          disabled={updateShift.isPending}
          onClick={() => setStatus("confirmed")}
          data-testid={`confirm-${shift.id}`}
          className="inline-flex items-center gap-1 rounded border border-border px-2 py-0.5 hover:bg-background disabled:opacity-50"
        >
          <ShieldCheck className="h-3 w-3" /> Confirm
        </button>
        <button
          type="button"
          disabled={updateShift.isPending}
          onClick={() => setStatus("checked_in")}
          data-testid={`checkin-${shift.id}`}
          className="inline-flex items-center gap-1 rounded border border-border px-2 py-0.5 hover:bg-background disabled:opacity-50"
        >
          <UserCheck className="h-3 w-3" /> Check in
        </button>
      </span>
    </li>
  );
}

function PoolDetail({ poolId }: { poolId: string }) {
  const { data: onDutyData, isLoading: onDutyLoading } = usePoolOnDuty(poolId);
  const { data: shiftsData, isLoading: shiftsLoading } = usePoolShifts(poolId);
  const onDutyCount = onDutyData?.onDuty?.onDutyCount ?? onDutyData?.onDuty?.onDuty?.length ?? 0;
  const shifts = shiftsData?.items ?? [];

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="pool-detail">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <RadioTower className="h-4 w-4" />
          <span className="font-mono text-xs">{poolId}</span>
        </h3>
        <span
          className="inline-flex items-center gap-1 rounded-full border border-emerald-300 bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700"
          data-testid="pool-onduty-count"
        >
          <CircleDot className="h-3 w-3" />
          {onDutyLoading ? "…" : `${onDutyCount} on duty now`}
        </span>
      </div>

      <AddMemberForm poolId={poolId} />

      <div className="mt-4">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Roster</h4>
        {shiftsLoading ? (
          <p className="mt-2 text-xs text-muted-foreground">Loading pool roster…</p>
        ) : shifts.length === 0 ? (
          <p className="mt-2 text-xs text-muted-foreground">No members rostered on this pool yet.</p>
        ) : (
          <ul className="mt-2 space-y-1.5" data-testid="pool-shifts">
            {shifts.map((shift) => (
              <ShiftStatusRow key={shift.id} shift={shift} />
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}

export function OnCallPoolPanel() {
  const { data, isLoading } = useVirtualPools();
  const pools: VirtualPool[] = data?.items ?? [];
  const [activePool, setActivePool] = useState<string | null>(null);

  return (
    <div className="space-y-4">
      <DefinePoolForm onDefined={setActivePool} />

      <section className="rounded-2xl border border-border bg-card p-5" data-testid="pool-list">
        <h2 className="text-sm font-semibold text-foreground">On-call pools</h2>
        {isLoading ? (
          <p className="mt-2 text-xs text-muted-foreground">Loading pools…</p>
        ) : pools.length === 0 ? (
          <p className="mt-2 text-xs text-muted-foreground">
            No on-call pools with coverage yet — define one above and add members.
          </p>
        ) : (
          <div className="mt-3 grid gap-2 md:grid-cols-2">
            {pools.map((pool) => (
              <button
                key={pool.poolId}
                type="button"
                onClick={() => setActivePool(pool.poolId)}
                data-testid={`pool-pick-${pool.poolId}`}
                className={`flex items-center justify-between gap-2 rounded-xl border px-3 py-2 text-left text-xs ${
                  activePool === pool.poolId ? "border-primary bg-primary/5" : "border-border hover:bg-background"
                }`}
              >
                <span className="font-mono">{pool.poolId}</span>
                <span className="text-emerald-700">{pool.onDutyCount ?? 0} on duty</span>
              </button>
            ))}
          </div>
        )}
      </section>

      {activePool ? <PoolDetail poolId={activePool} /> : null}
    </div>
  );
}
