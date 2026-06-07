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
    onCheckedIn: () => router.push("/queue"),
  });
  const cancelAppointment = useCancelAppointment();
  const reschedule = useRescheduleAppointment();

  const statusStyle =
    APPOINTMENT_STATUS_STYLES[appointment?.status.toUpperCase() ?? ""] ??
    "bg-gray-100 text-gray-600";

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
          className="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
        >
          <ArrowLeft className="h-4 w-4" /> My Appointments
        </Link>

        {error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-8 text-center">
            <AlertCircle className="mx-auto mb-2 h-8 w-8 text-red-300" />
            <p className="text-sm text-red-700">Could not load this appointment.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : !appointment ? (
          <p className="text-sm text-gray-500">Appointment not found.</p>
        ) : (
          <div className="space-y-4 max-w-2xl">
            <div className="rounded-xl border border-gray-200 bg-white p-5">
              <div className="flex flex-wrap items-center gap-2 mb-3">
                <h2 className="text-base font-semibold text-gray-900">
                  {appointment.appointmentType.replace(/_/g, " ")}
                </h2>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                  {appointment.status.replace(/_/g, " ")}
                </span>
              </div>
              <p className="flex items-center gap-1 text-sm text-gray-700">
                <Clock className="h-4 w-4 text-gray-400" />
                {formatWhen(appointment.startTime)}
              </p>
              {appointment.facilityName && (
                <p className="mt-1 flex items-center gap-1 text-sm text-gray-600">
                  <Building2 className="h-4 w-4 text-gray-400" />
                  {appointment.facilityName}
                </p>
              )}
              {appointment.providerName && (
                <p className="mt-1 text-sm text-gray-600">{appointment.providerName}</p>
              )}
              {appointment.bookingId && (
                <Link
                  href={`/home/bookings/${appointment.bookingId}`}
                  className="mt-3 inline-block text-xs font-medium text-impilo-600 hover:underline"
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
                className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600 disabled:opacity-50"
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

            <div className="rounded-xl border border-gray-200 bg-white p-5 space-y-3">
              <h3 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
                <Calendar className="h-4 w-4 text-impilo-500" />
                Reschedule
              </h3>
              <div className="grid gap-2 sm:grid-cols-2">
                <input
                  type="date"
                  value={rescheduleDate}
                  onChange={(e) => setRescheduleDate(e.target.value)}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
                <input
                  type="time"
                  value={rescheduleTime}
                  onChange={(e) => setRescheduleTime(e.target.value)}
                  className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
                />
              </div>
              <button
                type="button"
                onClick={() => void handleReschedule()}
                disabled={reschedule.isPending || !rescheduleDate}
                className="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
              >
                Request reschedule
              </button>
            </div>

            <button
              type="button"
              onClick={() => cancelAppointment.mutate({ id: appointmentId })}
              disabled={cancelAppointment.isPending}
              className="text-sm font-medium text-rose-600 hover:text-rose-700 disabled:opacity-50"
            >
              Cancel appointment
            </button>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
