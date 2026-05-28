"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, type ApiResponse } from "@/lib/api-client";

export type FeedScope =
  | "home"
  | "following"
  | "wellness"
  | "learning"
  | "public_health"
  | "facility"
  | "community"
  | "group"
  | "page"
  | "saved"
  | "drafts"
  | "my_posts"
  | "moderation"
  | "announcements";

export type SocialPostKind =
  | "text"
  | "announcement"
  | "question"
  | "event"
  | "poll"
  | "learning"
  | "health_promotion";

export type SocialVisibility =
  | "public"
  | "followers"
  | "community"
  | "private"
  | "tenant"
  | "facility"
  | "group"
  | "page_followers"
  | "role_only";

export type ReactionType = "LIKE" | "LOVE" | "SUPPORT" | "INSIGHT";

export type ComposerOp =
  | "improve"
  | "simplify"
  | "translate"
  | "draft_announcement"
  | "summarise_thread"
  | "suggest_tags"
  | "safety_check";

export interface SocialAuthor {
  kind: string;
  id: string;
  displayName: string;
  roleBadge?: string;
  facilityName?: string;
  verified?: boolean;
}

export interface SocialPost {
  id: string;
  tenantId?: string;
  kind?: SocialPostKind;
  status?: string;
  visibility?: SocialVisibility;
  body?: string;
  content?: string;
  author?: SocialAuthor;
  authorActorId?: string;
  reactionSummary?: {
    total: number;
    byType: Record<string, number>;
    viewerReaction: ReactionType | null;
  };
  commentCount?: number;
  likeCount?: number;
  bookmarked?: boolean;
  topics?: string[];
  pinned?: boolean;
  createdAt?: string;
  publishedAt?: string;
  postType?: string;
  title?: string;
  source?: string;
  attachments?: Array<{
    id?: string;
    url?: string;
    kind?: string;
    label?: string;
    previewText?: string;
  }>;
}

export interface SocialCommunity {
  id: string;
  name: string;
  category: string;
  memberCount: number;
  coverColor?: string;
  kind?: string;
  description?: string;
  viewerMembership?: string;
}

export interface SocialGroup {
  id: string;
  name: string;
  communityId?: string;
  category?: string;
  memberCount?: number;
  description?: string;
  viewerMembership?: string;
}

export interface SocialPage {
  id: string;
  name: string;
  kind: string;
  followerCount: number;
  verified?: boolean;
  bio?: string;
  viewerFollows?: boolean;
  adminIds?: string[];
}

export interface SocialSuggestions {
  communities: SocialCommunity[];
  pages: SocialPage[];
  groups?: SocialGroup[];
}

export interface CreatePostInput {
  kind: SocialPostKind;
  body: string;
  visibility: SocialVisibility;
  title?: string;
  topics?: string[];
  communityId?: string;
  groupId?: string;
  pageId?: string;
  scope?: {
    communityId?: string;
    groupId?: string;
    pageId?: string;
  };
  saveAsDraft?: boolean;
  source?: string;
  status?: "published" | "draft";
  scheduledAt?: string | null;
}

export interface ModerationCase {
  id: string;
  postId: string;
  reporterActorId: string;
  reason: string;
  details?: string;
  status: string;
}

function asArray<T>(raw: unknown): T[] {
  if (Array.isArray(raw)) return raw as T[];
  if (raw && typeof raw === "object") {
    const record = raw as Record<string, unknown>;
    if (Array.isArray(record.items)) return record.items as T[];
    if (Array.isArray(record.data)) return record.data as T[];
    if (Array.isArray(record.content)) return record.content as T[];
  }
  return [];
}

function mapCommunity(row: Record<string, unknown>): SocialCommunity {
  return {
    id: String(row.id ?? ""),
    name: String(row.name ?? "Community"),
    category: String(row.category ?? "general"),
    memberCount: Number(row.memberCount ?? row.member_count ?? 0),
    coverColor: row.coverColor ? String(row.coverColor) : undefined,
  };
}

function mapPage(row: Record<string, unknown>): SocialPage {
  return {
    id: String(row.id ?? ""),
    name: String(row.name ?? "Page"),
    kind: String(row.kind ?? "official"),
    followerCount: Number(row.followerCount ?? row.follower_count ?? 0),
  };
}

