/**
 * Work Home (Phase F, web consumer of the BFF's Phase E composition).
 *
 * Source: BFF GET /internal/v1/work-home?context_id=…&mode=… (fat, first paint) and
 * GET /internal/v1/work-home/sections/{id} (retry/differential polling for one section).
 * Always 200 when authenticated — a downstream failure degrades its own section
 * (status: "DEGRADED"), never the whole page; a context/mode that cannot be re-proven
 * from source comes back as a generic, non-enumerating friendlyState instead of a section
 * list, exactly like the C3 session mint's denial shape.
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

export type WorkHomeSectionStatus = "OK" | "EMPTY" | "DEGRADED";

export const WORK_HOME_BUCKET_ORDER = [
  "ACT_NOW",
  "ACT_TODAY",
  "AWAITING_SOMEONE_ELSE",
  "UPCOMING",
  "FOR_AWARENESS",
  "COMPLETED",
] as const;

export type WorkHomeBucket = (typeof WORK_HOME_BUCKET_ORDER)[number];

export interface WorkHomeItem {
  id: string;
  kind?: string;
  source?: string;
  title?: string;
  description?: string;
  priority?: string;
  status?: string;
  due_at?: string | null;
  created_at?: string | null;
  href?: string;
  [key: string]: unknown;
}

export interface WorkHomeSection {
  sectionId: string;
  title: string;
  status: WorkHomeSectionStatus;
  items: WorkHomeItem[];
  buckets: Record<string, WorkHomeItem[]>;
  summary: Record<string, unknown>;
  note?: string | null;
  generatedAt: string;
}

export interface WorkHomeAttributes {
  contextId: string | null;
  family: string | null;
  mode: string | null;
  friendlyState: string;
  sections: WorkHomeSection[];
}

interface WorkHomeResource {
  id: string;
  type: "work-home";
  attributes: WorkHomeAttributes;
}

interface WorkHomeSectionResource {
  id: string;
  type: "work-home-section";
  attributes: WorkHomeSection;
}

type WorkHomeResponse = ApiResponse<WorkHomeResource>;
type WorkHomeSectionResponse = ApiResponse<WorkHomeSectionResource>;

/**
 * Shape returned while no data exists. Deliberately NOT a server verdict:
 * `friendlyState` stays empty. The old constant here carried
 * `friendlyState: "work_context_unavailable"` and was returned from a
 * catch-all — so a BFF 502 rendered as "the assignment could not be re-proven
 * from its source", a false explanation of a transport outage (Target
 * Architecture v1.3.2 §29.2, backlog item 72, test A81). A failed read is now
 * surfaced as `readFailed`, never converted into a verdict the server never
 * issued.
 */
const NO_DATA: WorkHomeAttributes = {
  contextId: null,
  family: null,
  mode: null,
  friendlyState: "",
  sections: [],
};

export function workHomeQueryKey(contextId: string | undefined, mode: string | undefined) {
  return ["work-home", contextId, mode];
}

/**
 * @param contextId the resolved work context id to render Work Home for (from
 *   `useSessionExperienceContract().contract.recommendedContextId` or a chooser selection).
 *   Query is disabled while absent — Work Home has no meaning without a context.
 * @param mode      optional — falls back to the context's own default mode on the BFF.
 */
export function useWorkHome(contextId: string | undefined, mode?: string) {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const query = useQuery<WorkHomeAttributes>({
    queryKey: workHomeQueryKey(contextId, mode),
    queryFn: async () => {
      const params = new URLSearchParams({ context_id: contextId as string });
      if (mode) params.set("mode", mode);
      // No catch: a transport failure must surface as a failed READ
      // (query.isError), not masquerade as a server-issued verdict. EMPTY is
      // a successful read whose sections are empty; UNAVAILABLE is a read
      // that did not succeed — the page renders them differently (A81).
      const res = await apiClient.get<WorkHomeResponse>(`/internal/v1/work-home?${params.toString()}`);
      return res.data.attributes;
    },
    enabled: isAuthenticated && !!user && !!contextId,
    staleTime: 30_000,
    refetchOnWindowFocus: false,
    retry: 1,
    retryDelay: 500,
  });

  return {
    workHome: query.data ?? NO_DATA,
    isLoading: query.isLoading,
    isError: query.isError,
    /** True when the work-home READ failed (transport/5xx). Distinct from a server-stated friendlyState. */
    readFailed: query.isError,
    refetch: query.refetch,
  };
}

/** Retries exactly one section (differential polling) and merges the result back into the cache. */
export function useWorkHomeSectionRetry(contextId: string | undefined, mode?: string) {
  const queryClient = useQueryClient();

  return async (sectionId: string) => {
    if (!contextId) return;
    const params = new URLSearchParams({ context_id: contextId });
    if (mode) params.set("mode", mode);
    const res = await apiClient.get<WorkHomeSectionResponse>(
      `/internal/v1/work-home/sections/${encodeURIComponent(sectionId)}?${params.toString()}`,
    );
    const updated = res.data.attributes;
    queryClient.setQueryData<WorkHomeAttributes>(workHomeQueryKey(contextId, mode), (prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        sections: prev.sections.map((s) => (s.sectionId === sectionId ? updated : s)),
      };
    });
  };
}
