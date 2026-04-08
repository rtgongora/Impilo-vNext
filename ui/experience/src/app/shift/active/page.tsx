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

  const { data: shiftData, isLoading: shiftLoading } = useCurrentShift(user?.id ?? "");
  const { data: queueData } = useQueueEntries({ status: "IN_PROGRESS" });

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
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Clock className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No active shift. Start a shift to begin.</p>
            <button
              onClick={() => router.push("/shift")}
              className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700"
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
          <div className="bg-white rounded-lg border border-gray-200 p-5">
            <h3 className="font-medium text-gray-900 mb-4">Shift Information</h3>
            {shiftLoading ? (
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading shift details...
              </div>
            ) : (
              <dl className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <dt className="text-gray-500">Start Time</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {new Date(shift.startedAt).toLocaleString()}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Duration</dt>
                  <dd className="font-medium text-gray-900 mt-0.5 flex items-center gap-1">
                    <Clock className="w-4 h-4 text-blue-500" />
                    {formatDuration(shift.startedAt)}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Workspace</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {currentShift?.attributes.workspaceId || shift.workspaceId}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Facility</dt>
                  <dd className="font-medium text-gray-900 mt-0.5">
                    {currentShift?.attributes.facilityId || shift.facilityId}
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Status</dt>
                  <dd className="mt-0.5">
                    <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-green-100 text-green-700">
                      {currentShift?.attributes.status || "ACTIVE"}
                    </span>
                  </dd>
                </div>
              </dl>
            )}
          </div>

          {/* Activity Summary */}
          <div className="grid grid-cols-2 gap-4">
            <div className="bg-white rounded-lg border border-gray-200 p-5 text-center">
              <Users className="w-8 h-8 text-blue-500 mx-auto mb-2" />
              <p className="text-2xl font-bold text-gray-900">{activeEncounters.length}</p>
              <p className="text-xs text-gray-500 mt-1">Encounters In Progress</p>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-5 text-center">
              <Activity className="w-8 h-8 text-green-500 mx-auto mb-2" />
              <p className="text-2xl font-bold text-gray-900">{formatDuration(shift.startedAt)}</p>
              <p className="text-xs text-gray-500 mt-1">Shift Duration</p>
            </div>
          </div>

          {/* End Shift */}
          {showConfirm ? (
            <div className="bg-amber-50 rounded-lg border border-amber-200 p-5">
              <div className="flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm font-medium text-amber-900">End this shift?</p>
                  <p className="text-xs text-amber-700 mt-1">
                    {activeEncounters.length > 0
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
                      className="px-4 py-1.5 bg-white text-gray-700 border border-gray-300 rounded text-xs font-medium hover:bg-gray-50"
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
