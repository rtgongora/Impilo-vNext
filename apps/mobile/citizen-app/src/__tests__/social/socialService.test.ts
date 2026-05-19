/**
 * socialService tests — verifies wiring against the experience-bff
 * citizen social routes and graceful array/envelope adaptation.
 */

import { describe, it, expect, vi, beforeEach } from "vitest";

const mocks = vi.hoisted(() => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@impilo/mobile-api-client", () => ({ apiClient: mocks.apiClient }));

import {
  fetchSocialFeed,
  fetchSocialCommunities,
  fetchSocialPages,
  createSocialPost,
  reactToSocialPost,
  joinSocialCommunity,
  followSocialPage,
} from "../../services/socialService";

describe("socialService — citizen mobile timeline routes", () => {
  beforeEach(() => vi.clearAllMocks());

  it("fetchSocialFeed hits /internal/v1/mobile/citizen/social/feed with scope", async () => {
    mocks.apiClient.get.mockResolvedValueOnce({
      data: { scope: "home", items: [], nextCursor: null },
    });

    const res = await fetchSocialFeed("home", { limit: 25 });

    expect(mocks.apiClient.get).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/social/feed?scope=home&limit=25",
    );
    expect(res.items).toEqual([]);
  });

  it("fetchSocialCommunities accepts both array and envelope responses", async () => {
    mocks.apiClient.get.mockResolvedValueOnce({ data: [{ id: "c1", name: "A", category: "x", kind: "public", slug: "a", memberCount: 0, viewerMembership: "none" }] });
    let out = await fetchSocialCommunities();
    expect(out).toHaveLength(1);

    mocks.apiClient.get.mockResolvedValueOnce({ data: { data: [{ id: "c2", name: "B", category: "x", kind: "public", slug: "b", memberCount: 0, viewerMembership: "none" }] } });
    out = await fetchSocialCommunities("maternal_health");
    expect(out[0].id).toBe("c2");
    expect(mocks.apiClient.get).toHaveBeenLastCalledWith(
      "/internal/v1/mobile/citizen/social/communities?category=maternal_health",
    );
  });

  it("fetchSocialPages accepts both array and envelope responses", async () => {
    mocks.apiClient.get.mockResolvedValueOnce({ data: [] });
    expect(await fetchSocialPages()).toEqual([]);

    mocks.apiClient.get.mockResolvedValueOnce({
      data: { data: [{ id: "p1", name: "Page", kind: "facility", slug: "p", followerCount: 0 }] },
    });
    const out = await fetchSocialPages("facility");
    expect(out[0].id).toBe("p1");
    expect(mocks.apiClient.get).toHaveBeenLastCalledWith(
      "/internal/v1/mobile/citizen/social/pages?kind=facility",
    );
  });

  it("createSocialPost POSTs to the posts endpoint", async () => {
    mocks.apiClient.post.mockResolvedValueOnce({ data: { id: "new", body: "hello", kind: "text", status: "published", visibility: "public", author: { kind: "user", id: "u", displayName: "u" } } });

    const post = await createSocialPost({ kind: "text", body: "hello", visibility: "public", source: "citizen-app" });

    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/social/posts",
      expect.objectContaining({ kind: "text", body: "hello", visibility: "public" }),
    );
    expect(post.id).toBe("new");
  });

  it("reactToSocialPost defaults to LIKE", async () => {
    mocks.apiClient.post.mockResolvedValueOnce({ data: { total: 1, byType: { LIKE: 1 }, viewerReaction: "LIKE" } });

    const summary = await reactToSocialPost("post-1");

    expect(mocks.apiClient.post).toHaveBeenCalledWith(
      "/internal/v1/mobile/citizen/social/posts/post-1/reactions",
      { type: "LIKE" },
    );
    expect(summary.total).toBe(1);
  });

  it("joinSocialCommunity / followSocialPage POST to the correct endpoints", async () => {
    mocks.apiClient.post.mockResolvedValue({ data: {} });

    await joinSocialCommunity("c-1");
    expect(mocks.apiClient.post).toHaveBeenCalledWith("/internal/v1/mobile/citizen/social/communities/c-1/join");

    await followSocialPage("p-1");
    expect(mocks.apiClient.post).toHaveBeenCalledWith("/internal/v1/mobile/citizen/social/pages/p-1/follow");
  });
});
