"use client";

/**
 * useSessionTemplate — fetches the governed session-mode template
 * (doctrine-as-data from contracts/schemas/session-templates/) via the BFF.
 *
 * Returns undefined while loading or when the template is unavailable
 * (404 / error); per-domain fallbacks stay in the calling pages so a missing
 * template never blocks a session.
 */

import { useQuery } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { SessionMode, SessionTemplate } from "../../../../contracts/impilo-live";

export function useSessionTemplate(mode: SessionMode): SessionTemplate | undefined {
  const { data } = useQuery({
    queryKey: ["session-template", mode],
    staleTime: Infinity,
    retry: false,
    queryFn: async (): Promise<SessionTemplate | null> => {
      try {
        const res = await apiClient.get<SessionTemplate | { data: SessionTemplate }>(
          `/internal/v1/session-templates/${mode}`
        );
        const payload =
          res && typeof res === "object" && "data" in res && !("sessionMode" in res)
            ? (res as { data: SessionTemplate }).data
            : (res as SessionTemplate);
        return payload?.sessionMode ? payload : null;
      } catch {
        return null;
      }
    },
  });

  return data ?? undefined;
}
