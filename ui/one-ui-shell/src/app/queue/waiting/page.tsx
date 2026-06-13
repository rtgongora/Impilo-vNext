"use client";

/**
 * Waiting List — Patients waiting to be seen, ordered by priority then arrival.
 * Route: /queue/waiting
 */

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, ArrowRightLeft, ClipboardCheck, Clock, Loader2, Stethoscope, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { useQueueEntries, useCallPatient } from "@/hooks/queries/useQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { COORDINATION_COPY } from "@/lib/consult-workflows";
import {
  formatQueueDateTime,
  formatQueueWaitTime,
  getQueuePatientId,
  getQueuePatientName,
  getQueuePriority,
  getQueuePriorityMeta,
  getQueueQueuedAt,
  getQueueTriageCategory,
  QUEUE_STATUS_STYLES,
  QUEUE_TRIAGE_STYLES,
} from "@/lib/queue-workflows";

export default function WaitingListPage() {
  const router = useRouter();
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading, error } = useQueueEntries({ facilityId: facility?.id, status: "WAITING" });
  const callPatient = useCallPatient();

  const entries = useMemo(() => {
    return [...(data?.data ?? [])].sort((left, right) => {
      const leftPriority = getPriorityRank(getQueuePriority(left));
      const rightPriority = getPriorityRank(getQueuePriority(right));
      if (leftPriority !== rightPriority) {
        return leftPriority - rightPriority;
      }

      return new Date(getQueueQueuedAt(left)).getTime() - new Date(getQueueQueuedAt(right)).getTime();
    });
  }, [data?.data]);
  const needsTriageCount = entries.filter((entry) => !getQueueTriageCategory(entry)).length;
  const handoffReadyCount = entries.length - needsTriageCount;

  function handleCall(entryId: string, patientId: string) {
    callPatient.mutate(
      { id: entryId },
      {
        onSuccess: () => {
          router.push(`/ehr/${patientId}?entry=queue`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Waiting List" subtitle={facility ? `${facility.name}` : "Patients waiting to be seen"}>
        <div className="space-y-6">
          <QueueWorkspaceHeader
            badge="Waiting room"
            badgeIcon={Users}
            title="Watch pacing, spot missing triage, and hand the next patient into chart"
            description="The waiting flow now makes the next action explicit: complete triage if needed, then start encounter handoff without losing facility context."
            facilityName={facility?.name}
            actions={[
              { href: "/queue", label: "Queue Workboard", icon: ArrowRightLeft },
              { href: "/queue/triage", label: "Open Triage", icon: Stethoscope, tone: "secondary" },
              { href: "/queue/scheduled", label: "Scheduled Visits", icon: ClipboardCheck, tone: "secondary" },
            ]}
            metrics={[
              {
                label: "Waiting now",
                value: String(entries.length),
                detail: "Patients currently parked in the facility waiting flow.",
              },
              {
                label: "Needs triage",
                value: String(needsTriageCount),
                detail: "Rows that should be assessed before encounter handoff starts.",
              },
              {
                label: "Handoff ready",
                value: String(handoffReadyCount),
                detail: "Triaged patients who can move directly into chart.",
              },
            ]}
          />

          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">Loading waiting list...</span>
            </div>
          ) : error ? (
            <div className="rounded-3xl border border-danger/28 bg-danger-soft p-6 text-center">
              <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
              <p className="text-sm text-danger">Failed to load the waiting list.</p>
            </div>
          ) : entries.length === 0 ? (
            <div className="rounded-3xl border border-border bg-card p-12 text-center shadow-sm">
              <Users className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">No patients are currently waiting at this facility.</p>
            </div>
          ) : (
            <div className="overflow-hidden rounded-3xl border border-border bg-card shadow-sm">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-background">
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Patient</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Priority</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Triage</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Wait Time</th>
                    <th className="text-left px-4 py-3 font-medium text-muted-foreground">Queued At</th>
                    <th className="text-right px-4 py-3 font-medium text-muted-foreground">Action</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => {
                    const priority = getQueuePriorityMeta(getQueuePriority(entry));
                    const status = QUEUE_STATUS_STYLES.WAITING;
                    const triageCategory = getQueueTriageCategory(entry);
                    const patientId = getQueuePatientId(entry);
                    const queuedAt = getQueueQueuedAt(entry);

                    return (
                      <tr key={entry.id} className="border-b last:border-b-0 hover:bg-background">
                        <td className="px-4 py-3 font-medium text-foreground">
                          {getQueuePatientName(entry)}
                        </td>
                        <td className="px-4 py-3">
                          <span className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${priority.className}`}>
                            {priority.label}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          {triageCategory ? (
                            <div className="flex items-center gap-2">
                              <span
                                className={`inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${
                                  QUEUE_TRIAGE_STYLES[triageCategory] ?? "bg-border text-foreground"
                                }`}
                              >
                                {triageCategory.charAt(0)}
                              </span>
                              <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${status}`}>
                                {COORDINATION_COPY.startEncounterHandoff}
                              </span>
                            </div>
                          ) : (
                            <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-warning-foreground">
                              Triage first
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          <span className="inline-flex items-center gap-1">
                            <Clock className="h-3.5 w-3.5" />
                            {formatQueueWaitTime(queuedAt)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {formatQueueDateTime(queuedAt, { hour: "2-digit", minute: "2-digit" })}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {triageCategory && patientId ? (
                            <button
                              onClick={() => handleCall(entry.id, patientId)}
                              disabled={callPatient.isPending}
                              className="rounded-xl bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                            >
                              {COORDINATION_COPY.startEncounterHandoff}
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => router.push("/queue/triage")}
                              className="rounded-xl border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
                            >
                              Open Triage
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function getPriorityRank(priority: unknown) {
  const value = String(priority ?? "");
  switch (value) {
    case "1":
    case "EMERGENCY":
      return 1;
    case "2":
    case "URGENT":
      return 2;
    case "3":
    case "NORMAL":
      return 3;
    case "4":
    case "LOW":
      return 4;
    case "5":
      return 5;
    default:
      return 99;
  }
}
