/**
 * Wellness-SOCIAL service — Simba wellness-sovereign social layer for the citizen mobile app
 * (/internal/v1/wellness/social/**). DISTINCT from the generic community-service socialService.ts —
 * this layer is consent-governed with sensitive-category gating and care-linkage safety escalation.
 * Backed by the Simba sovereign (8125) via the Experience BFF proxy; reels use BFF composition.
 *
 * NOTE: the mobile vitest runner is not executable in this environment (apps/mobile uses the pnpm
 * workspace:* protocol; npm install fails with EUNSUPPORTEDPROTOCOL). This module is written and
 * type-checked against the shared @impilo/mobile-api-client export surface, mirroring the existing
 * wellnessDepthService.ts; its tests were NOT run here (documented honest partial).
 */
import { apiClient } from "@impilo/mobile-api-client";

const SOCIAL = "/internal/v1/wellness/social";

type Row = Record<string, unknown>;

export interface SocialPost {
  postId: string;
  actorType: string;
  body: string;
  visibility: string;
  reactionCount: number;
  commentCount: number;
  createdAt: string;
}

export interface SocialFeedPage {
  posts: SocialPost[];
  nextCursor: number | null;
}

export interface Reel {
  reelId: string;
  caption?: string;
  creatorType: string;
}

function str(v: unknown, fallback = ""): string {
  return v != null && v !== "" ? String(v) : fallback;
}
function num(v: unknown, fallback = 0): number {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isNaN(n) ? fallback : n;
}
function rowsOf(body: unknown): Row[] {
  const inner = (body as { data?: unknown })?.data ?? body;
  return Array.isArray(inner) ? (inner as Row[]) : [];
}
function nextCursorOf(body: unknown): number | null {
  const meta = (body as { meta?: { next_cursor?: number | null } })?.meta;
  return meta?.next_cursor ?? null;
}

function mapPost(row: Row): SocialPost {
  return {
    postId: str(row.postId ?? row.post_id),
    actorType: str(row.actorType ?? row.actor_type, "CLIENT"),
    body: str(row.body),
    visibility: str(row.visibility, "PRIVATE"),
    reactionCount: num(row.reactionCount ?? row.reaction_count),
    commentCount: num(row.commentCount ?? row.comment_count),
    createdAt: str(row.createdAt ?? row.created_at),
  };
}

export async function fetchSocialFeed(filter = "ALL", cursor?: number | null): Promise<SocialFeedPage> {
  const params = new URLSearchParams();
  params.set("filter", filter);
  params.set("limit", "20");
  if (cursor != null) params.set("cursor", String(cursor));
  const res = await apiClient.get<unknown>(`${SOCIAL}/feed?${params.toString()}`);
  return {
    posts: rowsOf(res.data).map(mapPost),
    nextCursor: nextCursorOf(res.data),
  };
}

export async function createPost(body: string, visibility = "PUBLIC_WITHIN_IMPILO"): Promise<void> {
  await apiClient.post(`${SOCIAL}/posts`, { body, visibility });
}

export async function reactToPost(postId: string, reaction = "ENCOURAGE"): Promise<void> {
  await apiClient.post(`${SOCIAL}/POST/${postId}/react`, { reaction });
}

export async function reportContent(
  subjectType: string,
  subjectId: string,
  subjectOwnerCpid: string,
  reason: string,
): Promise<{ escalated: boolean }> {
  const res = await apiClient.post<{ escalated?: boolean }>(`${SOCIAL}/moderation/reports`, {
    subject_type: subjectType,
    subject_id: subjectId,
    subject_owner_cpid: subjectOwnerCpid,
    reason,
  });
  return { escalated: Boolean(res.data?.escalated) };
}

export async function fetchReels(cursor?: number | null): Promise<{ reels: Reel[]; nextCursor: number | null }> {
  const params = new URLSearchParams();
  params.set("limit", "10");
  if (cursor != null) params.set("cursor", String(cursor));
  const res = await apiClient.get<unknown>(`${SOCIAL}/reels?${params.toString()}`);
  return {
    reels: rowsOf(res.data).map((r) => ({
      reelId: str(r.reelId ?? r.reel_id),
      caption: r.caption != null ? str(r.caption) : undefined,
      creatorType: str(r.creatorType ?? r.creator_type, "CLIENT"),
    })),
    nextCursor: nextCursorOf(res.data),
  };
}

export async function fetchReelPlayback(reelId: string): Promise<{ playbackUrl: string | null }> {
  const res = await apiClient.get<{ data?: { playback_url?: string } }>(
    `${SOCIAL}/reels/${reelId}/playback`,
  );
  return { playbackUrl: res.data?.data?.playback_url ?? null };
}
