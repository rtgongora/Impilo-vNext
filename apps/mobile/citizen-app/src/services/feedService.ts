/**
 * Feed Service — Social feed, campaigns, and health content.
 *
 * Backend: experience-bff (/internal/v1/mobile/citizen/feed/*)
 */

import { apiClient } from "@impilo/mobile-api-client";
import type { FeedItem } from "../types";

const V1 = "/internal/v1/mobile/citizen/feed";

interface PagedResult<T> {
  data: T[];
  meta: { page: { number: number; size: number; total_elements: number; total_pages: number } };
}

export async function fetchFeed(params: {
  category?: string;
  page?: number;
  size?: number;
} = {}): Promise<{ items: FeedItem[]; totalElements: number; hasNext: boolean }> {
  const query: string[] = [];
  if (params.category) query.push(`category=${params.category}`);
  if (params.page !== undefined) query.push(`page=${params.page}`);
  if (params.size !== undefined) query.push(`size=${params.size}`);
  const qs = query.length > 0 ? `?${query.join("&")}` : "";

  const response = await apiClient.get<PagedResult<FeedItem>>(`${V1}${qs}`);
  const result = response.data;
  return {
    items: result.data,
    totalElements: result.meta.page.total_elements,
    hasNext: result.meta.page.number < result.meta.page.total_pages - 1,
  };
}

export async function fetchFeedItem(id: string): Promise<FeedItem> {
  const response = await apiClient.get<{ data: FeedItem }>(`${V1}/${encodeURIComponent(id)}`);
  return response.data.data;
}

export async function likeFeedItem(id: string): Promise<void> {
  await apiClient.post(`${V1}/${encodeURIComponent(id)}/like`);
}

export async function unlikeFeedItem(id: string): Promise<void> {
  await apiClient.delete(`${V1}/${encodeURIComponent(id)}/like`);
}
