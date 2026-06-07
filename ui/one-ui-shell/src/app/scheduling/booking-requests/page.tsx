"use client";

/**
 * Provider booking requests inbox — triage, approve, reject, Mvumo, convert.
 * Route: /scheduling/booking-requests
 */

import Link from "next/link";
import { useState } from "react";
import {
  ArrowLeft,
  CheckCircle2,
  ClipboardList,
  Loader2,
  Shield,
  XCircle,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useApproveBooking,
  useConvertBooking,
  useFacilityBookings,
  useRejectBooking,
  useRequestBookingMvumo,
  useTriageBooking,
} from "@/hooks/queries/useBookings";
import { BOOKING_STATUS_STYLES } from "@/lib/booking-bff";

const INBOX_STATUSES = new Set([
  "REQUESTED",
  "PENDING_TRIAGE",
  "PENDING_CONSENT",
  "PENDING_AGREEMENT",
  "PENDING_AUTHORISATION",
  "PENDING_PAYMENT",
  "PENDING_APPROVAL",
]);

function formatWhen(iso?: string): string {
  if (!iso) return "Preferred time TBC";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
}

export default function BookingRequestsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [filter, setFilter] = useState<"inbox" | "all">("inbox");

  const { data: bookings = [], isLoading } = useFacilityBookings(facility?.id);
  const approve = useApproveBooking();
  const reject = useRejectBooking();
  const triage = useTriageBooking();
  const requestMvumo = useRequestBookingMvumo();
  const convert = useConvertBooking();

  const visible = bookings.filter((b) => {
    if (filter === "all") return true;
    return INBOX_STATUSES.has(b.bookingStatus.toUpperCase());
  });

  return (
    <AppLayout>
      <OrganizationPlaneContextBar />
      <FacilityWorkClusterRibbon shiftExpected={false} />
      <PageShell
        title="Booking requests"
        subtitle="Triage citizen booking transactions before they become appointments"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/scheduling"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Scheduling
          </Link>
          <div className="flex gap-2">
            <Link
              href="/scheduling/today"
              className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
            >
              Today&apos;s appointments
            </Link>
            <Link
              href="/scheduling/bookings/config"
              className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
            >
              Booking config
            </Link>
          </div>
        </div>

        <div className="mb-4 flex gap-2">
          {(["inbox", "all"] as const).map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                filter === f ? "bg-impilo-500 text-white" : "bg-gray-100 text-gray-600"
              }`}
            >
              {f === "inbox" ? "Needs action" : "All bookings"}
            </button>
          ))}
        </div>

        {!facility?.id ? (
          <p className="text-sm text-gray-500">Select a facility to view booking requests.</p>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : visible.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <ClipboardList className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No booking requests in this view.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {visible.map((booking) => {
              const statusStyle =
                BOOKING_STATUS_STYLES[booking.bookingStatus.toUpperCase()] ??
                "bg-gray-100 text-gray-600";
              return (
                <div
                  key={booking.id}
                  className="rounded-lg border border-gray-200 bg-white p-5"
                  data-testid={`booking-request-${booking.id}`}
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="text-sm font-semibold text-gray-900">
                          {booking.serviceName ?? booking.bookingType}
                        </h3>
                        <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                          {booking.bookingStatus.replace(/_/g, " ")}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-gray-600">{formatWhen(booking.preferredStartTime)}</p>
                      {booking.reasonForBooking && (
                        <p className="mt-1 text-xs text-gray-500">{booking.reasonForBooking}</p>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() =>
                          triage.mutate({
                            id: booking.id,
                            triageStatus: "TRIAGED",
                          })
                        }
                        disabled={triage.isPending}
                        className="rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
                      >
                        Triage
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          requestMvumo.mutate({
                            bookingId: booking.id,
                            mvumoType: "CONSENT",
                          })
                        }
                        disabled={requestMvumo.isPending}
                        className="inline-flex items-center gap-1 rounded-lg border border-purple-200 px-2.5 py-1.5 text-xs font-medium text-purple-700 hover:bg-purple-50"
                      >
                        <Shield className="h-3 w-3" />
                        Mvumo
                      </button>
                      <button
                        type="button"
                        onClick={() => approve.mutate(booking.id)}
                        disabled={approve.isPending}
                        className="inline-flex items-center gap-1 rounded-lg bg-green-600 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-green-700"
                      >
                        <CheckCircle2 className="h-3 w-3" />
                        Approve
                      </button>
                      <button
                        type="button"
                        onClick={() => convert.mutate(booking.id)}
                        disabled={convert.isPending}
                        className="rounded-lg bg-impilo-500 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-impilo-600"
                      >
                        Convert
                      </button>
                      <button
                        type="button"
                        onClick={() => reject.mutate({ id: booking.id })}
                        disabled={reject.isPending}
                        className="inline-flex items-center gap-1 rounded-lg border border-rose-200 px-2.5 py-1.5 text-xs font-medium text-rose-600 hover:bg-rose-50"
                      >
                        <XCircle className="h-3 w-3" />
                        Reject
                      </button>
                    </div>
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
