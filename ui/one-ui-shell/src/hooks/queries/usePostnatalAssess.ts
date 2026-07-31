import { useMutation } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface PncAlert {
  code: string;
  name: string;
  severity: string;
  required_action?: string | null;
  message?: string | null;
}

export interface PncMaternalAssessResponse {
  alerts: PncAlert[];
  top_line_action?: string | null;
  not_assessed: string[];
  incomplete: boolean;
  screening_complete: boolean;
  no_danger_signs_found: boolean;
  note?: string | null;
}

export interface PncNewbornAssessResponse {
  psbi: {
    alerts: PncAlert[];
    not_assessed: string[];
    incomplete: boolean;
    no_danger_signs_found?: boolean;
  };
  pnc_specific_alerts: PncAlert[];
  top_line_action?: string | null;
  systemic_emergency: boolean;
  incomplete: boolean;
  note?: string | null;
  psbi_delegation_note?: string | null;
}

export function usePncMaternalAssess() {
  return useMutation({
    mutationFn: (body: { facts: Record<string, unknown>; screeningComplete: boolean }) =>
      apiClient.post<ApiResponse<PncMaternalAssessResponse>>(
        "/internal/v1/clinical/maternal/pnc/maternal/assess",
        body,
      ),
  });
}

export function usePncNewbornAssess() {
  return useMutation({
    mutationFn: (body: { ageDays: number; facts: Record<string, unknown>; screeningComplete: boolean }) =>
      apiClient.post<ApiResponse<PncNewbornAssessResponse>>(
        "/internal/v1/clinical/maternal/pnc/newborn/assess",
        body,
      ),
  });
}

export function screeningIncomplete(answers: Record<string, unknown>): boolean {
  return answers.screeningComplete !== "YES";
}
