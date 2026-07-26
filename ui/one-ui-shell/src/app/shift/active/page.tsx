"use client";

/**
 * Active Shift — View current shift details and end shift.
 * Route: /shift/active
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, Clock, Users, Activity, LogOut, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { PageShell } from "@/components/PageShell";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useEndShift, useCurrentShift } from "@/hooks/queries/useShifts";
import { useQueueEntries } from "@/hooks/queries/useQueue";

export default function ActiveShiftPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const { shift, endShift: clearShift } = useShiftStore();
  const endShiftMutation = useEndShift();
  const [showConfirm, setShowConfirm] = useState(false);

  const { data: shiftData, isLoading: shiftLoading, isError: shiftUnavailable } = useCurrentShift(user?.id ?? "");
  const { data: queueData, isError: queueUnavailable } = useQueueEntries({ status: "IN_PROGRESS" });

  const currentShift = shiftData?.data ?? null;
  const activeEncounters = queueData?.data ?? [];

  function handleEndShift() {
    if (!shift) return;
    endShiftMutation.mutate(
      { id: shift.id },
      {
        onSuccess: () => {
          clearShift();
          router.push("/shift");
        },
      },
    );
  }

  function formatDuration(startedAt: string): string {
    const diff = Date.now() - new Date(startedAt).getTime();
    const hrs = Math.floor(diff / 3600000);
    const mins = Math.floor((diff % 3600000) / 60000);
    return `${hrs}h ${mins}m`;
  }

  if (!shift) {
    return (
      <AppLayout>
        <PageShell title="Active Shift" subtitle="No active shift">
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <Clock className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No active shift. Start a shift to begin.</p>
            <button
              onClick={() => router.push("/shift")}
              className="mt-4 px-4 py-2 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-hover"
            >
              Start Shift
            </button>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title="Active Shift" subtitle="Your current shift details">
        <FacilityWorkClusterRibbon shiftExpected activeEncounterCount={activeEncounters.length} />
        <div className="max-w-2xl space-y-6">
          {/* Shift Info */}
          <div className="bg-card rounded-lg border border-border p-5">
            <h3 className="font-medium text-foreground mb-4">Shift Information</h3>
            {shiftLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading shift details...
              </div>
            ) : (
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-muted-foreground">Start Time</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {new Date(shift.startedAt).toLocaleString()}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Duration</dt>
                  <dd className="font-medium text-foreground mt-0.5 flex items-center gap-1">
                    <Clock className="w-4 h-4 text-impilo-400" />
                    {formatDuration(shift.startedAt)}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Workspace</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {currentShift?.attributes.workspaceId || shift.workspaceId}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Facility</dt>
                  <dd className="font-medium text-foreground mt-0.5">
                    {currentShift?.attributes.facilityId || shift.facilityId}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Status</dt>
                  <dd className="mt-0.5">
                    {/* The green ACTIVE pill asserts that TUSO holds this shift open. The local
                        shift store is client state and proves nothing about the server, so when
                        the shift read fails the status is unknown, not ACTIVE. */}
                    {shiftUnavailable ? (
                      <span
                        className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-yellow-100 text-yellow-800"
                        title="The shift service could not be reached. This shift may not be registered upstream."
                      >
                        STATUS UNAVAILABLE
                      </span>
                    ) : (
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-green-100 text-green-700">
                        {currentShift?.attributes.status || "ACTIVE"}
                      </span>
                    )}
                  </dd>
                </div>
              </dl>
            )}
          </div>

          {/* Activity Summary */}
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-card rounded-lg border border-border p-5 text-center">
              <Users className="w-8 h-8 text-impilo-400 mx-auto mb-2" />
              <p className="text-2xl font-bold text-foreground">{queueUnavailable ? "—" : activeEncounters.length}</p>
              <p className="text-xs text-muted-foreground mt-1">
                {queueUnavailable ? "Encounters in progress — unknown" : "Encounters In Progress"}
              </p>
            </div>
            <div className="bg-card rounded-lg border border-border p-5 text-center">
              <Activity className="w-8 h-8 text-green-500 mx-auto mb-2" />
              <p className="text-2xl font-bold text-foreground">{formatDuration(shift.startedAt)}</p>
              <p className="text-xs text-muted-foreground mt-1">Shift Duration</p>
            </div>
          </div>

          {/* End Shift */}
          {showConfirm ? (
            <div className="bg-warning-soft rounded-lg border border-warning/35 p-5">
              <div className="flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-warning-foreground">End this shift?</p>
                  {/* "This will end your current shift" is the all-clear a clinician hands over
                      on. If the in-progress queue could not be read, we cannot give it. */}
                  <p className="text-xs text-warning-foreground mt-1">
                    {queueUnavailable
                      ? "The in-progress encounter list could not be read, so this shift cannot be confirmed clear. Encounters may still be open — check before handing over."
                      : activeEncounters.length > 0
                        ? `You have ${activeEncounters.length} encounter(s) in progress. Consider completing or handing over before ending.`
                        : "This will end your current shift."}
                  </p>
                  <div className="flex gap-2 mt-3">
                    <button
                      onClick={handleEndShift}
                      disabled={endShiftMutation.isPending}
                      className="px-4 py-1.5 bg-red-600 text-white rounded text-xs font-medium hover:bg-red-700 disabled:opacity-50"
                    >
                      {endShiftMutation.isPending ? "Ending..." : "Yes, End Shift"}
                    </button>
                    <button
                      onClick={() => setShowConfirm(false)}
                      className="px-4 py-1.5 bg-card text-foreground border border-border rounded text-xs font-medium hover:bg-background"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <div className="flex justify-end">
              <button
                onClick={() => setShowConfirm(true)}
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700"
              >
                <LogOut className="w-4 h-4" />
                End Shift
              </button>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
