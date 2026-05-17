/**
 * Provider Social Service — Thin wrapper around the experience-bff provider
 * social endpoints under /internal/v1/mobile/provider/social/*.
 *
 * Targets professional communities of practice, mentorship groups, facility
 * pages, and operational announcements.
 */

import { apiClient } from "@impilo/mobile-api-client";

const BASE = "/internal/v1/mobile/provider/social";

export type ProviderFeedScope =
  | "home"
  | "facility"
  | "public_health"
  | "learning"
  | "announcements"
  | "community"
  | "group";

export interface ProviderSocialIdentity {
  kind: string;
  id: string;
  displayName: string;
  avatarUrl?: string;
  roleBadge?: string;
  verified?: boolean;
  facilityName?: string;
}

export interface ProviderSocialReactionSummary {
  total: number;
  byType?: Record<string, number>;
  viewerReaction?: string | null;
}

export interface ProviderSocialPost {
  id: string;
  kind: string;
  status: string;
  visibility: string;
  title?: string;
  body: string;
  topics?: string[];
  author: ProviderSocialIdentity;
  reactionSummary?: ProviderSocialReactionSummary;
  commentCount?: number;
  pinned?: boolean;
  source?: string;
  publishedAt?: string;
  createdAt?: string;
}

export interface ProviderSocialCommunity {
  id: string;
  slug: string;
  name: string;
  description?: string;
  category: string;
  kind: string;
  memberCount: number;
  viewerMembership?: string;
}

export interface ProviderSocialGroup {
  id: string;
  name: string;
  description?: string;
  category?: string;
  memberCount: number;
  viewerMembership?: string;
}

export interface ProviderSocialFeedResponse {
  scope: string;
  items: ProviderSocialPost[];
  nextCursor?: string | null;
}

export async function fetchProviderFeed(
  scope: ProviderFeedScope = "home",
  options: { scopeId?: string; limit?: number; offset?: number } = {},
): Promise<ProviderSocialFeedResponse> {
  const params: string[] = [`scope=${scope}`];
  if (options.scopeId) params.push(`scopeId=${encodeURIComponent(options.scopeId)}`);
  if (options.limit !== undefined) params.push(`limit=${options.limit}`);
  if (options.offset !== undefined) params.push(`offset=${options.offset}`);
  const res = await apiClient.get<ProviderSocialFeedResponse>(`${BASE}/feed?${params.join("&")}`);
  return res.data;
}

export interface CreateProviderPostInput {
  kind: string;
  body: string;
  title?: string;
  visibility?: string;
  topics?: string[];
  scope?: { communityId?: string; groupId?: string; pageId?: string };
  saveAsDraft?: boolean;
  source?: string;
}

export async function createProviderPost(input: CreateProviderPostInput): Promise<ProviderSocialPost> {
  const res = await apiClient.post<ProviderSocialPost>(`${BASE}/posts`, input);
  return res.data;
}

export async function reactToProviderPost(postId: string, type = "LIKE"): Promise<ProviderSocialReactionSummary> {
  const res = await apiClient.post<ProviderSocialReactionSummary>(
    `${BASE}/posts/${encodeURIComponent(postId)}/reactions`,
    { type },
  );
  return res.data;
}

export async function fetchProviderCommunities(category?: string): Promise<ProviderSocialCommunity[]> {
  const qs = category ? `?category=${encodeURIComponent(category)}` : "";
  const res = await apiClient.get<ProviderSocialCommunity[] | { data: ProviderSocialCommunity[] }>(
    `${BASE}/communities${qs}`,
  );
  const payload = res.data;
  return Array.isArray(payload) ? payload : (payload as { data: ProviderSocialCommunity[] }).data ?? [];
}

export async function fetchProviderGroups(communityId?: string): Promise<ProviderSocialGroup[]> {
  const qs = communityId ? `?communityId=${encodeURIComponent(communityId)}` : "";
  const res = await apiClient.get<ProviderSocialGroup[] | { data: ProviderSocialGroup[] }>(`${BASE}/groups${qs}`);
  const payload = res.data;
  return Array.isArray(payload) ? payload : (payload as { data: ProviderSocialGroup[] }).data ?? [];
}

export async function joinProviderCommunity(id: string): Promise<unknown> {
  const res = await apiClient.post(`${BASE}/communities/${encodeURIComponent(id)}/join`);
  return res.data;
}
