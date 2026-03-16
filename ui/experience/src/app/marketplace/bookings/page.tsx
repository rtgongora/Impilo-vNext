"use client";

/**
 * Service Bookings — View and manage service bookings.
 * Route: /marketplace/bookings
 */

import { Loader2, AlertTriangle, CalendarCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

interface Booking {
  id: string;
  type: "booking";
  attributes: {
    bookingNumber: string;
    serviceName: string;
    providerName: string;
    scheduledAt: string;
    status: string;
    [key: string]: unknown;
  };
}

const STATUS_STYLES: Record<string, string> = {
  CONFIRMED: "bg-green-100 text-green-700",
  PENDING: "bg-amber-100 text-amber-700",
  CANCELLED: "bg-red-100 text-red-700",
  COMPLETED: "bg-blue-100 text-blue-700",
};

export default function BookingsPage() {
  const { data, isLoading, error } = useQuery<ApiResponse<Booking[]>>({
    queryKey: ["marketplace-bookings"],
    queryFn: () => apiClient.get<ApiResponse<Booking[]>>("/internal/v1/marketplace/bookings"),
  });

  const bookings = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Service Bookings" subtitle="View and manage service bookings">
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading bookings...</span>
          </div>
        ) : error ? (
          <div className="bg-red-50 rounded-lg border border-red-200 p-6 text-center">
            <AlertTriangle className="w-8 h-8 text-red-400 mx-auto mb-2" />
            <p className="text-red-600 text-sm">Failed to load bookings</p>
          </div>
        ) : bookings.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <CalendarCheck className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No bookings found</p>
          </div>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-gray-50">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Booking #</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Service</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Provider</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Date / Time</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Status</th>
                </tr>
              </thead>
              <tbody>
                {bookings.map((booking) => {
                  const statusStyle = STATUS_STYLES[booking.attributes.status] ?? "bg-gray-100 text-gray-700";
                  return (
                    <tr key={booking.id} className="border-b last:border-b-0 hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-xs text-gray-700">
                        {booking.attributes.bookingNumber || booking.id.slice(0, 8).toUpperCase()}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-900">
                        {booking.attributes.serviceName}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{booking.attributes.providerName}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(booking.attributes.scheduledAt).toLocaleString()}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${statusStyle}`}>
                          {booking.attributes.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
