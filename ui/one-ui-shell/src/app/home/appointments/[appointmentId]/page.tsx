"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import {
  AlertCircle,
  ArrowLeft,
  Building2,
  Calendar,
  CheckCircle2,
  Clock,
  Loader2,
  Video,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useAppointment,
  useCancelAppointment,
  useCheckInAppointment,
  useRescheduleAppointment,
} from "@/hooks/queries/useAppointments";
import { APPOINTMENT_STATUS_STYLES } from "@/lib/booking-bff";
import { buildPostCheckInRoute } from "@/lib/appointment-check-in-routing";
import { AppointmentCheckinQr } from "@/components/citizen/AppointmentCheckinQr";

function formatWhen(iso?: string): string {
  if (!iso) return "To be confirmed";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function AppointmentDetailPage() {
  const params = useParams();
  const router = useRouter();
  const appointmentId = String(params.appointmentId ?? "");
  const [rescheduleDate, setRescheduleDate] = useState("");
  const [rescheduleTime, setRescheduleTime] = useState("09:00");

  const { data: appointment, isLoading, error } = useAppointment(appointmentId);
  const checkIn = useCheckInAppointment({
    onCheckedIn: (meta) => {
      const route = buildPostCheckInRoute(meta);
      router.push(route ?? "/queue");
    },
  });
  const cancelAppointment = useCancelAppointment();
  const reschedule = useRescheduleAppointment();

  const statusStyle =
    APPOINTMENT_STATUS_STYLES[appointment?.status.toUpperCase() ?? ""] ??
    "bg-neutral-100 text-muted-foreground";

  const isVirtual =
    appointment?.channel?.toUpperCase() === "VIRTUAL" ||
    appointment?.appointmentType?.toUpperCase() === "TELEMEDICINE";

  async function handleReschedule() {
    if (!rescheduleDate) return;
    const startTime = `${rescheduleDate}T${rescheduleTime}:00Z`;
    await reschedule.mutateAsync({ id: appointmentId, startTime });
  }

  return (
    <AppLayout>
      <PageShell title="Appointment details" subtitle="Confirmed scheduled visit — check in or join telemedicine when due">
        <Link
          href="/home/appointments"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> My Appointments
        </Link>

        {error ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-8 text-center">
            <AlertCircle className="mx-auto mb-2 h-8 w-8 text-red-300" />
            <p className="text-sm text-danger">Could not load this appointment.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : !appointment ? (
          <p className="text-sm text-muted-foreground">Appointment not found.</p>
        ) : (
          <div className="space-y-4 max-w-2xl">
            <div className="rounded-xl border border-border bg-card p-5">
              <div className="flex flex-wrap items-center gap-2 mb-3">
                <h2 className="text-base font-semibold text-foreground">
                  {appointment.appointmentType.replace(/_/g, " ")}
                </h2>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                  {appointment.status.replace(/_/g, " ")}
                </span>
              </div>
              <p className="flex items-center gap-1 text-sm text-foreground">
                <Clock className="h-4 w-4 text-muted-foreground" />
                {formatWhen(appointment.startTime)}
              </p>
              {appointment.facilityName && (
                <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
                  <Building2 className="h-4 w-4 text-muted-foreground" />
                  {appointment.facilityName}
                </p>
              )}
              {appointment.providerName && (
                <p className="mt-1 text-sm text-muted-foreground">{appointment.providerName}</p>
              )}
              {appointment.bookingId && (
                <Link
                  href={`/home/bookings/${appointment.bookingId}`}
                  className="mt-3 inline-block text-xs font-medium text-primary hover:underline"
                >
                  View originating booking →
                </Link>
              )}
            </div>

            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => checkIn.mutate(appointmentId)}
                disabled={checkIn.isPending}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover disabled:opacity-50"
              >
                {checkIn.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <CheckCircle2 className="h-4 w-4" />
                )}
                Check in
              </button>
              {isVirtual && (
                <Link
                  href={
                    appointment.teleconsultSessionId
                      ? `/telemedicine/session/${appointment.teleconsultSessionId}`
                      : "/telemedicine"
                  }
                  className="inline-flex items-center gap-1.5 rounded-lg border border-green-300 bg-green-50 px-4 py-2 text-sm font-medium text-green-800 hover:bg-green-100"
                >
                  <Video className="h-4 w-4" />
                  Join telemedicine
                </Link>
              )}
            </div>

            <AppointmentCheckinQr appointmentId={appointmentId} />

            <div className="rounded-xl border border-border bg-card p-5 space-y-3">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Calendar className="h-4 w-4 text-primary" />
                Reschedule
              </h3>
              <div className="grid gap-2 sm:grid-cols-2">
                <input
                  type="date"
                  value={rescheduleDate}
                  onChange={(e) => setRescheduleDate(e.target.value)}
                  className="rounded-lg border border-border px-3 py-2 text-sm"
                />
                <input
                  type="time"
                  value={rescheduleTime}
                  onChange={(e) => setRescheduleTime(e.target.value)}
                  className="rounded-lg border border-border px-3 py-2 text-sm"
                />
              </div>
              <button
                type="button"
                onClick={() => void handleReschedule()}
                disabled={reschedule.isPending || !rescheduleDate}
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background disabled:opacity-50"
              >
                Request reschedule
              </button>
            </div>

            <button
              type="button"
              onClick={() => cancelAppointment.mutate({ id: appointmentId })}
              disabled={cancelAppointment.isPending}
              className="text-sm font-medium text-rose-600 hover:text-danger disabled:opacity-50"
            >
              Cancel appointment
            </button>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
