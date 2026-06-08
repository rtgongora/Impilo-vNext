/**
 * Experience UI — Appointment scheduling query hooks
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { normalizeAppointment, normalizeAppointmentList, type AppointmentRecord } from "@/lib/booking-bff";

export interface AppointmentResource {
  id: string;
  type: string;
  attributes: Record<string, unknown>;
}

export interface CreateAppointmentPayload {
  patient_id: string;
  facility_id: string;
  provider_id?: string | null;
  provider_name?: string | null;
  appointment_type: string;
  scheduled_at: string;
  end_at?: string | null;
  reason?: string | null;
  notes?: string | null;
  resource_id?: string | null;
}

export interface AppointmentCheckInMeta {
  patient_id?: string;
  journey_id?: string;
  core_transaction_id?: string;
}

type AppointmentsResponse = ApiResponse<AppointmentResource[]>;
type AppointmentResponse = ApiResponse<Record<string, unknown>>;

export function useAppointments(facilityId?: string, status?: string) {
  return useQuery<AppointmentsResponse>({
    queryKey: ["appointments", { facilityId, status }],
    queryFn: () => {
      const params = new URLSearchParams();
      if (facilityId) params.set("facility_id", facilityId);
      if (status) params.set("status", status);
      const qs = params.toString();
      return apiClient.get<AppointmentsResponse>(
        `/internal/v1/appointments${qs ? `?${qs}` : ""}`,
      );
    },
    enabled: !!facilityId,
  });
}

export function useAppointment(appointmentId?: string) {
  return useQuery({
    queryKey: ["appointment", appointmentId],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/appointments/${encodeURIComponent(appointmentId!)}`,
      );
      return normalizeAppointment(response.data ?? response) ?? null;
    },
    enabled: !!appointmentId,
  });
}

export function useClientAppointments(status?: string) {
  return useQuery({
    queryKey: ["client-appointments", { status }],
    queryFn: async () => {
      const params = new URLSearchParams({ page: "0", size: "50" });
      if (status) params.set("status", status);
      const response = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/appointments?${params.toString()}`,
      );
      return normalizeAppointmentList(response);
    },
  });
}

export type { AppointmentRecord };

export function useCreateAppointment() {
  const queryClient = useQueryClient();

  return useMutation<AppointmentResponse, unknown, CreateAppointmentPayload>({
    mutationFn: (payload) =>
      apiClient.post<AppointmentResponse>("/internal/v1/appointments", payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });
}

export function useConfirmAppointment() {
  const queryClient = useQueryClient();

  return useMutation<AppointmentResponse, unknown, string>({
    mutationFn: (appointmentId) =>
      apiClient.post<AppointmentResponse>(`/internal/v1/appointments/${appointmentId}/confirm`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });
}

export function useCancelAppointment() {
  const queryClient = useQueryClient();

  return useMutation<AppointmentResponse, unknown, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) =>
      apiClient.post<AppointmentResponse>(`/internal/v1/appointments/${id}/cancel`, {
        reason: reason ?? "Cancelled",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
    },
  });
}

export function useAppointmentResources(facilityId?: string) {
  return useQuery<ApiResponse<AppointmentResource[]>>({
    queryKey: ["appointment-resources", facilityId],
    queryFn: () =>
      apiClient.get<ApiResponse<AppointmentResource[]>>(
        `/internal/v1/appointments/resources?facility_id=${encodeURIComponent(facilityId!)}`,
      ),
    enabled: !!facilityId,
  });
}

export interface AvailabilitySlot {
  time: string;
  available: boolean;
  start?: string;
  duration_minutes?: number;
}

export function useAppointmentAvailability(resourceId?: string, date?: string) {
  return useQuery<ApiResponse<{ slots: AvailabilitySlot[] }>>({
    queryKey: ["appointment-availability", resourceId, date],
    queryFn: () =>
      apiClient.get<ApiResponse<{ slots: AvailabilitySlot[] }>>(
        `/internal/v1/appointments/availability?resource_id=${encodeURIComponent(resourceId!)}&date=${encodeURIComponent(date!)}`,
      ),
    enabled: !!resourceId && !!date,
  });
}

export function useCheckInAppointment(options?: {
  onCheckedIn?: (meta: AppointmentCheckInMeta) => void;
}) {
  const queryClient = useQueryClient();

  return useMutation<AppointmentResponse, unknown, string>({
    mutationFn: (appointmentId) =>
      apiClient.post<AppointmentResponse>(
        `/internal/v1/appointments/${encodeURIComponent(appointmentId)}/check-in`,
      ),
    onSuccess: (response, appointmentId) => {
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
      queryClient.invalidateQueries({ queryKey: ["appointment", appointmentId] });
      queryClient.invalidateQueries({ queryKey: ["client-appointments"] });
      const meta = response.meta as AppointmentCheckInMeta | undefined;
      if (meta && options?.onCheckedIn) {
        options.onCheckedIn(meta);
      }
    },
  });
}

export function useRescheduleAppointment() {
  const queryClient = useQueryClient();

  return useMutation<
    AppointmentResponse,
    unknown,
    { id: string; startTime: string; endTime?: string }
  >({
    mutationFn: ({ id, startTime, endTime }) =>
      apiClient.post<AppointmentResponse>(
        `/internal/v1/appointments/${encodeURIComponent(id)}/reschedule`,
        { startTime, endTime },
      ),
    onSuccess: (_response, vars) => {
      queryClient.invalidateQueries({ queryKey: ["appointments"] });
      queryClient.invalidateQueries({ queryKey: ["appointment", vars.id] });
      queryClient.invalidateQueries({ queryKey: ["client-appointments"] });
    },
  });
}
