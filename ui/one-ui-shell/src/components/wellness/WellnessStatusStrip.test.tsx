import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import React from "react";

vi.mock("@/lib/api-client", () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}));
vi.mock("@/hooks/useAuthStore", () => ({
  useAuthStore: (sel: (s: { user: { id: string } }) => unknown) => sel({ user: { id: "CPID-1" } }),
}));

import { apiClient } from "@/lib/api-client";
import { WellnessStatusStrip } from "./WellnessStatusStrip";

const get = apiClient.get as unknown as ReturnType<typeof vi.fn>;
const post = apiClient.post as unknown as ReturnType<typeof vi.fn>;

function renderStrip() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <WellnessStatusStrip />
    </QueryClientProvider>,
  );
}

describe("WellnessStatusStrip", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders active statuses from the sovereign endpoint", async () => {
    get.mockResolvedValue({
      data: [
        {
          statusId: "S1",
          actorCpid: "other",
          actorType: "CLIENT",
          text: "Feeling strong today",
          mood: "MOTIVATED",
          visibility: "PUBLIC_WITHIN_IMPILO",
          moderationStatus: "VISIBLE",
          createdAt: new Date().toISOString(),
        },
      ],
    });

    renderStrip();

    expect(await screen.findByText("Feeling strong today")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/wellness/social/statuses?limit=50");
  });

  it("posts a new status with mood, visibility and ttl", async () => {
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: {} });

    renderStrip();
    await screen.findByText(/No active statuses/i);

    fireEvent.click(screen.getByRole("button", { name: /Share a status/i }));
    fireEvent.change(screen.getByPlaceholderText(/How are you doing/i), {
      target: { value: "Just finished a run" },
    });
    fireEvent.click(screen.getByRole("button", { name: /Post status/i }));

    await waitFor(() => {
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/wellness/social/statuses",
        expect.objectContaining({ text: "Just finished a run", visibility: "PUBLIC_WITHIN_IMPILO", ttl_hours: 24 }),
      );
    });
  });
});
