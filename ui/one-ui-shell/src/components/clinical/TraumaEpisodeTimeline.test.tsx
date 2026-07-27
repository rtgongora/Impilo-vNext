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

  // CC-5 V-3: the continuum-link status, with the three states kept distinct so "not linked yet,
  // fine" is never shown as "linked" and an unanchored facility-phase episode reads as a gap.

  it("shows LINKED with the journey once the spine resolves to the continuum", async () => {
    get.mockResolvedValue({
      traumaEpisodeId: "ep-3", episodeReference: "TEP-3", status: "OPEN", currentPhase: "ED",
      continuumLinked: true, pctJourneyId: "J-77", continuumLinkedAt: "2026-07-15T06:00:00Z", timeline: [],
    });
    renderTl();
    await waitFor(() => expect(screen.getByTestId("continuum-link-status")).toHaveTextContent(/Linked to the care continuum/i));
    expect(screen.getByTestId("continuum-link-status")).toHaveTextContent("J-77");
  });

  it("shows UNANCHORED — not a benign 'not yet' — when a facility-phase episode has no journey", async () => {
    get.mockResolvedValue({
      traumaEpisodeId: "ep-4", episodeReference: "TEP-4", status: "OPEN", currentPhase: "RESUS",
      continuumLinked: false, timeline: [],
    });
    renderTl();
    await waitFor(() => expect(screen.getByTestId("continuum-link-status")).toHaveTextContent(/Not yet linked to a facility journey/i));
    // It must NOT read as linked.
    expect(screen.getByTestId("continuum-link-status")).not.toHaveTextContent(/Linked to the care continuum/i);
  });

  it("shows PREHOSPITAL when a link is not expected yet", async () => {
    get.mockResolvedValue({
      traumaEpisodeId: "ep-5", episodeReference: "TEP-5", status: "OPEN", currentPhase: "DISPATCH",
      continuumLinked: false, timeline: [],
    });
    renderTl();
    await waitFor(() => expect(screen.getByTestId("continuum-link-status")).toHaveTextContent(/Prehospital/i));
  });
});
