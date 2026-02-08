import { apiClient } from "./apiClient";

const V1 = "/v1";

export interface OrderLine {
  lineId: string;
  orderId: string;
  msikaCoreCode: string;
  kind: string;
  qty: number;
  unitPrice: string;
  lineTotal: string;
  fulfillmentMode: string | null;
  state: string;
  metadata: string | null;
}

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
  priceSnapshot: string | null;
  createdAt: string;
  updatedAt: string;
  lines: OrderLine[];
}

export interface PickupSlip {
  tokenId: string;
  rawToken: string;
  otp: string;
  expiresAt: string;
  qrPayload: string;
}

export interface ValidationResult {
  valid: boolean;
  items: Array<{
    msikaCoreCode: string;
    valid: boolean;
    errors: string[];
    restrictionsSnapshot: string | null;
  }>;
}

export const msikaFlowApi = {
  // Cart validation
  validateCart: (items: Array<{ msikaCoreCode: string; qty: number }>, channel?: string) =>
    apiClient.post<ValidationResult>(`${V1}/cart/validate`, { items, channel: channel ?? "portal" }),

  // Orders
  createOrder: (data: {
    orderType: string;
    patientCpid?: string;
    facilityId?: string;
    vendorId?: string;
    idempotencyKey?: string;
    lines?: Array<{
      msikaCoreCode: string;
      kind?: string;
      qty: number;
      unitPrice: number;
      fulfillmentMode?: string;
    }>;
  }) => apiClient.post<Order>(`${V1}/orders`, data),

  getOrder: (id: string) => apiClient.get<Order>(`${V1}/orders/${encodeURIComponent(id)}`),

  validateOrder: (id: string) => apiClient.post<Order>(`${V1}/orders/${encodeURIComponent(id)}/validate`),

  priceOrder: (id: string) => apiClient.post<Order>(`${V1}/orders/${encodeURIComponent(id)}/price`),

  payOrder: (id: string) =>
    apiClient.post<{ orderId: string; mushexPaymentIntentId: string; status: string }>(
      `${V1}/orders/${encodeURIComponent(id)}/pay`
    ),

  cancelOrder: (id: string) => apiClient.post<Order>(`${V1}/orders/${encodeURIComponent(id)}/cancel`),

  getTracking: (id: string) => apiClient.get<Order>(`${V1}/orders/${encodeURIComponent(id)}/tracking`),

  // Pickup
  issuePickupToken: (orderId: string) =>
    apiClient.post<PickupSlip>(`${V1}/orders/${encodeURIComponent(orderId)}/pickup/issue`),

  claimPickup: (tokenOrOtp: string) =>
    apiClient.post<{ orderId: string; tokenId: string; claimedBy: string }>(`${V1}/pickup/claim`, { tokenOrOtp }),

  // Substitutions
  approveSubstitution: (orderId: string, lineId: string) =>
    apiClient.post<OrderLine>(`${V1}/rx/${encodeURIComponent(orderId)}/substitution/approve`, { lineId }),

  // Bookings
  createBooking: (data: { patientCpid?: string; facilityId: string; serviceCode?: string }) =>
    apiClient.post<Order>(`${V1}/bookings/create`, data),

  cancelBooking: (id: string) => apiClient.post<Order>(`${V1}/bookings/${encodeURIComponent(id)}/cancel`),
};
