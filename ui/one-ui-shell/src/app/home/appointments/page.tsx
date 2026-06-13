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
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="w-4 h-4" /> Back to Home
          </Link>
          <div className="flex gap-2">
            <Link
              href="/home/bookings"
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background"
            >
              <CalendarClock className="w-4 h-4" />
              My Bookings
            </Link>
            <Link
              href="/home/bookings/new"
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover transition-colors"
            >
              <Plus className="w-4 h-4" />
              Book a service
            </Link>
          </div>
        </div>

        <div className="mb-4 rounded-lg border border-primary/20 bg-primary-soft px-4 py-3 text-xs text-impilo-800">
          Booking requests (pending consent, payment, or approval) are listed on{" "}
          <Link href="/home/bookings" className="font-medium underline">
            My Bookings
          </Link>
          . This page shows confirmed appointments only.
        </div>

        {error ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-12 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-red-300" />
            <p className="text-sm text-danger">Failed to load your appointments.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            <span className="ml-2 text-sm text-muted-foreground">Loading appointments…</span>
          </div>
        ) : upcoming.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <Calendar className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No confirmed appointments yet.</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Book a service to create a booking request — appointments appear here once confirmed.
            </p>
            <Link
              href="/home/bookings/new"
              className="mt-4 inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              <Plus className="w-4 h-4" /> Book a service
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {upcoming.map((appt) => {
              const statusStyle =
                APPOINTMENT_STATUS_STYLES[appt.status.toUpperCase()] ??
                "bg-neutral-100 text-muted-foreground";
              const cancellable = ["SCHEDULED", "CONFIRMED", "REMINDED"].includes(
                appt.status.toUpperCase(),
              );
              return (
                <div key={appt.id} className="rounded-lg border border-border bg-card p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <Link
                          href={`/home/appointments/${appt.id}`}
                          className="text-sm font-semibold text-foreground hover:text-primary"
                        >
                          {appt.appointmentType.replace(/_/g, " ")}
                        </Link>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {appt.status.replace(/_/g, " ")}
                        </span>
                      </div>
                      <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                        <Clock className="h-3 w-3" />
                        {formatWhen(appt.startTime)}
                      </p>
                      {appt.facilityName && (
                        <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-foreground">
                          <Building2 className="h-3 w-3" />
                          {appt.facilityName}
                        </p>
                      )}
                      {appt.reason && (
                        <p className="mt-1 text-xs text-muted-foreground">{appt.reason}</p>
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
                        className="inline-flex items-center gap-1 rounded-lg border border-danger/28 px-3 py-1.5 text-xs font-medium text-rose-600 hover:bg-danger-soft disabled:opacity-50"
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
