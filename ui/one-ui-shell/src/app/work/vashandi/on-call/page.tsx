"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { OnCallPoolPanel } from "@/components/vashandi/OnCallPoolPanel";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useVirtualPools, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data } = useVirtualPools();

  return (
    <VashandiShell
      title="On-call pools"
      subtitle="Compose trauma & clinical on-call rosters — who is standing by, right now"
    >
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <div className="mb-4 rounded-xl border border-indigo-100 bg-info-soft/60 px-4 py-3 text-sm text-primary-hover">
        On-call coverage feeds emergency workflows directly — a member counts as on duty only when confirmed or
        checked in and their window covers now. This is the same truth ED trauma-team activation reads.
      </div>
      <OnCallPoolPanel />
    </VashandiShell>
  );
}
