/**
 * Marketplace Service — Service discovery, booking, and order tracking.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/marketplace/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { MarketplaceService, ServiceRequest } from "../types";

const V1 = "/internal/v1/mobile/citizen/marketplace";

export type { MarketplaceService, ServiceRequest };

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchServices(params: {
  category?: string;
  search?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: MarketplaceService[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.category) query.push(`category=${encodeURIComponent(params.category)}`);
  if (params.search) query.push(`search=${encodeURIComponent(params.search)}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<MarketplaceService>>(`${V1}/services${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchServiceDetail(id: string): Promise<MarketplaceService> {
  const response = await apiClient.get<{ data: MarketplaceService }>(
    `${V1}/services/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function requestService(params: {
  serviceId: string;
  preferredDate?: string;
  notes?: string;
}): Promise<ServiceRequest> {
  const response = await apiClient.post<{ data: ServiceRequest }>(`${V1}/requests`, params);
  return response.data.data;
}

export async function fetchServiceRequests(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: ServiceRequest[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<ServiceRequest>>(`${V1}/requests${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchServiceRequest(id: string): Promise<ServiceRequest> {
  const response = await apiClient.get<{ data: ServiceRequest }>(
    `${V1}/requests/${encodeURIComponent(id)}`
  );
  return response.data.data;
}

export async function cancelServiceRequest(id: string, reason?: string): Promise<void> {
  await apiClient.post(`${V1}/requests/${encodeURIComponent(id)}/cancel`, { reason });
}
