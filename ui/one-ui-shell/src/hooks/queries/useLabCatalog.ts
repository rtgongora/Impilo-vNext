/**
 * Experience UI — Lab catalog hooks (OROS via BFF)
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface LabCatalogItem {
  catalogId?: string;
  orderType?: string;
  code: string;
  displayName?: string;
  category?: string;
  ziboCanonical?: string;
  active?: boolean;
  [key: string]: unknown;
}

export interface LabCatalogSummary {
  by_category: Record<string, number>;
  active: number;
  total: number;
}

export interface LabCatalogData {
  items: LabCatalogItem[];
  page: number;
  size: number;
  total_elements: number;
  summary: LabCatalogSummary;
}

type LabCatalogResponse = ApiResponse<LabCatalogData>;

export interface LabCatalogQueryParams {
  orderType?: string;
  page?: number;
  size?: number;
}

function buildLabCatalogQuery(params: LabCatalogQueryParams): string {
  const qp = new URLSearchParams();
  qp.set("order_type", params.orderType ?? "LAB");
  qp.set("page", String(params.page ?? 0));
  qp.set("size", String(params.size ?? 50));
  return qp.toString();
}

export function useLabCatalog(params: LabCatalogQueryParams = {}) {
  const qs = buildLabCatalogQuery(params);

  return useQuery<LabCatalogResponse>({
    queryKey: [
      "lab-catalog",
      params.orderType ?? "LAB",
      params.page ?? 0,
      params.size ?? 50,
    ],
    queryFn: () =>
      apiClient.get<LabCatalogResponse>(`/internal/v1/lab-catalog?${qs}`),
    staleTime: 60_000,
  });
}
