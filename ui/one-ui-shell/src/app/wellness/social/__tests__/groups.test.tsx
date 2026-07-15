import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { user: { id: string } }) => unknown) => sel({ user: { id: "CPID-1" } }),
}));
vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}));

import { apiClient } from "@/lib/api-client";
import SocialGroupsPage from "../groups/page";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderPage(node: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{node}</QueryClientProvider>);
}

describe("SocialGroupsPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("lists groups from Simba and joins one via the sovereign endpoint", async () => {
    get.mockResolvedValue({
      data: [
        {
          groupId: "G1",
          name: "Morning Walkers",
          description: "Walk together",
          groupKind: "GROUP",
          sensitiveFlag: false,
          visibility: "PUBLIC",
          joinPolicy: "OPEN",
          memberCount: 5,
          status: "ACTIVE",
        },
      ],
    });
    post.mockResolvedValue({ data: { status: "ACTIVE" } });

    renderPage(<SocialGroupsPage />);

    await waitFor(() => expect(screen.getByText("Morning Walkers")).toBeInTheDocument());
    expect(get).toHaveBeenCalledWith("/internal/v1/wellness/social/groups");

    fireEvent.click(screen.getByRole("button", { name: "Join" }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/wellness/social/groups/G1/join", {}),
    );
  });

  it("shows an empty state when there are no groups", async () => {
    get.mockResolvedValue({ data: [] });
    renderPage(<SocialGroupsPage />);
    await waitFor(() =>
      expect(screen.getByText(/No groups yet/i)).toBeInTheDocument(),
    );
  });
});
