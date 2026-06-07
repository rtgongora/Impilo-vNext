/**
 * Booking transaction hooks — citizen self-book and provider triage via Experience BFF.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import {
  normalizeBooking,
  normalizeBookingList,
  normalizeBookingMvumoStatus,
  toBookingRequest,
  type BookingFacilityConfig,
  type BookingMvumoStatus,
  type BookingRecord,
  type CreateBookingPayload,
} from "@/lib/booking-bff";

const BOOKINGS_PATH = "/internal/v1/bookings";

type BookingResponse = ApiResponse<unknown>;
type BookingListResponse = ApiResponse<unknown>;

export interface BookingAvailabilitySlot {
  time: string;
  available: boolean;
  start?: string;
  duration_minutes?: number;
}

export interface BookingRoutingOption {
  id: string;
  label: string;
  subtitle?: string;
  type?: string;
}

export function useClientBookings(status?: string) {
  return useQuery({
    queryKey: ["client-bookings", { status }],
    queryFn: async () => {
      const params = new URLSearchParams({ page: "0", size: "50" });
      if (status) params.set("bookingStatus", status);
      const response = await apiClient.get<BookingListResponse>(
        `${BOOKINGS_PATH}?${params.toString()}`,
      );
      return normalizeBookingList(response);
    },
  });
}

export function useFacilityBookings(facilityId?: string, status?: string) {
  return useQuery({
    queryKey: ["facility-bookings", { facilityId, status }],
    queryFn: async () => {
      const params = new URLSearchParams({ page: "0", size: "100" });
      if (facilityId) params.set("facilityId", facilityId);
      if (status) params.set("bookingStatus", status);
      const response = await apiClient.get<BookingListResponse>(
        `${BOOKINGS_PATH}?${params.toString()}`,
      );
      return normalizeBookingList(response);
    },
    enabled: !!facilityId,
  });
}

export function useBooking(bookingId?: string) {
  return useQuery({
    queryKey: ["booking", bookingId],
    queryFn: async () => {
      const response = await apiClient.get<BookingResponse>(
        `${BOOKINGS_PATH}/${encodeURIComponent(bookingId!)}`,
      );
      return normalizeBooking(response.data ?? response) ?? null;
    },
    enabled: !!bookingId,
  });
}

export function useCreateBooking() {
  const queryClient = useQueryClient();

  return useMutation<BookingRecord | null, unknown, CreateBookingPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<BookingResponse>(
        BOOKINGS_PATH,
        toBookingRequest(payload),
      );
      const rows = normalizeBookingList(response);
      return rows[0] ?? normalizeBooking(response.data ?? response) ?? null;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["client-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
    },
  });
}

export function useCancelBooking() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(id)}/cancel`, {
        reason: reason ?? "Cancelled by user",
      }),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["client-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", vars.id] });
    },
  });
}

export function useBookingMvumoStatus(bookingId?: string) {
  return useQuery({
    queryKey: ["booking-mvumo-status", bookingId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<Record<string, unknown>>>(
        `${BOOKINGS_PATH}/${encodeURIComponent(bookingId!)}/mvumo/status`,
      );
      return normalizeBookingMvumoStatus(response);
    },
    enabled: !!bookingId,
  });
}

export function useRequestBookingMvumo() {
  const queryClient = useQueryClient();

  return useMutation<
    unknown,
    unknown,
    { bookingId: string; mvumoType: string; consentType?: string }
  >({
    mutationFn: ({ bookingId, mvumoType, consentType }) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(bookingId)}/mvumo/request`, {
        mvumoType,
        consentType,
      }),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["booking-mvumo-status", vars.bookingId] });
      queryClient.invalidateQueries({ queryKey: ["booking", vars.bookingId] });
      queryClient.invalidateQueries({ queryKey: ["client-bookings"] });
    },
  });
}

export function useApproveBooking() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, string>({
    mutationFn: (bookingId) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(bookingId)}/approve`),
    onSuccess: (_data, bookingId) => {
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", bookingId] });
    },
  });
}

export function useRejectBooking() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(id)}/reject`, {
        reason: reason ?? "Rejected by provider",
      }),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", vars.id] });
    },
  });
}

export function useTriageBooking() {
  const queryClient = useQueryClient();

  return useMutation<
    unknown,
    unknown,
    { id: string; triageStatus: string; notes?: string }
  >({
    mutationFn: ({ id, triageStatus, notes }) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(id)}/triage`, {
        triageStatus,
        notes,
      }),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", vars.id] });
    },
  });
}

export function useConvertBooking() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, string>({
    mutationFn: (bookingId) =>
      apiClient.post(`${BOOKINGS_PATH}/${encodeURIComponent(bookingId)}/convert`),
    onSuccess: (_data, bookingId) => {
      queryClient.invalidateQueries({ queryKey: ["facility-bookings"] });
      queryClient.invalidateQueries({ queryKey: ["booking", bookingId] });
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });
}

export function useBookingAvailability(
  targetType?: string,
  targetRef?: string,
  facilityId?: string,
  date?: string,
) {
  return useQuery({
    queryKey: ["booking-availability", targetType, targetRef, facilityId, date],
    queryFn: async () => {
      const params = new URLSearchParams({
        targetType: targetType!,
        targetRef: targetRef!,
        date: date!,
      });
      if (facilityId) params.set("facilityId", facilityId);
      const response = await apiClient.get<ApiResponse<{ slots?: BookingAvailabilitySlot[] }>>(
        `${BOOKINGS_PATH}/availability?${params.toString()}`,
      );
      const data = response.data as { slots?: BookingAvailabilitySlot[] } | undefined;
      return data?.slots ?? [];
    },
    enabled: !!targetType && !!targetRef && !!date,
  });
}

function normalizeRoutingOptions(payload: unknown): BookingRoutingOption[] {
  const root = payload as { data?: unknown } | null | undefined;
  const raw = root?.data;
  const arr: unknown[] = Array.isArray(raw) ? raw : [];
  const options: BookingRoutingOption[] = [];
  arr.forEach((row, i) => {
    if (!row || typeof row !== "object") return;
    const o = row as Record<string, unknown>;
    const attrs =
      o.attributes && typeof o.attributes === "object"
        ? (o.attributes as Record<string, unknown>)
        : o;
    const id = String(o.id ?? attrs.id ?? attrs.providerPublicId ?? `opt-${i}`);
    const label = String(
      attrs.displayName ??
        attrs.name ??
        attrs.label ??
        attrs.providerName ??
        attrs.workspaceName ??
        "Option",
    );
    const option: BookingRoutingOption = {
      id,
      label,
    };
    const subtitle = attrs.specialty ?? attrs.facilityType ?? attrs.resourceType ?? attrs.type;
    if (typeof subtitle === "string") option.subtitle = subtitle;
    const type = attrs.type ?? attrs.resourceType;
    if (typeof type === "string") option.type = type;
    options.push(option);
  });
  return options;
}

export function useBookingRoutingProviders(facilityId?: string, search?: string) {
  return useQuery({
    queryKey: ["booking-routing-providers", facilityId, search],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (facilityId) params.set("facility_id", facilityId);
      if (search && search.length >= 2) params.set("q", search);
      const qs = params.toString();
      const response = await apiClient.get<ApiResponse<unknown>>(
        `${BOOKINGS_PATH}/routing/providers${qs ? `?${qs}` : ""}`,
      );
      return normalizeRoutingOptions(response);
    },
    enabled: !!facilityId,
  });
}

export function useBookingRoutingWorkspaces(facilityId?: string) {
  return useQuery({
    queryKey: ["booking-routing-workspaces", facilityId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<unknown>>(
        `${BOOKINGS_PATH}/routing/workspaces?facility_id=${encodeURIComponent(facilityId!)}`,
      );
      return normalizeRoutingOptions(response);
    },
    enabled: !!facilityId,
  });
}

export function useBookingRoutingResources(facilityId?: string) {
  return useQuery({
    queryKey: ["booking-routing-resources", facilityId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<unknown>>(
        `${BOOKINGS_PATH}/routing/resources?facility_id=${encodeURIComponent(facilityId!)}`,
      );
      return normalizeRoutingOptions(response);
    },
    enabled: !!facilityId,
  });
}

export function useBookingFacilityConfig(facilityId?: string) {
  return useQuery({
    queryKey: ["booking-facility-config", facilityId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<BookingFacilityConfig>>(
        `${BOOKINGS_PATH}/config?facility_id=${encodeURIComponent(facilityId!)}`,
      );
      return response.data ?? { facilityId: facilityId! };
    },
    enabled: !!facilityId,
  });
}

export function useUpdateBookingFacilityConfig() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, BookingFacilityConfig>({
    mutationFn: (config) =>
      apiClient.put(`${BOOKINGS_PATH}/config`, config),
    onSuccess: (_data, config) => {
      queryClient.invalidateQueries({ queryKey: ["booking-facility-config", config.facilityId] });
    },
  });
}
