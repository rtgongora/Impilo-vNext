"use client";

import { useState } from "react";
import Link from "next/link";
import { KeyRound, RefreshCw, Users } from "lucide-react";
import { useCreateCheckinToken, useSessionAttendance } from "@/hooks/queries/useFundoDelivery";

/**
 * Facilitator roster — the session attendance register (learning-service is
 * the system of record for check-ins), polled while the class runs, plus the
 * check-in code generator learners type into /learning/sessions/{id}/checkin.
 *
 * HONEST SCOPE: the BFF exposes no cohort-member/enrolment-by-course read, so
 * the roster is the attendance register (who has checked in), not the full
 * enrolled cohort list.
 */

type AttendanceItem = {
  id: string;
  subjectId?: string;
  subjectType?: string;
  status?: string;
  checkInMethod?: string;
};

export function FacilitatorRosterPanel({ sessionId }: { sessionId: string }) {
  const { data, refetch, isFetching } = useSessionAttendance(sessionId, 15_000);
  const createToken = useCreateCheckinToken(sessionId);
  const [tokenCode, setTokenCode] = useState<string | null>(null);
  const [tokenError, setTokenError] = useState<string | null>(null);

  const attendees =
    ((data?.data as { items?: AttendanceItem[] } | undefined)?.items ?? []).filter(Boolean);

  async function onCreateToken() {
    setTokenError(null);
    try {
      const res = (await createToken.mutateAsync({})) as {
        data?: { token?: { code?: string; token?: string } | string; code?: string };
      };
      const tokenField = res?.data?.token;
      const code =
        (typeof tokenField === "object" && tokenField ? tokenField.code ?? tokenField.token : tokenField) ??
        res?.data?.code;
      setTokenCode(typeof code === "string" && code ? code : null);
      if (typeof code !== "string" || !code) {
        setTokenError("Token created but no code was returned — check the delivery API payload.");
      }
    } catch {
      setTokenError("Could not create a check-in token.");
    }
  }

  return (
    <div data-testid="facilitator-roster" className="space-y-3">
      <div className="flex items-center justify-between gap-2">
        <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          <Users className="h-3.5 w-3.5" /> Attendance ({attendees.length})
        </p>
        <button
          type="button"
          onClick={() => void refetch()}
          className="inline-flex items-center gap-1 rounded border border-border px-2 py-0.5 text-[11px] text-foreground hover:bg-neutral-100"
        >
          <RefreshCw className={`h-3 w-3 ${isFetching ? "animate-spin" : ""}`} /> Refresh
        </button>
      </div>

      <div className="rounded border border-border bg-background p-2">
        <button
          type="button"
          onClick={() => void onCreateToken()}
          disabled={createToken.isPending}
          data-testid="create-checkin-token"
          className="inline-flex items-center gap-1.5 rounded bg-teal-700 px-2.5 py-1.5 text-xs font-medium text-white disabled:opacity-60"
        >
          <KeyRound className="h-3.5 w-3.5" />
          {createToken.isPending ? "Creating…" : "New check-in code"}
        </button>
        {tokenCode ? (
          <p className="mt-2 text-center font-mono text-2xl font-bold tracking-[0.3em] text-foreground" data-testid="checkin-token-code">
            {tokenCode}
          </p>
        ) : null}
        {tokenError ? <p className="mt-2 text-xs text-warning-foreground">{tokenError}</p> : null}
        <p className="mt-1 text-[11px] text-muted-foreground">
          Learners enter this code on the{" "}
          <Link href={`/learning/sessions/${sessionId}/checkin`} className="text-teal-700 underline">
            session check-in page
          </Link>
          .
        </p>
      </div>

      {attendees.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="roster-empty">
          No check-ins yet. The register fills as learners check in.
        </p>
      ) : (
        <ul className="space-y-1">
          {attendees.map((a) => (
            <li
              key={a.id}
              data-testid="roster-entry"
              className="flex items-center justify-between gap-2 rounded border border-border px-2 py-1.5 text-xs"
            >
              <span className="min-w-0 truncate">{a.subjectId ?? a.id}</span>
              <span className="flex-shrink-0 rounded bg-success-soft px-1.5 py-0.5 text-[10px] uppercase text-primary-hover">
                {a.status ?? "PRESENT"}
                {a.checkInMethod ? ` · ${a.checkInMethod}` : ""}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
