/**
 * Citizen self-booking hooks — web parity with mobile citizen appointment BFF.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import {
  normalizeCitizenAppointmentList,
  toCitizenAppointmentRequest,
  type CitizenAppointmentRecord,
  type RequestCitizenAppointmentPayload,
} from "@/lib/citizen-appointment-bff";

const CITIZEN_APPOINTMENTS_PATH = "/internal/v1/mobile/citizen/appointments";

type CitizenAppointmentsResponse = ApiResponse<unknown>;

export function useCitizenAppointments(status?: string) {
  return useQuery({
    queryKey: ["citizen-appointments", { status }],
    queryFn: async () => {
      const params = new URLSearchParams({ page: "0", size: "50" });
      if (status) params.set("status", status);
      const response = await apiClient.get<CitizenAppointmentsResponse>(
        `${CITIZEN_APPOINTMENTS_PATH}?${params.toString()}`,
      );
      return normalizeCitizenAppointmentList(response);
    },
  });
}

export function useRequestCitizenAppointment() {
  const queryClient = useQueryClient();

  return useMutation<CitizenAppointmentRecord | null, unknown, RequestCitizenAppointmentPayload>({
    mutationFn: async (payload) => {
      const response = await apiClient.post<CitizenAppointmentsResponse>(
        CITIZEN_APPOINTMENTS_PATH,
        toCitizenAppointmentRequest(payload),
      );
      const rows = normalizeCitizenAppointmentList(response);
      return rows[0] ?? normalizeCitizenAppointmentList({ data: response.data })[0] ?? null;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["citizen-appointments"] });
      queryClient.invalidateQueries({ queryKey: ["citizen-upcoming-appointments-mobile"] });
    },
  });
}

export function useCancelCitizenAppointment() {
  const queryClient = useQueryClient();

  return useMutation<unknown, unknown, { id: string; reason?: string }>({
    mutationFn: ({ id, reason }) =>
      apiClient.post(`${CITIZEN_APPOINTMENTS_PATH}/${encodeURIComponent(id)}/cancel`, {
        reason: reason ?? "Cancelled by citizen",
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["citizen-appointments"] });
      queryClient.invalidateQueries({ queryKey: ["citizen-upcoming-appointments-mobile"] });
    },
  });
}
