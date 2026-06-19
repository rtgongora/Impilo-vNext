"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { WorkforceProfileCard } from "@/components/vashandi/WorkforceProfileCard";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useWorkforceProfiles, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data, isLoading } = useWorkforceProfiles();

  return (
    <VashandiShell title="Workforce Registry" subtitle="Operational workforce profiles in your scope">
      {isLoading ? <p className="text-sm text-muted-foreground">Loading workforce profiles…</p> : null}
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage ?? "Vashandi upstream is unavailable."}
        />
      ) : null}
      {data?.items.length === 0 && data?.response?.success ? (
        <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-6 text-sm text-muted-foreground">
          No workforce profiles returned for your scope yet.
        </div>
      ) : null}
      <div className="grid gap-4 md:grid-cols-2">
        {data?.items.map((profile) => (
          <WorkforceProfileCard key={profile.id} profile={profile} />
        ))}
      </div>
    </VashandiShell>
  );
}
