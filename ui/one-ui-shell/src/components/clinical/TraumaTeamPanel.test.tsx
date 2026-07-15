import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { TraumaTeamPanel } from "./TraumaTeamPanel";

const TID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

const ROSTER = [
  { id: "m1", workforce_profile_id: "wp-1", role: "TRAUMA_SURGEON", ack_status: "PENDING" },
  { id: "m2", workforce_profile_id: "wp-2", role: "ANAESTHETIST", ack_status: "ACKED" },
];

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <TraumaTeamPanel traumaId={TID} visitId="v1" actorId="PROV-1" />
    </QueryClientProvider>,
  );
}

describe("TraumaTeamPanel", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    get.mockResolvedValue({ data: ROSTER });
    post.mockResolvedValue({ data: {} });
  });

  it("renders the resolved on-call roster with ack states", async () => {
    renderPanel();
    await waitFor(() => expect(screen.getByText("TRAUMA SURGEON")).toBeInTheDocument());
    expect(screen.getByText("ANAESTHETIST")).toBeInTheDocument();
    expect(screen.getByText("ACKED")).toBeInTheDocument();
  });

  it("acknowledges a pending member via the real ack endpoint", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByText("TRAUMA SURGEON")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Acknowledge/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/trauma/${TID}/team/m1/ack`,
        expect.objectContaining({ ackBy: "PROV-1" }),
      ),
    );
  });

  it("escalates no-ack members", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderPanel();
    await waitFor(() => expect(screen.getByText("TRAUMA SURGEON")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /Escalate no-ack/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/trauma/${TID}/team/escalate`,
        expect.objectContaining({ timeoutSeconds: 0 }),
      ),
    );
  });
});
