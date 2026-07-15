import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}));
vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "POST-1" }),
  usePathname: () => "/wellness/social/posts/POST-1",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("next/link", () => ({
  default: ({ children }: { children: React.ReactNode }) => <span>{children}</span>,
}));

import { apiClient } from "@/lib/api-client";
import WellnessSocialPostDetailPage from "../posts/[id]/page";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WellnessSocialPostDetailPage />
    </QueryClientProvider>,
  );
}

describe("WellnessSocialPostDetailPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("loads the post and its comments, and adds a comment via the sovereign endpoint", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/comments")) {
        return Promise.resolve({
          data: [{ commentId: "C1", actorType: "CLIENT", body: "Well done!", createdAt: new Date().toISOString() }],
        });
      }
      return Promise.resolve({
        data: {
          postId: "POST-1",
          id: 1,
          actorCpid: "cpid",
          actorType: "CLIENT",
          body: "Ran 5km today",
          visibility: "PUBLIC_WITHIN_IMPILO",
          reactionCount: 2,
          commentCount: 1,
          createdAt: new Date().toISOString(),
        },
      });
    });
    post.mockResolvedValue({ data: {} });

    renderPage();

    expect(await screen.findByText("Ran 5km today")).toBeInTheDocument();
    expect(await screen.findByText("Well done!")).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText(/Add an encouraging comment/i), {
      target: { value: "Keep it up!" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Post comment" }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/wellness/social/posts/POST-1/comments",
        { body: "Keep it up!" },
      );
    });
  });

  it("offers all six reaction types and reacts via the sovereign endpoint", async () => {
    get.mockImplementation((url: string) =>
      url.endsWith("/comments")
        ? Promise.resolve({ data: [] })
        : Promise.resolve({
            data: {
              postId: "POST-1",
              id: 1,
              actorCpid: "cpid",
              actorType: "CLIENT",
              body: "Hello",
              visibility: "PUBLIC_WITHIN_IMPILO",
              reactionCount: 0,
              commentCount: 0,
              createdAt: new Date().toISOString(),
            },
          }),
    );
    post.mockResolvedValue({ data: { active: true, count: 1 } });

    renderPage();
    await screen.findByText("Hello");

    // open the reaction menu (the reaction count button)
    fireEvent.click(screen.getAllByRole("button")[1]);
    fireEvent.click(await screen.findByRole("menuitem", { name: /Celebrate/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/wellness/social/post/POST-1/react",
        { reaction: "CELEBRATE" },
      );
    });
  });
});
