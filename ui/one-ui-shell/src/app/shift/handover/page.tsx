"use client";

/**
 * Shift Handover — Review pending items and complete handover.
 * Route: /shift/handover
 */

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Loader2,
  AlertTriangle,
  ClipboardCheck,
  FileText,
  Users,
  ListChecks,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { PageShell } from "@/components/PageShell";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useQueueEntries } from "@/hooks/queries/useQueue";
import { useEndShift } from "@/hooks/queries/useShifts";
import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function ShiftHandoverPage() {
  const router = useRouter();
  const { shift, endShift: clearShift } = useShiftStore();
  const endShiftMutation = useEndShift();
  const [handoverNotes, setHandoverNotes] = useState("");

  const { data: queueData, isLoading: queueLoading } = useQueueEntries({ status: "IN_PROGRESS" });
  const activeEntries = queueData?.data ?? [];

  const submitHandover = useMutation({
    mutationFn: (payload: { shiftId: string; notes: string }) =>
      apiClient.post<ApiResponse<unknown>>("/internal/v1/shifts/handover", payload),
    onSuccess: () => {
      if (shift) {
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
    },
  });

  function handleCompleteHandover() {
    if (!shift) return;
    submitHandover.mutate({ shiftId: shift.id, notes: handoverNotes });
  }

  if (!shift) {
    return (
      <AppLayout>
        <PageShell title="Shift Handover" subtitle="No active shift">
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <ClipboardCheck className="w-10 h-10 text-muted-foreground mx-auto mb-3" />
            <p className="text-muted-foreground text-sm">No active shift to hand over</p>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  const isPending = submitHandover.isPending || endShiftMutation.isPending;

  return (
    <AppLayout>
      <PageShell title="Shift Handover" subtitle="Review pending items and complete your handover">
        <FacilityWorkClusterRibbon shiftExpected activeEncounterCount={activeEntries.length} />
        <div className="max-w-2xl space-y-6">
          {/* Pending Items */}
          <div className="bg-card rounded-lg border border-border p-5">
            <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
              <ListChecks className="w-4 h-4" /> Pending Items
            </h3>
            {queueLoading ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="w-4 h-4 animate-spin" /> Loading...
              </div>
            ) : activeEntries.length === 0 ? (
              <p className="text-sm text-muted-foreground">No pending items to hand over.</p>
            ) : (
              <div className="space-y-2">
                {activeEntries.map((entry) => {
                  const attrs = entry.attributes as Record<string, unknown>;
                  return (
                    <div
                      key={entry.id}
                      className="flex items-center justify-between p-3 bg-background rounded-lg"
                    >
                      <div className="flex items-center gap-3">
                        <Users className="w-4 h-4 text-muted-foreground" />
                        <div>
                          <p className="text-sm font-medium text-foreground">
                            {(attrs.patientName as string) || entry.attributes.patientId}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            Status: {entry.attributes.status}
                          </p>
                        </div>
                      </div>
                      <span className="inline-block px-2 py-0.5 text-xs rounded-full font-medium bg-amber-100 text-warning-foreground">
                        In Progress
                      </span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          {/* Handover Notes */}
          <div className="bg-card rounded-lg border border-border p-5">
            <h3 className="font-medium text-foreground mb-3 flex items-center gap-2">
              <FileText className="w-4 h-4" /> Handover Notes
            </h3>
            <textarea
              value={handoverNotes}
              onChange={(e) => setHandoverNotes(e.target.value)}
              rows={5}
              placeholder="Describe any important information for the incoming shift..."
              className="w-full border border-border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/40 resize-none"
            />
          </div>

          {/* Warnings */}
          {activeEntries.length > 0 && (
            <div className="flex items-start gap-3 p-4 bg-warning-soft rounded-lg border border-warning/35">
              <AlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
              <p className="text-sm text-warning-foreground">
                You have {activeEntries.length} active encounter(s). These will be transferred to the
                incoming provider upon handover completion.
              </p>
            </div>
          )}

          {/* Submit */}
          {(submitHandover.isError || endShiftMutation.isError) && (
            <div className="p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger">
              Failed to complete handover. Please try again.
            </div>
          )}

          <div className="flex justify-end">
            <button
              onClick={handleCompleteHandover}
              disabled={isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary-hover disabled:opacity-50"
            >
              {isPending ? (
                <Loader2 className="w-4 h-4 animate-spin" />
              ) : (
                <ClipboardCheck className="w-4 h-4" />
              )}
              Complete Handover
            </button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
