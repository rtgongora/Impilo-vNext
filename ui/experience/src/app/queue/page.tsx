"use client";

/**
 * Patient Queue — Queue dashboard with entries table.
 * Route: /queue | pageTitle: "Patient Queue"
 */

import { useRouter } from "next/navigation";
import Link from "next/link";
import { Users, Loader2, UserPlus, Clock, AlertTriangle, XCircle, ArrowRightLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQueueEntries, useCallPatient } from "@/hooks/queries/useQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const PRIORITY_LABELS: Record<string, { label: string; className: string }> = {
  EMERGENCY: { label: "Emergency", className: "bg-red-100 text-red-700" },
  URGENT: { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  NORMAL: { label: "Normal", className: "bg-blue-100 text-blue-700" },
  LOW: { label: "Low", className: "bg-gray-100 text-gray-600" },
  // Numeric fallbacks for backward compat
  "1": { label: "Emergency", className: "bg-red-100 text-red-700" },
  "2": { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  "3": { label: "Normal", className: "bg-blue-100 text-blue-700" },
  "4": { label: "Low", className: "bg-gray-100 text-gray-600" },
};

const TRIAGE_CATEGORY_STYLES: Record<string, string> = {
  RED: "bg-red-500 text-white",
  ORANGE: "bg-orange-500 text-white",
  YELLOW: "bg-yellow-400 text-black",
  GREEN: "bg-green-500 text-white",
  BLUE: "bg-blue-500 text-white",
};

const STATUS_STYLES: Record<string, string> = {
  WAITING: "bg-yellow-100 text-yellow-700",
  CALLED: "bg-blue-100 text-blue-700",
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

  const entries = data?.data ?? [];

  function handleCall(entryId: string, patientId: string) {
    callPatient.mutate(
      { id: entryId },
      {
        onSuccess: () => {
          router.push(`/ehr/${patientId}`);
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Patient Queue"
        subtitle={facility ? `${facility.name}` : "Current queue entries"}
      >
        {/* Supervisor Stats Bar */}
        {facility && (
          <div className="mb-4 grid grid-cols-5 gap-2">
            <div className="bg-amber-50 rounded-lg border border-amber-200 p-2.5 text-center">
              <p className="text-lg font-bold text-amber-700">{stats.waiting ?? 0}</p>
              <p className="text-[10px] text-amber-600 uppercase">Waiting</p>
            </div>
            <div className="bg-blue-50 rounded-lg border border-blue-200 p-2.5 text-center">
              <p className="text-lg font-bold text-blue-700">{stats.called ?? 0}</p>
              <p className="text-[10px] text-blue-600 uppercase">Called</p>
            </div>
            <div className="bg-green-50 rounded-lg border border-green-200 p-2.5 text-center">
              <p className="text-lg font-bold text-green-700">{stats.inService ?? 0}</p>
              <p className="text-[10px] text-green-600 uppercase">In Service</p>
            </div>
            <div className="bg-gray-50 rounded-lg border border-gray-200 p-2.5 text-center">
              <p className="text-lg font-bold text-gray-700">{stats.completed ?? 0}</p>
              <p className="text-[10px] text-gray-500 uppercase">Completed</p>
            </div>
            <div className="bg-purple-50 rounded-lg border border-purple-200 p-2.5 text-center">
              <p className="text-lg font-bold text-purple-700">{stats.avgWaitSeconds ? Math.round((stats.avgWaitSeconds) / 60) : 0}m</p>
              <p className="text-[10px] text-purple-600 uppercase">Avg Wait</p>
            </div>
          </div>
        )}

        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Users className="w-4 h-4" />
            <span>{entries.length} patient{entries.length !== 1 ? "s" : ""} in queue</span>
          </div>
          <div className="flex items-center gap-2">
            <Link
              href="/queue/incoming-referrals"
              className="inline-flex items-center gap-2 px-4 py-2 bg-purple-50 text-purple-700 text-sm font-medium rounded-lg hover:bg-purple-100 transition-colors"
            >
              Incoming Referrals
            </Link>
            {isQueueManager && (
            <Link
              href="/queue/walk-in"
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <UserPlus className="w-4 h-4" />
              Walk-in Registration
            </Link>
            )}
          </div>
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
              className="mt-3 inline-block text-sm text-blue-600 hover:text-blue-800"
            >
              Register a walk-in patient
            </Link>
            )}
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
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
                            className="px-3 py-1.5 bg-blue-600 text-white text-xs font-medium rounded-md hover:bg-blue-700 disabled:opacity-50 transition-colors"
                          >
                            Call
                          </button>
                        )}
                        {entry.attributes.status === "CALLED" && (
                          <Link
                            href={`/ehr/${entry.attributes.patientId}`}
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
        )}
      </PageShell>
    </AppLayout>
  );
}
