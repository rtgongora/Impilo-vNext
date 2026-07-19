"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { AssignmentCreateForm } from "@/components/vashandi/AssignmentCreateForm";
import { AssignmentStatusBadge } from "@/components/vashandi/AssignmentStatusBadge";
import { AssignmentLifecycleActions } from "@/components/vashandi/AssignmentLifecycleActions";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useAssignments, isVashandiDegraded } from "@/hooks/useVashandi";
import type { WorkforceAssignment } from "@/lib/vashandi/types";

/** An assignment is provider-access-derived when a varapi FACILITY_ACCESS approval materialised it. */
function isProviderAccessDerived(sourceAuthority?: string): boolean {
  return !!sourceAuthority && sourceAuthority.startsWith("VARAPI_ACCESS_REQUEST:");
}

/** Days until endDate (negative = past); null when there is no end date. */
function daysUntil(endDate?: string): number | null {
  if (!endDate) return null;
  const end = new Date(endDate);
  if (Number.isNaN(end.getTime())) return null;
  const ms = end.getTime() - Date.now();
  return Math.ceil(ms / (1000 * 60 * 60 * 24));
}

function validityLabel(a: WorkforceAssignment): string | null {
  if (!a.startDate && !a.endDate) return null;
  const from = a.startDate ? a.startDate : "—";
  const to = a.endDate ? a.endDate : "open-ended";
  return `${from} → ${to}`;
}

export default function Page() {
  const { data, isLoading } = useAssignments();

  return (
    <VashandiShell title="Assignments" subtitle="Workforce assignment lifecycle">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading assignments…</p> : null}
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <AssignmentCreateForm onCreated={() => undefined} />
      {data?.items.length === 0 && data?.response?.success ? (
        <div className="mt-4 rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
          No assignments returned for your scope yet.
        </div>
      ) : null}
      <ul className="mt-4 space-y-2">
        {data?.items.map((assignment) => {
          const providerDerived = isProviderAccessDerived(assignment.sourceAuthority);
          const validity = validityLabel(assignment);
          const remaining = daysUntil(assignment.endDate);
          const isPermanent = (assignment.engagementType ?? "").toUpperCase() === "PERMANENT";
          const showExpiry =
            assignment.status === "active" && !isPermanent && remaining !== null;
          return (
            <li key={assignment.id} className="rounded-xl border border-border bg-card px-4 py-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-medium">{assignment.assignmentType ?? "Assignment"}</p>
                  <p className="text-muted-foreground">{assignment.roleTemplateId ?? assignment.id}</p>
                </div>
                <AssignmentStatusBadge status={assignment.status} />
              </div>

              {/* Identity-program facts (D-P7): engagement, validity, expiry, provenance. */}
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                {assignment.engagementType ? (
                  <span
                    className="rounded-full border border-primary/30 bg-primary/5 px-2 py-0.5 font-medium text-primary"
                    data-testid="engagement-badge"
                  >
                    {assignment.engagementType.replace(/_/g, " ")}
                  </span>
                ) : null}
                {validity ? (
                  <span className="rounded-full border border-border bg-muted px-2 py-0.5 text-muted-foreground">
                    {validity}
                  </span>
                ) : null}
                {showExpiry ? (
                  <span
                    className={[
                      "rounded-full px-2 py-0.5 font-medium",
                      remaining !== null && remaining <= 7
                        ? "border border-danger/30 bg-danger-soft text-red-800"
                        : "border border-border bg-muted text-muted-foreground",
                    ].join(" ")}
                  >
                    {remaining !== null && remaining < 0
                      ? "Expired — pending sweep"
                      : `Expires in ${remaining} day${remaining === 1 ? "" : "s"}`}
                  </span>
                ) : null}
                {providerDerived ? (
                  <span
                    className="rounded-full border border-violet-400/30 bg-violet-500/10 px-2 py-0.5 font-medium text-violet-700"
                    title={assignment.sourceAuthority}
                    data-testid="provider-access-provenance"
                  >
                    From a provider access request
                  </span>
                ) : null}
              </div>

              <div className="mt-3 border-t border-border pt-3">
                <AssignmentLifecycleActions assignment={assignment} />
              </div>
            </li>
          );
        })}
      </ul>
    </VashandiShell>
  );
}
