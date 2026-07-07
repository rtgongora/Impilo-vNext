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

// ── Groups ────────────────────────────────────────────────────────────────────
export interface SocialGroup {
  groupId: string;
  name: string;
  description?: string | null;
  groupKind: string;
  topic?: string | null;
  sensitiveFlag: boolean;
  visibility: string;
  joinPolicy: string;
  memberCount: number;
  status: string;
}

export interface SocialGroupMember {
  membershipId: string;
  personCpid: string;
  role: string;
  canPost: boolean;
  canComment: boolean;
  status: string;
}

export function useSocialGroups(mine = false) {
  return useQuery<ApiResponse<SocialGroup[]>>({
    queryKey: ["simba-social-groups", mine],
    queryFn: () =>
      apiClient.get(`/internal/v1/wellness/social/groups${mine ? "?mine=true" : ""}`),
  });
}

export function useSocialGroup(groupId?: string | null) {
  return useQuery<ApiResponse<SocialGroup> & { members: SocialGroupMember[] }>({
    queryKey: ["simba-social-group", groupId ?? null],
    queryFn: () => apiClient.get(`/internal/v1/wellness/social/groups/${groupId}`),
    enabled: !!groupId,
  });
}

export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<SocialGroup>,
    Error,
    {
      name: string;
      description?: string;
      group_kind?: string;
      topic?: string;
      sensitive_flag?: boolean;
      visibility?: string;
      join_policy?: string;
      max_members?: number;
    }
  >({
    mutationFn: (payload) => apiClient.post("/internal/v1/wellness/social/groups", payload),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-groups"] }),
  });
}

export function useJoinGroup() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<SocialGroupMember>, Error, { groupId: string }>({
    mutationFn: ({ groupId }) =>
      apiClient.post(`/internal/v1/wellness/social/groups/${groupId}/join`, {}),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["simba-social-groups"] });
      void qc.invalidateQueries({ queryKey: ["simba-social-group", vars.groupId] });
    },
  });
}

export function useLeaveGroup() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { groupId: string }>({
    mutationFn: ({ groupId }) =>
      apiClient.post(`/internal/v1/wellness/social/groups/${groupId}/leave`, {}),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["simba-social-groups"] });
      void qc.invalidateQueries({ queryKey: ["simba-social-group", vars.groupId] });
    },
  });
}

export function useApproveMember() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<SocialGroupMember>, Error, { groupId: string; memberCpid: string }>({
    mutationFn: ({ groupId, memberCpid }) =>
      apiClient.post(
        `/internal/v1/wellness/social/groups/${groupId}/members/${memberCpid}/approve`,
        {},
      ),
    onSuccess: (_r, vars) =>
      void qc.invalidateQueries({ queryKey: ["simba-social-group", vars.groupId] }),
  });
}

export function useSetMemberRole() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<SocialGroupMember>,
    Error,
    { groupId: string; memberCpid: string; role: string }
  >({
    mutationFn: ({ groupId, memberCpid, role }) =>
      apiClient.patch(
        `/internal/v1/wellness/social/groups/${groupId}/members/${memberCpid}/role`,
        { role },
      ),
    onSuccess: (_r, vars) =>
      void qc.invalidateQueries({ queryKey: ["simba-social-group", vars.groupId] }),
  });
}

export function usePublishAnnouncement() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<SocialPost>, Error, { groupId: string; body: string }>({
    mutationFn: ({ groupId, body }) =>
      apiClient.post(`/internal/v1/wellness/social/groups/${groupId}/announcements`, { body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-feed"] }),
  });
}

// ── Communities ─────────────────────────────────────────────────────────────
export interface SocialCommunity {
  communityId: string;
  name: string;
  description?: string | null;
  communityType: string;
  visibility: string;
  memberCount: number;
  status: string;
}

export function useSocialCommunities() {
  return useQuery<ApiResponse<SocialCommunity[]>>({
    queryKey: ["simba-social-communities"],
    queryFn: () => apiClient.get("/internal/v1/wellness/social/communities"),
  });
}

export function useSocialCommunity(communityId?: string | null) {
  return useQuery<ApiResponse<SocialCommunity> & { members: SocialGroupMember[] }>({
    queryKey: ["simba-social-community", communityId ?? null],
    queryFn: () => apiClient.get(`/internal/v1/wellness/social/communities/${communityId}`),
    enabled: !!communityId,
  });
}

export function useCreateCommunity() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<SocialCommunity>,
    Error,
    { name: string; description?: string; community_type?: string; visibility?: string }
  >({
    mutationFn: (payload) => apiClient.post("/internal/v1/wellness/social/communities", payload),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-communities"] }),
  });
}

export function useJoinCommunity() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { communityId: string }>({
    mutationFn: ({ communityId }) =>
      apiClient.post(`/internal/v1/wellness/social/communities/${communityId}/join`, {}),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["simba-social-communities"] });
      void qc.invalidateQueries({ queryKey: ["simba-social-community", vars.communityId] });
    },
  });
}

