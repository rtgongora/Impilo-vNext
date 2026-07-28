import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MciCasualtiesPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/lib/api-client", () => ({
  apiClient: { get, post },
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

const { facilityState } = vi.hoisted(() => ({ facilityState: { facility: { id: "fac-1", name: "Test Facility" } } }));

vi.mock("@/hooks/useFacilityStore", () => ({
  useFacilityStore: (selector: (s: typeof facilityState) => unknown) => selector(facilityState),
}));

const CASUALTY = {
  id: "c-1",
  casualtyReference: "C-001",
  sieveCategory: "IMMEDIATE",
  status: "ON_SCENE",
  subjectIdentityMode: "UNKNOWN",
  subjectCpid: null,
  emergencyEpisodeId: null,
  taggedBy: "medic-1",
  taggedAt: "2026-07-28T10:00:00Z",
};

describe("/work/daidzai/disasters/[id]/casualties", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("shows an empty state when no casualties are tagged", async () => {
    get.mockResolvedValue([]);
    render(<MciCasualtiesPage params={{ id: "incident-1" }} />);
    expect(await screen.findByText(/no casualties tagged yet/i)).toBeInTheDocument();
  });

  it("lists a tagged casualty and flags it as not minted", async () => {
    get.mockResolvedValue([CASUALTY]);
    render(<MciCasualtiesPage params={{ id: "incident-1" }} />);
    expect(await screen.findByText("C-001")).toBeInTheDocument();
    expect(screen.getByText(/not minted/i)).toBeInTheDocument();
    expect(screen.getByText(/identity unresolved/i)).toBeInTheDocument();
  });

  it("tagging a casualty posts to the incident's casualties endpoint", async () => {
    get.mockResolvedValue([]);
    post.mockResolvedValue(CASUALTY);
    const user = userEvent.setup();
    render(<MciCasualtiesPage params={{ id: "incident-1" }} />);

    await screen.findByText(/no casualties tagged yet/i);
    await user.type(screen.getByPlaceholderText(/e.g. c-014/i), "C-002");
    await user.type(screen.getByLabelText(/tagged by/i), "medic-2");
    await user.click(screen.getByRole("button", { name: /tag casualty/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/daidzai/disasters/incident-1/casualties",
        expect.objectContaining({ casualtyReference: "C-002", taggedBy: "medic-2" }),
      ),
    );
  });

  it("bulk-mint calls the pct bulk-mint endpoint with the current facility id and reports the count", async () => {
    get.mockResolvedValue([CASUALTY]);
    post.mockResolvedValue({ data: [{ created: true }, { created: false }] });
    const user = userEvent.setup();
    render(<MciCasualtiesPage params={{ id: "incident-1" }} />);

    await screen.findByText("C-001");
    await user.click(screen.getByRole("button", { name: /bulk-mint episodes/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/emergency-episodes/mci/incident-1/bulk-mint?facilityId=fac-1",
        {},
      ),
    );
    expect(await screen.findByText(/1 new emergency episode\(s\) minted/i)).toBeInTheDocument();
  });
});
