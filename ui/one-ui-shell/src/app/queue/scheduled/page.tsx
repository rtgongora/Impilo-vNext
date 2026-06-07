"use client";

/**
 * Scheduled Appointments — View scheduled queue entries.
 * Route: /queue/scheduled
 */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertTriangle, ArrowRightLeft, CalendarClock, ClipboardCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { QueueWorkspaceHeader } from "@/components/queue/QueueWorkspaceHeader";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useAppointments,
  useCancelAppointment,
  useCheckInAppointment,
  useConfirmAppointment,
} from "@/hooks/queries/useAppointments";
import {
  formatQueueDateTime,
  getAppointmentPatientId,
  getAppointmentProvider,
  getAppointmentReason,
  getAppointmentStatus,
  getAppointmentTime,
  getAppointmentType,
  QUEUE_STATUS_STYLES,
} from "@/lib/queue-workflows";

export default function ScheduledQueuePage() {
  const router = useRouter();
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading, error } = useAppointments(facility?.id);
  const confirmAppointment = useConfirmAppointment();
  const cancelAppointment = useCancelAppointment();
  const checkInAppointment = useCheckInAppointment({
    onCheckedIn: (meta) => {
      const { patient_id: patientId, journey_id: journeyId, core_transaction_id: transactionId } = meta;
      if (patientId && journeyId && transactionId) {
        const params = new URLSearchParams({ journey_id: journeyId, transaction_id: transactionId });
        router.push(`/ehr/${patientId}/encounters?${params.toString()}`);
      }
    },
  });

  const entries = data?.data ?? [];
  const scheduledToday = entries.filter((entry) => {
    const scheduledAt = getAppointmentTime(entry);
    return scheduledAt ? new Date(scheduledAt).toDateString() === new Date().toDateString() : false;
  }).length;
  const teleconsultCount = entries.filter((entry) => getAppointmentType(entry).toUpperCase().includes("TELE")).length;

  return (
    <AppLayout>
      <PageShell title="Scheduled Appointments" subtitle={facility ? `${facility.name}` : "Patients with scheduled appointments"}>
        <div className="space-y-6">
          <QueueWorkspaceHeader
            badge="Scheduled queue"
            badgeIcon={CalendarClock}
            title="Keep scheduled visits visible, confirm readiness, and open the right downstream surface"
            description="This view now uses the live appointments feed for the active facility so the queue team can confirm attendance, route into chart, or pivot to scheduling without a dead endpoint."
            facilityName={facility?.name}
            actions={[
              { href: "/queue", label: "Queue Workboard", icon: ArrowRightLeft },
              { href: "/scheduling", label: "Scheduling Workspace", icon: ClipboardCheck, tone: "secondary" },
            ]}
            metrics={[
              {
                label: "Scheduled",
                value: String(entries.length),
                detail: "Upcoming and active appointments loaded for the current facility.",
              },
              {
                label: "Today",
                value: String(scheduledToday),
                detail: "Appointments due today and likely to convert into live queue work.",
              },
              {
                label: "Check-in chain",
                value: "BFF",
                detail: "Check-in posts to scheduling BFF → booking CHECKED_IN + PCT journey enqueue for chart handoff.",
              },
              {
                label: "Teleconsults",
                value: String(teleconsultCount),
                detail: "Scheduled virtual visits that may need consult continuity.",
              },
            ]}
          />

          {isLoading ? (
            <div className="flex items-center justify-center py-16">
              <Loader2 className="h-6 w-6 animate-spin text-slate-400" />
              <span className="ml-2 text-sm text-slate-500">Loading scheduled appointments...</span>
            </div>
          ) : error ? (
            <div className="rounded-3xl border border-red-200 bg-red-50 p-6 text-center">
              <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
              <p className="text-sm text-red-700">Failed to load appointments.</p>
            </div>
          ) : entries.length === 0 ? (
            <div className="rounded-3xl border border-slate-200 bg-white p-12 text-center shadow-sm">
              <CalendarClock className="mx-auto mb-3 h-10 w-10 text-slate-300" />
              <p className="text-sm text-slate-500">No scheduled appointments for this facility.</p>
            </div>
          ) : (
            <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Appointment Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Provider</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Action</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => {
                  const status = getAppointmentStatus(entry);
                  const statusStyle = QUEUE_STATUS_STYLES[status] ?? "bg-gray-100 text-gray-700";
                  const patientId = getAppointmentPatientId(entry);
                  const appointmentTime = getAppointmentTime(entry);
                  return (
                    <tr key={entry.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {patientId || "Patient not linked"}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {formatQueueDateTime(appointmentTime)}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        <div>
                          <p>{getAppointmentType(entry)}</p>
                          <p className="mt-1 text-xs text-slate-500">{getAppointmentReason(entry)}</p>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {getAppointmentProvider(entry)}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-2">
                          {patientId ? (
                            <Link
                              href={`/ehr/${patientId}`}
                              className="rounded-xl bg-impilo-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-impilo-600"
                            >
                              Open Chart
                            </Link>
                          ) : null}
                          {status === "SCHEDULED" || status === "CONFIRMED" ? (
                            <button
                              type="button"
                              onClick={() => checkInAppointment.mutate(entry.id)}
                              disabled={checkInAppointment.isPending}
                              className="rounded-xl bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-emerald-700 disabled:opacity-50"
                            >
                              Check in
                            </button>
                          ) : null}
                          {status === "SCHEDULED" ? (
                            <button
                              type="button"
                              onClick={() => confirmAppointment.mutate(entry.id)}
                              disabled={confirmAppointment.isPending}
                              className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 disabled:opacity-50"
                            >
                              Confirm
                            </button>
                          ) : null}
                          {status !== "CANCELLED" ? (
                            <button
                              type="button"
                              onClick={() =>
                                cancelAppointment.mutate({
                                  id: entry.id,
                                  reason: "Cancelled from scheduled queue",
                                })
                              }
                              disabled={cancelAppointment.isPending}
                              className="rounded-xl border border-rose-200 bg-white px-3 py-1.5 text-xs font-medium text-rose-600 transition-colors hover:bg-rose-50 disabled:opacity-50"
                            >
                              Cancel
                            </button>
                          ) : null}
                          {!patientId ? (
                            <button
                              type="button"
                              onClick={() => router.push("/queue/search")}
                              className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50"
                            >
                              Find Patient
                            </button>
                          ) : null}
                        </div>
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
