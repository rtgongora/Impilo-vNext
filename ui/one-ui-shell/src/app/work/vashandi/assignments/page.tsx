"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { AssignmentCreateForm } from "@/components/vashandi/AssignmentCreateForm";
import { AssignmentStatusBadge } from "@/components/vashandi/AssignmentStatusBadge";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useAssignments, isVashandiDegraded } from "@/hooks/useVashandi";

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
        {data?.items.map((assignment) => (
          <li key={assignment.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card px-4 py-3 text-sm">
            <div>
              <p className="font-medium">{assignment.assignmentType ?? "Assignment"}</p>
              <p className="text-muted-foreground">{assignment.roleTemplateId ?? assignment.id}</p>
            </div>
            <AssignmentStatusBadge status={assignment.status} />
          </li>
        ))}
      </ul>
    </VashandiShell>
  );
}
