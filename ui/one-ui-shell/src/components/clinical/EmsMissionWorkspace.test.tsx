import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { EmsMissionWorkspace } from "./EmsMissionWorkspace";

const MID = "mission-1";

function renderWs() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <EmsMissionWorkspace missionId={MID} />
    </QueryClientProvider>,
  );
}

describe("EmsMissionWorkspace", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    post.mockResolvedValue({});
    // useEmsMission GET (mission), useEmsEpcr GET (epcr — none yet)
    get.mockImplementation((path: string) => {
      if (path.endsWith("/epcr")) return Promise.reject({ status: 404 });
      return Promise.resolve({ id: MID, missionReference: "EMS-1", state: "DISPATCHED", dispatchPriority: "CRITICAL" });
    });
  });

  it("shows the current state and offers the next legal transition", async () => {
    renderWs();
    await waitFor(() => expect(screen.getByText("DISPATCHED")).toBeInTheDocument());
    expect(screen.getByRole("button", { name: /Advance to ACKNOWLEDGED/i })).toBeInTheDocument();
  });

  it("advances the mission state via the real endpoint", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderWs();
    await waitFor(() => expect(screen.getByRole("button", { name: /Advance to ACKNOWLEDGED/i })).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Advance to ACKNOWLEDGED/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/daidzai/ems/missions/${MID}/advance`,
        expect.objectContaining({ toState: "ACKNOWLEDGED" }),
      ),
    );
  });

  it("opens an ePCR when none exists", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderWs();
    await waitFor(() => expect(screen.getByText(/No ePCR yet/i)).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Open ePCR/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/daidzai/ems/missions/${MID}/epcr`,
        expect.objectContaining({ primarySurvey: expect.objectContaining({ airway: "patent" }) }),
      ),
    );
  });
});
