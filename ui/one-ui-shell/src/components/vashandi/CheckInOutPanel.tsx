"use client";

import { useState } from "react";
import { useCheckIn, useCheckOut } from "@/hooks/useVashandi";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import type { AttendanceEvent } from "@/lib/vashandi/types";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface CheckInOutPanelProps {
  shiftId: string;
  workforceProfileId: string;
}

export function CheckInOutPanel({ shiftId, workforceProfileId }: CheckInOutPanelProps) {
  const checkIn = useCheckIn();
  const checkOut = useCheckOut();
  const startShift = useShiftStore((state) => state.startShift);
  const endShift = useShiftStore((state) => state.endShift);
  const facility = useFacilityStore((state) => state.facility);
  const workspace = useWorkspaceStore((state) => state.workspace);
  const [lastAction, setLastAction] = useState<string | null>(null);
  const blocked = checkIn.data && !checkIn.data.success ? checkIn.data : checkOut.data && !checkOut.data.success ? checkOut.data : null;

  if (blocked) {
    return (
      <VashandiFriendlyBlockedState
        state={blocked.blockedReason}
        title={blocked.friendlyTitle ?? "Attendance blocked"}
        description={blocked.friendlyMessage ?? blocked.integrationMessage}
      />
    );
  }

  return (
    <div className="rounded-2xl border border-border bg-card p-5 space-y-3">
      <h3 className="font-medium text-foreground">Check in / out</h3>
      <p className="text-sm text-muted-foreground">Records attendance against shift {shiftId}.</p>
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={checkIn.isPending}
          onClick={async () => {
            const result = await checkIn.mutateAsync({ shiftId, workforceProfileId, checkInMode: "self_check_in" });
            if (result.success) {
              const event = (result.data ?? {}) as AttendanceEvent;
              startShift({
                id: event.shiftId ?? event.id ?? shiftId,
                startedAt: event.eventTime ?? new Date().toISOString(),
                workspaceId: workspace?.id ?? "",
                facilityId: facility?.id ?? "",
              });
              setLastAction("Checked in");
            }
          }}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          Check in
        </button>
        <button
          type="button"
          disabled={checkOut.isPending}
          onClick={async () => {
            const result = await checkOut.mutateAsync({ shiftId, workforceProfileId, checkInMode: "self_check_in" });
            if (result.success) {
              endShift();
              setLastAction("Checked out");
            }
          }}
          className="rounded-lg border border-border px-4 py-2 text-sm font-medium hover:bg-muted disabled:opacity-50"
        >
          Check out
        </button>
      </div>
      {lastAction ? <p className="text-sm text-success-foreground">{lastAction}</p> : null}
    </div>
  );
}
