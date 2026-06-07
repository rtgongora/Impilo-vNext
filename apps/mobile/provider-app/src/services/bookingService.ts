/**
 * Provider booking requests inbox.
 * BFF: /internal/v1/booking-requests
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/booking-requests";

export interface BookingRequest {
  id: string;
  bookingNumber?: string;
  bookingStatus?: string;
  bookingType?: string;
  clientId?: string;
  facilityId?: string;
  providerId?: string;
  reasonForBooking?: string;
  consentStatus?: string;
  approvalStatus?: string;
}

export async function fetchBookingRequests(params: {
  facilityId?: string;
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<BookingRequest[]> {
  const query: string[] = [];
  if (params.facilityId) query.push(`facility_id=${encodeURIComponent(params.facilityId)}`);
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size ?? 50}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<{ data: BookingRequest[] }>(`${V1}${qs}`);
  const raw = response.data.data;
  return Array.isArray(raw) ? raw : [];
}

export async function approveBookingRequest(id: string): Promise<void> {
  await apiClient.post(`/internal/v1/bookings/${encodeURIComponent(id)}/approve`, {});
}

export async function rejectBookingRequest(id: string, reason: string): Promise<void> {
  await apiClient.post(`/internal/v1/bookings/${encodeURIComponent(id)}/reject`, { reason });
}

export async function convertBookingToAppointment(id: string): Promise<void> {
  await apiClient.post(`/internal/v1/bookings/${encodeURIComponent(id)}/convert`, {});
}
