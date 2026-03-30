"use client";

/**
 * Scheduled Appointments — View scheduled queue entries.
 * Route: /queue/scheduled
 */

import { Loader2, AlertTriangle, CalendarClock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import type { QueueEntryResource } from "@/hooks/queries/useQueue";

const STATUS_STYLES: Record<string, string> = {
  SCHEDULED: "bg-blue-100 text-blue-700",
  CHECKED_IN: "bg-green-100 text-green-700",
  NO_SHOW: "bg-red-100 text-red-700",
};

export default function ScheduledQueuePage() {
  const { data, isLoading, error } = useQuery<ApiResponse<QueueEntryResource[]>>({
    queryKey: ["queue-entries", { type: "scheduled" }],
    queryFn: () =>
      apiClient.get<ApiResponse<QueueEntryResource[]>>("/internal/v1/queue?type=scheduled"),
  });

  const entries = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Scheduled Appointments" subtitle="Patients with scheduled appointments">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading scheduled appointments...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load appointments</p>
          </div>
        ) : entries.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <CalendarClock className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No scheduled appointments</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Appointment Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Provider</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => {
                  const attrs = entry.attributes as Record<string, unknown>;
                  const status = (attrs.appointmentStatus as string) || entry.attributes.status;
                  const statusStyle = STATUS_STYLES[status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={entry.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {(attrs.patientName as string) || entry.attributes.patient_id}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {attrs.appointmentTime
                          ? new Date(attrs.appointmentTime as string).toLocaleString()
                          : new Date(entry.attributes.arrival_time).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(attrs.appointmentType as string) || "General"}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {(attrs.providerName as string) || "\u2014"}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {status}
                        </span>
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
