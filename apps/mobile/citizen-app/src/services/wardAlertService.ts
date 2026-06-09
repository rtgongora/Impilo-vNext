/**
 * Inpatient ward call / bedside alert for admitted citizens.
 */
import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/citizen/inpatient";

export async function callWardStaff(body: {
  patientId: string;
  message?: string;
  alertType?: string;
  admissionRef?: string;
}): Promise<{ id: string; status: string; message: string }> {
  const response = await apiClient.post<{ data: { id: string; status: string; message: string } }>(
    `${V1}/ward-alert`,
    body,
  );
  return response.data.data;
}
