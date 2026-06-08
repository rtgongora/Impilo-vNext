/**
 * Experience UI — Reports Query Hooks
 */

import { useMutation, useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/** Legacy shape used by /reports/* pages */
export interface ReportResource {
  id: string;
  type: string;
  attributes: {
    reportType: string;
    status: string;
    generatedAt: string;
    downloadUrl: string | null;
    [key: string]: unknown;
  };
}

/** Response from POST /internal/v1/reports/generate */
export interface ReportJobCreatedResource {
  id: string;
  type: string;
  attributes: {
    report_type: string;
    status: string;
    requested_by: string;
    queued_at: string;
    [key: string]: unknown;
  };
}

export type ReportGenerateResponse = ApiResponse<ReportJobCreatedResource>;

function readRequestedByFromSession(): string {
  if (typeof window === "undefined") return "unknown";
  try {
    const raw = sessionStorage.getItem("exp:auth_user");
    if (!raw) return "unknown";
    const u = JSON.parse(raw) as { id?: string; email?: string; username?: string; name?: string };
    return u.email ?? u.username ?? u.name ?? u.id ?? "unknown";
  } catch {
    return "unknown";
  }
}

/** Accepts either camelCase (UI) or snake_case (BFF-aligned) keys */
export type GenerateReportPayload = {
  reportType?: string;
  report_type?: string;
  parameters?: Record<string, unknown>;
  requestedBy?: string;
  requested_by?: string;
};

function toGenerateBody(payload: GenerateReportPayload) {
  const report_type = payload.report_type ?? payload.reportType;
  if (!report_type) throw new Error("report_type is required");
  return {
    report_type,
    parameters: payload.parameters ?? {},
    requested_by: payload.requested_by ?? payload.requestedBy ?? readRequestedByFromSession(),
  };
}

export function useGenerateReport() {
  return useMutation<ReportGenerateResponse, unknown, GenerateReportPayload>({
    mutationFn: (payload) =>
      apiClient.post<ReportGenerateResponse>("/internal/v1/reports/generate", toGenerateBody(payload)),
  });
}

export function useReportJob(jobId: string | null | undefined) {
  return useQuery<ApiResponse<ReportJobCreatedResource>>({
    queryKey: ["report-job", jobId],
    queryFn: () => apiClient.get<ApiResponse<ReportJobCreatedResource>>(`/internal/v1/reports/${jobId}`),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.data?.attributes?.status;
      if (!status || status === "completed" || status === "failed") return false;
      return 3000;
    },
  });
}
