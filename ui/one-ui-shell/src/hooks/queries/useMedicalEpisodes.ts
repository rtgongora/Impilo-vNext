/**
 * Medical episodes — BFF /internal/v1/medical-episodes → PCT V101.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface MedicalEpisode {
  episode_id: string;
  subject_cpid: string;
  episode_type: string;
  status: string;
  title: string | null;
  responsible_service: string | null;
  started_on: string | null;
  ended_on: string | null;
  end_reason: string | null;
}

export function useMedicalEpisodes(subjectCpid: string, openOnly = false) {
  return useQuery({
    queryKey: ["medical-episodes", subjectCpid, openOnly],
    queryFn: async () => {
      const qs = new URLSearchParams({
        subject_cpid: subjectCpid,
        open_only: String(openOnly),
      });
      const res = await apiClient.get<ApiResponse<Record<string, unknown>[]>>(
        `/internal/v1/medical-episodes?${qs.toString()}`
      );
      const rows = res.data ?? [];
      return {
        ...res,
        data: rows.map(
          (row): MedicalEpisode => ({
            episode_id: String(row.episode_id ?? ""),
            subject_cpid: String(row.subject_cpid ?? ""),
            episode_type: String(row.episode_type ?? ""),
            status: String(row.status ?? ""),
            title: row.title == null ? null : String(row.title),
            responsible_service:
              row.responsible_service == null ? null : String(row.responsible_service),
            started_on: row.started_on == null ? null : String(row.started_on),
            ended_on: row.ended_on == null ? null : String(row.ended_on),
            end_reason: row.end_reason == null ? null : String(row.end_reason),
          })
        ),
      } as ApiResponse<MedicalEpisode[]>;
    },
    enabled: !!subjectCpid,
  });
}
