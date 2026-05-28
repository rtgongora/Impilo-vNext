import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface ClinicalWorklistItem {
  id: string;
  kind: string;
  source: string;
  title: string;
  description?: string;
  priority?: string;
  status?: string;
  due_at?: string;
  created_at?: string;
  patient_id?: string;
  href?: string;
  [key: string]: unknown;
}

export interface ClinicalWorklistSummary {
  total: number;
  urgent: number;
  overdue: number;
  by_source: Record<string, number>;
}

export interface ClinicalWorklistData {
  items: ClinicalWorklistItem[];
  summary: ClinicalWorklistSummary;
}

export function useClinicalWorklist(params: {
  facilityId?: string;
  assigneeId?: string;
  size?: number;
}) {
  const qp = new URLSearchParams();
  if (params.facilityId) qp.set("facility_id", params.facilityId);
  if (params.assigneeId) qp.set("assignee_id", params.assigneeId);
  qp.set("size", String(params.size ?? 40));
  const qs = qp.toString();

  return useQuery<ApiResponse<ClinicalWorklistData>>({
    queryKey: [
      "clinical-worklist",
      params.facilityId ?? null,
      params.assigneeId ?? null,
      params.size ?? 40,
    ],
    queryFn: () =>
      apiClient.get<ApiResponse<ClinicalWorklistData>>(
        `/internal/v1/clinical-worklist${qs ? `?${qs}` : ""}`
      ),
    enabled: !!params.facilityId,
    staleTime: 15_000,
  });
}
