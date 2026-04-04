"use client";

/**
 * Patient Queue — Queue dashboard with entries table.
 * Route: /queue | pageTitle: "Patient Queue"
 */

import { useRouter } from "next/navigation";
import Link from "next/link";
import { Users, Loader2, UserPlus, Clock, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQueueEntries, useCallPatient } from "@/hooks/queries/useQueue";
import { useFacilityStore } from "@/hooks/useFacilityStore";

const PRIORITY_LABELS: Record<number, { label: string; className: string }> = {
  1: { label: "Emergency", className: "bg-red-100 text-red-700" },
  2: { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  3: { label: "Normal", className: "bg-blue-100 text-blue-700" },
  4: { label: "Low", className: "bg-gray-100 text-gray-600" },
};

const STATUS_STYLES: Record<string, string> = {
  WAITING: "bg-yellow-100 text-yellow-700",
  CALLED: "bg-blue-100 text-blue-700",
  IN_PROGRESS: "bg-green-100 text-green-700",
  COMPLETED: "bg-gray-100 text-gray-600",
};

export default function QueuePage() {
  const router = useRouter();
  const facility = useFacilityStore((s) => s.facility);
  const { data, isLoading } = useQueueEntries({ facilityId: facility?.id });
  const callPatient = useCallPatient();

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
            <Link
              href="/queue/walk-in"
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
            >
              <UserPlus className="w-4 h-4" />
              Walk-in Registration
            </Link>
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
            <Link
              href="/queue/walk-in"
              className="mt-3 inline-block text-sm text-blue-600 hover:text-blue-800"
            >
              Register a walk-in patient
            </Link>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Queue Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Priority</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Arrival Time</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {entries.map((entry) => {
                  const priority = PRIORITY_LABELS[entry.attributes.priority] ?? {
                    label: `P${entry.attributes.priority}`,
                    className: "bg-gray-100 text-gray-600",
                  };
                  const statusStyle =
                    STATUS_STYLES[entry.attributes.status] ?? "bg-gray-100 text-gray-600";

                  return (
                    <tr key={entry.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {(entry.attributes as Record<string, unknown>).patientName as string ??
                          entry.attributes.patientId}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(entry.attributes as Record<string, unknown>).queueType as string ?? "General"}
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
