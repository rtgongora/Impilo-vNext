"use client";

/**
 * Supervisor attendance confirmation — the third leg of the attendance
 * lifecycle (check-in → check-out → supervisor confirm). Confirmation is a
 * supervisory attestation and is what payroll-grade hours derive credibility from.
 */

import { useState } from "react";
import { BadgeCheck, Loader2 } from "lucide-react";
import { useSupervisorConfirmAttendance } from "@/hooks/useVashandi";

const inputCls = "mt-1 w-full rounded border border-border px-2 py-1.5 text-sm text-foreground";
const labelCls = "text-xs text-muted-foreground";

export function SupervisorConfirmPanel() {
  const confirm = useSupervisorConfirmAttendance();
  const [workforceProfileId, setWorkforceProfileId] = useState("");
  const [shiftId, setShiftId] = useState("");
  const [confirmationType, setConfirmationType] = useState("presence");
  const [error, setError] = useState<string | null>(null);
  const [confirmed, setConfirmed] = useState(false);

  const submit = () => {
    setError(null);
    setConfirmed(false);
    if (!workforceProfileId.trim()) {
      setError("The workforce profile being confirmed is required.");
      return;
    }
    confirm.mutate(
      {
        workforceProfileId: workforceProfileId.trim(),
        shiftId: shiftId.trim() || undefined,
        confirmationType: confirmationType.trim() || "presence",
      },
      {
        onSuccess: (response) => {
          if (response.success === false) {
            setError(response.friendlyMessage ?? "The confirmation was not recorded.");
            return;
          }
          setConfirmed(true);
          setWorkforceProfileId("");
          setShiftId("");
        },
        onError: (e) => setError(e instanceof Error ? e.message : "The confirmation was not recorded."),
      },
    );
  };

  return (
    <section className="rounded-2xl border border-border bg-card p-5" data-testid="supervisor-confirm-panel">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <BadgeCheck className="h-4 w-4" />
        Supervisor confirmation
      </h2>
      <p className="mt-1 text-xs text-muted-foreground">
        Attest a team member&apos;s attendance for the shift — payroll-grade hours derive from confirmed events.
      </p>
      {error ? (
        <p className="mt-2 rounded border border-danger/28 bg-danger-soft px-2 py-1 text-xs text-danger" data-testid="supervisor-confirm-error">
          {error}
        </p>
      ) : null}
      {confirmed ? (
        <p className="mt-2 rounded border border-emerald-200 bg-emerald-50 px-2 py-1 text-xs text-emerald-800" data-testid="supervisor-confirm-ok">
          Attendance confirmed.
        </p>
      ) : null}
      <div className="mt-3 grid gap-3 md:grid-cols-3">
        <label className={labelCls}>
          Workforce profile ID
          <input value={workforceProfileId} onChange={(e) => setWorkforceProfileId(e.target.value)} data-testid="supervisor-profile" className={inputCls} />
        </label>
        <label className={labelCls}>
          Shift ID (optional for ad-hoc)
          <input value={shiftId} onChange={(e) => setShiftId(e.target.value)} data-testid="supervisor-shift" className={inputCls} />
        </label>
        <label className={labelCls}>
          Confirmation type
          <input value={confirmationType} onChange={(e) => setConfirmationType(e.target.value)} data-testid="supervisor-type" className={inputCls} />
        </label>
      </div>
      <button
        type="button"
        disabled={confirm.isPending}
        onClick={submit}
        data-testid="supervisor-confirm-submit"
        className="mt-3 inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
      >
        {confirm.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BadgeCheck className="h-3.5 w-3.5" />}
        Confirm attendance
      </button>
    </section>
  );
}
