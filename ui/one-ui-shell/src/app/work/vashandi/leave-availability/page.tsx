"use client";

/**
 * Leave & availability management: request leave, decide it (approve/reject —
 * rejection is a first-class decision, recorded with the deciding actor),
 * and see fiscal-year balances. Terminal decisions are final in the backend
 * (pending → approved | rejected | cancelled; approved → cancelled).
 */

import { useState } from "react";
import { CalendarClock, CheckCircle2, Loader2, XCircle } from "lucide-react";
import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import {
  isVashandiDegraded,
  useCreateLeave,
  useLeaveAvailability,
  useLeaveBalances,
  useLeaveTypes,
  useUpdateLeave,
} from "@/hooks/useVashandi";

const inputCls = "mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground";
const labelCls = "text-xs text-muted-foreground";

function LeaveRequestForm() {
  const create = useCreateLeave();
  const { data: typesData } = useLeaveTypes();
  const [workforceProfileId, setWorkforceProfileId] = useState("");
  const [leaveType, setLeaveType] = useState("annual");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  const types = typesData?.items ?? [];

  const submit = () => {
    setError(null);
    if (!workforceProfileId.trim() || !startDate || !endDate) {
      setError("Workforce profile and both dates are required.");
      return;
    }
    create.mutate(
      {
        workforceProfileId: workforceProfileId.trim(),
        leaveType: leaveType.trim() || "annual",
        status: "pending",
        startDate,
        endDate,
        sourceAuthority: "self",
      },
      {
        onSuccess: (response) => {
          if (response.success === false) {
            setError(response.friendlyMessage ?? "The leave request was not recorded.");
            return;
          }
          setStartDate("");
          setEndDate("");
        },
        onError: (e) => setError(e instanceof Error ? e.message : "The leave request was not recorded."),
      },
    );
  };

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="leave-request-form">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <CalendarClock className="h-4 w-4" />
        Request leave
      </h2>
      {error ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger">{error}</p>
      ) : null}
      <div className="mt-3 grid gap-3 md:grid-cols-4">
        <label className={labelCls}>
          Workforce profile ID
          <input value={workforceProfileId} onChange={(e) => setWorkforceProfileId(e.target.value)} data-testid="leave-profile" className={inputCls} />
        </label>
        <label className={labelCls}>
          Leave type
          {types.length > 0 ? (
            <select value={leaveType} onChange={(e) => setLeaveType(e.target.value)} data-testid="leave-type" className={inputCls}>
              {types.map((t) => (
                <option key={t.id} value={t.name}>
                  {t.name}
                </option>
              ))}
            </select>
          ) : (
            <input value={leaveType} onChange={(e) => setLeaveType(e.target.value)} data-testid="leave-type" className={inputCls} />
          )}
        </label>
        <label className={labelCls}>
          From
          <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} data-testid="leave-start" className={inputCls} />
        </label>
        <label className={labelCls}>
          To
          <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} data-testid="leave-end" className={inputCls} />
        </label>
      </div>
      <button
        type="button"
        disabled={create.isPending}
        onClick={submit}
        data-testid="leave-submit"
        className="mt-3 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {create.isPending ? "Submitting…" : "Submit leave request"}
      </button>
    </section>
  );
}

