/**
 * Support Service — Citizen support ticket management.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/support/*)
 */

import { apiClient } from "@impilo/mobile-api-client";

const V1 = "/internal/v1/mobile/citizen/support";

export interface SupportTicket {
  id: string;
  category: string;
  subject: string;
  description: string;
  priority: string;
  status: string;
  resolution?: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeArticle {
  id: string;
  title: string;
  body: string;
  category: string;
  tags: string[];
  publishedAt: string;
}

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchTickets(params: {
  status?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: SupportTicket[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.status) query.push(`status=${params.status}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<SupportTicket>>(`${V1}/tickets${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function createTicket(params: {
  category: string;
  subject: string;
  description: string;
  priority?: string;
}): Promise<SupportTicket> {
  const response = await apiClient.post<{ data: SupportTicket }>(`${V1}/tickets`, params);
  return response.data.data;
}

export async function fetchArticles(params: {
  category?: string;
  search?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: KnowledgeArticle[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.category) query.push(`category=${encodeURIComponent(params.category)}`);
  if (params.search) query.push(`search=${encodeURIComponent(params.search)}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<KnowledgeArticle>>(`${V1}/articles${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}
