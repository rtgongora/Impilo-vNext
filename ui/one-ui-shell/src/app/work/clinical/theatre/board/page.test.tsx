import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import TheatreReadinessBoardPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

vi.mock("@/components/AppLayout", () => ({ AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div> }));
vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (<div><h1>{title}</h1>{children}</div>),
}));
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({ NompiloContextualGuidance: () => <div data-testid="nompilo" /> }));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("TheatreReadinessBoardPage", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); });

  it("renders real board rows with state and a blocker resolve action", async () => {
    get.mockResolvedValueOnce({
      data: { rows: [
        { case: { episode_id: "e-1", patient_id: "CPID-1", procedure_name: "Cholecystectomy" },
          board: { board_state: "Blocked", blockers: [{ code: "RECOVERY_NOT_READY", message: "No recovery bed" }] } },
      ] },
    });
    post.mockResolvedValueOnce({});
    render(<TheatreReadinessBoardPage />);

    await waitFor(() => expect(screen.getByText("Cholecystectomy")).toBeInTheDocument());
    expect(screen.getByText("Blocked")).toBeInTheDocument();
    expect(get).toHaveBeenCalledWith("/internal/v1/theatre/readiness-board");

    await userEvent.click(screen.getByRole("button", { name: "Resolve" }));
    await waitFor(() => expect(post).toHaveBeenCalledWith(
      "/internal/v1/theatre/cases/e-1/resolve-blocker",
      { domain: "RECOVERY", action: "ROUTE_TO_OWNER" }));
  });

  it("shows an honest empty state", async () => {
    get.mockResolvedValueOnce({ data: { rows: [] } });
    render(<TheatreReadinessBoardPage />);
    await waitFor(() => expect(screen.getByText(/No theatre cases on the board/)).toBeInTheDocument());
  });

  it("surfaces a real error", async () => {
    get.mockRejectedValueOnce({ error: { message: "Board service unavailable" } });
    render(<TheatreReadinessBoardPage />);
    await waitFor(() => expect(screen.getByText(/Board service unavailable/)).toBeInTheDocument());
  });
});
