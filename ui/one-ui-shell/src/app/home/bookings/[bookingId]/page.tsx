"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import {
  AlertCircle,
  ArrowLeft,
  Building2,
  Calendar,
  Clock,
  Loader2,
  Shield,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useBooking,
  useBookingMvumoStatus,
  useCancelBooking,
  useRequestBookingMvumo,
} from "@/hooks/queries/useBookings";
import { BOOKING_STATUS_STYLES } from "@/lib/booking-bff";

function formatWhen(iso?: string): string {
  if (!iso) return "To be confirmed";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function BookingDetailPage() {
  const params = useParams();
  const bookingId = String(params.bookingId ?? "");

  const { data: booking, isLoading, error } = useBooking(bookingId);
  const { data: mvumoStatus } = useBookingMvumoStatus(bookingId);
  const cancelBooking = useCancelBooking();
  const requestMvumo = useRequestBookingMvumo();

  const statusStyle =
    BOOKING_STATUS_STYLES[booking?.bookingStatus.toUpperCase() ?? ""] ??
    "bg-neutral-100 text-muted-foreground";

  return (
    <AppLayout>
      <PageShell title="Booking details" subtitle="Transaction status, Mvumo gates, and linked appointment">
        <Link
          href="/home/bookings"
          className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="h-4 w-4" /> My Bookings
        </Link>

        {error ? (
          <div className="rounded-lg border border-danger/28 bg-danger-soft p-8 text-center">
            <AlertCircle className="mx-auto mb-2 h-8 w-8 text-red-300" />
            <p className="text-sm text-danger">Could not load this booking.</p>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : !booking ? (
          <p className="text-sm text-muted-foreground">Booking not found.</p>
        ) : (
          <div className="space-y-4 max-w-2xl">
            <div className="rounded-xl border border-border bg-card p-5">
              <div className="flex flex-wrap items-center gap-2 mb-3">
                <h2 className="text-base font-semibold text-foreground">
                  {booking.serviceName ?? booking.bookingType.replace(/_/g, " ")}
                </h2>
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                  {booking.bookingStatus.replace(/_/g, " ")}
                </span>
              </div>
              {booking.bookingNumber && (
                <p className="text-xs text-muted-foreground mb-2">Ref: {booking.bookingNumber}</p>
              )}
              <p className="flex items-center gap-1 text-sm text-foreground">
                <Clock className="h-4 w-4 text-muted-foreground" />
                {formatWhen(booking.preferredStartTime)}
              </p>
              {booking.facilityName && (
                <p className="mt-1 flex items-center gap-1 text-sm text-muted-foreground">
                  <Building2 className="h-4 w-4 text-muted-foreground" />
                  {booking.facilityName}
                </p>
              )}
              {booking.reasonForBooking && (
                <p className="mt-3 text-sm text-muted-foreground">{booking.reasonForBooking}</p>
              )}
            </div>

            <div className="rounded-xl border border-border bg-card p-5">
              <h3 className="text-sm font-semibold text-foreground flex items-center gap-2 mb-3">
                <Shield className="h-4 w-4 text-primary" />
                Mvumo status
              </h3>
              <dl className="grid grid-cols-2 gap-3 text-xs">
                <div>
                  <dt className="text-muted-foreground">Consent</dt>
                  <dd className="font-medium text-foreground">
                    {mvumoStatus?.consentStatus ?? booking.consentStatus ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Agreement</dt>
                  <dd className="font-medium text-foreground">
                    {mvumoStatus?.agreementStatus ?? booking.agreementStatus ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Authorisation</dt>
                  <dd className="font-medium text-foreground">
                    {mvumoStatus?.authorisationStatus ?? booking.authorisationStatus ?? "—"}
                  </dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">Payment</dt>
                  <dd className="font-medium text-foreground">
                    {mvumoStatus?.paymentStatus ?? booking.paymentStatus ?? "—"}
                  </dd>
                </div>
              </dl>
              <button
                type="button"
                onClick={() =>
                  requestMvumo.mutate({
                    bookingId,
                    mvumoType: "CONSENT",
                  })
                }
                disabled={requestMvumo.isPending}
                className="mt-4 rounded-lg border border-impilo-300 px-3 py-2 text-xs font-medium text-primary-hover hover:bg-primary-soft disabled:opacity-50"
              >
                Request Mvumo consent
              </button>
            </div>

            {booking.linkedAppointmentId && (
              <Link
                href={`/home/appointments/${booking.linkedAppointmentId}`}
                className="flex items-center gap-2 rounded-xl border border-green-200 bg-green-50 p-4 text-sm font-medium text-green-800 hover:border-green-300"
              >
                <Calendar className="h-4 w-4" />
                View linked appointment →
              </Link>
            )}

            <button
              type="button"
              onClick={() => cancelBooking.mutate({ id: bookingId })}
              disabled={cancelBooking.isPending || booking.bookingStatus === "CANCELLED"}
              className="inline-flex items-center gap-1 rounded-lg border border-danger/28 px-4 py-2 text-sm font-medium text-rose-600 hover:bg-danger-soft disabled:opacity-50"
            >
              <XCircle className="h-4 w-4" />
              Cancel booking
            </button>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
