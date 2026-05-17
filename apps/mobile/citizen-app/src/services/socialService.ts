/**
 * Social Service — Mobile-citizen social timeline API client.
 *
 * Wraps the experience-bff endpoints under
 *   /internal/v1/mobile/citizen/social/*
 * which are thin adapters around community-service /internal/v1/community/social/*.
 *
 * The legacy {@link feedService} (likeFeedItem/unlikeFeedItem/fetchFeed) continues
 * to work in parallel — that endpoint is now backed by the same social timeline
 * data, but returns the legacy {data,meta} envelope for screens that haven't
 * been migrated yet.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/mobile/citizen/social";

export type SocialFeedScope =
  | "home"
  | "following"
  | "wellness"
  | "learning"
  | "public_health"
  | "facility"
  | "announcements"
  | "saved";

export interface SocialIdentity {
  kind: "user" | "community" | "group" | "page" | "system";
  id: string;
  displayName: string;
  avatarUrl?: string;
  roleBadge?: string;
  verified?: boolean;
  facilityName?: string;
}

export interface SocialReactionSummary {
  total: number;
  byType?: Record<string, number>;
  viewerReaction?: string | null;
}

export interface SocialPost {
  id: string;
  tenantId?: string;
  kind: string;
  status: string;
  visibility: string;
  title?: string;
  body: string;
  topics?: string[];
  author: SocialIdentity;
  attachments?: Array<{ id?: string; kind?: string; url?: string; previewText?: string }>;
  reactionSummary?: SocialReactionSummary;
  commentCount?: number;
  bookmarked?: boolean;
  pinned?: boolean;
  source?: string;
  publishedAt?: string;
  createdAt?: string;
}

export interface SocialCommunity {
  id: string;
  slug: string;
  name: string;
  description?: string;
  category: string;
  kind: string;
  coverColor?: string;
  iconUrl?: string;
  memberCount: number;
  viewerMembership: "none" | "requested" | "member" | "moderator" | "admin";
}

export interface SocialPage {
  id: string;
  slug: string;
  name: string;
  kind: string;
  bio?: string;
  verified?: boolean;
  followerCount: number;
  viewerFollows?: boolean;
}

export interface SocialFeedResponse {
  scope: string;
  items: SocialPost[];
  nextCursor?: string | null;
}

export async function fetchSocialFeed(
  scope: SocialFeedScope = "home",
  options: { scopeId?: string; limit?: number; offset?: number } = {},
): Promise<SocialFeedResponse> {
  const params: string[] = [`scope=${scope}`];
  if (options.scopeId) params.push(`scopeId=${encodeURIComponent(options.scopeId)}`);
  if (options.limit !== undefined) params.push(`limit=${options.limit}`);
  if (options.offset !== undefined) params.push(`offset=${options.offset}`);
  const res = await apiClient.get<SocialFeedResponse>(`${BASE}/feed?${params.join("&")}`);
  return res.data;
}

export interface CreateSocialPostInput {
  kind: string;
  body: string;
  title?: string;
  visibility?: string;
  topics?: string[];
  scope?: { communityId?: string; groupId?: string; pageId?: string };
  saveAsDraft?: boolean;
  source?: string;
}

export async function createSocialPost(input: CreateSocialPostInput): Promise<SocialPost> {
  const res = await apiClient.post<SocialPost>(`${BASE}/posts`, input);
  return res.data;
}

export async function reactToSocialPost(postId: string, type: string = "LIKE"): Promise<SocialReactionSummary> {
  const res = await apiClient.post<SocialReactionSummary>(`${BASE}/posts/${encodeURIComponent(postId)}/reactions`, { type });
  return res.data;
}

export async function unreactSocialPost(postId: string): Promise<SocialReactionSummary> {
  const res = await apiClient.delete<SocialReactionSummary>(`${BASE}/posts/${encodeURIComponent(postId)}/reactions`);
  return res.data;
}

export async function bookmarkSocialPost(postId: string, on: boolean): Promise<void> {
  if (on) {
    await apiClient.post(`${BASE}/posts/${encodeURIComponent(postId)}/bookmark`);
  } else {
    await apiClient.delete(`${BASE}/posts/${encodeURIComponent(postId)}/bookmark`);
  }
}

export async function addSocialComment(postId: string, body: string, parentCommentId?: string): Promise<unknown> {
  const res = await apiClient.post(`${BASE}/posts/${encodeURIComponent(postId)}/comments`, { body, parentCommentId });
  return res.data;
}

export async function fetchSocialCommunities(category?: string): Promise<SocialCommunity[]> {
  const qs = category ? `?category=${encodeURIComponent(category)}` : "";
  const res = await apiClient.get<SocialCommunity[] | { data: SocialCommunity[] }>(`${BASE}/communities${qs}`);
  const payload = res.data as SocialCommunity[] | { data: SocialCommunity[] };
  return Array.isArray(payload) ? payload : (payload as { data: SocialCommunity[] }).data ?? [];
}

export async function joinSocialCommunity(id: string): Promise<unknown> {
  const res = await apiClient.post(`${BASE}/communities/${encodeURIComponent(id)}/join`);
  return res.data;
}

export async function fetchSocialPages(kind?: string): Promise<SocialPage[]> {
  const qs = kind ? `?kind=${encodeURIComponent(kind)}` : "";
  const res = await apiClient.get<SocialPage[] | { data: SocialPage[] }>(`${BASE}/pages${qs}`);
  const payload = res.data as SocialPage[] | { data: SocialPage[] };
  return Array.isArray(payload) ? payload : (payload as { data: SocialPage[] }).data ?? [];
}

export async function followSocialPage(id: string): Promise<unknown> {
  const res = await apiClient.post(`${BASE}/pages/${encodeURIComponent(id)}/follow`);
  return res.data;
}

export interface ComposerAssistResult {
  op: string;
  result: unknown;
  fallbackUsed?: boolean;
}

export async function nompiloComposerAssist(
  op: "improve" | "simplify" | "translate" | "suggest_tags" | "safety_check",
  content: string,
  options: { language?: string; audience?: string } = {},
): Promise<ComposerAssistResult> {
  const res = await apiClient.post<ComposerAssistResult>(
    `/internal/v1/social/composer/assist`,
    { op, content, ...options },
  );
  return res.data;
}
