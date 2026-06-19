"use client";

import { VashandiShell } from "@/components/vashandi/VashandiShell";
import { AttendanceEventList } from "@/components/vashandi/AttendanceEventList";
import { CheckInOutPanel } from "@/components/vashandi/CheckInOutPanel";
import { VashandiFriendlyBlockedState } from "@/components/vashandi/VashandiFriendlyBlockedState";
import { useAttendance, isVashandiDegraded } from "@/hooks/useVashandi";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";

export default function Page() {
  const { contract } = useSessionExperienceContract();
  const { data, isLoading } = useAttendance();
  const profileId = contract?.vashandiWorkforceProfileId ?? "";

  return (
    <VashandiShell title="Attendance" subtitle="Check-in, check-out and supervisor confirmation">
      {data?.response && isVashandiDegraded(data.response) ? (
        <VashandiFriendlyBlockedState
          title={data.response.friendlyTitle ?? "Service degraded"}
          description={data.response.friendlyMessage ?? data.response.integrationMessage}
        />
      ) : null}
      {profileId ? (
        <div className="mb-4">
          <CheckInOutPanel shiftId="self-service" workforceProfileId={profileId} />
        </div>
      ) : (
        <p className="mb-4 text-sm text-muted-foreground">No bound workforce profile on this session — check-in requires an active profile.</p>
      )}
      <AttendanceEventList events={data?.items ?? []} isLoading={isLoading} />
    </VashandiShell>
  );
}
