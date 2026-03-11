/**
 * Experience UI — Reports Query Hooks
 */

import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ReportResource {
  id: string;
  type: "report";
  attributes: {
    reportType: string;
    status: string;
    generatedAt: string;
    downloadUrl: string | null;
    [key: string]: unknown;
  };
}

interface GenerateReportPayload {
  reportType: string;
  parameters: Record<string, unknown>;
  [key: string]: unknown;
}

type ReportResponse = ApiResponse<ReportResource>;

export function useGenerateReport() {
  return useMutation<ReportResponse, unknown, GenerateReportPayload>({
    mutationFn: (payload: GenerateReportPayload) =>
      apiClient.post<ReportResponse>("/internal/v1/reports/generate", payload),
  });
}
