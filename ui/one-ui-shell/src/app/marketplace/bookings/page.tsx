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
  CONFIRMED: "bg-emerald-100 text-primary-hover",
  PENDING: "bg-amber-100 text-warning-foreground",
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
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            <Loader2 className="mr-2 h-5 w-5 animate-spin" /> Loading bookings...
          </div>
        ) : bookings.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <CalendarCheck className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No bookings found.</p>
          </div>
        ) : (
          <div className="rounded-2xl border border-border bg-card shadow-sm">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-background text-left text-muted-foreground">
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
                  <tr key={booking.id} className="border-b border-border last:border-b-0 hover:bg-background">
                    <td className="px-4 py-3 font-mono text-xs text-foreground">{booking.bookingNumber}</td>
                    <td className="px-4 py-3 font-medium text-foreground">{booking.serviceName}</td>
                    <td className="px-4 py-3 text-muted-foreground">{booking.providerName}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatDate(booking.requestedAt)}</td>
                    <td className="px-4 py-3 text-muted-foreground">{formatDate(booking.scheduledAt)}</td>
                    <td className="px-4 py-3"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${STATUS_STYLES[booking.status] ?? "bg-neutral-100 text-foreground"}`}>{booking.status}</span></td>
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
