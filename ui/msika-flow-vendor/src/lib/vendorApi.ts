import { apiClient } from "./apiClient";

const V1 = "/v1";

export interface Order {
  orderId: string;
  tenantId: string;
  actorId: string;
  actorType: string;
  patientCpid: string | null;
  orderType: string;
  status: string;
  facilityId: string | null;
  vendorId: string | null;
  amountTotal: string;
  currency: string;
  createdAt: string;
  updatedAt: string;
  lines: Array<{
    lineId: string;
    msikaCoreCode: string;
    kind: string;
    qty: number;
    unitPrice: string;
    lineTotal: string;
    state: string;
    metadata: string | null;
  }>;
}

export const vendorApi = {
  getVendorOrders: (
    vendorId: string,
    params?: Record<string, string>
  ) => {
    const qs = params
      ? `?${new URLSearchParams(params).toString()}`
      : "";
    return apiClient.get<{ content: Order[] }>(
      `${V1}/vendors/${encodeURIComponent(vendorId)}/orders${qs}`
    );
  },

  acceptOrder: (vendorId: string, orderId: string) =>
    apiClient.post<Order>(
      `${V1}/vendors/${encodeURIComponent(vendorId)}/orders/${encodeURIComponent(orderId)}/accept`
    ),

  markReady: (orderId: string) =>
    apiClient.post<Order>(
      `${V1}/orders/${encodeURIComponent(orderId)}/mark-ready`
    ),

  markDelivered: (orderId: string) =>
    apiClient.post<Order>(
      `${V1}/orders/${encodeURIComponent(orderId)}/mark-delivered`
    ),

  proposeSubstitution: (
    orderId: string,
    lineId: string,
    substituteCode: string,
    substitutePrice: number,
    reason: string
  ) =>
    apiClient.post(
      `${V1}/rx/${encodeURIComponent(orderId)}/substitution/propose`,
      { lineId, substituteCode, substitutePrice, reason }
    ),
};
