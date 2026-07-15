import { describe, expect, it } from "vitest";
import { flattenFeed, type SocialPost } from "./useSimbaSocial";

function post(id: number): SocialPost {
  return {
    id,
    postId: `post-${id}`,
    actorCpid: "cpid",
    actorType: "CLIENT",
    body: `Body ${id}`,
    visibility: "PUBLIC_WITHIN_IMPILO",
    reactionCount: 0,
    commentCount: 0,
    createdAt: new Date().toISOString(),
  };
}

describe("flattenFeed", () => {
  it("returns [] for undefined pages", () => {
    expect(flattenFeed(undefined)).toEqual([]);
  });

  it("flattens and preserves order across pages", () => {
    const flat = flattenFeed([{ data: [post(3), post(2)] }, { data: [post(1)] }]);
    expect(flat.map((p) => p.id)).toEqual([3, 2, 1]);
  });

  it("tolerates a missing data array", () => {
    expect(flattenFeed([{ data: undefined as unknown as SocialPost[] }])).toEqual([]);
  });
});
