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
import { buildPostCheckInRoute } from "@/lib/appointment-check-in-routing";

export default function ScheduledQueuePage() {
  const router = useRouter();
  const facility = useFacilityStore((state) => state.facility);
  const { data, isLoading, error } = useAppointments(facility?.id);
  const confirmAppointment = useConfirmAppointment();
  const cancelAppointment = useCancelAppointment();
  const checkInAppointment = useCheckInAppointment({
    onCheckedIn: (meta) => {
      const route = buildPostCheckInRoute(meta);
      if (route) {
        router.push(route);
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
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">Loading scheduled appointments...</span>
            </div>
          ) : error ? (
            <div className="rounded-3xl border border-danger/28 bg-danger-soft p-6 text-center">
              <AlertTriangle className="mx-auto mb-2 h-8 w-8 text-red-400" />
              <p className="text-sm text-danger">Failed to load appointments.</p>
            </div>
          ) : entries.length === 0 ? (
            <div className="rounded-3xl border border-border bg-card p-12 text-center shadow-sm">
              <CalendarClock className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">No scheduled appointments for this facility.</p>
            </div>
          ) : (
            <div className="overflow-hidden rounded-3xl border border-border bg-card shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background">
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Patient</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Appointment Time</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Type</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Provider</th>
                  <th className="text-left px-4 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="text-right px-4 py-3 font-medium text-muted-foreground">Action</th>
                </tr>
              </thead>
              <tbody>
                {entries.map((entry) => {
                  const status = getAppointmentStatus(entry);
                  const statusStyle = QUEUE_STATUS_STYLES[status] ?? "bg-neutral-100 text-foreground";
                  const patientId = getAppointmentPatientId(entry);
                  const appointmentTime = getAppointmentTime(entry);
                  return (
                    <tr key={entry.id} className="border-b last:border-b-0 hover:bg-background">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {patientId || "Patient not linked"}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        {formatQueueDateTime(appointmentTime)}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
                        <div>
                          <p>{getAppointmentType(entry)}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{getAppointmentReason(entry)}</p>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">
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
                              className="rounded-xl bg-primary px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-hover"
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
                              className="rounded-xl border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-background disabled:opacity-50"
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
                              className="rounded-xl border border-danger/28 bg-card px-3 py-1.5 text-xs font-medium text-rose-600 transition-colors hover:bg-danger-soft disabled:opacity-50"
                            >
                              Cancel
                            </button>
                          ) : null}
                          {!patientId ? (
                            <button
                              type="button"
                              onClick={() => router.push("/queue/search")}
                              className="rounded-xl border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-background"
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
