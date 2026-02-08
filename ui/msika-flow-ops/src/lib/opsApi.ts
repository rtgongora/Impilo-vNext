import { apiClient } from "./apiClient";

const V1 = "/v1";

export interface Review {
  id: string;
  entityType: string;
  entityId: string;
  status: string;
  assignedTo: string | null;
  notes: string | null;
  tenantId: string;
  createdAt: string;
  updatedAt: string;
}

export interface StuckRoute {
  id: string;
  orderId: string;
  routeType: string;
  status: string;
  lastError: string | null;
  retryCount: number;
  updatedAt: string;
}

export interface Order {
  orderId: string;
  tenantId: string;
  orderType: string;
  status: string;
  amountTotal: string;
  currency: string;
  createdAt: string;
}

export interface Vendor {
  vendorId: string;
  tenantId: string;
  type: string;
  name: string;
  status: string;
  createdAt: string;
}

export const opsApi = {
  getPendingReviews: (params?: Record<string, string>) => {
    const qs = params ? `?${new URLSearchParams(params).toString()}` : "";
    return apiClient.get<{ content: Review[] }>(`${V1}/ops/reviews/pending${qs}`);
  },
  approveReview: (id: string) =>
    apiClient.post<Review>(`${V1}/ops/reviews/${encodeURIComponent(id)}/approve`),
  rejectReview: (id: string, notes?: string) =>
    apiClient.post<Review>(`${V1}/ops/reviews/${encodeURIComponent(id)}/reject`, { notes }),
  getStuckOrders: () => apiClient.get<StuckRoute[]>(`${V1}/ops/stuck-orders`),
  getOrder: (id: string) =>
    apiClient.get<Order>(`${V1}/orders/${encodeURIComponent(id)}`),
  suspendVendor: (vendorId: string, reason: string) =>
    apiClient.post<Vendor>(`${V1}/vendors/${encodeURIComponent(vendorId)}/suspend`, { reason }),
};