function parseSuggestions(raw: unknown): SocialSuggestions {
  if (!raw || typeof raw !== "object") {
    return { communities: [], pages: [] };
  }
  const record = raw as Record<string, unknown>;
  return {
    communities: asArray<Record<string, unknown>>(record.communities).map(mapCommunity),
    pages: asArray<Record<string, unknown>>(record.pages).map(mapPage),
    groups: asArray<Record<string, unknown>>(record.groups).map((g) => ({
      id: String(g.id ?? ""),
      name: String(g.name ?? "Group"),
      communityId: g.communityId ? String(g.communityId) : undefined,
    })),
  };
}

export function useSocialFeed(scope: FeedScope, scopeId?: string) {
  return useQuery({
    queryKey: ["social", "feed", scope, scopeId ?? ""],
    queryFn: async () => {
      const params = new URLSearchParams({ scope, limit: "30", offset: "0" });
      if (scopeId) params.set("scopeId", scopeId);
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/social/feed?${params.toString()}`,
      );
      return { items: asArray<SocialPost>(res.data) };
    },
  });
}

export function useSocialSuggestions() {
  return useQuery({
    queryKey: ["social", "suggestions"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/social/suggestions");
      return parseSuggestions(res.data);
    },
  });
}

export function useSocialCommunities(category?: string, q?: string) {
  return useQuery({
    queryKey: ["social", "communities", category ?? "", q ?? ""],
    queryFn: async () => {
      const params = new URLSearchParams();
      if (category) params.set("category", category);
      if (q) params.set("q", q);
      const qs = params.toString() ? `?${params.toString()}` : "";
      const res = await apiClient.get<ApiResponse<unknown>>(`/internal/v1/social/communities${qs}`);
      return asArray<Record<string, unknown>>(res.data).map(mapCommunity);
    },
  });
}

export function useSocialCommunity(id: string) {
  return useQuery({
    queryKey: ["social", "community", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/social/communities/${encodeURIComponent(id)}`,
      );
      return mapCommunity(res.data ?? {});
    },
    enabled: id.length > 0,
  });
}

function resolveId(input: string | { id: string }): string {
  return typeof input === "string" ? input : input.id;
}

export function useJoinCommunity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: string | { id: string }) =>
      apiClient.post(`/internal/v1/social/communities/${resolveId(input)}/join`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "communities"] }),
  });
}

export function useLeaveCommunity() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: string | { id: string }) =>
      apiClient.post(`/internal/v1/social/communities/${resolveId(input)}/leave`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "communities"] }),
  });
}

export function useSocialGroups(communityId?: string) {
  return useQuery({
    queryKey: ["social", "groups", communityId ?? ""],
    queryFn: async () => {
      const qs = communityId ? `?communityId=${encodeURIComponent(communityId)}` : "";
      const res = await apiClient.get<ApiResponse<unknown>>(`/internal/v1/social/groups${qs}`);
      return asArray<Record<string, unknown>>(res.data).map((row) => ({
        id: String(row.id ?? ""),
        name: String(row.name ?? "Group"),
        communityId: row.communityId ? String(row.communityId) : undefined,
        category: row.category ? String(row.category) : undefined,
        memberCount: Number(row.memberCount ?? 0),
        description: row.description ? String(row.description) : undefined,
      })) as SocialGroup[];
    },
  });
}

export function useSocialGroup(id: string) {
  return useQuery({
    queryKey: ["social", "group", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/social/groups/${encodeURIComponent(id)}`,
      );
      const row = res.data ?? {};
      return {
        id: String(row.id ?? id),
        name: String(row.name ?? "Group"),
        communityId: row.communityId ? String(row.communityId) : undefined,
        category: row.category ? String(row.category) : undefined,
        memberCount: Number(row.memberCount ?? 0),
        description: row.description ? String(row.description) : undefined,
      } as SocialGroup;
    },
    enabled: id.length > 0,
  });
}

export function useSocialPages(kind?: string) {
  return useQuery({
    queryKey: ["social", "pages", kind ?? ""],
    queryFn: async () => {
      const qs = kind ? `?kind=${encodeURIComponent(kind)}` : "";
      const res = await apiClient.get<ApiResponse<unknown>>(`/internal/v1/social/pages${qs}`);
      return asArray<Record<string, unknown>>(res.data).map(mapPage);
    },
  });
}

export function useSocialPage(id: string) {
  return useQuery({
    queryKey: ["social", "page", id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/social/pages/${encodeURIComponent(id)}`,
      );
      return mapPage(res.data ?? {});
    },
    enabled: id.length > 0,
  });
}

