/**
 * Experience UI — SIMBA wellness-SOCIAL hooks (sovereign layer, /internal/v1/wellness/social/**).
 * DISTINCT from the generic community-service social stack. Served by Simba (8125) via the
 * experience-bff proxy; reel upload/playback go through BFF composition endpoints.
 */

import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

// ── Feed ────────────────────────────────────────────────────────────────────
export interface SocialPost {
  postId: string;
  id: number;
  actorCpid: string;
  actorType: string;
  body: string;
  visibility: string;
  sensitiveCategory?: string | null;
  reactionCount: number;
  commentCount: number;
  createdAt: string;
}

interface FeedPageResponse {
  data: SocialPost[];
  meta: { next_cursor: number | null };
}

export function useSocialFeed(filter?: string, groupId?: string | null, limit = 20) {
  return useInfiniteQuery<FeedPageResponse>({
    queryKey: ["simba-social-feed", filter ?? "ALL", groupId ?? null],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const sp = new URLSearchParams();
      if (filter) sp.set("filter", filter);
      if (groupId) sp.set("group_id", groupId);
      sp.set("limit", String(limit));
      if (pageParam != null) sp.set("cursor", String(pageParam));
      return apiClient.get<FeedPageResponse>(`/internal/v1/wellness/social/feed?${sp.toString()}`);
    },
    getNextPageParam: (last) => last.meta?.next_cursor ?? undefined,
  });
}

export function useCreatePost() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<SocialPost>,
    Error,
    {
      body: string;
      visibility?: string;
      sensitive_category?: string;
      group_id?: string;
      milestone_ref?: string;
    }
  >({
    mutationFn: (payload) => apiClient.post("/internal/v1/wellness/social/posts", payload),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-feed"] }),
  });
}

// ── Interactions ────────────────────────────────────────────────────────────
export function useReact() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<{ active: boolean; count: number }>,
    Error,
    { subjectType: string; subjectId: string; reaction: string }
  >({
    mutationFn: ({ subjectType, subjectId, reaction }) =>
      apiClient.post(
        `/internal/v1/wellness/social/${subjectType.toLowerCase()}/${subjectId}/react`,
        { reaction },
      ),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-feed"] }),
  });
}

export function useBookmark() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<{ saved: boolean }>,
    Error,
    { subjectType: string; subjectId: string }
  >({
    mutationFn: ({ subjectType, subjectId }) =>
      apiClient.post(
        `/internal/v1/wellness/social/${subjectType.toLowerCase()}/${subjectId}/bookmark`,
        {},
      ),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["simba-social-saved"] });
    },
  });
}

export function usePostComments(postId?: string | null) {
  return useQuery<ApiResponse<unknown[]>>({
    queryKey: ["simba-social-comments", postId ?? null],
    queryFn: () => apiClient.get(`/internal/v1/wellness/social/posts/${postId}/comments`),
    enabled: !!postId,
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { postId: string; body: string }>({
    mutationFn: ({ postId, body }) =>
      apiClient.post(`/internal/v1/wellness/social/posts/${postId}/comments`, { body }),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["simba-social-comments", vars.postId] });
      void qc.invalidateQueries({ queryKey: ["simba-social-feed"] });
    },
  });
}

// ── Reels ───────────────────────────────────────────────────────────────────
export function useReelFeed(limit = 10) {
  return useInfiniteQuery<{ data: unknown[]; meta: { next_cursor: number | null } }>({
    queryKey: ["simba-social-reels"],
    initialPageParam: null as number | null,
    queryFn: ({ pageParam }) => {
      const sp = new URLSearchParams();
      sp.set("limit", String(limit));
      if (pageParam != null) sp.set("cursor", String(pageParam));
      return apiClient.get(`/internal/v1/wellness/social/reels?${sp.toString()}`);
    },
    getNextPageParam: (last) => last.meta?.next_cursor ?? undefined,
  });
}

export function useReelPlayback(reelId?: string | null) {
  return useQuery<ApiResponse<{ reel: unknown; playback_url: string; thumbnail_url?: string }>>({
    queryKey: ["simba-social-reel-playback", reelId ?? null],
    queryFn: () => apiClient.get(`/internal/v1/wellness/social/reels/${reelId}/playback`),
    enabled: !!reelId,
  });
}

export function useUploadReel() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, FormData>({
    mutationFn: (form) => apiClient.postForm("/internal/v1/wellness/social/reels/upload", form),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-reels"] }),
  });
}

// ── Moderation / safety report ──────────────────────────────────────────────
export function useReportContent() {
  return useMutation<
    ApiResponse<unknown> & { escalated?: boolean },
    Error,
    {
      subject_type: string;
      subject_id: string;
      subject_owner_cpid?: string;
      reason: string;
      detail?: string;
    }
  >({
    mutationFn: (payload) =>
      apiClient.post("/internal/v1/wellness/social/moderation/reports", payload),
  });
}

/** Flatten paginated feed pages. */
export function flattenFeed(pages?: { data: SocialPost[] }[]): SocialPost[] {
  if (!pages) return [];
  return pages.flatMap((p) => p.data ?? []);
}
