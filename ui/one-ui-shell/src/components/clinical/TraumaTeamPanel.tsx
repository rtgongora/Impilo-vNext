"use client";

/**
 * Trauma-team roster panel (WU2) — the live on-call panel resolved from VASHANDI at
 * activation, with per-member acknowledgement and no-ack escalation. Reads/writes the
 * pct-backed BFF proxies (/internal/v1/ed/trauma/{traumaId}/team[, /{memberId}/ack,
 * /escalate]). Real targeting: each row is a rostered workforce profile that received a
 * notification delivery-intent; ack/escalate transition its status. No mocks.
 */

import { AlertTriangle, BellRing, CheckCircle2, Loader2, Users } from "lucide-react";
import { useTraumaTeam, type TraumaTeamMember } from "@/hooks/queries/useEdVisit";

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const ACK_STYLES: Record<string, string> = {
  ACKED: "bg-green-100 text-green-800",
  PENDING: "bg-yellow-100 text-yellow-900",
  ESCALATED: "bg-red-100 text-red-800",
};

export function TraumaTeamPanel({
  traumaId,
  visitId,
  actorId,
}: {
  traumaId: string;
  visitId?: string;
  actorId?: string;
}) {
  const { team, ack, escalate } = useTraumaTeam(traumaId, visitId);
  const members = team.data ?? [];
  const pending = members.filter((m) => str(m.ack_status).toUpperCase() === "PENDING");

  return (
    <section className="rounded-xl border border-border bg-card p-4 shadow-sm" data-testid="trauma-team-panel">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <Users className="h-4 w-4 text-orange-600" /> Trauma team
          <span className="text-xs font-normal text-muted-foreground">{members.length} paged · {pending.length} awaiting ack</span>
        </h3>
        <button
          type="button"
          disabled={escalate.isPending || pending.length === 0}
          onClick={() => escalate.mutate({ timeoutSeconds: 0 })}
          className="inline-flex items-center gap-1.5 rounded-lg border border-red-300 px-3 py-1.5 text-xs font-medium text-danger hover:bg-danger-soft disabled:opacity-40"
          title={pending.length === 0 ? "No un-acknowledged members to escalate" : "Escalate members who have not acknowledged"}
        >
          {escalate.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <BellRing className="h-3.5 w-3.5" />}
          Escalate no-ack
        </button>
      </div>

      {team.isLoading ? (
        <div className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" /> Resolving on-call panel…
        </div>
      ) : team.isError ? (
        <div className="mt-3 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          <AlertTriangle className="h-4 w-4" /> Could not load the trauma team.
          <button type="button" onClick={() => void team.refetch()} className="underline">Retry</button>
        </div>
      ) : members.length === 0 ? (
        <p className="mt-3 rounded-lg border border-dashed border-border bg-background px-3 py-4 text-center text-xs text-muted-foreground">
          No team members resolved. Check the facility on-call roster (VASHANDI) is published for this shift.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-border">
          {members.map((m: TraumaTeamMember) => {
            const st = str(m.ack_status).toUpperCase() || "PENDING";
            return (
              <li key={str(m.id)} className="flex items-center justify-between gap-3 py-2">
                <div className="min-w-0">
                  <div className="text-sm font-medium text-foreground">{str(m.role).replace(/_/g, " ") || "Member"}</div>
                  <div className="truncate font-mono text-[11px] text-muted-foreground">{str(m.workforce_profile_id)}</div>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${ACK_STYLES[st] ?? "bg-neutral-100 text-foreground"}`}>
                    {st === "ACKED" ? <CheckCircle2 className="h-3 w-3" /> : null}
                    {st}
                  </span>
                  {st === "PENDING" ? (
                    <button
                      type="button"
                      disabled={ack.isPending}
                      onClick={() => ack.mutate({ memberId: str(m.id), ackBy: actorId })}
                      className="rounded-lg bg-primary px-2.5 py-1 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      Acknowledge
                    </button>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