export function usePublishCommunityAnnouncement() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<SocialPost>, Error, { communityId: string; body: string }>({
    mutationFn: ({ communityId, body }) =>
      apiClient.post(`/internal/v1/wellness/social/communities/${communityId}/announcements`, {
        body,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-feed"] }),
  });
}

// ── Challenges (social extension) ────────────────────────────────────────────
export interface SocialChallenge {
  challengeId: string;
  title: string;
  description?: string | null;
  challengeType: string;
  targetValue?: number | null;
  unit?: string | null;
  status: string;
  campaignFlag?: boolean;
  visibility?: string;
}

export interface LeaderboardRow {
  rank: number;
  personCpid: string;
  progressValue: number;
  sharedToFeed: boolean;
}

export function useChallenges() {
  return useQuery<SocialChallenge[] | ApiResponse<SocialChallenge[]>>({
    queryKey: ["simba-social-challenges"],
    queryFn: () => apiClient.get("/internal/v1/wellness/challenges"),
  });
}

export function useChallengeLeaderboard(challengeId?: string | null) {
  return useQuery<ApiResponse<LeaderboardRow[]>>({
    queryKey: ["simba-social-challenge-leaderboard", challengeId ?? null],
    queryFn: () =>
      apiClient.get(`/internal/v1/wellness/social/challenges/${challengeId}/leaderboard`),
    enabled: !!challengeId,
  });
}

export function useJoinChallenge() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<unknown>, Error, { challengeId: string; personCpid: string }>({
    mutationFn: ({ challengeId, personCpid }) =>
      apiClient.post(`/internal/v1/wellness/challenges/${challengeId}/join`, {
        person_cpid: personCpid,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-challenges"] }),
  });
}

export function useShareChallengeToFeed() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<SocialPost>,
    Error,
    { challengeId: string; message?: string; visibility?: string }
  >({
    mutationFn: ({ challengeId, message, visibility }) =>
      apiClient.post(`/internal/v1/wellness/social/challenges/${challengeId}/share`, {
        message,
        visibility,
      }),
    onSuccess: (_r, vars) => {
      void qc.invalidateQueries({ queryKey: ["simba-social-feed"] });
      void qc.invalidateQueries({
        queryKey: ["simba-social-challenge-leaderboard", vars.challengeId],
      });
    },
  });
}

// ── Notifications ────────────────────────────────────────────────────────────
export interface SocialNotification {
  notificationId: string;
  actorCpid?: string | null;
  notificationType: string;
  subjectType?: string | null;
  subjectId?: string | null;
  summary?: string | null;
  status: string;
  createdAt: string;
}

export function useSocialNotifications(limit = 20) {
  return useQuery<{ data: SocialNotification[]; meta: { next_cursor: number | null }; unread: number }>(
    {
      queryKey: ["simba-social-notifications"],
      queryFn: () =>
        apiClient.get(`/internal/v1/wellness/social/notifications?limit=${limit}`),
    },
  );
}

export function useMarkNotificationRead() {
  const qc = useQueryClient();
  return useMutation<ApiResponse<SocialNotification>, Error, { notificationId: string }>({
    mutationFn: ({ notificationId }) =>
      apiClient.post(`/internal/v1/wellness/social/notifications/${notificationId}/read`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-notifications"] }),
  });
}

// ── Programme engagement dashboard (aggregate-only) ──────────────────────────
export interface ProgrammeEngagement {
  programme_id: string | null;
  groups: number;
  communities: number;
  active_members: number;
  posts: number;
  official_posts: number;
  challenge_enrolment: number;
  challenge_completions: number;
  challenge_completion_pct: number;
  reported_content: number;
  moderation_backlog: number;
  safety_escalations: number;
}

export function useProgrammeEngagement(programmeId?: string | null) {
  return useQuery<ApiResponse<ProgrammeEngagement>>({
    queryKey: ["simba-social-programme-engagement", programmeId ?? null],
    queryFn: () => {
      const q = programmeId ? `?programme_id=${programmeId}` : "";
      return apiClient.get(`/internal/v1/wellness/social/programme-engagement/dashboard${q}`);
    },
  });
}

// ── Moderation inbox (provider/programme workbench) ──────────────────────────
export interface ContentReport {
  reportId: string;
  reporterCpid: string;
  subjectType: string;
  subjectId: string;
  subjectOwnerCpid?: string | null;
  reason: string;
  detail?: string | null;
  careLinkageId?: string | null;
  status: string;
  createdAt: string;
}

export function useModerationInbox(status = "OPEN") {
  return useQuery<{ data: ContentReport[]; backlog: number }>({
    queryKey: ["simba-social-moderation-inbox", status],
    queryFn: () =>
      apiClient.get(`/internal/v1/wellness/social/moderation/inbox?status=${status}`),
  });
}

export function useModerationAction() {
  const qc = useQueryClient();
  return useMutation<
    ApiResponse<unknown>,
    Error,
    { reportId: string; action_state: string; rationale?: string }
  >({
    mutationFn: ({ reportId, action_state, rationale }) =>
      apiClient.post(`/internal/v1/wellness/social/moderation/${reportId}/action`, {
        action_state,
        rationale,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["simba-social-moderation-inbox"] }),
  });
}
