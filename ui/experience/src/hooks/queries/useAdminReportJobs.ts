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

export function useAdminReportJobs(params: { page?: number; size?: number }) {
  const page = params.page ?? 0;
  const size = params.size ?? 50;

  return useQuery({
    queryKey: ["admin", "reports", "jobs", page, size],
    queryFn: () =>
      apiClient.get<ReportJobsListResponse>(`/internal/v1/admin/reports/jobs?page=${page}&size=${size}`),
  });
}
