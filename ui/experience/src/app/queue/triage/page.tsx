"use client";

/**
 * Triage Queue — Patients awaiting triage assessment.
 * Route: /queue/triage
 */

import { useState } from "react";
import { Loader2, AlertTriangle, Stethoscope, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQueueEntries } from "@/hooks/queries/useQueue";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export default function TriageQueuePage() {
  const { data, isLoading, error } = useQueueEntries({ status: "AWAITING_TRIAGE" });
  const queryClient = useQueryClient();
  const [assigningId, setAssigningId] = useState<string | null>(null);

  const triageMutation = useMutation({
    mutationFn: ({ id, priority }: { id: string; priority: number }) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/queue/entries/${id}/triage`, { priority }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["queue-entries"] });
      setAssigningId(null);
    },
  });

  const entries = data?.data ?? [];

  function formatWaitTime(queuedAt: string): string {
    const diff = Date.now() - new Date(queuedAt).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `${mins}m`;
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m`;
  }

  return (
    <AppLayout>
      <PageShell title="Triage Queue" subtitle="Patients awaiting triage assessment">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading triage queue...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load triage queue</p>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Stethoscope className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No patients awaiting triage</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Arrival Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Wait Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Chief Complaint</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => (
                  <tr key={entry.id} className="border-b last:border-b-0 hover:bg-gray-50">
                    <td className="px-4 py-3 font-medium text-gray-900">
                      {entry.attributes.patient_id}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {new Date(entry.attributes.arrival_time).toLocaleTimeString()}
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      <span className="inline-flex items-center gap-1">
                        <Clock className="w-3.5 h-3.5" />
                        {formatWaitTime(entry.attributes.arrival_time)}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {(entry.attributes as Record<string, unknown>).chiefComplaint as string || "—"}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {assigningId === entry.id ? (
                        <div className="inline-flex gap-1">
                          {[1, 2, 3, 4, 5].map((p) => (
                            <button
                              key={p}
                              onClick={() => triageMutation.mutate({ id: entry.id, priority: p })}
                              disabled={triageMutation.isPending}
                              className={`w-8 h-8 rounded text-xs font-medium border ${
                                p <= 2
                                  ? "border-red-300 text-red-700 hover:bg-red-50"
                                  : p === 3
                                  ? "border-amber-300 text-amber-700 hover:bg-amber-50"
                                  : "border-green-300 text-green-700 hover:bg-green-50"
                              }`}
                            >
                              P{p}
                            </button>
                          ))}
                        </div>
                      ) : (
                        <button
                          onClick={() => setAssigningId(entry.id)}
                          className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs font-medium hover:bg-blue-700"
                        >
                          Start Triage
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
