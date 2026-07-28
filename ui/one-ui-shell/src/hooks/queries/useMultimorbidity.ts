"use client";

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

/**
 * The multimorbidity view (brief.md §9) — eleven panels and seven detections over the whole patient.
 *
 * The state fields are the whole point of this type. A panel is KNOWN or UNKNOWN and a detection is
 * DETECTED, NOT_DETECTED or UNDETERMINED, because this is the one screen a clinician opens to find
 * the harm the single-disease screens hid. If it rendered an unreadable source as an empty panel it
 * would hide that harm more convincingly than the screens it replaces.
 */

export type PanelState = "KNOWN" | "UNKNOWN";
export type DetectionState = "DETECTED" | "NOT_DETECTED" | "UNDETERMINED";

export interface MultimorbidityPanel {
  key: string;
  title: string;
  state: PanelState;
  entries: string[];
  unknown_reason?: string;
}

export interface MultimorbidityFinding {
  code: string;
  severity: string;
  message: string;
  explanation: string;
  required_action: string;
}

export interface MultimorbidityDetection {
  key: string;
  title: string;
  state: DetectionState;
  findings: MultimorbidityFinding[];
  undetermined_reason?: string;
}

export interface MultimorbidityAssessment {
  panels: MultimorbidityPanel[];
  detections: MultimorbidityDetection[];
  incomplete: boolean;
  content_version: string | null;
  approval_status: string | null;
  completeness_note?: string;
}

export function useMultimorbidity(subjectCpid: string) {
  return useQuery<ApiResponse<MultimorbidityAssessment>>({
    queryKey: ["multimorbidity", { subjectCpid }],
    queryFn: () =>
      apiClient.get<ApiResponse<MultimorbidityAssessment>>(
        `/internal/v1/medicine/multimorbidity?subject_cpid=${encodeURIComponent(subjectCpid)}`,
      ),
    enabled: !!subjectCpid,
  });
}
