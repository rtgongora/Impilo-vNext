/**
 * Admin - report job listing (`GET /internal/v1/admin/reports/jobs`).
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ReportJobResource {
  id: string;
  type: string;
  attributes: {
    report_type: string;
    status: string;
    requested_by: string;
    parameters: string | null;
    result_url: string | null;
    error_message: string | null;
    queued_at: string;
    started_at: string | null;
    completed_at: string | null;
  };
}

export type ReportJobsListResponse = ApiResponse<ReportJobResource[]>;

/** Single job — `GET /internal/v1/reports/{id}` (same attributes as admin list rows). */
export type ReportJobDetailResponse = ApiResponse<ReportJobResource>;

const NON_TERMINAL_STATUSES = new Set(["QUEUED", "RUNNING", "PROCESSING", "GENERATING", "SCHEDULED"]);

export function useReportJob(id: string | undefined) {
  return useQuery({
    queryKey: ["reports", id],
    queryFn: () => apiClient.get<ReportJobDetailResponse>(`/internal/v1/reports/${id}`),
    enabled: Boolean(id),
    refetchInterval: (q) => {
      const s = (q.state.data?.data?.attributes?.status ?? "").toUpperCase();
      return NON_TERMINAL_STATUSES.has(s) ? 2500 : false;
    },
  });
}

export function useAdminReportJobs(params: { page?: number; size?: number }) {
  const page = params.page ?? 0;
  const size = params.size ?? 50;

  return useQuery({
    queryKey: ["admin", "reports", "jobs", page, size],
    queryFn: () =>
      apiClient.get<ReportJobsListResponse>(`/internal/v1/admin/reports/jobs?page=${page}&size=${size}`),
  });
}
