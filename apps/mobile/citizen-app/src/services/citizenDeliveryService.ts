import { apiClient } from "@impilo/mobile-api-client";
import type { CitizenDelivery } from "../types";

const BASE = "/internal/v1/mobile/citizen/deliveries";

function mapDelivery(raw: any): CitizenDelivery {
  return {
    id: String(raw.id),
    status: String(raw.status ?? "REQUESTED"),
    priority: String(raw.priority ?? "NORMAL"),
    pickupLocation: String(raw.pickup_location ?? raw.pickupLocation ?? ""),
    dropoffLocation: String(raw.dropoff_location ?? raw.dropoffLocation ?? ""),
    createdAt: String(raw.created_at ?? raw.createdAt ?? new Date().toISOString()),
    updatedAt: String(raw.updated_at ?? raw.updatedAt ?? new Date().toISOString()),
  };
}

export async function listCitizenDeliveries(): Promise<CitizenDelivery[]> {
  const response = await apiClient.get<{ data: { items?: any[] } | any[] }>(BASE);
  const container = response.data.data;
  const rows = Array.isArray(container) ? container : container.items ?? [];
  return rows.map(mapDelivery);
}

export async function createCitizenDeliveryRequest(params: {
  pickupLocation: string;
  dropoffLocation: string;
  priority?: string;
  externalRef?: string;
}): Promise<CitizenDelivery> {
  const response = await apiClient.post<{ data: any }>(BASE, params);
  return mapDelivery(response.data.data);
}

export async function getCitizenDeliveryTracking(id: string): Promise<any> {
  const response = await apiClient.get<{ data: any }>(`${BASE}/${encodeURIComponent(id)}/tracking`);
  return response.data.data;
}

export async function confirmCitizenDeliveryProof(id: string, otp: string): Promise<CitizenDelivery> {
  const response = await apiClient.post<{ data: any }>(
    `${BASE}/${encodeURIComponent(id)}/confirm-proof`,
    { proofType: "OTP", otp },
  );
  return mapDelivery(response.data.data);
}
