import { apiClient } from "@impilo/mobile-api-client";

export interface ProviderDelivery {
  id: string;
  status: string;
  pickupLocation: string;
  dropoffLocation: string;
}

function mapDelivery(raw: any): ProviderDelivery {
  return {
    id: String(raw.id),
    status: String(raw.status ?? "ASSIGNED"),
    pickupLocation: String(raw.pickup_location ?? raw.pickupLocation ?? ""),
    dropoffLocation: String(raw.dropoff_location ?? raw.dropoffLocation ?? ""),
  };
}

export async function listAssignedDeliveries(): Promise<ProviderDelivery[]> {
  const response = await apiClient.get<{ data: { items?: any[] } | any[] }>("/internal/v1/mobile/provider/deliveries");
  const container = response.data.data;
  const rows = Array.isArray(container) ? container : container.items ?? [];
  return rows.map(mapDelivery);
}

export async function deliveryAction(id: string, action: "accept" | "decline" | "pickup" | "start" | "location" | "proof" | "fail" | "return", body?: Record<string, unknown>) {
  const response = await apiClient.post<{ data: any }>(
    `/internal/v1/mobile/provider/deliveries/${encodeURIComponent(id)}/${action}`,
    body ?? {},
  );
  return mapDelivery(response.data.data);
}

export async function pushDeliveryLocation(id: string, latitude: number, longitude: number) {
  return deliveryAction(id, "location", { latitude, longitude });
}
