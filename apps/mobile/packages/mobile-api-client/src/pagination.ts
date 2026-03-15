/**
 * Pagination Helpers — Utilities for working with PagedResponse<T>.
 */

import type { PagedResponse } from "@impilo/mobile-trust";
import { apiClient } from "./client";
import type { RequestOptions } from "./client";

export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Builds query string for pagination parameters.
 */
export function buildPaginationQuery(params: PaginationParams): string {
  const parts: string[] = [];
  if (params.page !== undefined) parts.push(`page=${params.page}`);
  if (params.size !== undefined) parts.push(`size=${params.size}`);
  if (params.sort) parts.push(`sort=${encodeURIComponent(params.sort)}`);
  return parts.length > 0 ? `?${parts.join("&")}` : "";
}

/**
 * Fetches a single page of results.
 */
export async function fetchPage<T>(
  path: string,
  params: PaginationParams,
  options?: RequestOptions
): Promise<PagedResponse<T>> {
  const query = buildPaginationQuery(params);
  const response = await apiClient.get<PagedResponse<T>>(`${path}${query}`, options);
  return response.data;
}

/**
 * Creates an async iterator that pages through all results.
 */
export async function* fetchAllPages<T>(
  path: string,
  pageSize: number = 20,
  options?: RequestOptions
): AsyncGenerator<T[], void, unknown> {
  let page = 0;
  let hasMore = true;

  while (hasMore) {
    const result = await fetchPage<T>(path, { page, size: pageSize }, options);
    yield result.items;
    hasMore = result.hasNext;
    page++;
  }
}
