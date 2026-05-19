import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { FeedList, FeedSkeleton } from "../FeedList";
import type { SocialPost } from "@/hooks/queries/useSocial";

function withQueryClient(node: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{node}</QueryClientProvider>;
}

const samplePost: SocialPost = {
  id: "p1",
  tenantId: "t1",
  kind: "text",
  status: "published",
  visibility: "public",
  body: "Sample timeline post",
  author: { kind: "user", id: "u1", displayName: "Tendai Moyo", roleBadge: "Citizen" },
  reactionSummary: { total: 2, byType: { LIKE: 2 }, viewerReaction: null },
  commentCount: 0,
};

describe("FeedList", () => {
  it("renders the empty state when posts is empty", () => {
    render(withQueryClient(<FeedList posts={[]} emptyTitle="Nothing yet" emptyHint="Follow a community" />));
    expect(screen.getByText("Nothing yet")).toBeInTheDocument();
    expect(screen.getByText("Follow a community")).toBeInTheDocument();
  });

  it("renders the error state when error is provided", () => {
    render(withQueryClient(<FeedList error={new Error("boom")} />));
    expect(screen.getByText("Couldn’t load the feed")).toBeInTheDocument();
  });

  it("renders skeletons while loading", () => {
    const { container } = render(<FeedSkeleton count={2} />);
    const skeletons = container.querySelectorAll(".animate-pulse");
    expect(skeletons.length).toBeGreaterThanOrEqual(2);
  });

  it("renders a post when supplied", () => {
    render(withQueryClient(<FeedList posts={[samplePost]} />));
    expect(screen.getByText("Sample timeline post")).toBeInTheDocument();
    expect(screen.getByText("Tendai Moyo")).toBeInTheDocument();
  });
});
