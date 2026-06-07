"use client";

/**
 * My Bookings — citizen booking transaction list (not confirmed appointments).
 * Route: /home/bookings
 */

import Link from "next/link";
import {
  AlertCircle,
  ArrowLeft,
  Building2,
  CalendarClock,
  Clock,
  Loader2,
  Plus,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCancelBooking, useClientBookings } from "@/hooks/queries/useBookings";
import { BOOKING_STATUS_STYLES, TERMINAL_BOOKING_STATUSES } from "@/lib/booking-bff";

function formatWhen(iso?: string): string {
  if (!iso) return "Preferred time to be confirmed";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function MyBookingsPage() {
  const { data: bookings = [], isLoading, error, refetch } = useClientBookings();
  const cancelBooking = useCancelBooking();

  const active = bookings.filter(
    (b) => !TERMINAL_BOOKING_STATUSES.has(b.bookingStatus.toUpperCase()),
  );

  return (
    <AppLayout>
      <PageShell
        title="My Bookings"
        subtitle="Track booking requests, consent, payment, and approval before they become appointments"
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
              href="/home/appointments"
              className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              My Appointments
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

        {error ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-12 text-center">
            <AlertCircle className="mx-auto mb-3 h-10 w-10 text-red-300" />
            <p className="text-sm text-red-700">Failed to load your bookings.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading bookings…</span>
          </div>
        ) : active.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <CalendarClock className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No active booking requests.</p>
            <p className="mt-1 text-xs text-gray-400">
              Book a service to request access — confirmed visits appear under My Appointments.
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
            {active.map((booking) => {
              const statusStyle =
                BOOKING_STATUS_STYLES[booking.bookingStatus.toUpperCase()] ??
                "bg-gray-100 text-gray-600";
              const cancellable = !["CANCELLED", "REJECTED", "FULFILLED"].includes(
                booking.bookingStatus.toUpperCase(),
              );
              return (
                <div key={booking.id} className="rounded-lg border border-gray-200 bg-white p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <Link
                          href={`/home/bookings/${booking.id}`}
                          className="text-sm font-semibold text-gray-900 hover:text-impilo-600"
                        >
                          {booking.serviceName ?? booking.bookingType.replace(/_/g, " ")}
                        </Link>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {booking.bookingStatus.replace(/_/g, " ")}
                        </span>
                      </div>
                      <p className="mt-1 flex items-center gap-1 text-xs text-gray-600">
                        <Clock className="h-3 w-3" />
                        {formatWhen(booking.preferredStartTime)}
                      </p>
                      {booking.facilityName && (
                        <p className="mt-0.5 flex items-center gap-1 text-xs text-gray-500">
                          <Building2 className="h-3 w-3" />
                          {booking.facilityName}
                        </p>
                      )}
                      {booking.reasonForBooking && (
                        <p className="mt-1 text-xs text-gray-500">{booking.reasonForBooking}</p>
                      )}
                      {booking.linkedAppointmentId && (
                        <Link
                          href={`/home/appointments/${booking.linkedAppointmentId}`}
                          className="mt-2 inline-block text-xs font-medium text-impilo-600 hover:underline"
                        >
                          View linked appointment →
                        </Link>
                      )}
                    </div>
                    {cancellable && (
                      <button
                        type="button"
                        onClick={() => {
                          cancelBooking.mutate({ id: booking.id });
                          void refetch();
                        }}
                        disabled={cancelBooking.isPending}
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
