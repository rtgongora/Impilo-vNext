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

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditEvent {
  id: string;
  eventType: string;
  entityType: string;
  entityId: string;
  actorId: string;
  tenantId: string;
  payload: string;
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
  searchOrders: (params?: { query?: string; status?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params?.query) qs.set("q", params.query);
    if (params?.status) qs.set("status", params.status);
    qs.set("page", String(params?.page ?? 0));
    qs.set("size", String(params?.size ?? 20));
    return apiClient.get<PagedResponse<Order>>(`${V1}/orders?${qs.toString()}`);
  },
  listVendors: (params?: { status?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params?.status) qs.set("status", params.status);
    qs.set("page", String(params?.page ?? 0));
    qs.set("size", String(params?.size ?? 20));
    return apiClient.get<PagedResponse<Vendor>>(`${V1}/vendors?${qs.toString()}`);
  },
  getVendor: (vendorId: string) =>
    apiClient.get<Vendor>(`${V1}/vendors/${encodeURIComponent(vendorId)}`),
  suspendVendor: (vendorId: string, reason: string) =>
    apiClient.post<Vendor>(`${V1}/vendors/${encodeURIComponent(vendorId)}/suspend`, { reason }),
  reinstateVendor: (vendorId: string) =>
    apiClient.post<Vendor>(`${V1}/vendors/${encodeURIComponent(vendorId)}/reinstate`),
  listAuditEvents: (params?: { eventType?: string; page?: number; size?: number }) => {
    const qs = new URLSearchParams();
    if (params?.eventType) qs.set("eventType", params.eventType);
    qs.set("page", String(params?.page ?? 0));
    qs.set("size", String(params?.size ?? 50));
    return apiClient.get<PagedResponse<AuditEvent>>(`${V1}/ops/audit?${qs.toString()}`);
  },
};
