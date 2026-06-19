"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { RosterCalendar } from "@/components/vashandi/RosterCalendar";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useRosters, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data, isLoading } = useRosters();

  return (
    <VashandiShell title="Rosters" subtitle="Roster planning and shift coverage">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading rosters…</p> : null}
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <RosterCalendar rosters={data?.items ?? []} />
    </VashandiShell>
  );
}