export function useFollowPage() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: string | { id: string }) =>
      apiClient.post(`/internal/v1/social/pages/${resolveId(input)}/follow`, {}),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "pages"] }),
  });
}

export function useCreatePost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: CreatePostInput) =>
      apiClient.post<ApiResponse<SocialPost>>("/internal/v1/social/posts", {
        kind: input.kind,
        body: input.body,
        title: input.title,
        visibility: input.visibility,
        topics: input.topics ?? [],
        communityId: input.communityId ?? input.scope?.communityId,
        groupId: input.groupId ?? input.scope?.groupId,
        pageId: input.pageId ?? input.scope?.pageId,
        status: input.saveAsDraft ? "draft" : (input.status ?? "published"),
        source: input.source,
        scheduledAt: input.scheduledAt,
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "feed"] }),
  });
}

export function useNompiloComposer() {
  return useMutation({
    mutationFn: async (input: {
      op: ComposerOp;
      content: string;
      language?: string;
      audience?: SocialVisibility;
    }): Promise<{ result: unknown; fallbackUsed?: boolean }> => {
      try {
        const res = await apiClient.post<ApiResponse<unknown>>("/internal/v1/social/composer/assist", input);
        return { result: res.data, fallbackUsed: false };
      } catch {
        if (input.op === "suggest_tags") {
          const words = input.content
            .toLowerCase()
            .split(/\W+/)
            .filter((w) => w.length > 4)
            .slice(0, 3);
          return { result: words, fallbackUsed: true };
        }
        if (input.op === "safety_check") {
          return { result: { flagged: false }, fallbackUsed: true };
        }
        return { result: input.content, fallbackUsed: true };
      }
    },
  });
}

export function useReactToPost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ postId, type }: { postId: string; type: ReactionType }) =>
      apiClient.post(`/internal/v1/social/posts/${postId}/reactions`, { type }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "feed"] }),
  });
}

export function useUnreactPost() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: string | { postId: string }) => {
      const postId = typeof input === "string" ? input : input.postId;
      return apiClient.delete(`/internal/v1/social/posts/${postId}/reactions`);
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "feed"] }),
  });
}

export function usePostComments(postId: string | null) {
  return useQuery({
    queryKey: ["social", "comments", postId],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>(
        `/internal/v1/social/posts/${postId}/comments`,
      );
      return asArray<Record<string, unknown>>(res.data);
    },
    enabled: !!postId,
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ postId, body }: { postId: string; body: string }) =>
      apiClient.post(`/internal/v1/social/posts/${postId}/comments`, { body }),
    onSuccess: (_data, vars) => {
      void qc.invalidateQueries({ queryKey: ["social", "comments", vars.postId] });
      void qc.invalidateQueries({ queryKey: ["social", "feed"] });
    },
  });
}

export function useToggleBookmark() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ postId, on }: { postId: string; on: boolean }) =>
      on
        ? apiClient.post(`/internal/v1/social/posts/${postId}/bookmark`, {})
        : apiClient.delete(`/internal/v1/social/posts/${postId}/bookmark`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["social", "feed"] }),
  });
}

export function useReportPost() {
  return useMutation({
    mutationFn: ({ postId, reason }: { postId: string; reason: string }) =>
      apiClient.post(`/internal/v1/social/posts/${postId}/report`, { reason }),
  });
}

export function useModerationQueue() {
  return useQuery({
    queryKey: ["social", "moderation", "queue"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<unknown>>("/internal/v1/social/moderation/queue");
      return asArray<ModerationCase>(res.data);
    },
  });
}

export function useResolveModeration() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (input: { caseId: string; decision: string; rationale?: string }) => {
      await apiClient.post(`/internal/v1/social/moderation/${input.caseId}/resolve`, {
        decision: input.decision,
        rationale: input.rationale ?? "",
      });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social", "moderation"] });
    },
  });
}
