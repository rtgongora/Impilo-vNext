"use client";

/**
 * Start Shift — Begin a new clinical shift.
 * Route: /shift | pageTitle: "Start Shift"
 */

import { useRouter } from "next/navigation";
import { Clock, Building2, LayoutGrid, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useStartShift } from "@/hooks/queries/useShifts";

export default function ShiftPage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const startShiftStore = useShiftStore((s) => s.startShift);
  const user = useAuthStore((s) => s.user);
  const startShiftMutation = useStartShift();

  function handleStartShift() {
    if (!facility || !workspace || !user) return;

    startShiftMutation.mutate(
      {
        facility_id: facility.id,
        workspace_id: workspace.id,
        user_id: user.id,
      },
      {
        onSuccess: (res) => {
          startShiftStore({
            id: res.data.id,
            startedAt: res.data.attributes.startedAt,
            workspaceId: res.data.attributes.workspaceId,
            facilityId: res.data.attributes.facilityId,
          });
          router.push("/queue");
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Start Shift"
        subtitle="Review your session details and begin your shift"
      >
        <div className="max-w-lg mx-auto">
          <div className="bg-white rounded-lg border border-gray-200 p-6 space-y-4">
            <h3 className="font-medium text-gray-900">Session Details</h3>

            <div className="space-y-3">
              <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                <Building2 className="w-5 h-5 text-gray-400 shrink-0" />
                <div>
                  <p className="text-xs text-gray-500">Facility</p>
                  <p className="text-sm font-medium text-gray-900">
                    {facility?.name ?? "Not selected"}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                <LayoutGrid className="w-5 h-5 text-gray-400 shrink-0" />
                <div>
                  <p className="text-xs text-gray-500">Workspace</p>
                  <p className="text-sm font-medium text-gray-900">
                    {workspace?.name ?? "Not selected"}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-gray-50 rounded-lg">
                <Clock className="w-5 h-5 text-gray-400 shrink-0" />
                <div>
                  <p className="text-xs text-gray-500">Shift Start</p>
                  <p className="text-sm font-medium text-gray-900">
                    {new Date().toLocaleString()}
                  </p>
                </div>
              </div>
            </div>

            {startShiftMutation.isError && (
              <div className="flex items-center gap-2 p-3 bg-red-50 border border-red-200 rounded-lg">
                <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
                <p className="text-sm text-red-700">
                  Failed to start shift. Please try again.
                </p>
              </div>
            )}

            <button
              onClick={handleStartShift}
              disabled={startShiftMutation.isPending || !facility || !workspace}
              className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              {startShiftMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Starting Shift...
                </>
              ) : (
                <>
                  <Clock className="w-4 h-4" />
                  Start Shift
                </>
              )}
            </button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
