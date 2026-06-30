import type { ReactNode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import DaidzaiDisastersPage from "./page";

const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));

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
vi.mock("@/components/intelligent/NompiloContextualGuidance", () => ({
  NompiloContextualGuidance: () => <div data-testid="nompilo" />,
}));
vi.mock("@/lib/api-client", () => ({ apiClient: { get, post } }));

describe("DaidzaiDisastersPage", () => {
  beforeEach(() => {
    get.mockReset();
    post.mockReset();
  });

  it("renders real disasters and a working close action", async () => {
    get.mockResolvedValue([
      {
        id: "d-1",
        incidentReference: "INC-20260630-FLD",
        incidentType: "DISASTER",
        emergencyCategory: "FLOOD",
        severity: "HIGH",
        status: "RESPONDING",
        title: "Tokwe flooding",
        commandPost: "Chivi DDC",
      },
    ]);
    post.mockResolvedValue({ status: "CLOSED" });
    const user = userEvent.setup();
    render(<DaidzaiDisastersPage />);
    await waitFor(() => expect(screen.getByText("Tokwe flooding")).toBeInTheDocument());

    const closeBtn = screen.getByRole("button", { name: /Close \+ after-action/ });
    await user.click(closeBtn);
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith("/internal/v1/daidzai/disasters/d-1/close", {})
    );
  });

  it("declare button posts a real disaster", async () => {
    get.mockResolvedValue([]);
    post.mockResolvedValue({ id: "d-2" });
    const user = userEvent.setup();
    render(<DaidzaiDisastersPage />);
    await waitFor(() => expect(screen.getByText(/No active disasters/)).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /Declare disaster/ }));
    await user.click(screen.getByRole("button", { name: /^Declare$/ }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        "/internal/v1/daidzai/disasters",
        expect.objectContaining({ incidentType: "DISASTER" })
      )
    );
  });
});
