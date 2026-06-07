"use client";

/**
 * My Appointments — confirmed scheduled events only (not pending bookings).
 * Route: /home/appointments
 *
 * Chain: GET /internal/v1/appointments → booking-service appointment aggregate
 */

import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  Building2,
  Calendar,
  CalendarClock,
  Clock,
  Loader2,
  Plus,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCancelAppointment, useClientAppointments } from "@/hooks/queries/useAppointments";
import {
  APPOINTMENT_STATUS_STYLES,
  CONFIRMED_APPOINTMENT_STATUSES,
} from "@/lib/booking-bff";

function formatWhen(iso?: string): string {
  if (!iso) return "Date to be confirmed";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function MyAppointmentsPage() {
  const { data: appointments = [], isLoading, error, refetch } = useClientAppointments();
  const cancelAppointment = useCancelAppointment();

  const confirmed = appointments.filter((a) => {
    const status = a.status.toUpperCase();
    return CONFIRMED_APPOINTMENT_STATUSES.has(status);
  });

  const upcoming = confirmed.filter(
    (a) => !["CANCELLED", "COMPLETED", "NO_SHOW"].includes(a.status.toUpperCase()),
  );

  return (
    <AppLayout>
      <PageShell
        title="My Appointments"
        subtitle="Confirmed scheduled visits — pending requests live under My Bookings"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
          <div className="flex gap-2">
            <Link
              href="/home/bookings"
              className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              <CalendarClock className="w-4 h-4" />
              My Bookings
            </Link>
            <Link
              href="/home/bookings/new"
              className="inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600 transition-colors"
            >
              <Plus className="w-4 h-4" />
              Book a service
            </Link>
          </div>
        </div>

        <div className="mb-4 rounded-lg border border-impilo-100 bg-impilo-50 px-4 py-3 text-xs text-impilo-800">
          Booking requests (pending consent, payment, or approval) are listed on{" "}
          <Link href="/home/bookings" className="font-medium underline">
            My Bookings
          </Link>
          . This page shows confirmed appointments only.
        </div>

        {error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-12 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-red-300" />
            <p className="text-sm text-red-700">Failed to load your appointments.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading appointments…</span>
          </div>
        ) : upcoming.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <Calendar className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No confirmed appointments yet.</p>
            <p className="mt-1 text-xs text-gray-400">
              Book a service to create a booking request — appointments appear here once confirmed.
            </p>
            <Link
              href="/home/bookings/new"
              className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-impilo-500 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-600"
            >
              <Plus className="w-4 h-4" /> Book a service
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {upcoming.map((appt) => {
              const statusStyle =
                APPOINTMENT_STATUS_STYLES[appt.status.toUpperCase()] ??
                "bg-gray-100 text-gray-600";
              const cancellable = ["SCHEDULED", "CONFIRMED", "REMINDED"].includes(
                appt.status.toUpperCase(),
              );
              return (
                <div key={appt.id} className="rounded-lg border border-gray-200 bg-white p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <Link
                          href={`/home/appointments/${appt.id}`}
                          className="text-sm font-semibold text-gray-900 hover:text-impilo-600"
                        >
                          {appt.appointmentType.replace(/_/g, " ")}
                        </Link>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {appt.status.replace(/_/g, " ")}
                        </span>
                      </div>
                      <p className="mt-1 flex items-center gap-1 text-xs text-gray-600">
                        <Clock className="h-3 w-3" />
                        {formatWhen(appt.startTime)}
                      </p>
                      {appt.facilityName && (
                        <p className="mt-0.5 flex items-center gap-1 text-xs text-gray-500">
                          <Building2 className="h-3 w-3" />
                          {appt.facilityName}
                        </p>
                      )}
                      {appt.reason && (
                        <p className="mt-1 text-xs text-gray-500">{appt.reason}</p>
                      )}
                    </div>
                    {cancellable && (
                      <button
                        type="button"
                        onClick={() => {
                          cancelAppointment.mutate({ id: appt.id });
                          void refetch();
                        }}
                        disabled={cancelAppointment.isPending}
                        className="inline-flex items-center gap-1 rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50 disabled:opacity-50"
                      >
                        <XCircle className="h-3 w-3" />
                        Cancel
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
