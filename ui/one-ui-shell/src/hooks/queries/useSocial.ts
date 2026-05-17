/**
 * Social Timeline — React Query hooks.
 *
 * Backed by the Experience BFF SocialController at /internal/v1/social/**.
 * Shared between the Impilo One UI Shell (Next.js) web experience and any
 * other web surfaces that want to render the same timeline.
 *
 * Each hook accepts the actor context implicitly through api-client header
 * injection (X-Tenant-ID, X-Actor-ID, X-Purpose-Of-Use, etc.).
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";

const BASE = "/internal/v1/social";

export type FeedScope =
  | "home"
  | "following"
  | "community"
  | "group"
  | "page"
  | "facility"
  | "public_health"
  | "learning"
  | "wellness"
  | "saved"
  | "my_posts"
  | "drafts"
  | "moderation"
  | "announcements";

export type SocialPostKind =
  | "text"
  | "announcement"
  | "question"
  | "event"
  | "poll"
  | "learning"
  | "health_promotion"
  | "campaign"
  | "facility_update"
  | "resource";

export type SocialVisibility =
  | "public"
  | "tenant"
  | "organisation"
  | "facility"
  | "community"
  | "group"
  | "page_followers"
  | "role_only"
  | "private";

export type ReactionType = "LIKE" | "CELEBRATE" | "SUPPORT" | "INSIGHTFUL" | "CARE";

export interface SocialIdentity {
  kind: "user" | "community" | "group" | "page" | "system";
  id: string;
  displayName: string;
  avatarUrl?: string;
  roleBadge?: string;
  verified?: boolean;
  facilityName?: string;
}

export interface SocialAttachment {
  id?: string;
  kind?: "image" | "document" | "link" | "video" | "audio" | "event_card" | "poll_card" | "resource_card";
  mediaType?: string;
  url?: string;
  previewText?: string;
  metadata?: Record<string, unknown>;
}

export interface SocialReactionSummary {
  total: number;
  byType: Partial<Record<ReactionType, number>>;
  viewerReaction?: ReactionType | null;
}

export interface SocialScope {
  communityId?: string | null;
  groupId?: string | null;
  pageId?: string | null;
  facilityId?: string | null;
}

export interface SocialPost {
  id: string;
  tenantId: string;
  kind: SocialPostKind;
  status: "draft" | "scheduled" | "published" | "archived" | "rejected" | "removed";
  visibility: SocialVisibility;
  title?: string | null;
  body: string;
  topics?: string[];
  author: SocialIdentity;
  scope?: SocialScope;
  attachments?: SocialAttachment[];
  reactionSummary?: SocialReactionSummary;
  commentCount?: number;
  bookmarked?: boolean;
  pinned?: boolean;
  source?: string;
  createdAt?: string;
  publishedAt?: string;
  scheduledFor?: string;
  nompiloFlags?: Record<string, unknown>;
}

export interface SocialCommunity {
  id: string;
  tenantId: string;
  slug: string;
  name: string;
  description?: string;
  category: string;
  kind: "public" | "private" | "restricted" | "verified";
  coverColor?: string;
  iconUrl?: string;
  coverImageUrl?: string;
  memberCount: number;
  rules?: string[];
  moderatorIds?: string[];
  viewerMembership: "none" | "requested" | "member" | "moderator" | "admin";
  createdAt?: string;
  nompiloAssistantEnabled?: boolean;
}

export interface SocialGroup {
  id: string;
  communityId?: string | null;
  name: string;
  description?: string;
  category?: string;
  facilityId?: string | null;
  organisationId?: string | null;
  memberCount: number;
  viewerMembership: "none" | "requested" | "member" | "moderator" | "admin";
  createdAt?: string;
}

export interface SocialActionLink {
  label: string;
  href: string;
  kind?: string;
}

export interface SocialPage {
  id: string;
  tenantId: string;
  kind: "facility" | "organisation" | "programme" | "campaign" | "service" | "provider" | "department" | "district" | "province";
  name: string;
  slug: string;
  bio?: string;
  iconUrl?: string;
  coverImageUrl?: string;
  verified?: boolean;
  followerCount: number;
  actionLinks?: SocialActionLink[];
  adminIds?: string[];
  viewerFollows?: boolean;
  createdAt?: string;
}

export interface SocialFeedPage {
  scope: FeedScope;
  items: SocialPost[];
  nextCursor?: string | null;
}

export interface SocialSuggestionsBundle {
  communities: SocialCommunity[];
  groups: SocialGroup[];
  pages: SocialPage[];
}

export interface SocialModerationCase {
  id: string;
  postId: string;
  reporterActorId: string;
  reason: string;
  details?: string;
  status: "OPEN" | "ACK" | "RESOLVED" | "DISMISSED";
  decision?: "NO_ACTION" | "REMOVE" | "RESTRICT" | "ARCHIVE" | "ESCALATE" | null;
  createdAt: string;
  resolvedAt?: string | null;
}

// ── Feed ────────────────────────────────────────────────────────────────
export function useSocialFeed(scope: FeedScope = "home", scopeId?: string, limit = 20, offset = 0) {
  return useQuery<SocialFeedPage>({
    queryKey: ["social-feed", scope, scopeId ?? null, limit, offset],
    queryFn: async () => {
      const params = new URLSearchParams();
      params.set("scope", scope);
      if (scopeId) params.set("scopeId", scopeId);
      params.set("limit", String(limit));
      params.set("offset", String(offset));
      return apiClient.get<SocialFeedPage>(`${BASE}/feed?${params.toString()}`);
    },
  });
}

// ── Post mutations ─────────────────────────────────────────────────────
export interface CreatePostInput {
  kind: SocialPostKind;
  body: string;
  title?: string;
  visibility?: SocialVisibility;
  topics?: string[];
  scope?: SocialScope;
  attachments?: SocialAttachment[];
  scheduledFor?: string;
  saveAsDraft?: boolean;
  source?: string;
  nompiloAssist?: Record<string, unknown>;
}

export function useCreatePost() {
  const qc = useQueryClient();
  return useMutation<SocialPost, unknown, CreatePostInput>({
    mutationFn: (body) => apiClient.post<SocialPost>(`${BASE}/posts`, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
      void qc.invalidateQueries({ queryKey: ["social-suggestions"] });
    },
  });
}

export function useReactToPost() {
  const qc = useQueryClient();
  return useMutation<SocialReactionSummary, unknown, { postId: string; type: ReactionType }>({
    mutationFn: ({ postId, type }) =>
      apiClient.post<SocialReactionSummary>(`${BASE}/posts/${postId}/reactions`, { type }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

export function useUnreactPost() {
  const qc = useQueryClient();
  return useMutation<SocialReactionSummary, unknown, { postId: string }>({
    mutationFn: ({ postId }) =>
      apiClient.delete<SocialReactionSummary>(`${BASE}/posts/${postId}/reactions`),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

export function usePostComments(postId: string | null | undefined) {
  return useQuery({
    queryKey: ["social-comments", postId],
    queryFn: () => apiClient.get(`${BASE}/posts/${postId}/comments`),
    enabled: !!postId,
  });
}

export function useAddComment() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { postId: string; body: string; parentCommentId?: string }>({
    mutationFn: ({ postId, body, parentCommentId }) =>
      apiClient.post(`${BASE}/posts/${postId}/comments`, { body, parentCommentId }),
    onSuccess: (_d, vars) => {
      void qc.invalidateQueries({ queryKey: ["social-comments", vars.postId] });
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

export function useToggleBookmark() {
  const qc = useQueryClient();
  return useMutation<{ bookmarked: boolean }, unknown, { postId: string; on: boolean }>({
    mutationFn: ({ postId, on }) => {
      if (on) return apiClient.post<{ bookmarked: boolean }>(`${BASE}/posts/${postId}/bookmark`, {});
      return apiClient.delete<{ bookmarked: boolean }>(`${BASE}/posts/${postId}/bookmark`);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

export function useReportPost() {
  return useMutation<unknown, unknown, { postId: string; reason: string; details?: string }>({
    mutationFn: ({ postId, reason, details }) =>
      apiClient.post(`${BASE}/posts/${postId}/report`, { reason, details }),
  });
}

// ── Communities ────────────────────────────────────────────────────────
export function useSocialCommunities(category?: string, q?: string) {
  return useQuery<SocialCommunity[]>({
    queryKey: ["social-communities", category ?? null, q ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (category) params.set("category", category);
      if (q) params.set("q", q);
      const qs = params.toString();
      return apiClient.get<SocialCommunity[]>(`${BASE}/communities${qs ? `?${qs}` : ""}`);
    },
  });
}

export function useSocialCommunity(id: string | null | undefined) {
  return useQuery<SocialCommunity>({
    queryKey: ["social-community", id],
    queryFn: () => apiClient.get<SocialCommunity>(`${BASE}/communities/${id}`),
    enabled: !!id,
  });
}

export function useJoinCommunity() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { id: string }>({
    mutationFn: ({ id }) => apiClient.post(`${BASE}/communities/${id}/join`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-communities"] });
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
      void qc.invalidateQueries({ queryKey: ["social-suggestions"] });
    },
  });
}

export function useLeaveCommunity() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { id: string }>({
    mutationFn: ({ id }) => apiClient.post(`${BASE}/communities/${id}/leave`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-communities"] });
    },
  });
}

// ── Groups ─────────────────────────────────────────────────────────────
export function useSocialGroups(communityId?: string) {
  return useQuery<SocialGroup[]>({
    queryKey: ["social-groups", communityId ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (communityId) params.set("communityId", communityId);
      const qs = params.toString();
      return apiClient.get<SocialGroup[]>(`${BASE}/groups${qs ? `?${qs}` : ""}`);
    },
  });
}

export function useSocialGroup(id: string | null | undefined) {
  return useQuery<SocialGroup>({
    queryKey: ["social-group", id],
    queryFn: () => apiClient.get<SocialGroup>(`${BASE}/groups/${id}`),
    enabled: !!id,
  });
}

// ── Pages ──────────────────────────────────────────────────────────────
export function useSocialPages(kind?: string) {
  return useQuery<SocialPage[]>({
    queryKey: ["social-pages", kind ?? null],
    queryFn: () => {
      const params = new URLSearchParams();
      if (kind) params.set("kind", kind);
      const qs = params.toString();
      return apiClient.get<SocialPage[]>(`${BASE}/pages${qs ? `?${qs}` : ""}`);
    },
  });
}

export function useSocialPage(id: string | null | undefined) {
  return useQuery<SocialPage>({
    queryKey: ["social-page", id],
    queryFn: () => apiClient.get<SocialPage>(`${BASE}/pages/${id}`),
    enabled: !!id,
  });
}

export function useFollowPage() {
  const qc = useQueryClient();
  return useMutation<{ following: boolean }, unknown, { id: string }>({
    mutationFn: ({ id }) => apiClient.post<{ following: boolean }>(`${BASE}/pages/${id}/follow`, {}),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-pages"] });
      void qc.invalidateQueries({ queryKey: ["social-page"] });
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

// ── Suggestions, moderation ────────────────────────────────────────────
export function useSocialSuggestions() {
  return useQuery<SocialSuggestionsBundle>({
    queryKey: ["social-suggestions"],
    queryFn: () => apiClient.get<SocialSuggestionsBundle>(`${BASE}/suggestions`),
  });
}

export function useModerationQueue() {
  return useQuery<SocialModerationCase[]>({
    queryKey: ["social-moderation-queue"],
    queryFn: () => apiClient.get<SocialModerationCase[]>(`${BASE}/moderation/queue`),
  });
}

export function useResolveModeration() {
  const qc = useQueryClient();
  return useMutation<unknown, unknown, { caseId: string; decision: string; rationale?: string }>({
    mutationFn: ({ caseId, decision, rationale }) =>
      apiClient.post(`${BASE}/moderation/${caseId}/resolve`, { decision, rationale }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ["social-moderation-queue"] });
      void qc.invalidateQueries({ queryKey: ["social-feed"] });
    },
  });
}

// ── Nompilo composer assist ───────────────────────────────────────────
export type ComposerOp =
  | "improve"
  | "simplify"
  | "translate"
  | "suggest_tags"
  | "suggest_audience"
  | "safety_check"
  | "draft_announcement"
  | "summarise_thread";

export interface ComposerAssistResult {
  op: ComposerOp;
  language?: string;
  audience?: string;
  provider?: string;
  model?: string;
  fallbackUsed?: boolean;
  result: unknown;
  note?: string;
}

export function useNompiloComposer() {
  return useMutation<ComposerAssistResult, unknown, { op: ComposerOp; content: string; language?: string; audience?: string }>({
    mutationFn: (body) => apiClient.post<ComposerAssistResult>(`${BASE}/composer/assist`, body),
  });
}
