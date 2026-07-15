import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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
vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "G1" }),
}));

import { apiClient } from "@/lib/api-client";
import SocialGroupDetailPage from "../groups/[id]/page";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <SocialGroupDetailPage />
    </QueryClientProvider>,
  );
}

describe("SocialGroupDetailPage", () => {
  beforeEach(() => get.mockReset());

  it("shows the announcement composer only to an official member", async () => {
    get.mockResolvedValue({
      data: {
        groupId: "G1",
        name: "Walkers",
        description: "d",
        groupKind: "GROUP",
        sensitiveFlag: false,
        visibility: "PUBLIC",
        joinPolicy: "OPEN",
        memberCount: 2,
        status: "ACTIVE",
      },
      members: [
        { membershipId: "M1", personCpid: "CPID-1", role: "OWNER", canPost: true, canComment: true, status: "ACTIVE" },
      ],
    });

    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/Publish official announcement/i)).toBeInTheDocument(),
    );
  });

  it("hides the announcement composer from a plain member", async () => {
    get.mockResolvedValue({
      data: {
        groupId: "G1",
        name: "Walkers",
        description: "d",
        groupKind: "GROUP",
        sensitiveFlag: false,
        visibility: "PUBLIC",
        joinPolicy: "OPEN",
        memberCount: 2,
        status: "ACTIVE",
      },
      members: [
        { membershipId: "M1", personCpid: "CPID-1", role: "MEMBER", canPost: true, canComment: true, status: "ACTIVE" },
      ],
    });

    renderPage();
    await waitFor(() => expect(screen.getByText("Members")).toBeInTheDocument());
    expect(screen.queryByText(/Publish official announcement/i)).not.toBeInTheDocument();
  });
});
