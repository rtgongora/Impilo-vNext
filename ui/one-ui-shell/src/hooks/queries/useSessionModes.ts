/**
 * Session-template registry hooks — reads the governed doctrine-as-data
 * templates the W0 session suite serves via the BFF
 * (`GET /internal/v1/session-templates[/{mode}]`).
 *
 * Read-only consumers: this stream never modifies the template registry.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export interface SessionTemplateSummary {
  sessionMode: string;
  owningService?: string;
  joinFlow?: string;
  maxParticipants?: number;
  tokenTtlSeconds?: number;
  moderationRequired?: boolean;
  auditDepth?: string;
  roles?: Record<string, unknown>;
  recording?: { consentRequired?: boolean; defaultOn?: boolean; [key: string]: unknown };
  khulumaNotificationKeys?: string[];
  [key: string]: unknown;
}

/** List of governed session-template mode names available from the registry. */
export function useSessionTemplateModes() {
  return useQuery({
    queryKey: ["session-template-modes"],
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<string[]> => {
      const res = await apiClient.get<ApiResponse<string[]> | string[]>(
        "/internal/v1/session-templates",
      );
      if (Array.isArray(res)) return res;
      const data = (res as ApiResponse<string[]>)?.data;
      return Array.isArray(data) ? data : [];
    },
  });
}

/** Full governed template for one mode (null when the registry lacks it). */
export function useSessionTemplateDetail(mode: string | null) {
  return useQuery({
    queryKey: ["session-template-detail", mode],
    enabled: !!mode,
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<SessionTemplateSummary | null> => {
      if (!mode) return null;
      try {
        const res = await apiClient.get<SessionTemplateSummary | { data: SessionTemplateSummary }>(
          `/internal/v1/session-templates/${encodeURIComponent(mode)}`,
        );
        const payload =
          res && typeof res === "object" && "data" in res && !("sessionMode" in res)
            ? (res as { data: SessionTemplateSummary }).data
            : (res as SessionTemplateSummary);
        return payload?.sessionMode ? payload : null;
      } catch {
        return null;
      }
    },
  });
}
