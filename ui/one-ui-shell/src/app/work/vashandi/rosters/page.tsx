"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { RosterCreateForm, RosterPlanningCard } from "@/components/vashandi/RosterPlanningPanel";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useRosters, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data, isLoading } = useRosters();
  const rosters = data?.items ?? [];

  return (
    <VashandiShell title="Rosters" subtitle="Plan rosters, schedule shifts, approve coverage">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading rosters…</p> : null}
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <div className="space-y-4">
        <RosterCreateForm />
        {rosters.length === 0 && data?.response?.success ? (
          <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
            No rosters in your scope yet — plan the first one above.
          </div>
        ) : null}
        {rosters.map((roster) => (
          <RosterPlanningCard key={roster.id} roster={roster} />
        ))}
      </div>
    </VashandiShell>
  );
}
