/**
 * Citizen booking transactions — distinct from confirmed appointments.
 * BFF: /internal/v1/mobile/citizen/bookings
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/citizen/bookings";

export interface CitizenBooking {
  id: string;
  bookingNumber?: string;
  bookingStatus?: string;
  bookingType?: string;
  facilityId?: string;
  providerId?: string;
  preferredStartTime?: string;
  reasonForBooking?: string;
  linkedAppointmentId?: string;
  consentStatus?: string;
  paymentStatus?: string;
}

interface PagedResult<T> {
  data: T[];
  meta: { request_id?: string; correlation_id?: string };
}

export async function fetchBookings(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: CitizenBooking[] }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";
  const response = await apiClient.get<PagedResult<CitizenBooking>>(`${V1}${qs}`);
  const raw = response.data.data;
  const items = Array.isArray(raw) ? raw : [];
  return { items };
}

export async function fetchBooking(id: string): Promise<CitizenBooking> {
  const response = await apiClient.get<{ data: CitizenBooking }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}

export async function cancelBooking(id: string, reason?: string): Promise<void> {
  await apiClient.post(`${V1}/${encodeURIComponent(id)}/cancel`, { reason });
}

/** Creates a booking REQUESTED transaction (not a confirmed appointment). */
export async function createBookingRequest(params: {
  facilityId: string;
  appointmentType: string;
  preferredDate: string;
  reason?: string;
}): Promise<CitizenBooking> {
  const response = await apiClient.post<{ data: CitizenBooking }>(V1, params);
  return response.data.data;
}
