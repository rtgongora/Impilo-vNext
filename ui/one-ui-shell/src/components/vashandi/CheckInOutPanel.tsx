"use client";

import { useState } from "react";
import { useAdhocCheckIn, useCheckIn, useCheckOut } from "@/hooks/useVashandi";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import type { AttendanceEvent } from "@/lib/vashandi/types";
import { BiometricVerifyField } from "@/components/biometric/BiometricVerifyField";
import type { Modality } from "@/hooks/queries/useAbisBiometric";
import { VashandiFriendlyBlockedState } from "./VashandiFriendlyBlockedState";

interface CheckInOutPanelProps {
  shiftId: string;
  workforceProfileId: string;
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function CheckInOutPanel({ shiftId, workforceProfileId }: CheckInOutPanelProps) {
  // Vashandi types shiftId as a UUID; the self-service surface passes a
  // sentinel ("self-service") which must route to the ad-hoc check-in that
  // anchors to assignment/facility context instead of a rostered shift.
  const hasRosteredShift = UUID_RE.test(shiftId);
  const checkIn = useCheckIn();
  const adhocCheckIn = useAdhocCheckIn();
  const checkOut = useCheckOut();
  const startShift = useShiftStore((state) => state.startShift);
  const endShift = useShiftStore((state) => state.endShift);
  const facility = useFacilityStore((state) => state.facility);
  const workspace = useWorkspaceStore((state) => state.workspace);
  const [lastAction, setLastAction] = useState<string | null>(null);
  const [liveProbe, setLiveProbe] = useState<{ modality: Modality; probeBase64: string } | null>(null);
  const blocked =
    checkIn.data && !checkIn.data.success ? checkIn.data
    : adhocCheckIn.data && !adhocCheckIn.data.success ? adhocCheckIn.data
    : checkOut.data && !checkOut.data.success ? checkOut.data
    : null;

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
      {hasRosteredShift ? (
        <BiometricVerifyField
          label="Verify by biometric (optional)"
          onProbe={setLiveProbe}
          disabled={checkIn.isPending}
        />
      ) : (
        <p className="text-[11px] text-muted-foreground">
          Biometric check-in is recorded against a rostered shift; this self-service check-in uses your
          bound workforce profile and facility context.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          disabled={checkIn.isPending || adhocCheckIn.isPending}
          onClick={async () => {
            const result = hasRosteredShift
              ? await checkIn.mutateAsync({
                  shiftId,
                  workforceProfileId,
                  checkInMode: "self_check_in",
                  // Live ABIS probe: the server verifies against the worker's enrolled
                  // template (MATCH → recorded as biometric; NO_MATCH → denied;
                  // UNAVAILABLE → falls back on self_check_in).
                  biometricSubjectRef: liveProbe ? workforceProfileId : undefined,
                  biometricModality: liveProbe?.modality,
                  biometricProbeBase64: liveProbe?.probeBase64,
                })
              : await adhocCheckIn.mutateAsync({ workforceProfileId, facilityId: facility?.id || undefined });
            if (result.success) {
              const event = (result.data ?? {}) as AttendanceEvent & { eventId?: string };
              startShift({
                id: event.shiftId ?? event.eventId ?? event.id ?? shiftId,
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
            const result = await checkOut.mutateAsync({
              ...(hasRosteredShift ? { shiftId } : {}),
              workforceProfileId,
              checkInMode: "self_check_in",
            });
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
