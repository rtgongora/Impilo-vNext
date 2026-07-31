import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

import { ResuscitationWorkspace } from "./ResuscitationWorkspace";

const ACT = "11111111-1111-1111-1111-111111111111";
const EP = "22222222-2222-2222-2222-222222222222";

function renderWorkspace() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ResuscitationWorkspace activationId={ACT} traumaEpisodeId={EP} patientLabel="TEMP-ED-TR0" location="RESUS-1" />
    </QueryClientProvider>,
  );
}

describe("ResuscitationWorkspace", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
    // record GET, then phases / cpr-cycles / medications lists (all empty to start)
    get.mockResolvedValue({ data: [] });
    post.mockResolvedValue({ data: { id: "new" } });
  });

  it("renders the glanceable identity strip and an explained empty timeline", async () => {
    renderWorkspace();
    expect(screen.getByText("TEMP-ED-TR0")).toBeInTheDocument();
    expect(screen.getByText(/Resuscitation in progress/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/No resus events yet/i)).toBeInTheDocument());
  });

  it("logs a CPR cycle to the real cpr-cycles endpoint with the trauma-episode header", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /Log CPR cycle/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/resuscitation/${ACT}/cpr-cycles`,
        expect.objectContaining({ cycleNumber: 1, rhythm: "PEA" }),
        expect.objectContaining({ extraHeaders: { "X-Trauma-Episode-ID": EP } }),
      ),
    );
  });

  it("starts an ABCDE phase when a channel is tapped", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /A · Airway/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/resuscitation/${ACT}/phases`,
        expect.objectContaining({ phase: "AIRWAY" }),
        expect.anything(),
      ),
    );
  });

  it("records a quick-add medication to the medications endpoint", async () => {
    const user = (await import("@testing-library/user-event")).default.setup();
    renderWorkspace();
    await user.click(screen.getByRole("button", { name: /Adrenaline 1 mg/i }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        `/internal/v1/ed/resuscitation/${ACT}/medications`,
        expect.objectContaining({ name: "Adrenaline", dose: "1 mg", route: "IV" }),
        expect.anything(),
      ),
    );
  });
});
