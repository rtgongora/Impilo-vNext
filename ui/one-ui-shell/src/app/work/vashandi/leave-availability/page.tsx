"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { LeaveAvailabilityPanel } from "@/components/vashandi/LeaveAvailabilityPanel";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useLeaveAvailability, isVashandiDegraded } from "@/hooks/useVashandi";

export default function Page() {
  const { data, isLoading } = useLeaveAvailability();

  return (
    <VashandiShell title="Leave & Availability" subtitle="Leave records and availability windows">
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      <LeaveAvailabilityPanel records={data?.items ?? []} isLoading={isLoading} />
    </VashandiShell>
  );
}
