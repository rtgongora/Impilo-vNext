import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post: vi.fn() } }));

import { TraumaEpisodeTimeline } from "./TraumaEpisodeTimeline";

function renderTl() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TraumaEpisodeTimeline episodeId="ep-1" />
    </QueryClientProvider>,
  );
}

describe("TraumaEpisodeTimeline", () => {
  beforeEach(() => get.mockReset());

  it("renders the episode reference, status and ordered phase timeline", async () => {
    get.mockResolvedValue({
      traumaEpisodeId: "ep-1",
      episodeReference: "TEP-1",
      status: "OPEN",
      currentPhase: "BLOOD",
      subjectIdentityMode: "ANONYMOUS",
      timeline: [
        { phase: "INCIDENT", ownerService: "daidzai", status: "MINTED", occurredAt: "2026-07-15T05:38:43Z" },
        { phase: "ED", ownerService: "pct-service", status: "ARRIVED", occurredAt: "2026-07-15T05:38:44Z" },
        { phase: "BLOOD", ownerService: "madi-service", status: "DRAFT", occurredAt: "2026-07-15T05:38:45Z" },
      ],
    });
    renderTl();
    await waitFor(() => expect(screen.getByText("TEP-1")).toBeInTheDocument());
    expect(screen.getByText("INCIDENT")).toBeInTheDocument();
    expect(screen.getByText("ED")).toBeInTheDocument();
    expect(screen.getByText("BLOOD")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/daidzai/trauma-episodes/ep-1");
  });

  it("explains an episode with no phases", async () => {
    get.mockResolvedValue({ traumaEpisodeId: "ep-2", episodeReference: "TEP-2", status: "OPEN", timeline: [] });
    renderTl();
    await waitFor(() => expect(screen.getByText(/No phases recorded yet/i)).toBeInTheDocument());
  });
});
