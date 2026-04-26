"use client";

import { CalendarCheck, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useMarketplaceBookings } from "@/hooks/queries/useMarketplace";

function formatDate(value: string | null) {
  if (!value) return "Awaiting scheduling";
  return new Date(value).toLocaleString();
}

const STATUS_STYLES: Record<string, string> = {
  CONFIRMED: "bg-emerald-100 text-emerald-800",
  PENDING: "bg-amber-100 text-amber-800",
  CANCELLED: "bg-rose-100 text-rose-800",
  COMPLETED: "bg-sky-100 text-sky-800",
};

export default function BookingsPage() {
  const bookingsQuery = useMarketplaceBookings();
  const bookings = bookingsQuery.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Marketplace Bookings" subtitle="Track scheduled service work and external fulfilment commitments in one place.">
        {bookingsQuery.isLoading ? (
          <div className="flex items-center justify-center py-16 text-sm text-gray-500">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading bookings...
          </div>
        ) : bookings.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <CalendarCheck className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No bookings found.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-slate-50 text-left text-slate-600">
                  <th className="px-4 py-3 font-medium">Booking #</th>
                  <th className="px-4 py-3 font-medium">Service</th>
                  <th className="px-4 py-3 font-medium">Provider</th>
                  <th className="px-4 py-3 font-medium">Requested</th>
                  <th className="px-4 py-3 font-medium">Scheduled</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((booking) => (
                  <tr key={booking.id} className="border-b border-slate-100 last:border-b-0 hover:bg-slate-50">
                    <td className="px-4 py-3 font-mono text-xs text-slate-700">{booking.bookingNumber}</td>
                    <td className="px-4 py-3 font-medium text-slate-900">{booking.serviceName}</td>
                    <td className="px-4 py-3 text-slate-600">{booking.providerName}</td>
                    <td className="px-4 py-3 text-slate-600">{formatDate(booking.requestedAt)}</td>
                    <td className="px-4 py-3 text-slate-600">{formatDate(booking.scheduledAt)}</td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[booking.status] ?? "bg-slate-100 text-slate-700"}`}>{booking.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
