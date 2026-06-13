"use client";

/**
 * Start Shift — Begin a new clinical shift.
 * Route: /shift | pageTitle: "Start Shift"
 */

import { useRouter } from "next/navigation";
import { Clock, Building2, LayoutGrid, Loader2, AlertCircle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useStartShift } from "@/hooks/queries/useShifts";

export default function ShiftPage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const startShiftStore = useShiftStore((s) => s.startShift);
  const startShiftMutation = useStartShift();

  function handleStartShift() {
    if (!facility || !workspace) return;

    startShiftMutation.mutate(
      {
        facilityId: facility.id,
        workspaceId: workspace.id,
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
        title="Confirm Session"
        subtitle="You are about to start a work session"
      >
        <div className="max-w-lg mx-auto">
          <div className="bg-card rounded-lg border border-border p-6 space-y-4">
            <h3 className="font-medium text-foreground">Session Details</h3>
            <p className="text-sm text-muted-foreground">
              Review your work session details below and confirm to begin.
            </p>

            <div className="space-y-3">
              <div className="flex items-center gap-3 p-3 bg-background rounded-lg">
                <Building2 className="w-5 h-5 text-primary shrink-0" />
                <div>
                  <p className="text-xs text-muted-foreground">Facility</p>
                  <p className="text-sm font-medium text-foreground">
                    {facility?.name ?? "Not selected"}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-background rounded-lg">
                <LayoutGrid className="w-5 h-5 text-primary shrink-0" />
                <div>
                  <p className="text-xs text-muted-foreground">Workspace</p>
                  <p className="text-sm font-medium text-foreground">
                    {workspace?.name ?? "Not selected"}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-3 p-3 bg-background rounded-lg">
                <Clock className="w-5 h-5 text-primary shrink-0" />
                <div>
                  <p className="text-xs text-muted-foreground">Session Start</p>
                  <p className="text-sm font-medium text-foreground">
                    {new Date().toLocaleString()}
                  </p>
                </div>
              </div>
            </div>

            {startShiftMutation.isError && (
              <div className="flex items-center gap-2 p-3 bg-danger-soft border border-danger/28 rounded-lg">
                <AlertCircle className="w-4 h-4 text-red-500 shrink-0" />
                <p className="text-sm text-danger">
                  Failed to start shift. Please try again.
                </p>
              </div>
            )}

            <button
              onClick={handleStartShift}
              disabled={startShiftMutation.isPending || !facility || !workspace}
              className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
            >
              {startShiftMutation.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Starting Session...
                </>
              ) : (
                <>
                  <Clock className="w-4 h-4" />
                  Start Session
                </>
              )}
            </button>
          </div>
        </div>
      </PageShell>

      <NompiloHint
        message="Review your session details before starting. Once you start, your actions will be audited under this facility, workspace, and role."
      />
    </AppLayout>
  );
}
