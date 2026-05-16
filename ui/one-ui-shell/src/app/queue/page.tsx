"use client";

/**
 * Patient Queue — Queue dashboard with entries table.
 * Route: /queue | pageTitle: "Patient Queue"
 */

import { useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  Users,
  Loader2,
  UserPlus,
  Clock,
  AlertTriangle,
  XCircle,
  ArrowRightLeft,
  PlayCircle,
  ClipboardCheck,
  PauseCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQueueEntries, useCallPatient } from "@/hooks/queries/useQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import { COORDINATION_COPY } from "@/lib/consult-workflows";

const PRIORITY_LABELS: Record<string, { label: string; className: string }> = {
  EMERGENCY: { label: "Emergency", className: "bg-red-100 text-red-700" },
  URGENT: { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  NORMAL: { label: "Normal", className: "bg-impilo-100 text-impilo-600" },
  LOW: { label: "Low", className: "bg-gray-100 text-gray-600" },
  // Numeric fallbacks for backward compat
  "1": { label: "Emergency", className: "bg-red-100 text-red-700" },
  "2": { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  "3": { label: "Normal", className: "bg-impilo-100 text-impilo-600" },
  "4": { label: "Low", className: "bg-gray-100 text-gray-600" },
};

const TRIAGE_CATEGORY_STYLES: Record<string, string> = {
  RED: "bg-red-500 text-white",
  ORANGE: "bg-orange-500 text-white",
  YELLOW: "bg-yellow-400 text-black",
  GREEN: "bg-green-500 text-white",
  BLUE: "bg-impilo-500 text-white",
};

const STATUS_STYLES: Record<string, string> = {
  WAITING: "bg-yellow-100 text-yellow-700",
  CALLED: "bg-impilo-100 text-impilo-600",
  IN_PROGRESS: "bg-green-100 text-green-700",
  IN_SERVICE: "bg-green-100 text-green-700",
  SEEN: "bg-green-100 text-green-700",
  COMPLETED: "bg-gray-100 text-gray-600",
  PAUSED: "bg-amber-100 text-amber-700",
  NO_SHOW: "bg-red-100 text-red-600",
  TRANSFERRED: "bg-purple-100 text-purple-600",
  CANCELLED: "bg-gray-100 text-gray-400",
};

export default function QueuePage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const { data, isLoading } = useQueueEntries({ facilityId: facility?.id });
  const callPatient = useCallPatient();
  const { isQueueManager } = useRoleGroup();
  const queryClient = useQueryClient();

  // Queue stats for supervisor summary
  const { data: statsData } = useQuery<{ data: Record<string, number> }>({
    queryKey: ["queue-stats", facility?.id],
    queryFn: () => apiClient.post("/internal/v1/queue/entries/stats", { facilityId: facility?.id }),
    enabled: !!facility?.id,
  });
  const stats = statsData?.data ?? {};

  const markNoShow = useMutation({
    mutationFn: (entryId: string) => apiClient.post(`/internal/v1/queue/entries/${entryId}/no-show`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["queue-entries"] }); queryClient.invalidateQueries({ queryKey: ["queue-stats"] }); },
  });
  const pauseEntry = useMutation({
    mutationFn: (entryId: string) => apiClient.post(`/internal/v1/queue/entries/${entryId}/pause`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["queue-entries"] }); queryClient.invalidateQueries({ queryKey: ["queue-stats"] }); },
  });
  const resumeEntry = useMutation({
    mutationFn: (entryId: string) => apiClient.post(`/internal/v1/queue/entries/${entryId}/resume`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ["queue-entries"] }); queryClient.invalidateQueries({ queryKey: ["queue-stats"] }); },
  });

  const entries = useMemo(() => data?.data ?? [], [data?.data]);
  const waitingEntries = useMemo(
    () => entries.filter((entry) => entry.attributes.status === "WAITING"),
    [entries],
  );
  const inHandoffEntries = useMemo(
    () =>
      entries.filter((entry) =>
        ["CALLED", "IN_PROGRESS", "IN_SERVICE", "SEEN"].includes(entry.attributes.status),
      ),
    [entries],
  );
  const exceptionEntries = useMemo(
    () =>
      entries.filter((entry) =>
        ["PAUSED", "NO_SHOW", "TRANSFERRED", "CANCELLED"].includes(entry.attributes.status),
      ),
    [entries],
  );

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

  function renderLaneAction(entry: (typeof entries)[number]) {
    if (entry.attributes.status === "WAITING") {
      return (
        <button
          onClick={() => handleCall(entry.id, entry.attributes.patientId)}
          disabled={callPatient.isPending}
          className="rounded-xl bg-impilo-500 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-impilo-600 disabled:opacity-50"
        >
          {COORDINATION_COPY.startEncounterHandoff}
        </button>
      );
    }

    if (["CALLED", "IN_PROGRESS", "IN_SERVICE", "SEEN"].includes(entry.attributes.status)) {
      return (
        <Link
          href={`/ehr/${entry.attributes.patientId}?entry=queue`}
          className="rounded-xl bg-green-600 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-green-700"
        >
          {COORDINATION_COPY.openEncounterHandoff}
        </Link>
      );
    }

    if (entry.attributes.status === "PAUSED") {
      return (
        <button
          onClick={() => resumeEntry.mutate(entry.id)}
          disabled={resumeEntry.isPending}
          className="rounded-xl bg-green-600 px-3 py-2 text-xs font-medium text-white transition-colors hover:bg-green-700 disabled:opacity-50"
        >
          {COORDINATION_COPY.resumeEncounterHandoff}
        </button>
      );
    }

    return null;
  }

  return (
    <AppLayout>
      <PageShell
        title="Patient Queue"
        subtitle={facility ? `${facility.name}` : "Current queue entries"}
      >
        {facility && (
          <div className="mb-5 grid gap-3 lg:grid-cols-[minmax(0,1.5fr)_repeat(4,minmax(0,1fr))]">
            <div className="rounded-3xl border border-slate-200 bg-[linear-gradient(135deg,#fffaf0_0%,#ffffff_55%,#eff6ff_100%)] p-5 shadow-sm">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
                  <ArrowRightLeft className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-slate-900">{COORDINATION_COPY.coordinationWorkboard}</p>
                  <p className="mt-1 text-sm text-slate-600">
                    The queue now mirrors the same workflow language as referrals and chart handoff:
                    call the next patient, move the handoff into chart, then track pauses and exceptions
                    without losing the thread.
                  </p>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Link
                      href="/queue/incoming-referrals"
                      className="inline-flex items-center gap-1.5 rounded-xl bg-slate-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-slate-800"
                    >
                      <ArrowRightLeft className="h-4 w-4" />
                      Incoming Referrals
                    </Link>
                    {isQueueManager && (
                      <Link
                        href="/queue/walk-in"
                        className="inline-flex items-center gap-1.5 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition-colors hover:bg-slate-50"
                      >
                        <UserPlus className="h-4 w-4" />
                        Walk-in Registration
                      </Link>
                    )}
                  </div>
                </div>
              </div>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Ready now</p>
              <p className="mt-2 text-2xl font-semibold text-amber-700">{stats.waiting ?? waitingEntries.length}</p>
              <p className="mt-1 text-xs text-slate-500">Patients ready to be called from the waiting flow.</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">In handoff</p>
              <p className="mt-2 text-2xl font-semibold text-impilo-600">{(stats.called ?? 0) + (stats.inService ?? 0)}</p>
              <p className="mt-1 text-xs text-slate-500">Patients already called or actively in service.</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Completed</p>
              <p className="mt-2 text-2xl font-semibold text-green-700">{stats.completed ?? 0}</p>
              <p className="mt-1 text-xs text-slate-500">Finished visits kept visible for operational pacing.</p>
            </div>
            <div className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-xs font-medium uppercase tracking-[0.18em] text-slate-500">Avg wait</p>
              <p className="mt-2 text-2xl font-semibold text-purple-700">
                {stats.avgWaitSeconds ? Math.round((stats.avgWaitSeconds) / 60) : 0}m
              </p>
              <p className="mt-1 text-xs text-slate-500">Current pacing signal across the live queue board.</p>
            </div>
          </div>
        )}

        <div className="mb-4 flex items-center gap-2 text-sm text-gray-500">
          <Users className="w-4 h-4" />
          <span>{entries.length} patient{entries.length !== 1 ? "s" : ""} in queue</span>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading queue...</span>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No patients in queue</p>
            {isQueueManager && (
            <Link
              href="/queue/walk-in"
              className="mt-3 inline-block text-sm text-impilo-500 hover:text-impilo-700"
            >
              Register a walk-in patient
            </Link>
            )}
          </div>
        ) : (
          <div className="space-y-5">
            <div className="grid gap-4 xl:grid-cols-3">
              {[
                {
                  key: "ready",
                  title: COORDINATION_COPY.needsActionNow,
                  description: "Patients currently waiting for first contact from the queue team.",
                  icon: PlayCircle,
                  iconClassName: "bg-amber-100 text-amber-700",
                  entries: waitingEntries,
                },
                {
                  key: "handoff",
                  title: COORDINATION_COPY.trackingInProgress,
                  description: "Patients already called or actively moving through chart and service handoff.",
                  icon: ClipboardCheck,
                  iconClassName: "bg-impilo-100 text-impilo-600",
                  entries: inHandoffEntries,
                },
                {
                  key: "exceptions",
                  title: COORDINATION_COPY.needsAttention,
                  description: "Paused visits, no-shows, and other exceptions that need queue communication.",
                  icon: PauseCircle,
                  iconClassName: "bg-rose-100 text-rose-700",
                  entries: exceptionEntries,
                },
              ].map((lane) => (
                <div key={lane.key} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
                  <div className="flex items-start gap-3">
                    <div className={`flex h-11 w-11 items-center justify-center rounded-2xl ${lane.iconClassName}`}>
                      <lane.icon className="h-5 w-5" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{lane.title}</p>
                      <p className="mt-1 text-sm text-slate-600">{lane.description}</p>
                    </div>
                  </div>

                  <div className="mt-4 space-y-3">
                    {lane.entries.length === 0 ? (
                      <div className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-sm text-slate-500">
                        No patients in this workflow lane right now.
                      </div>
                    ) : (
                      lane.entries.slice(0, 3).map((entry) => {
                        const priorityKey = String(entry.attributes.priority);
                        const priority = PRIORITY_LABELS[priorityKey] ?? {
                          label: priorityKey,
                          className: "bg-gray-100 text-gray-600",
                        };
                        const triageCat =
                          (entry.attributes as Record<string, unknown>).triage_category as string ??
                          (entry.attributes as Record<string, unknown>).triageCategory as string;
                        const patientName =
                          (entry.attributes as Record<string, unknown>).patientName as string ??
                          entry.attributes.patientId;

                        return (
                          <div key={entry.id} className="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                            <div className="flex flex-wrap items-start justify-between gap-2">
                              <div>
                                <p className="text-sm font-semibold text-slate-900">{patientName}</p>
                                <p className="mt-1 text-xs text-slate-500">
                                  Queued {new Date(entry.attributes.queuedAt).toLocaleTimeString()}
                                </p>
                              </div>
                              <div className="flex flex-wrap gap-2 text-xs">
                                {triageCat ? (
                                  <span className={`inline-flex h-6 w-6 items-center justify-center rounded-full font-bold ${
                                    TRIAGE_CATEGORY_STYLES[triageCat] ?? "bg-slate-200 text-slate-700"
                                  }`}>
                                    {triageCat.charAt(0)}
                                  </span>
                                ) : null}
                                <span className={`rounded-full px-2.5 py-1 font-medium ${priority.className}`}>
                                  {priority.label}
                                </span>
                                <span className={`rounded-full px-2.5 py-1 font-medium ${
                                  STATUS_STYLES[entry.attributes.status] ?? "bg-gray-100 text-gray-600"
                                }`}>
                                  {entry.attributes.status}
                                </span>
                              </div>
                            </div>

                            <div className="mt-3 flex flex-wrap gap-2">
                              {renderLaneAction(entry)}
                              {(entry.attributes.status === "WAITING" || entry.attributes.status === "CALLED") && (
                                <button
                                  onClick={() => markNoShow.mutate(entry.id)}
                                  disabled={markNoShow.isPending}
                                  className="rounded-xl border border-rose-200 bg-white px-3 py-2 text-xs font-medium text-rose-600 transition-colors hover:bg-rose-50 disabled:opacity-50"
                                >
                                  Mark No-show
                                </button>
                              )}
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>
                </div>
              ))}
            </div>

            <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
              <div className="border-b border-slate-200 bg-slate-50/80 px-4 py-3">
                <p className="text-sm font-semibold text-slate-900">Full queue board</p>
                <p className="mt-1 text-sm text-slate-600">
                  Detailed operational view for auditing status, timing, and direct queue actions
                  across every patient in the facility flow.
                </p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Triage</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Priority</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Arrival</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {entries.map((entry) => {
                  const priorityKey = String(entry.attributes.priority);
                  const priority = PRIORITY_LABELS[priorityKey] ?? {
                    label: priorityKey,
                    className: "bg-gray-100 text-gray-600",
                  };
                  const statusStyle =
                    STATUS_STYLES[entry.attributes.status] ?? "bg-gray-100 text-gray-600";
                  const triageCat = (entry.attributes as Record<string, unknown>).triage_category as string
                    ?? (entry.attributes as Record<string, unknown>).triageCategory as string;
                  const triageStyle = triageCat ? TRIAGE_CATEGORY_STYLES[triageCat] ?? "" : "";

                  return (
                    <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {(entry.attributes as Record<string, unknown>).patientName as string ??
                          entry.attributes.patientId}
                      </td>
                      <td className="px-4 py-3">
                        {triageCat ? (
                          <span className={`inline-block w-6 h-6 rounded-full text-center text-xs font-bold leading-6 ${triageStyle}`}>
                            {triageCat.charAt(0)}
                          </span>
                        ) : (
                          <span className="text-xs text-gray-400">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center gap-1 px-2 py-0.5 text-xs rounded-full ${priority.className}`}
                        >
                          {entry.attributes.priority <= 2 && (
                            <AlertTriangle className="w-3 h-3" />
                          )}
                          {priority.label}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-block px-2 py-0.5 text-xs rounded-full ${statusStyle}`}
                        >
                          {entry.attributes.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-500">
                        <div className="flex items-center gap-1">
                          <Clock className="w-3 h-3" />
                          {new Date(entry.attributes.queuedAt).toLocaleTimeString()}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {entry.attributes.status === "WAITING" && (
                          <button
                            onClick={() =>
                              handleCall(entry.id, entry.attributes.patientId)
                            }
                            disabled={callPatient.isPending}
                            className="px-3 py-1.5 bg-impilo-500 text-white text-xs font-medium rounded-md hover:bg-impilo-600 disabled:opacity-50 transition-colors"
                          >
                            Call
                          </button>
                        )}
                        {entry.attributes.status === "CALLED" && (
                          <Link
                            href={`/ehr/${entry.attributes.patientId}?entry=queue`}
                            className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-md hover:bg-green-700 transition-colors inline-block"
                          >
                            Open Chart
                          </Link>
                        )}
                        {(entry.attributes.status === "WAITING" || entry.attributes.status === "CALLED") && (
                          <button
                            onClick={() => markNoShow.mutate(entry.id)}
                            disabled={markNoShow.isPending}
                            className="ml-1 px-2 py-1.5 text-red-600 hover:bg-red-50 text-xs font-medium rounded-md transition-colors"
                            title="Mark as no-show"
                          >
                            <XCircle className="w-3.5 h-3.5" />
                          </button>
                        )}
                        {(entry.attributes.status === "CALLED" || entry.attributes.status === "IN_SERVICE" || entry.attributes.status === "SEEN") && (
                          <button
                            onClick={() => pauseEntry.mutate(entry.id)}
                            disabled={pauseEntry.isPending}
                            className="ml-1 px-2 py-1.5 text-amber-600 hover:bg-amber-50 text-xs font-medium rounded-md transition-colors"
                            title="Pause service"
                          >
                            Pause
                          </button>
                        )}
                        {entry.attributes.status === "PAUSED" && (
                          <button
                            onClick={() => resumeEntry.mutate(entry.id)}
                            disabled={resumeEntry.isPending}
                            className="px-3 py-1.5 bg-green-600 text-white text-xs font-medium rounded-md hover:bg-green-700 transition-colors"
                          >
                            Resume
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