function BalancesSection() {
  const year = new Date().getFullYear();
  const [profileId, setProfileId] = useState("");
  const { data, isLoading } = useLeaveBalances(
    profileId ? { workforceProfileId: profileId, fiscalYear: String(year) } : undefined,
  );
  const balances = profileId ? data?.items ?? [] : [];

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="leave-balances">
      <h2 className="text-sm font-semibold text-foreground">Fiscal-year balances ({year})</h2>
      <label className={labelCls}>
        Workforce profile ID
        <input value={profileId} onChange={(e) => setProfileId(e.target.value)} data-testid="balance-profile" className={`${inputCls} max-w-sm`} placeholder="Paste a profile ID to see balances" />
      </label>
      {profileId && isLoading ? <p className="mt-2 text-xs text-muted-foreground">Loading balances…</p> : null}
      {profileId && !isLoading && balances.length === 0 ? (
        <p className="mt-2 text-xs text-muted-foreground">
          No balance rows for this profile — entitlement enforcement is off until one is configured.
        </p>
      ) : null}
      {balances.length > 0 ? (
        <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {balances.map((b) => (
            <li key={b.id} className="rounded-xl border border-border px-3 py-2 text-sm">
              <p className="font-medium capitalize">{b.leaveType ?? "leave"}</p>
              <p className="text-xs text-muted-foreground">
                {b.available ?? 0} available · {b.used ?? 0} used of {(b.entitlement ?? 0) + (b.carriedOver ?? 0)}
                {b.carriedOver ? ` (incl. ${b.carriedOver} carried over)` : ""}
              </p>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}

export default function Page() {
  const { data, isLoading } = useLeaveAvailability();
  const decide = useUpdateLeave();
  const [decideError, setDecideError] = useState<string | null>(null);

  const records = data?.items ?? [];

  const decideLeave = (leaveId: string, status: "approved" | "rejected") => {
    setDecideError(null);
    decide.mutate(
      { leaveId, body: { status } },
      {
        onSuccess: (response) => {
          if (response.success === false) {
            setDecideError(response.friendlyMessage ?? "The decision was not recorded.");
          }
        },
        onError: (e) => setDecideError(e instanceof Error ? e.message : "The decision was not recorded."),
      },
    );
  };

  return (
    <VashandiShell title="Leave & Availability" subtitle="Request, decide and track leave with fiscal-year balances">
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}

      <div className="space-y-4">
        <LeaveRequestForm />
        <BalancesSection />

        <section className="rounded-2xl border border-border bg-card p-5">
          <h2 className="text-sm font-semibold text-foreground">Leave records</h2>
          {decideError ? (
            <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger" data-testid="leave-decide-error">
              {decideError}
            </p>
          ) : null}
          {isLoading ? <p className="mt-2 text-sm text-muted-foreground">Loading leave and availability…</p> : null}
          {!isLoading && records.length === 0 ? (
            <p className="mt-2 text-sm text-muted-foreground">No leave or availability records in your scope yet.</p>
          ) : null}
          <ul className="mt-2 space-y-2">
            {records.map((record) => {
              const pending = (record.status ?? "").toLowerCase() === "pending";
              return (
                <li key={record.id} className="rounded-xl border border-border px-4 py-3 text-sm" data-testid={`leave-row-${record.id}`}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <span className="font-medium capitalize">{(record.leaveType ?? "leave").replace(/_/g, " ")}</span>
                      <span className="ml-2 text-muted-foreground">
                        {record.startDate} → {record.endDate}
                      </span>
                      {record.approvedBy ? (
                        <span className="ml-2 text-xs text-muted-foreground">decided by {record.approvedBy}</span>
                      ) : null}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="rounded-full border border-border px-2 py-0.5 text-xs capitalize">{record.status}</span>
                      {pending ? (
                        <>
                          <button
                            type="button"
                            disabled={decide.isPending}
                            onClick={() => decideLeave(record.id, "approved")}
                            data-testid={`leave-approve-${record.id}`}
                            className="inline-flex items-center gap-1 rounded-lg border border-emerald-300 px-2 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-50 disabled:opacity-50"
                          >
                            {decide.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <CheckCircle2 className="h-3 w-3" />}
                            Approve
                          </button>
                          <button
                            type="button"
                            disabled={decide.isPending}
                            onClick={() => decideLeave(record.id, "rejected")}
                            data-testid={`leave-reject-${record.id}`}
                            className="inline-flex items-center gap-1 rounded-lg border border-rose-300 px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 disabled:opacity-50"
                          >
                            <XCircle className="h-3 w-3" />
                            Reject
                          </button>
                        </>
                      ) : null}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      </div>
    </VashandiShell>
  );
}
