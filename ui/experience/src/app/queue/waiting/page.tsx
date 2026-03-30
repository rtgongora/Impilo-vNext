"use client";

/**
 * Waiting List — Patients waiting to be seen, ordered by priority then arrival.
 * Route: /queue/waiting
 */

import { Loader2, AlertTriangle, Users, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQueueEntries, useCallPatient } from "@/hooks/queries/useQueue";

const PRIORITY_ORDER: Record<string, number> = {
  EMERGENCY: 1, URGENT: 2, NORMAL: 3, LOW: 4,
};

const PRIORITY_LABELS: Record<string, { label: string; className: string }> = {
  EMERGENCY: { label: "Emergency", className: "bg-red-100 text-red-700" },
  URGENT: { label: "Urgent", className: "bg-orange-100 text-orange-700" },
  NORMAL: { label: "Standard", className: "bg-amber-100 text-amber-700" },
  LOW: { label: "Routine", className: "bg-gray-100 text-gray-700" },
};

export default function WaitingListPage() {
  const { data, isLoading, error } = useQueueEntries({ status: "WAITING" });
  const callPatient = useCallPatient();

  const entries = (data?.data ?? []).sort((a, b) => {
    const pa = PRIORITY_ORDER[a.attributes.priority] ?? 99;
    const pb = PRIORITY_ORDER[b.attributes.priority] ?? 99;
    if (pa !== pb) return pa - pb;
    return new Date(a.attributes.arrival_time).getTime() - new Date(b.attributes.arrival_time).getTime();
  });

  function formatWaitTime(queuedAt: string): string {
    const diff = Date.now() - new Date(queuedAt).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m`;
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m`;
  }

  return (
    <AppLayout>
      <PageShell title="Waiting List" subtitle="Patients waiting to be seen">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading waiting list...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load waiting list</p>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No patients currently waiting</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Priority</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Wait Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Queued At</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => {
                  const prio = PRIORITY_LABELS[entry.attributes.priority] ?? { label: entry.attributes.priority, className: "bg-gray-100 text-gray-700" };
                  return (
                    <tr key={entry.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {entry.attributes.patient_id}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${prio.className}`}>
                          {prio.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{entry.attributes.status}</td>
                      <td className="px-4 py-3 text-gray-600">
                        <span className="inline-flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {formatWaitTime(entry.attributes.arrival_time)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(entry.attributes.arrival_time).toLocaleTimeString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => callPatient.mutate({ id: entry.id })}
                          disabled={callPatient.isPending}
                          className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium hover:bg-blue-700 disabled:opacity-50"
                        >
                          Call Patient
                        </button>
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
